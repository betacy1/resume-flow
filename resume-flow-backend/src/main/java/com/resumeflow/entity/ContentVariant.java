package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内容版本表 content_variant
 * 每条实习经历 / 项目经历 / 开放题素材可拥有多个版本：
 * audienceType: big_tech（大厂）/ state_owned（国央企）/ bank（银行）/ general（通用）
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

    /** 长度类型：within_200 / within_300 / within_500 / within_1000 */
    @Column(name = "length_type", nullable = false, length = 20)
    private String lengthType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
