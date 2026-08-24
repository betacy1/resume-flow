package com.resumeflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 插件采集投递信息请求（POST /api/application-records/capture）
 */
@Data
public class ApplicationCaptureRequest {

    private String companyName;
    private String organizationName;
    private String positionName;
    private String pageUrl;
    private String pageTitle;
    private String domain;
    private String recruitmentUrl;
    private String resumeEditUrl;
    private LocalDateTime resumeModifiedAt;
    /** 简历修改时间来源：page_text / save_action / detected_time / manual */
    private String resumeModifiedSource;
    private LocalDateTime detectedAt;
    /** 采集来源，固定 plugin */
    private String source;
    /** 识别置信度 0-1，低于 0.6 且未确认时不自动入库 */
    private Double confidenceScore;
    /** 用户在插件中确认后强制保存（低置信度也入库） */
    private Boolean confirmed;
}
