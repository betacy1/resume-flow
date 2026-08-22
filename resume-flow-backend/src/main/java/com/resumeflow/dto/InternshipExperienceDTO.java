package com.resumeflow.dto;

import lombok.Data;

/**
 * 实习经历 DTO
 */
@Data
public class InternshipExperienceDTO {

    private Long id;
    private String company;
    private String department;
    private String position;
    private String startDate;
    private String endDate;
    private String techStack;
    private String highlights;
    private Boolean isDefault;
    private String shortName;
    private String description;
    private Integer sortOrder;
}
