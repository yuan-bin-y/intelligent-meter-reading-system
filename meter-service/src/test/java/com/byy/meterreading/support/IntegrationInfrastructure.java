package com.byy.meterreading.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.byy.meterreading.mapper.DeviceMapper;
import com.byy.meterreading.mapper.MqOutboxEventMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.core.io.ClassPathResource;

/** 只接受独立测试库；不读取开发者本地凭据，也不清理其他库和Redis Key。 */
public final class IntegrationInfrastructure {
    public final DriverManagerDataSource dataSource;
    public final JdbcTemplate jdbc;
    public final DeviceMapper devices;
    public final MqOutboxEventMapper outbox;
    public final LettuceConnectionFactory redisConnection;
    public final StringRedisTemplate redis;

    public IntegrationInfrastructure() throws Exception {
        String url = System.getenv().getOrDefault("METER_IT_MYSQL_URL",
                "jdbc:mysql://127.0.0.1:13306/meter_hardening?allowPublicKeyRetrieval=true&useSSL=false");
        if (!url.contains("/meter_hardening?")) {
            throw new IllegalArgumentException("集成测试只能使用meter_hardening隔离库");
        }
        dataSource = new DriverManagerDataSource(url, "root", "test-only-root");
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:../meter-api/src/main/resources/db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        var configuration = new MybatisConfiguration();
        configuration.addMapper(DeviceMapper.class);
        configuration.addMapper(MqOutboxEventMapper.class);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/MqOutboxEventMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        devices = new SqlSessionTemplate(sessionFactory).getMapper(DeviceMapper.class);
        outbox = new SqlSessionTemplate(sessionFactory).getMapper(MqOutboxEventMapper.class);
        redisConnection = new LettuceConnectionFactory("127.0.0.1",
                Integer.parseInt(System.getenv().getOrDefault("METER_IT_REDIS_PORT", "16389")));
        redisConnection.afterPropertiesSet();
        redis = new StringRedisTemplate(redisConnection);
    }

    public void close() {
        redisConnection.destroy();
    }
}
