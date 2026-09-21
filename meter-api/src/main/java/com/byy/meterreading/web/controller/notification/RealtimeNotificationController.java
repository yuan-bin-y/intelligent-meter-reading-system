package com.byy.meterreading.web.controller.notification;

import com.byy.meterreading.realtime.service.RealtimeNotificationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 当前用户的 SSE 实时通知连接。 */
@RestController
@RequestMapping("/api/v1/realtime")
public class RealtimeNotificationController {

    private final RealtimeNotificationService realtimeService;

    public RealtimeNotificationController(
            RealtimeNotificationService realtimeService
    ) {
        this.realtimeService = realtimeService;
    }

    @GetMapping(
            value = "/notifications",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> subscribe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(
                    value = "Last-Event-ID",
                    required = false
            ) String lastEventId
    ) {
        SseEmitter emitter = realtimeService.subscribe(
                extractUserId(jwt),
                lastEventId
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
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
