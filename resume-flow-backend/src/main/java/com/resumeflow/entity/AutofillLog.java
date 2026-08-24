package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自动填充日志表 autofill_log
 * 记录每次插件调用匹配接口的日志
 */
@Entity
@Table(name = "autofill_log")
@Data
@EqualsAndHashCode(callSuper = true)
public class AutofillLog extends BaseEntity {

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "page_url", length = 500)
    private String pageUrl;

    @Column(name = "page_title", length = 500)
    private String pageTitle;

    @Column(name = "total_fields")
    private Integer totalFields;

    @Column(name = "matched_count")
    private Integer matchedCount;

    @Column(name = "filled_count")
    private Integer filledCount;

    @Column(name = "skipped_count")
    private Integer skippedCount;

    @Column(name = "sensitive_count")
    private Integer sensitiveCount;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    /** 填充方式：auto 一键自动填充 / manual 手动点选填充 */
    @Column(name = "fill_type", length = 20)
    private String fillType;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;
}
