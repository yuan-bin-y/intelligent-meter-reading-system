package com.byy.meterreading.web.controller.notification;

import com.byy.meterreading.common.result.Result;
import com.byy.meterreading.dto.notification.NotificationPageQueryDTO;
import com.byy.meterreading.service.NotificationService;
import com.byy.meterreading.vo.common.PageVO;
import com.byy.meterreading.vo.notification.NotificationUnreadCountVO;
import com.byy.meterreading.vo.notification.NotificationVO;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已登录用户的通知列表、未读数量和已读操作接口。 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(
            NotificationService notificationService
    ) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<PageVO<NotificationVO>> list(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute NotificationPageQueryDTO queryDTO
    ) {
        return Result.success(notificationService.list(
                extractUserId(jwt),
                queryDTO
        ));
    }

    @GetMapping("/unread-count")
    public Result<NotificationUnreadCountVO> getUnreadCount(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return Result.success(notificationService.getUnreadCount(
                extractUserId(jwt)
        ));
    }

    @PutMapping("/{notificationId}/read")
    public Result<NotificationVO> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long notificationId
    ) {
        return Result.success(notificationService.markRead(
                extractUserId(jwt),
                notificationId
        ));
    }

    @PutMapping("/read-all")
    public Result<NotificationUnreadCountVO> markAllRead(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return Result.success(notificationService.markAllRead(
                extractUserId(jwt)
        ));
    }

    private Long extractUserId(Jwt jwt) {
        Object userIdClaim = jwt.getClaim("userId");
        if (!(userIdClaim instanceof Number userId)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "JWT中缺少有效的userId"
            );
        }
        return userId.longValue();
    }
}
