package com.byy.meterreading.auth.service;

import com.byy.meterreading.auth.security.CustomUserDetails;
import com.byy.meterreading.auth.service.impl.AuthServiceImpl;
import com.byy.meterreading.auth.token.IssuedTokenPair;
import com.byy.meterreading.auth.token.JwtTokenService;
import com.byy.meterreading.auth.token.RedisAuthSessionService;
import com.byy.meterreading.dto.auth.ChangePasswordDTO;
import com.byy.meterreading.dto.auth.LoginDTO;
import com.byy.meterreading.dto.auth.RefreshTokenDTO;
import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {
    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final RedisAuthSessionService sessions = mock(RedisAuthSessionService.class);
    private final RedisAuthProtectionService protection = mock(RedisAuthProtectionService.class);
    private final SysUserService users = mock(SysUserService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(manager, tokens, decoder,
                sessions, protection, users, encoder);
    }

    @Test
    void loginCreatesSessionForFullRefreshLifetime() {
        CustomUserDetails principal = new CustomUserDetails(
                7L, "reader", null, "抄表员", 1, List.of("METER_READER"));
        when(manager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        when(tokens.issue(principal)).thenReturn(
                new IssuedTokenPair("access", "refresh", "sid", "jti", 7200, 604800));
        var result = service.login(new LoginDTO("reader", "password"));
        assertEquals("access", result.accessToken());
        assertEquals("refresh", result.refreshToken());
        verify(sessions).activate(7L, "sid", "jti", Duration.ofDays(7));
        verify(protection).clearLoginFailures("reader");
    }

    @Test
    void failedLoginIncrementsFailureCounterWithoutCreatingSession() {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("bad password"));
        assertThrows(BadCredentialsException.class,
                () -> service.login(new LoginDTO("reader", "wrong")));
        verify(protection).recordLoginFailure("reader");
        verifyNoInteractions(tokens, sessions);
    }

    @Test
    void changingPasswordStoresHashAndRevokesEverySession() {
        when(users.findById(7L)).thenReturn(SysUser.builder().id(7L).status(1)
                .passwordHash(encoder.encode("old-password")).build());
        when(users.updatePasswordHash(eq(7L), any(String.class), any(LocalDateTime.class)))
                .thenReturn(1);
        service.changePassword(7L, new ChangePasswordDTO("old-password", "new-password"));
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(users).updatePasswordHash(eq(7L), hash.capture(), any(LocalDateTime.class));
        assertTrue(encoder.matches("new-password", hash.getValue()));
        verify(sessions).revokeAll(7L);
    }

    @Test
    void wrongOldPasswordCannotUpdateHashOrRevokeSessions() {
        when(users.findById(7L)).thenReturn(SysUser.builder().id(7L).status(1)
                .passwordHash(encoder.encode("old-password")).build());
        assertThrows(IllegalArgumentException.class, () -> service.changePassword(
                7L, new ChangePasswordDTO("wrong", "new-password")));
        verify(users, never()).updatePasswordHash(any(), any(), any());
        verifyNoInteractions(sessions);
    }

    @Test
    void reusedRefreshTokenRevokesTheCompromisedSession() {
        Jwt jwt = Jwt.withTokenValue("refresh").header("alg", "HS256")
                .claim("userId", 7L).claim("sid", "sid").claim("jti", "old-jti").build();
        when(decoder.decode("refresh")).thenReturn(jwt);
        when(users.findById(7L)).thenReturn(SysUser.builder()
                .id(7L).username("reader").displayName("抄表员").status(1).build());
        when(users.findRoleCodesByUserId(7L)).thenReturn(List.of("METER_READER"));
        when(tokens.rotate(any(CustomUserDetails.class), eq("sid"))).thenReturn(
                new IssuedTokenPair("access", "new-refresh", "sid", "new-jti", 7200, 604800));
        when(sessions.rotateRefreshToken(7L, "sid", "old-jti", "new-jti", Duration.ofDays(7)))
                .thenReturn(false);
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> service.refresh(new RefreshTokenDTO("refresh")));
        verify(sessions).revoke(7L, "sid");
    }
}
