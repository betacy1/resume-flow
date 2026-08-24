package com.resumeflow.dto;

import lombok.Data;

/**
 * 技能信息 DTO
 */
@Data
public class SkillProfileDTO {

    private Long id;
    /** 技能字段 key，如 skill_backend */
    private String skillKey;
    private String skillName;
    private String level;
    private String category;
    /** 技能详细描述内容 */
    private String content;
    private Integer sortOrder;
}
