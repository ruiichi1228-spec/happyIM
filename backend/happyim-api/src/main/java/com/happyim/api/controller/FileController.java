package com.happyim.api.controller;

import com.happyim.common.util.ApiResponse;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.entity.User;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.security.LoginRequired;
import com.happyim.common.service.AvatarUrlResolver;
import com.happyim.common.service.FileStorageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorageService;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final MinioClient minioClient;
    private final AvatarUrlResolver avatarUrlResolver;

    @Value("${minio.bucket}")
    private String bucket;

    public FileController(FileStorageService fileStorageService, JwtUtil jwtUtil, UserMapper userMapper,
                          MinioClient minioClient, AvatarUrlResolver avatarUrlResolver) {
        this.fileStorageService = fileStorageService;
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.minioClient = minioClient;
        this.avatarUrlResolver = avatarUrlResolver;
    }

    @PostMapping("/upload-avatar")
    @LoginRequired
    public ApiResponse<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                          HttpServletRequest request) {
        Long userId = getUserId(request);
        try {
            String path = fileStorageService.uploadAvatar(file, userId);
            User user = userMapper.findById(userId);
            if (user != null) {
                user.setAvatarUrl(path);
                userMapper.updateProfile(user);
            }
            return ApiResponse.success(Map.of("url", path));
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "头像上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/upload")
    @LoginRequired
    public ApiResponse<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String path = fileStorageService.uploadFile(file, "images");
            return ApiResponse.success(Map.of("url", path));
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 头像回显代理。通过后端从 MinIO 读取并返回，避免 403。
     * 访问路径: GET /api/files/avatar/{userId}
     */
    @GetMapping("/avatar/{userId}")
    public void getAvatar(@PathVariable Long userId, HttpServletResponse response) {
        try {
            User user = userMapper.findById(userId);
            if (user == null || user.getAvatarUrl() == null) {
                response.setStatus(404);
                return;
            }

            // 从存储路径提取 MinIO object name
            String avatarPath = user.getAvatarUrl();
            String objectName = avatarPath;
            if (avatarPath.contains(bucket + "/")) {
                objectName = avatarPath.substring(avatarPath.indexOf(bucket + "/") + bucket.length() + 1);
            }

            // 获取文件信息
            var stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(objectName).build());

            response.setContentType(stat.contentType());
            response.setContentLengthLong(stat.size());

            try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(objectName).build());
                 OutputStream os = response.getOutputStream()) {
                is.transferTo(os);
            }
        } catch (Exception e) {
            response.setStatus(404);
        }
    }

    /**
     * 通用文件下载代理。路径: /api/files/download/** → MinIO object
     */
    @GetMapping("/download/**")
    public void downloadFile(HttpServletRequest request, HttpServletResponse response) {
        String fullPath = request.getRequestURI();
        String objectName = fullPath.substring("/api/files/download/".length());
        try {
            var stat = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
            long fileSize = stat.size();
            String contentType = stat.contentType();
            String rangeHeader = request.getHeader("Range");

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                // Range 请求（视频拖进度条）
                String rangeValue = rangeHeader.substring(6);
                String[] parts = rangeValue.split("-");
                long start = Long.parseLong(parts[0]);
                long end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : fileSize - 1;
                if (end >= fileSize) end = fileSize - 1;
                long contentLength = end - start + 1;

                response.setStatus(206);
                response.setContentType(contentType);
                response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
                response.setContentLengthLong(contentLength);
                response.setHeader("Accept-Ranges", "bytes");

                try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucket).object(objectName).offset(start).length(contentLength).build());
                     OutputStream os = response.getOutputStream()) {
                    is.transferTo(os);
                }
            } else {
                response.setContentType(contentType);
                response.setContentLengthLong(fileSize);
                response.setHeader("Accept-Ranges", "bytes");
                try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucket).object(objectName).build());
                     OutputStream os = response.getOutputStream()) {
                    is.transferTo(os);
                }
            }
        } catch (Exception e) {
            response.setStatus(404);
        }
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return jwtUtil.getUserId(header.substring(7));
        }
        throw new BizException(ErrorCode.NOT_LOGIN);
    }
}
