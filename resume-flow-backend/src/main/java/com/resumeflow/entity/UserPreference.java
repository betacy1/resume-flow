package com.resumeflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户偏好表 user_preference
 * 保存插件侧使用偏好（JSON）：最近使用记录、收藏内容、站点默认模板/岗位方向。
 * 每个用户一行，content 为 JSON 文本；不影响简历数据版本号。
 */
@Entity
@Table(name = "user_preference")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPreference extends BaseEntity {

    /** 偏好内容（JSON）：recentUsed / favoriteFieldIds / favoriteMaterialIds / sitePrefs */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
}
