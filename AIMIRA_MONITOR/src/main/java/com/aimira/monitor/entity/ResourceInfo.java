package com.aimira.monitor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 资源基础信息（重点关注到期时间）
 */
@Entity
@Table(name = "resource_info", indexes = {
        @Index(name = "idx_res_type", columnList = "resourceType"),
        @Index(name = "idx_res_expire_time", columnList = "expireTime"),
        @Index(name = "idx_res_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 阿里云 Resource ID */
    @Column(nullable = false, length = 128)
    private String resourceId;

    /** 资源名称 */
    @Column(length = 256)
    private String resourceName;

    /** 资源类型 (ECS, RDS, SLB, etc.) */
    @Column(nullable = false, length = 64)
    private String resourceType;

    /** 地域 */
    @Column(nullable = false, length = 64)
    private String region;

    /** 状态 (Running, Stopped, etc.) */
    @Column(length = 32)
    private String status;

    /** 到期时间 */
    private LocalDateTime expireTime;

    /** 数据同步时间 */
    @Column(nullable = false)
    private LocalDateTime syncTime;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
