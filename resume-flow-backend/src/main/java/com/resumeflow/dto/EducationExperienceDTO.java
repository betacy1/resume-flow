package com.resumeflow.dto;

import lombok.Data;

/**
 * 教育经历 DTO
 */
@Data
public class EducationExperienceDTO {

    private Long id;
    private String school;
    private String schoolTags;
    private String major;
    private String degree;
    private String college;
    private String startDate;
    private String endDate;
    private String gpa;
    private String rank;
    private String advisor;
    private String lab;
    private String researchDirection;
    private String thesis;
    private String honors;
    private Boolean isDefault;
    private String description;
    private Integer sortOrder;
}
