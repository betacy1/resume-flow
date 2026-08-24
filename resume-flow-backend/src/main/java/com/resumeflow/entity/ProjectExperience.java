package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目经历表 project_experience
 */
@Entity
@Table(name = "project_experience")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectExperience extends BaseEntity {

    @Column(length = 200)
    private String projectName;

    @Column(length = 100)
    private String role;

    @Column(name = "start_date", length = 20)
    private String startDate;

    @Column(name = "end_date", length = 20)
    private String endDate;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 项目简介 */
    @Column(name = "project_intro", columnDefinition = "TEXT")
    private String projectIntro;

    /** 职责描述 */
    @Column(name = "responsibilities", columnDefinition = "TEXT")
    private String responsibilities;

    /** 项目成果 */
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "tech_stack", length = 500)
    private String techStack;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    /** 排除的受众场景（逗号分隔，如 big_tech），该场景下不会选用此项目 */
    @Column(name = "audience_exclude", length = 100)
    private String audienceExclude;
}
