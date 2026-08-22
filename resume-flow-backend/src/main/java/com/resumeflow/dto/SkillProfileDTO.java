package com.resumeflow.dto;

import lombok.Data;

/**
 * 技能信息 DTO
 */
@Data
public class SkillProfileDTO {

    private Long id;
    private String skillName;
    private String level;
    private String category;
    private Integer sortOrder;
}
