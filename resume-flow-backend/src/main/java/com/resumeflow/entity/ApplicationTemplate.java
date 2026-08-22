package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位模板表 application_template
 * 用户可创建多个岗位版本（后端开发版、AI 应用版、金融科技版、国企央企版、自定义）
 * 每个版本可绑定不同的自我评价、实习描述、项目描述、职业规划、AI 协作经历、技能关键词
 */
@Entity
@Table(name = "application_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class ApplicationTemplate extends BaseEntity {

    @Column(length = 100)
    private String name;

    /** 受众类型：big_tech / state_owned / bank / general_backend */
    @Column(name = "audience_type", length = 30)
    private String audienceType;

    /** 适用场景说明 */
    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String category;

    @Column(name = "self_evaluation", columnDefinition = "TEXT")
    private String selfEvaluation;

    @Column(name = "internship_description", columnDefinition = "TEXT")
    private String internshipDescription;

    @Column(name = "project_description", columnDefinition = "TEXT")
    private String projectDescription;

    @Column(name = "career_plan", columnDefinition = "TEXT")
    private String careerPlan;

    @Column(name = "ai_collaboration", columnDefinition = "TEXT")
    private String aiCollaboration;

    @Column(name = "skill_keywords", columnDefinition = "TEXT")
    private String skillKeywords;

    @Column(name = "is_default")
    private Boolean isDefault = false;
}
