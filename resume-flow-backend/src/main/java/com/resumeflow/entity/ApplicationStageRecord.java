package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 投递流程记录表 application_stage_record（笔试、一面、二面、offer 等阶段）
 */
@Entity
@Table(name = "application_stage_record")
@Data
@EqualsAndHashCode(callSuper = true)
public class ApplicationStageRecord extends BaseEntity {

    @Column(name = "application_record_id", nullable = false)
    private Long applicationRecordId;

    /** 阶段名称：初筛、测评、笔试、一面、二面、终面、offer */
    @Column(name = "stage_name", length = 50)
    private String stageName;

    /** 阶段状态：待开始、已完成、通过、未通过、放弃、未参加 */
    @Column(name = "stage_status", length = 30)
    private String stageStatus;

    /** 结果：通过、挂、拒、offer */
    @Column(name = "stage_result", length = 30)
    private String stageResult;

    /** 时间 */
    @Column(name = "stage_time")
    private LocalDateTime stageTime;

    /** 备注 */
    @Column(length = 500)
    private String note;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
