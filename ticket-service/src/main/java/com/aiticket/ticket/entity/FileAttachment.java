package com.aiticket.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 交付物 —— 接单者点「去完成」时传上来的那个压缩包。
 *
 * <p>表 {@code file_attachment} 在 {@code db/schema.sql} 里早就建好了，但此前
 * **一行 Java 都没有**（实体、仓库、接口全无）。本次「去完成要传文件」把它接上。
 *
 * <p><b>刻意不继承 {@code BaseEntity}</b>：那张表只有 {@code created_at}，没有
 * {@code updated_at} / {@code deleted_at}。继承的话 {@code ddl-auto=update} 会往
 * 表上添两列没人写的字段；而交付物的语义本来就是"落盘即定稿"—— 要换就是删掉 OSS
 * 对象再插一行，软删这套在这里没有意义。
 *
 * <p>{@code fileName} 与 {@code filePath} 存的是<b>同一个 OSS key</b> 的两半：
 * 前者是对象名（最后一段），后者是完整 key。key 形如
 * {@code delivery/{ticketId}/{uuid}.{ext}} —— 带 ticketId 是为了出事时在 OSS 控制台
 * 一眼看出这包属于哪张单，带 uuid 是为了**同名文件重传不互相覆盖**（否则接单人
 * 第二次交的包会把第一次那份悄悄顶掉，而验收页列的还是两行）。
 */
@Data
@Entity
@Table(name = "file_attachment")
public class FileAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** 存储端对象名（OSS key 的最后一段） */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** 用户机器上的原始文件名，下载时还给他这个名字 */
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    /** 完整 OSS key，拼下载地址用 */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
