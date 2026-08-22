package com.resumeflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserCustomFieldDTO {

    private Long id;
    private Long templateId;
    private String fieldKey;
    private String fieldName;
    private String fieldType;
    private String fieldCategory;
    private String fieldValue;
    private List<String> matchKeywords;
    /** 来源引用，如 internship:1 / project:3 / material:5 */
    private String sourceRef;
    private Boolean sensitive;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime updateTime;
}
