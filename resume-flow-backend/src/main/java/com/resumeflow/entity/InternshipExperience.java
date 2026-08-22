package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 实习经历表 internship_experience
 */
@Entity
@Table(name = "internship_experience")
@Data
@EqualsAndHashCode(callSuper = true)
public class InternshipExperience extends BaseEntity {

    @Column(length = 100)
    private String company;

    /** 部门 */
    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String position;

    @Column(name = "start_date", length = 20)
    private String startDate;

    @Column(name = "end_date", length = 20)
    private String endDate;

    @Column(name = "tech_stack", length = 500)
    private String techStack;

    /** 亮点概述 */
    @Column(name = "highlights", columnDefinition = "TEXT")
    private String highlights;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "short_name", length = 50)
    private String shortName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
