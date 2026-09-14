package com.byy.meterreading.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

public final class CustomUserDetails implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private String passwordHash;
    private final String displayName;
    private final Integer status;
    private final List<String> roles;

    public CustomUserDetails(Long userId,
                             String username,
                             String passwordHash,
                             String displayName,
                             Integer status,
                             List<String> roles) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = status;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Integer getStatus() {
        return status;
    }

    public List<String> getRoles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Integer.valueOf(1).equals(status);
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
