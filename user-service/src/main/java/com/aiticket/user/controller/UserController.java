package com.aiticket.user.controller;

import com.aiticket.common.dto.PageResult;
import com.aiticket.common.dto.Result;
import com.aiticket.common.enums.Role;
import com.aiticket.user.dto.UpdateSkillRequest;
import com.aiticket.user.dto.UserDTO;
import com.aiticket.user.dto.UserSkillDTO;
import com.aiticket.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
