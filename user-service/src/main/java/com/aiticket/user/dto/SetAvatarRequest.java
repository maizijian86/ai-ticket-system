package com.aiticket.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 「换一个默认头像」的请求体。
 *
 * <p>刻意**不叫 UpdateAvatarRequest**：改头像有两条路 —— 传自己的图（multipart，
 * 见 UserController 的 POST /me/avatar）和挑一个默认头像（就是这个类）。
 * 名字里带 set/default 便于在控制器里一眼分清。
 */
@Data
public class SetAvatarRequest {

    /**
     * 形如 {@code "default:1"} ~ {@code "default:9"}。
     *
     * <p>只收这一个形态：**不接受任意 URL**。否则这就是一个 SSRF/存储投毒的口子 ——
     * 客户端能把 avatar_url 写成任何地址，前端 img 会去请求它，别的用户看到的头像
     * 也就成了攻击者指定的内容。合法取值由 UserService.isDefaultAvatar 逐个校验。
     */
    @NotBlank(message = "头像标识不能为空")
    private String avatarUrl;
}
