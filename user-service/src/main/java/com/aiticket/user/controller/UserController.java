package com.aiticket.user.controller;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.dto.Result;
import com.aiticket.common.enums.Role;
import com.aiticket.user.dto.SetAvatarRequest;
import com.aiticket.user.dto.UpdateSkillRequest;
import com.aiticket.user.dto.UserAvatarDTO;
import com.aiticket.user.dto.UserDTO;
import com.aiticket.user.dto.UserSkillDTO;
import com.aiticket.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserDTO> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @GetMapping
    public Result<PageResult<UserDTO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Role role) {
        return Result.success(userService.listUsers(page, pageSize, role));
    }

    @PutMapping("/{id}")
    public Result<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO dto) {
        return Result.success(userService.updateUser(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    // ==================== 头像 ====================
    //
    // 两条路都走 /me，**路径里不带 id**，身份取自网关注入的 X-User-Id（与
    // AuthController.logout 同一套取法）。带 id 的话任何人都能改别人的头像 ——
    // 网关只校验"是不是登录了"，不校验"这个 id 是不是你"。

    /**
     * 上传自定义头像。multipart，字段名固定为 {@code file}。
     *
     * <p>前端会先把图裁成 1:1、压到 200×200 再传，所以正常只有几十 KB；
     * 服务端仍有 2MB 与 MIME 白名单两道校验，因为**前端的压缩不可信**。
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserDTO> uploadAvatar(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam("file") MultipartFile file) {
        return Result.success(userService.updateAvatar(userId, file));
    }

    /**
     * 换成一个默认头像，形如 {@code {"avatarUrl":"default:3"}}。
     * 前端把 9 个默认头像做成一个九宫格，"随机换一个"也走这个接口。
     */
    @PutMapping("/me/avatar")
    public Result<UserDTO> setDefaultAvatar(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody SetAvatarRequest request) {
        return Result.success(userService.setDefaultAvatar(userId, request.getAvatarUrl()));
    }

    /**
     * 匿名可读：按用户名查头像，登录卡"边输边显示头像"用。
     *
     * <p>这条路径在网关白名单与 SecurityConfig 里都放行匿名 GET —— 它服务的就是
     * "还没登录"的人。**查不到返回 {@code data:null} 而不是 404**：前端拦截器会把
     * 非 200 的业务码弹成 toast，边打字边弹"用户不存在"等于把正常输入变成报错现场。
     */
    @GetMapping("/avatar")
    public Result<UserAvatarDTO> avatarByUsername(@RequestParam String username) {
        return Result.success(userService.getAvatarByUsername(username));
    }

    /**
     * 匿名可读：批量取头像，{@code ids=1,2,3}（大厅一页卡片的发布者）。
     *
     * <p>一次请求解掉 N+1。返回 id → 头像地址，不存在的 id 不出现在结果里。
     */
    @GetMapping("/avatars")
    public Result<Map<Long, String>> avatars(@RequestParam List<Long> ids) {
        return Result.success(userService.getAvatars(ids));
    }

    @GetMapping("/{id}/skill")
    public Result<UserSkillDTO> getUserSkill(@PathVariable Long id) {
        return Result.success(userService.getUserSkill(id));
    }

    @PutMapping("/{id}/skills")
    public Result<UserSkillDTO> updateUserSkill(@PathVariable Long id, @Valid @RequestBody UpdateSkillRequest request) {
        request.setUserId(id);
        return Result.success(userService.updateUserSkill(request));
    }

    @GetMapping("/handlers")
    public Result<List<UserDTO>> listHandlers() {
        return Result.success(userService.listHandlers());
    }

    @GetMapping("/handlers/available")
    public Result<List<UserSkillDTO>> listAvailableHandlers() {
        return Result.success(userService.listAvailableHandlers());
    }
}
