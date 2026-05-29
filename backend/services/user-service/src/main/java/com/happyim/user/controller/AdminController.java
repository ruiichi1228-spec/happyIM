package com.happyim.user.controller;

import com.happyim.user.service.AdminService;
import com.happyim.common.security.AdminRequired;
import com.happyim.common.util.ApiResponse;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostConstruct
    public void init() {
        adminService.ensureDefaultAdmin();
    }

    // ==================== 登录 ====================

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        Map<String, Object> result = adminService.login(body.get("username"), body.get("password"));
        return ApiResponse.success(result);
    }

    // ==================== 仪表盘 ====================

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(adminService.dashboard());
    }

    // ==================== 用户管理 ====================

    @AdminRequired
    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> listUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(adminService.listUsers(keyword, page, pageSize));
    }

    @AdminRequired
    @GetMapping("/users/{id}")
    public ApiResponse<?> getUserDetail(@PathVariable Long id) {
        return ApiResponse.success(adminService.getUserDetail(id));
    }

    @AdminRequired
    @PutMapping("/users/{id}/status")
    public ApiResponse<?> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminService.updateUserStatus(id, body.get("status"));
        return ApiResponse.message("success");
    }

    // ==================== 群组管理 ====================

    @AdminRequired
    @GetMapping("/groups")
    public ApiResponse<Map<String, Object>> listGroups(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(adminService.listGroups(keyword, page, pageSize));
    }

    @AdminRequired
    @GetMapping("/groups/{id}/members")
    public ApiResponse<?> getGroupMembers(@PathVariable Long id) {
        return ApiResponse.success(adminService.getGroupMembers(id));
    }

    @AdminRequired
    @PutMapping("/groups/{id}/dissolve")
    public ApiResponse<?> dissolveGroup(@PathVariable Long id) {
        adminService.dissolveGroup(id);
        return ApiResponse.message("success");
    }

    // ==================== 敏感词管理 ====================

    @AdminRequired
    @GetMapping("/sensitive-words")
    public ApiResponse<?> listSensitiveWords() {
        return ApiResponse.success(adminService.listSensitiveWords());
    }

    @AdminRequired
    @PostMapping("/sensitive-words")
    public ApiResponse<?> addSensitiveWord(@RequestBody Map<String, String> body) {
        adminService.addSensitiveWord(body.get("word"));
        return ApiResponse.message("success");
    }

    @AdminRequired
    @DeleteMapping("/sensitive-words/{id}")
    public ApiResponse<?> deleteSensitiveWord(@PathVariable Long id) {
        adminService.deleteSensitiveWord(id);
        return ApiResponse.message("success");
    }

    // ==================== 系统公告 ====================

    @GetMapping("/announcements")
    public ApiResponse<?> listAnnouncements() {
        return ApiResponse.success(adminService.listAnnouncements());
    }

    @AdminRequired
    @PostMapping("/announcements")
    public ApiResponse<?> publishAnnouncement(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("adminId");
        adminService.publishAnnouncement(body.get("content"), adminId != null ? adminId : 0L);
        return ApiResponse.message("公告已发送");
    }

    @AdminRequired
    @DeleteMapping("/announcements/{id}")
    public ApiResponse<?> deleteAnnouncement(@PathVariable Long id) {
        adminService.deleteAnnouncement(id);
        return ApiResponse.message("success");
    }

    // ==================== 文件管理 ====================

    @AdminRequired
    @GetMapping("/files")
    public ApiResponse<?> listFiles(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "all") String fileType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(adminService.listFiles(keyword, fileType, page, pageSize));
    }

    @AdminRequired
    @GetMapping("/files/stats")
    public ApiResponse<?> fileStats() {
        return ApiResponse.success(adminService.fileStats());
    }

    @AdminRequired
    @DeleteMapping("/files/{messageId}")
    public ApiResponse<?> deleteFile(@PathVariable String messageId) {
        adminService.deleteFile(messageId);
        return ApiResponse.message("success");
    }
}
