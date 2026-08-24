package com.resumeflow.dto;

import lombok.Data;

/**
 * 模板-经历关系配置 DTO
 */
@Data
public class TemplateExperienceConfigDTO {

    private Long id;

    private Long templateId;

    /** internship / project */
    private String sourceType;

    private Long sourceId;

    /** 经历名称（查询时回填，便于前端展示；保存时忽略） */
    private String sourceName;

    private Boolean includedInResume;

    private Boolean autoFillEnabled;

    private Integer autoFillPriority;

    private Boolean manualSelectable;

    private String emphasisTags;

    private Integer displayOrder;
}
