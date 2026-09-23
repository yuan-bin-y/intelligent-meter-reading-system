package com.byy.meterreading.cache;

import com.byy.meterreading.vo.device.DeviceDetailVO;
import com.byy.meterreading.vo.meter.MeterDetailVO;
import com.byy.meterreading.vo.user.AssignableRoleVO;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 使用 Cache Aside 模式管理低频修改、高频读取的业务详情缓存。
 *
 * <p>空值标记防止不存在的 ID 持续查询 MySQL，随机 TTL 降低同时过期风险，
 * Redisson 分布式锁保证多实例中只有一个请求负责重建同一个热点缓存。</p>
 */
@Service
public class BusinessCacheService {

    private static final Logger log = LoggerFactory.getLogger(
            BusinessCacheService.class
    );

    private static final String NULL_VALUE = "__NULL__";
    private static final String METER_DETAIL_PREFIX =
            "meter:cache:meter:detail:";
    private static final String DEVICE_DETAIL_PREFIX =
            "meter:cache:device:detail:";
    private static final String ROLE_LIST_KEY =
            "meter:cache:role:assignable";
    private static final String LOCK_PREFIX = "meter:lock:cache:";

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final BusinessCacheProperties properties;

    public BusinessCacheService(
            StringRedisTemplate redisTemplate,
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            BusinessCacheProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public MeterDetailVO getMeterDetail(
            Long meterId,
            Supplier<MeterDetailVO> loader
    ) {
        return getOrLoad(
                METER_DETAIL_PREFIX + meterId,
                MeterDetailVO.class,
                loader
        );
    }

    public DeviceDetailVO getDeviceDetail(
            Long deviceId,
            Supplier<DeviceDetailVO> loader
    ) {
        return getOrLoad(
                DEVICE_DETAIL_PREFIX + deviceId,
                DeviceDetailVO.class,
                loader
        );
    }

    public List<AssignableRoleVO> getAssignableRoles(
            Supplier<List<AssignableRoleVO>> loader
    ) {
        JavaType listType = objectMapper.getTypeFactory()
                .constructCollectionType(
                        List.class,
                        AssignableRoleVO.class
                );
        List<AssignableRoleVO> roles = getOrLoad(
                ROLE_LIST_KEY,
                listType,
                loader
        );
        return roles == null ? List.of() : List.copyOf(roles);
    }

    public void evictMeterDetailAfterCommit(Long meterId) {
        evictAfterCommit(METER_DETAIL_PREFIX + meterId);
    }

    public void evictDeviceDetailAfterCommit(Long deviceId) {
        evictAfterCommit(DEVICE_DETAIL_PREFIX + deviceId);
    }

    public void evictAssignableRolesAfterCommit() {
        evictAfterCommit(ROLE_LIST_KEY);
    }

    private <T> T getOrLoad(
            String cacheKey,
            Class<T> resultType,
            Supplier<T> loader
    ) {
        return getOrLoad(
                cacheKey,
                objectMapper.getTypeFactory().constructType(resultType),
                loader
        );
    }

    private <T> T getOrLoad(
            String cacheKey,
            JavaType resultType,
            Supplier<T> loader
    ) {
        if (!properties.isEnabled()) {
            return loader.get();
        }

        CacheLookup<T> firstLookup = read(cacheKey, resultType);
        if (firstLookup.redisAvailable() && firstLookup.hit()) {
            return firstLookup.value();
        }
        if (!firstLookup.redisAvailable()) {
            return loader.get();
        }

        RLock lock;
        try {
            lock = redissonClient.getLock(LOCK_PREFIX + cacheKey);
        } catch (RuntimeException exception) {
            log.warn("获取业务缓存重建锁失败，已回退 MySQL，key={}",
                    cacheKey, exception);
            return loader.get();
        }

        boolean locked = false;
        try {
            locked = lock.tryLock(
                    properties.getLockWait().toMillis(),
                    properties.getLockLease().toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!locked) {
                // 锁等待超时后再读一次；仍未命中时直查数据库，保证接口可用。
                CacheLookup<T> retryLookup = read(cacheKey, resultType);
                return retryLookup.hit()
                        ? retryLookup.value()
                        : loader.get();
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("等待业务缓存重建锁时线程被中断，key={}", cacheKey);
            return loader.get();
        } catch (RuntimeException exception) {
            log.warn("获取业务缓存重建锁失败，已回退 MySQL，key={}",
                    cacheKey, exception);
            return loader.get();
        }

        try {
            // 获得锁后必须二次检查，避免等待期间前一个请求已经完成重建。
            CacheLookup<T> secondLookup = read(cacheKey, resultType);
            if (secondLookup.hit()) {
                return secondLookup.value();
            }

            // 数据库查询异常必须原样抛出，不能被当成缓存异常后重复查询。
            T loaded = loader.get();
            write(cacheKey, loaded);
            return loaded;
        } finally {
            unlockSafely(lock, locked, cacheKey);
        }
    }

    private <T> CacheLookup<T> read(
            String cacheKey,
            JavaType resultType
    ) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                return CacheLookup.miss();
            }
            if (NULL_VALUE.equals(cached)) {
                return CacheLookup.hit(null);
            }
            return CacheLookup.hit(
                    objectMapper.readValue(cached, resultType)
            );
        } catch (RuntimeException exception) {
            log.warn("读取业务缓存失败，key={}", cacheKey, exception);
            return CacheLookup.unavailable();
        }
    }

    private void write(String cacheKey, Object value) {
        try {
            if (value == null) {
                redisTemplate.opsForValue().set(
                        cacheKey,
                        NULL_VALUE,
                        properties.getNullTtl()
                );
                return;
            }
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(value),
                    randomizedTtl()
            );
        } catch (RuntimeException exception) {
            // 数据已经从 MySQL 查询成功，缓存写入失败只记录日志。
            log.warn("写入业务缓存失败，key={}", cacheKey, exception);
        }
    }

    /**
     * 有事务时等待提交成功再删缓存；无事务时立即删除。
     */
    private void evictAfterCommit(String cacheKey) {
        if (!properties.isEnabled()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager
                .isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            evict(cacheKey);
                        }
                    }
            );
            return;
        }
        evict(cacheKey);
    }

    private void evict(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (RuntimeException exception) {
            // MySQL 已成功提交，缓存删除失败依靠较短 TTL 最终恢复一致。
            log.warn("删除业务缓存失败，key={}", cacheKey, exception);
        }
    }

    private Duration randomizedTtl() {
        long jitterBound = properties.getTtlJitter().toMillis();
        long jitter = jitterBound == 0
                ? 0
                : ThreadLocalRandom.current().nextLong(jitterBound + 1);
        return properties.getTtl().plusMillis(jitter);
    }

    private void unlockSafely(
            RLock lock,
            boolean locked,
            String cacheKey
    ) {
        if (!locked) {
            return;
        }
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException exception) {
            log.warn("释放业务缓存重建锁失败，key={}", cacheKey, exception);
        }
    }

    private record CacheLookup<T>(
            boolean redisAvailable,
            boolean hit,
            T value
    ) {

        static <T> CacheLookup<T> hit(T value) {
            return new CacheLookup<>(true, true, value);
        }

        static <T> CacheLookup<T> miss() {
            return new CacheLookup<>(true, false, null);
        }

        static <T> CacheLookup<T> unavailable() {
            return new CacheLookup<>(false, false, null);
        }
    }
}
