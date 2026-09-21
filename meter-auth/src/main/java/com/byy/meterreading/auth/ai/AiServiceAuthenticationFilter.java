package com.byy.meterreading.auth.ai;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * AI 内部回调接口认证过滤器，读取签名请求头并将认证交给 Provider 完成。
 */
@Component
public class AiServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String SERVICE_ID_HEADER = "X-AI-Service-Id";
    public static final String TIMESTAMP_HEADER = "X-AI-Timestamp";
    public static final String NONCE_HEADER = "X-AI-Nonce";
    public static final String SIGNATURE_HEADER = "X-AI-Signature";

    private static final String AI_INTERNAL_API_PREFIX =
            "/api/v1/internal/ai/";

    private final AuthenticationManager authenticationManager;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AiServiceProperties properties;

    public AiServiceAuthenticationFilter(
            AuthenticationManager authenticationManager,
            AuthenticationEntryPoint authenticationEntryPoint,
            AiServiceProperties properties
    ) {
        this.authenticationManager = authenticationManager;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String serviceId = requireHeader(request, SERVICE_ID_HEADER);
            String timestamp = requireHeader(request, TIMESTAMP_HEADER);
            String nonce = requireHeader(request, NONCE_HEADER);
            String signature = requireHeader(request, SIGNATURE_HEADER);

            byte[] requestBody = readRequestBody(request);
            CachedBodyRequest wrappedRequest = new CachedBodyRequest(
                    request,
                    requestBody
            );
            String requestPath = extractRequestPath(request);
            String bodySha256 = sha256Hex(requestBody);

            AiServiceRequestCredentials credentials =
                    new AiServiceRequestCredentials(
                            timestamp,
                            nonce,
                            signature,
                            request.getMethod().toUpperCase(Locale.ROOT),
                            requestPath,
                            bodySha256
                    );
            AiServiceAuthenticationToken authenticationRequest =
                    AiServiceAuthenticationToken.unauthenticated(
                            serviceId,
                            credentials
                    );
            authenticationRequest.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request)
            );

            Authentication authenticated = authenticationManager.authenticate(
                    authenticationRequest
            );
            SecurityContext context = SecurityContextHolder
                    .createEmptyContext();
            context.setAuthentication(authenticated);
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(wrappedRequest, response);
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, exception);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !extractRequestPath(request).startsWith(
                AI_INTERNAL_API_PREFIX
        );
    }

    private String requireHeader(
            HttpServletRequest request,
            String headerName
    ) {
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value)) {
            throw new BadCredentialsException(
                    "缺少AI服务认证请求头：" + headerName
            );
        }
        return value.trim();
    }

    private byte[] readRequestBody(HttpServletRequest request)
            throws IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > properties.maxBodySize()) {
            throw new BadCredentialsException("AI服务请求体过大");
        }

        byte[] body = request.getInputStream().readNBytes(
                properties.maxBodySize() + 1
        );
        if (body.length > properties.maxBodySize()) {
            throw new BadCredentialsException("AI服务请求体过大");
        }
        return body;
    }

    private String extractRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty()
                ? requestUri
                : requestUri.substring(contextPath.length());
    }

    private String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JDK不支持SHA-256",
                    exception
            );
        }
    }

    /** 认证时读取请求体后，为 Controller 提供一份可再次读取的副本。 */
    private static final class CachedBodyRequest
            extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(
                HttpServletRequest request,
                byte[] body
        ) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ByteArrayServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(encoding);
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), charset)
            );
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class ByteArrayServletInputStream
            extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        private ByteArrayServletInputStream(byte[] body) {
            this.inputStream = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return inputStream.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            try {
                if (!isFinished()) {
                    readListener.onDataAvailable();
                }
                if (isFinished()) {
                    readListener.onAllDataRead();
                }
            } catch (IOException exception) {
                readListener.onError(exception);
            }
        }
    }
}
