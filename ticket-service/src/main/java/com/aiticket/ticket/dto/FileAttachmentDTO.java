package com.aiticket.ticket.dto;

import java.time.LocalDateTime;

/**
 * 一个交付物（接单者「去完成」时传上来的压缩包），给验收页列出并下载用。
 *
 * <p>{@code url} 是 OSS 的**公网地址**（bucket 公共读，直接拼出来的，不带签名）。
 * 也就是说拿到这个字符串的任何人都能下载 —— 所以这个 DTO **只能发给该工单的
 * 发布者或接单人**，出口在 {@code TicketService.deliveryFiles} 的参与者门禁上。
 * 别把它塞进 {@code TicketDTO}：那个 DTO 会随**匿名可读**的详情接口一起返回。
 *
 * @param id           附件行 id
 * @param originalName 上传时用户机器上的文件名（`需求交付.zip`），下载时显示这个，
 *                     而不是 OSS 上那个带 uuid 的对象名
 * @param fileSize     字节数，可能为 null（历史行）
 * @param mimeType     上传时浏览器报的类型，仅供展示
 * @param url          OSS 公网地址
 * @param uploadedBy   上传者 id
 * @param createdAt    上传时间
 */
public record FileAttachmentDTO(Long id,
                                String originalName,
                                Long fileSize,
                                String mimeType,
                                String url,
                                Long uploadedBy,
                                LocalDateTime createdAt) {
}
