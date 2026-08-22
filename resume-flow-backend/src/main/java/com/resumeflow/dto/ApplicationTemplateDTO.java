package com.resumeflow.dto;

import lombok.Data;

/**
 * 岗位模板 DTO
 */
@Data
public class ApplicationTemplateDTO {

    private Long id;
    private String name;
    /** 受众类型：big_tech / state_owned / bank / general_backend */
    private String audienceType;
    /** 适用场景说明 */
    private String description;
    private String category;
    private String selfEvaluation;
    private String internshipDescription;
    private String projectDescription;
    private String careerPlan;
    private String aiCollaboration;
    private String skillKeywords;
    private Boolean isDefault;
}
