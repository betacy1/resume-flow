package com.resumeflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户自定义字段表 user_custom_field
 */
@Entity
@Table(name = "user_custom_field")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserCustomField extends BaseEntity {

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "field_type", nullable = false, length = 30)
    private String fieldType;

    @Column(name = "field_category", length = 50)
    private String fieldCategory;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;

    @Column(name = "match_keywords", columnDefinition = "TEXT")
    private String matchKeywords;

    /** 适用模板 id 列表（JSON 数组）；为空表示全部模板适用 */
    @Column(name = "template_ids", length = 200)
    private String templateIds;

    /** 字数档位：within_100 / within_200 / within_300 / within_500 / within_1000 / full */
    @Column(name = "length_type", length = 30)
    private String lengthType;

    /** 是否参与一键自动填充 */
    @Column(name = "auto_fill_enabled", nullable = false, columnDefinition = "boolean default true")
    private Boolean autoFillEnabled = true;

    /** 是否允许插件手动点选填充 */
    @Column(name = "manual_fill_enabled", nullable = false, columnDefinition = "boolean default true")
    private Boolean manualFillEnabled = true;

    /** 乐观锁版本号（手动维护）：插件保存时提交，落后于服务端则 409 冲突 */
    @Column(name = "version", nullable = false, columnDefinition = "bigint default 0")
    private Long version = 0L;

    /** 来源引用，格式 sourceType:sourceId，如 internship:1 / project:3 / material:5，用于选择内容版本与日期 */
    @Column(name = "source_ref", length = 50)
    private String sourceRef;

    // sensitive 是 MySQL 8.0.17+ 保留字，列名必须加反引号转义，否则自动建表/validate 报语法错误。
    // 已废弃：敏感字段概念已移除（个人自用场景），数据库列保留但恒为 false，业务逻辑不再依赖。
    @Column(name = "`sensitive`", nullable = false)
    private Boolean sensitive = false;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
