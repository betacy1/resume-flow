package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 素材库表 answer_material
 * 维护常用开放题答案：自我评价、AI 协作项目、实习经历、项目经历、
 * 职业规划、为什么选择本公司、为什么选择本岗位、兴趣特长、补充信息
 */
@Entity
@Table(name = "answer_material")
@Data
@EqualsAndHashCode(callSuper = true)
public class AnswerMaterial extends BaseEntity {

    @Column(length = 100)
    private String title;

    /**
     * 素材类型：SELF_EVALUATION / AI_COLLABORATION / INTERNSHIP /
     * PROJECT / CAREER_PLAN / WHY_COMPANY / WHY_POSITION /
     * HOBBY / SUPPLEMENT
     */
    @Column(length = 50)
    private String materialType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "word_limit_type", length = 30)
    private String wordLimitType;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
