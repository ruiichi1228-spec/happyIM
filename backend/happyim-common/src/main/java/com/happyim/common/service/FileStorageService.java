package com.happyim.common.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public FileStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * 上传头像。生成两份：
     * - 原图:  avatars/{userId}.{ext}
     * - 缩略图: avatars/{userId}_thumb.{ext}
     *
     * @return 返回存储的相对路径，不包含 MinIO endpoint
     */
    public String uploadAvatar(MultipartFile file, Long userId) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String ext = "jpg";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }

        String objectName = "avatars/" + userId + "." + ext;
        String thumbObjectName = "avatars/" + userId + "_thumb." + ext;

        // 上传原图
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        // 生成并上传缩略图 (200x200)
        try (InputStream is = file.getInputStream()) {
            BufferedImage original = ImageIO.read(is);
            if (original != null) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                Thumbnails.of(original)
                        .size(200, 200)
                        .outputFormat(ext)
                        .toOutputStream(bos);
                byte[] thumbBytes = bos.toByteArray();

                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(thumbObjectName)
                        .stream(new ByteArrayInputStream(thumbBytes), thumbBytes.length, -1)
                        .contentType("image/" + ext)
                        .build());
                log.info("缩略图已生成: {}", thumbObjectName);
            }
        } catch (Exception e) {
            log.warn("缩略图生成失败，跳过: {}", e.getMessage());
        }

        log.info("头像上传成功: {}", objectName);
        // 返回不带 bucket 前缀的路径，解析时由策略拼接
        return bucket + "/" + objectName;
    }

    /**
     * 通用文件上传
     */
    public String uploadFile(MultipartFile file, String folder) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String objectName = folder + "/" + UUID.randomUUID().toString() + ext;

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        return bucket + "/" + objectName;
    }
}
