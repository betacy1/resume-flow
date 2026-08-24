package com.resumeflow.dto;

import com.resumeflow.entity.ApplicationStageRecord;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递流程记录 DTO
 */
@Data
public class ApplicationStageRecordDTO {

    private Long id;
    private Long applicationRecordId;
    private String stageName;
    private String stageStatus;
    private String stageResult;
    private LocalDateTime stageTime;
    private String note;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static ApplicationStageRecordDTO fromEntity(ApplicationStageRecord s) {
        ApplicationStageRecordDTO dto = new ApplicationStageRecordDTO();
        dto.setId(s.getId());
        dto.setApplicationRecordId(s.getApplicationRecordId());
        dto.setStageName(s.getStageName());
        dto.setStageStatus(s.getStageStatus());
        dto.setStageResult(s.getStageResult());
        dto.setStageTime(s.getStageTime());
        dto.setNote(s.getNote());
        dto.setSortOrder(s.getSortOrder());
        dto.setCreateTime(s.getCreateTime());
        dto.setUpdateTime(s.getUpdateTime());
        return dto;
    }

    public void applyTo(ApplicationStageRecord s) {
        s.setStageName(stageName);
        s.setStageStatus(stageStatus);
        s.setStageResult(stageResult);
        s.setStageTime(stageTime);
        s.setNote(note);
        if (sortOrder != null) {
            s.setSortOrder(sortOrder);
        }
    }
}
