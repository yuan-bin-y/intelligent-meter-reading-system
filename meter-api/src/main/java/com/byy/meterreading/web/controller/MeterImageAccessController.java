package com.byy.meterreading.web.controller;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.service.MeterImageService;
import com.byy.meterreading.vo.meterimage.MeterImageAccessUrlVO;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

/** 为有业务访问权的登录用户签发短时 OSS 访问地址。 */
@RestController
@RequestMapping("/api/v1/meter-images")
public class MeterImageAccessController {

    private static final String ROLE_PREFIX = "ROLE_";

    private final MeterImageService meterImageService;

    public MeterImageAccessController(MeterImageService meterImageService) {
        this.meterImageService = meterImageService;
    }

    @GetMapping("/{imageId}/access-url")
    public Result<MeterImageAccessUrlVO> getAccessUrl(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable Long imageId
    ) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
        return Result.success(meterImageService.getAccessUrl(
                extractUserId(jwt), roles, imageId
        ));
    }

    private Long extractUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        if (!(userIdClaim instanceof Number userId)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "JWT 中缺少有效的 userId"
            );
        }
        return userId.longValue();
    }
}
