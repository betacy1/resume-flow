package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 投递信息表 application_record（秋招投递记录 / Application Tracker）
 */
@Entity
@Table(name = "application_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class ApplicationRecord extends BaseEntity {

    /** 批次，例如 2027秋招、2026暑期实习 */
    @Column(name = "batch_name", length = 50)
    private String batchName;

    /** 来源类型：企业、体制、基金、互联网、银行、券商、国央企、其他 */
    @Column(name = "source_type", length = 30)
    private String sourceType;

    /** 类别：北京定向选调、北京优培一类、特招、新华社、北京市考 */
    @Column(name = "category_type", length = 50)
    private String categoryType;

    /** 公司 / 单位名称 */
    @Column(name = "company_name", length = 200)
    private String companyName;

    /** 机构 / 部门 / 分支机构 */
    @Column(name = "organization_name", length = 200)
    private String organizationName;

    /** 岗位名称 */
    @Column(name = "position_name", length = 200)
    private String positionName;

    /** 岗位方向：后端、AI、金融科技、算法、管培、产品、其他 */
    @Column(name = "position_direction", length = 50)
    private String positionDirection;

    /** 企业性质：民营、公募基金、国有控股公募基金、民营互联网、银行、券商、国央企等 */
    @Column(name = "company_nature", length = 100)
    private String companyNature;

    /** 投递状态：未投/准备中/已投/已截止/简历挂/测评/笔试/一面/二面/三面/HR面/终面/offer/已拒/已放弃/待确认/其他 */
    @Column(name = "apply_status", length = 30)
    private String applyStatus;

    /** 当前阶段 */
    @Column(name = "current_stage", length = 50)
    private String currentStage;

    /** 优先级 */
    @Column(length = 30)
    private String priority;

    /** 工作城市 */
    @Column(length = 50)
    private String city;

    /** 投递渠道：官网、公众号、内推、第三方系统、手动添加、插件采集、Excel初始化、Excel导入 */
    @Column(name = "application_channel", length = 50)
    private String applicationChannel;

    /** 官网 */
    @Column(name = "official_website", length = 500)
    private String officialWebsite;

    /** 公众号 */
    @Column(name = "public_account", length = 100)
    private String publicAccount;

    /** 招聘官网 / 招聘系统链接 */
    @Column(name = "recruitment_url", length = 500)
    private String recruitmentUrl;

    /** 实际投递链接 */
    @Column(name = "application_url", length = 500)
    private String applicationUrl;

    /** 写简历 / 编辑简历的网址（插件自动记录） */
    @Column(name = "resume_edit_url", length = 500)
    private String resumeEditUrl;

    /** 插件采集到的当前页面网址 */
    @Column(name = "page_url", length = 500)
    private String pageUrl;

    /** 插件采集到的页面标题 */
    @Column(name = "page_title", length = 300)
    private String pageTitle;

    /** 域名 */
    @Column(length = 200)
    private String domain;

    /** 简历修改时间（插件尽量自动采集） */
    @Column(name = "resume_modified_at")
    private LocalDateTime resumeModifiedAt;

    /** 简历修改时间来源：page_text / save_action / detected_time / manual */
    @Column(name = "resume_modified_source", length = 30)
    private String resumeModifiedSource;

    /** 简历修改时间说明 */
    @Column(name = "resume_modified_remark", length = 300)
    private String resumeModifiedRemark;

    /** 首次被插件采集时间 */
    @Column(name = "first_detected_at")
    private LocalDateTime firstDetectedAt;

    /** 最近访问时间 */
    @Column(name = "last_visited_at")
    private LocalDateTime lastVisitedAt;

    /** 实际投递时间 */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    /** 截止时间，可为空 */
    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    /** 备注 */
    @Column(length = 1000)
    private String remark;

    /** 限制说明 */
    @Column(name = "warning_note", length = 500)
    private String warningNote;

    /** 最近一次插件采集置信度 */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    /** 公司/机构/岗位名称是否被用户手动编辑过（插件低置信度采集不覆盖） */
    @Column(name = "name_manually_edited", nullable = false)
    private Boolean nameManuallyEdited = false;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "enabled")
    private Boolean enabled = true;
}
