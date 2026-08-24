package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 简历数据同步状态表 profile_sync_state
 * 记录每个用户简历数据的版本号与内容哈希，插件据此判断是否需要拉取最新数据
 */
@Entity
@Table(name = "profile_sync_state")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProfileSyncState extends BaseEntity {

    /** 数据版本号，每次用户数据变更 +1 */
    @Column(name = "profile_version", nullable = false)
    private Long profileVersion = 0L;

    /** 全部简历数据的内容哈希（SHA-256），读取同步状态时惰性重算 */
    @Column(name = "data_hash", length = 64)
    private String dataHash;
}
