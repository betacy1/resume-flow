package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模板-经历关系配置表 template_experience_config
 * 控制某模板下各实习/项目经历的可见性与自动填充策略（不删除任何经历与内容版本）：
 * includedInResume 是否在该模板最终简历中展示；
 * autoFillEnabled 是否参与一键自动填充；
 * autoFillPriority 自动填充优先级（数值越小越优先）；
 * manualSelectable 是否允许用户手动选择填入；
 * emphasisTags 该模板下的侧重点标签；
 * displayOrder 展示顺序。
 */
@Entity
@Table(name = "template_experience_config")
@Data
@EqualsAndHashCode(callSuper = true)
public class TemplateExperienceConfig extends BaseEntity {

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    /** 来源类型：internship / project */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "included_in_resume", nullable = false)
    private Boolean includedInResume = true;

    @Column(name = "auto_fill_enabled", nullable = false)
    private Boolean autoFillEnabled = true;

    @Column(name = "auto_fill_priority")
    private Integer autoFillPriority;

    @Column(name = "manual_selectable", nullable = false)
    private Boolean manualSelectable = true;

    /** 该模板下的侧重点标签（逗号分隔） */
    @Column(name = "emphasis_tags", length = 512)
    private String emphasisTags;

    @Column(name = "display_order")
    private Integer displayOrder;
}
