package com.byy.meterreading.service.impl;

import com.byy.meterreading.config.OssProperties;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** 根据文件头识别图片类型，并计算尺寸和 SHA-256。 */
@Component
public class MeterImageFileInspector {

    private static final int MAX_IMAGE_DIMENSION = 12_000;

    private final OssProperties properties;

    public MeterImageFileInspector(OssProperties properties) {
        this.properties = properties;
    }

    public InspectedImage inspect(
            byte[] content,
            String declaredContentType
    ) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("上传图片不能为空");
        }
        if (content.length > properties.getMaxFileSize().toBytes()) {
            throw new IllegalArgumentException("上传图片超过允许的最大大小");
        }

        FileType type = detectType(content);
        validateDeclaredType(declaredContentType, type.contentType());
        Dimension dimension = readDimension(content, type);
        return new InspectedImage(
                type.contentType(),
                type.extension(),
                content.length,
                dimension.width(),
                dimension.height(),
                sha256(content)
        );
    }

    private FileType detectType(byte[] content) {
        if (content.length >= 3
                && unsigned(content[0]) == 0xFF
                && unsigned(content[1]) == 0xD8
                && unsigned(content[2]) == 0xFF) {
            return new FileType("image/jpeg", "jpg");
        }
        if (content.length >= 8
                && unsigned(content[0]) == 0x89
                && content[1] == 0x50
                && content[2] == 0x4E
                && content[3] == 0x47
                && content[4] == 0x0D
                && content[5] == 0x0A
                && content[6] == 0x1A
                && content[7] == 0x0A) {
            return new FileType("image/png", "png");
        }
        if (content.length >= 12
                && asciiEquals(content, 0, "RIFF")
                && asciiEquals(content, 8, "WEBP")) {
            return new FileType("image/webp", "webp");
        }
        throw new IllegalArgumentException(
                "仅支持 JPEG、PNG 和 WebP 图片"
        );
    }

    private void validateDeclaredType(
            String declaredContentType,
            String detectedContentType
    ) {
        if (declaredContentType == null || declaredContentType.isBlank()
                || "application/octet-stream".equalsIgnoreCase(
                declaredContentType)) {
            return;
        }
        String normalized = declaredContentType.toLowerCase(Locale.ROOT);
        if (!normalized.equals(detectedContentType)) {
            throw new IllegalArgumentException(
                    "文件声明类型与真实图片类型不一致"
            );
        }
    }

    private Dimension readDimension(byte[] content, FileType type) {
        if ("image/webp".equals(type.contentType())) {
            // JDK 默认 ImageIO 不保证支持 WebP，尺寸允许为空。
            return new Dimension(null, null);
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalArgumentException("图片内容已经损坏");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0
                    || width > MAX_IMAGE_DIMENSION
                    || height > MAX_IMAGE_DIMENSION) {
                throw new IllegalArgumentException("图片尺寸不合法或过大");
            }
            return new Dimension(width, height);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法读取图片尺寸", exception);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private boolean asciiEquals(byte[] content, int offset, String expected) {
        if (content.length < offset + expected.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if (content[offset + index] != (byte) expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    public record InspectedImage(
            String contentType,
            String extension,
            long fileSize,
            Integer width,
            Integer height,
            String sha256
    ) {
    }

    private record FileType(String contentType, String extension) {
    }

    private record Dimension(Integer width, Integer height) {
    }
}
