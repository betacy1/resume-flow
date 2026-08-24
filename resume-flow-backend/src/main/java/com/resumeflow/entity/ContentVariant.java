package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内容版本表 content_variant
 * 模板 = 场景风格 × 岗位方向 × 字段类型 × 字数限制：
 * audienceType: big_tech（大厂）/ state_owned（国央企）/ bank（银行）/ general（通用）
 * jobDirection: backend（后端开发）/ ai（AI 应用工程化）/ fintech（金融科技）/ general（通用）
 * fieldType: internship_overview / internship_responsibility / internship_result /
 *            internship_tech_stack / internship_combined（项目/素材为 project_* / combined）
 * lengthType: within_200 / within_300 / within_500 / within_1000
 */
@Entity
@Table(name = "content_variant")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentVariant extends BaseEntity {

    /** 来源类型：internship / project / material */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    /** 受众类型：big_tech / state_owned / bank / general */
    @Column(name = "audience_type", nullable = false, length = 20)
    private String audienceType;

    /** 岗位方向：backend / ai / fintech / general */
    @Column(name = "job_direction", length = 20)
    private String jobDirection;

    /** 字段类型：internship_overview / internship_responsibility / internship_result / internship_tech_stack / internship_combined / combined */
    @Column(name = "field_type", length = 40)
    private String fieldType;

    /** 长度类型：within_200 / within_300 / within_500 / within_1000 */
    @Column(name = "length_type", nullable = false, length = 20)
    private String lengthType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
