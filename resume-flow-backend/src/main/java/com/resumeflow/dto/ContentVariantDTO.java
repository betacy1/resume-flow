package com.resumeflow.dto;

import lombok.Data;

/**
 * 内容版本 DTO
 */
@Data
public class ContentVariantDTO {

    private Long id;
    /** internship / project / material */
    private String sourceType;
    private Long sourceId;
    /** big_tech / state_owned / bank / general */
    private String audienceType;
    /** backend / ai / fintech / general */
    private String jobDirection;
    /** internship_overview / internship_responsibility / internship_result / internship_tech_stack / internship_combined / combined */
    private String fieldType;
    /** within_200 / within_300 / within_500 / within_1000 */
    private String lengthType;
    private String content;
    private Boolean enabled;
}
