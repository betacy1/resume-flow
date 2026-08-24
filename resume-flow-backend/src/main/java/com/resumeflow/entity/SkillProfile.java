package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能信息表 skill_profile
 * 每条记录代表一个专业技能分组（后端开发、数据库与中间件、分布式与稳定性等）
 * skillKey 固定取值：skill_backend / skill_database_middleware / skill_distributed_stability /
 * skill_ai_engineering / skill_devops_platform / skill_frontend_tools / skill_computer_basic
 */
@Entity
@Table(name = "skill_profile")
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillProfile extends BaseEntity {

    /** 技能字段 key，如 skill_backend */
    @Column(name = "skill_key", length = 50)
    private String skillKey;

    /** 技能分组标题，如 后端开发 */
    @Column(length = 100)
    private String skillName;

    @Column(length = 50)
    private String level;

    @Column(name = "category", length = 50)
    private String category;

    /** 技能详细描述内容 */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
