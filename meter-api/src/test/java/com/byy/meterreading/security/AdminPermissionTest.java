package com.byy.meterreading.security;

import com.byy.meterreading.service.MeterService;
import com.byy.meterreading.web.controller.admin.AdminMeterController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminPermissionTest {
    private AnnotationConfigApplicationContext context;
    private AdminMeterController controller;
    private MeterService meters;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        controller = context.getBean(AdminMeterController.class);
        meters = context.getBean(MeterService.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void unauthenticatedRequestCannotReachBusinessService() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.getMeter(1L));
        verifyNoInteractions(meters);
    }

    @Test
    void residentCannotCallAdministratorController() {
        authenticate("RESIDENT");
        assertThrows(AccessDeniedException.class, () -> controller.getMeter(1L));
        verifyNoInteractions(meters);
    }

    @Test
    void administratorCanCallAdministratorController() {
        authenticate("ADMIN");
        assertDoesNotThrow(() -> controller.getMeter(1L));
        verify(meters).getMeter(1L);
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("tester", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfiguration {
        @Bean
        MeterService meters() {
            return mock(MeterService.class);
        }

        @Bean
        AdminMeterController adminMeters(MeterService meterService) {
            return new AdminMeterController(meterService);
        }
    }
}
