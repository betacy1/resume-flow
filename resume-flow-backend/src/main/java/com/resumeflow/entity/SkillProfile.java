package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 技能信息表 skill_profile
 * 每条记录代表一个技能标签或技能分组
 */
@Entity
@Table(name = "skill_profile")
@Data
@EqualsAndHashCode(callSuper = true)
public class SkillProfile extends BaseEntity {

    @Column(length = 100)
    private String skillName;

    @Column(length = 50)
    private String level;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
