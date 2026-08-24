package com.resumeflow.dto;

import com.resumeflow.entity.ApplicationRecord;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递记录 DTO（新增/编辑请求与列表响应共用）
 */
@Data
public class ApplicationRecordDTO {

    private Long id;
    private String batchName;
    private String sourceType;
    private String categoryType;
    private String companyName;
    private String organizationName;
    private String positionName;
    private String positionDirection;
    private String companyNature;
    private String applyStatus;
    private String currentStage;
    private String priority;
    private String city;
    private String applicationChannel;
    private String officialWebsite;
    private String publicAccount;
    private String recruitmentUrl;
    private String applicationUrl;
    private String resumeEditUrl;
    private String pageUrl;
    private String pageTitle;
    private String domain;
    private LocalDateTime resumeModifiedAt;
    private String resumeModifiedSource;
    private String resumeModifiedRemark;
    private LocalDateTime firstDetectedAt;
    private LocalDateTime lastVisitedAt;
    private LocalDateTime appliedAt;
    private LocalDateTime deadlineAt;
    private String remark;
    private String warningNote;
    private Double confidenceScore;
    private Boolean nameManuallyEdited;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static ApplicationRecordDTO fromEntity(ApplicationRecord r) {
        ApplicationRecordDTO dto = new ApplicationRecordDTO();
        dto.setId(r.getId());
        dto.setBatchName(r.getBatchName());
        dto.setSourceType(r.getSourceType());
        dto.setCategoryType(r.getCategoryType());
        dto.setCompanyName(r.getCompanyName());
        dto.setOrganizationName(r.getOrganizationName());
        dto.setPositionName(r.getPositionName());
        dto.setPositionDirection(r.getPositionDirection());
        dto.setCompanyNature(r.getCompanyNature());
        dto.setApplyStatus(r.getApplyStatus());
        dto.setCurrentStage(r.getCurrentStage());
        dto.setPriority(r.getPriority());
        dto.setCity(r.getCity());
        dto.setApplicationChannel(r.getApplicationChannel());
        dto.setOfficialWebsite(r.getOfficialWebsite());
        dto.setPublicAccount(r.getPublicAccount());
        dto.setRecruitmentUrl(r.getRecruitmentUrl());
        dto.setApplicationUrl(r.getApplicationUrl());
        dto.setResumeEditUrl(r.getResumeEditUrl());
        dto.setPageUrl(r.getPageUrl());
        dto.setPageTitle(r.getPageTitle());
        dto.setDomain(r.getDomain());
        dto.setResumeModifiedAt(r.getResumeModifiedAt());
        dto.setResumeModifiedSource(r.getResumeModifiedSource());
        dto.setResumeModifiedRemark(r.getResumeModifiedRemark());
        dto.setFirstDetectedAt(r.getFirstDetectedAt());
        dto.setLastVisitedAt(r.getLastVisitedAt());
        dto.setAppliedAt(r.getAppliedAt());
        dto.setDeadlineAt(r.getDeadlineAt());
        dto.setRemark(r.getRemark());
        dto.setWarningNote(r.getWarningNote());
        dto.setConfidenceScore(r.getConfidenceScore());
        dto.setNameManuallyEdited(r.getNameManuallyEdited());
        dto.setSortOrder(r.getSortOrder());
        dto.setEnabled(r.getEnabled());
        dto.setCreateTime(r.getCreateTime());
        dto.setUpdateTime(r.getUpdateTime());
        return dto;
    }

    /** 把请求字段写入实体（手动新增/编辑，名称字段视为用户手动编辑） */
    public void applyTo(ApplicationRecord r) {
        r.setBatchName(batchName);
        r.setSourceType(sourceType);
        r.setCategoryType(categoryType);
        if (companyName != null) {
            r.setCompanyName(companyName.trim());
        }
        if (organizationName != null) {
            r.setOrganizationName(organizationName.trim());
        }
        if (positionName != null) {
            r.setPositionName(positionName.trim());
        }
        r.setPositionDirection(positionDirection);
        r.setCompanyNature(companyNature);
        r.setApplyStatus(applyStatus);
        r.setCurrentStage(currentStage);
        r.setPriority(priority);
        r.setCity(city);
        r.setApplicationChannel(applicationChannel);
        r.setOfficialWebsite(officialWebsite);
        r.setPublicAccount(publicAccount);
        r.setRecruitmentUrl(recruitmentUrl);
        r.setApplicationUrl(applicationUrl);
        r.setResumeEditUrl(resumeEditUrl);
        r.setPageUrl(pageUrl);
        r.setPageTitle(pageTitle);
        r.setDomain(domain);
        r.setResumeModifiedAt(resumeModifiedAt);
        r.setResumeModifiedSource(resumeModifiedSource);
        r.setResumeModifiedRemark(resumeModifiedRemark);
        r.setAppliedAt(appliedAt);
        r.setDeadlineAt(deadlineAt);
        r.setRemark(remark);
        r.setWarningNote(warningNote);
        if (sortOrder != null) {
            r.setSortOrder(sortOrder);
        }
        if (enabled != null) {
            r.setEnabled(enabled);
        }
        r.setNameManuallyEdited(Boolean.TRUE);
    }
}
