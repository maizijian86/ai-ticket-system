package com.aiticket.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 匿名可读的头像信息 —— 登录页"输入用户名就显示头像"用。
 *
 * <p>刻意**不是** {@link UserDTO}：那个带 email、role、status、lastLoginAt，而这条接口
 * 在网关上是白名单（没有 token 也能调）。匿名能拿到的信息必须掐到最小 ——
 * 头像本来就是要给所有人看的，昵称也会出现在工单卡片上，除此之外一个字段都不多给。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAvatarDTO {
    private String username;
    private String nickname;
    /**
     * 与 UserDTO.avatarUrl 同构，但 OSS 对象 key 已换成可直接访问的 https 地址。
     * 未知用户时整个对象为 null（接口返回 {@code data: null}，不是 404）。
     */
    private String avatarUrl;
}
