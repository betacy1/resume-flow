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
    /** 排除的受众场景（逗号分隔，如 big_tech） */
    private String audienceExclude;
    /** 模板优先级 JSON，如 {"bank":1,"state_owned":2} */
    private String templatePriority;
}
