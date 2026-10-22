package com.byy.meterreading.auth.security;

import com.byy.meterreading.model.SysUser;
import com.byy.meterreading.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationTest {
    private final SysUserService users = mock(SysUserService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private ProviderManager manager;
    private SysUser user;

    @BeforeEach
    void setUp() {
        user = SysUser.builder().id(7L).username("reader")
                .passwordHash(encoder.encode("correct-password"))
                .displayName("抄表员").status(1).build();
        when(users.findByUsername("reader")).thenReturn(user);
        when(users.findRoleCodesByUserId(7L)).thenReturn(List.of("METER_READER"));
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(
                new CustomUserDetailsService(users));
        provider.setPasswordEncoder(encoder);
        manager = new ProviderManager(provider);
    }

    @Test
    void authenticatesBcryptAndErasesPasswordWhilePreservingRoles() {
        Authentication result = manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("reader", "correct-password"));
        CustomUserDetails principal = (CustomUserDetails) result.getPrincipal();
        assertTrue(result.isAuthenticated());
        assertEquals(7L, principal.getUserId());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_METER_READER")));
        assertTrue(result.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
        assertNull(principal.getPassword());
        assertNull(result.getCredentials());
    }

    @Test
    void rejectsWrongPassword() {
        assertThrows(BadCredentialsException.class, () -> manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("reader", "wrong-password")));
    }

    @Test
    void rejectsDisabledAccountEvenWithCorrectPassword() {
        user.setStatus(0);
        assertThrows(DisabledException.class, () -> manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("reader", "correct-password")));
    }
}
