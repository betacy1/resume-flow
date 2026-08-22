package com.resumeflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 素材库 DTO
 */
@Data
public class AnswerMaterialDTO {

    private Long id;
    private String title;
    private String materialType;
    private String content;
    private String shortName;
    private Long templateId;
    private String wordLimitType;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime updateTime;
}
