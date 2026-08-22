package com.resumeflow.dto;

import lombok.Data;

/**
 * 项目经历 DTO
 */
@Data
public class ProjectExperienceDTO {

    private Long id;
    private String projectName;
    private String role;
    private String startDate;
    private String endDate;
    private Boolean isDefault;
    private String shortName;
    private String description;
    private String projectIntro;
    private String responsibilities;
    private String result;
    private String techStack;
    private Integer sortOrder;
}
