package com.byy.meterreading.auth.config;

import com.byy.meterreading.auth.ai.AiServiceAuthenticationFilter;
import com.byy.meterreading.auth.ai.AiServiceAuthenticationProvider;
import com.byy.meterreading.auth.ai.AiServiceProperties;
import com.byy.meterreading.auth.device.DeviceAuthenticationFilter;
import com.byy.meterreading.auth.device.DeviceAuthenticationProvider;
import com.byy.meterreading.auth.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import static org.springframework.http.HttpMethod.POST;

/**
 * Spring Security 基础配置。
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
        AuthProtectionProperties.class,
        AiServiceProperties.class
})
public class SecurityConfig {

    /**
     * 创建 BCrypt 密码编码器，用于登录时比对明文密码和数据库密码哈希。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 创建数据库用户名密码认证器。
     * CustomUserDetailsService 负责查询用户，PasswordEncoder 负责校验 BCrypt 密码。
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * 创建统一认证管理器，注册用户、设备和 AI 服务三种认证器。
     * 登录流程及两个专用认证过滤器都会调用该对象的 authenticate 方法，
     * 认证管理器会按照认证 Token 类型选择对应的 Provider。
     */
    @Bean
    public AuthenticationManager authenticationManager(
            DaoAuthenticationProvider daoAuthenticationProvider,
            DeviceAuthenticationProvider deviceAuthenticationProvider,
            AiServiceAuthenticationProvider aiServiceAuthenticationProvider
    ) {
        return new ProviderManager(
                daoAuthenticationProvider,
                deviceAuthenticationProvider,
                aiServiceAuthenticationProvider
        );
    }

    /**
     * 将 JWT 的 roles 字段转换成 Spring Security 权限。
     * 例如 JWT 中的 ADMIN 会被转换为 ROLE_ADMIN。
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );
        return authenticationConverter;
    }

    /**
     * 配置接口访问规则和 JWT 鉴权流程。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider daoAuthenticationProvider,
            DeviceAuthenticationProvider deviceAuthenticationProvider,
            AiServiceAuthenticationProvider aiServiceAuthenticationProvider,
            DeviceAuthenticationFilter deviceAuthenticationFilter,
            AiServiceAuthenticationFilter aiServiceAuthenticationFilter,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                // REST API 使用 JWT，不需要 CSRF Token
                .csrf(AbstractHttpConfigurer::disable)

                // 不在服务端创建或保存登录 Session
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 注册用户名密码认证器，供登录流程使用
                .authenticationProvider(daoAuthenticationProvider)

                // 注册设备密钥认证器，供设备接口认证过滤器使用
                .authenticationProvider(deviceAuthenticationProvider)

                // 注册AI服务签名认证器，供内部识别回调过滤器使用
                .authenticationProvider(aiServiceAuthenticationProvider)

                // 登录、注册和刷新允许匿名访问；设备和AI接口使用各自身份
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/refresh"
                        ).permitAll()
                        .requestMatchers("/api/v1/device/**")
                        .hasRole("DEVICE")
                        .requestMatchers("/api/v1/internal/ai/**")
                        .hasRole("AI_SERVICE")
                        .anyRequest().authenticated())

                // 设备使用请求头密钥认证，应当在 Bearer JWT 过滤器之前完成
                .addFilterBefore(
                        deviceAuthenticationFilter,
                        BearerTokenAuthenticationFilter.class
                )

                // AI服务使用带时间戳和随机数的HMAC签名认证
                .addFilterBefore(
                        aiServiceAuthenticationFilter,
                        BearerTokenAuthenticationFilter.class
                )

                // 将未认证和无权限异常转换为项目统一响应结构
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                // 使用 JwtDecoder 校验 Bearer Token，并转换 roles 权限
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter)));

        return http.build();
    }
}
