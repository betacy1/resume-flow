package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 家庭成员表 family_member（每个用户默认的标准字段结构，普通用户注册后内容为空）
 */
@Entity
@Table(name = "family_member")
@Data
@EqualsAndHashCode(callSuper = true)
public class FamilyMember extends BaseEntity {

    /** 与本人关系：父亲/母亲/配偶/其他 */
    @Column(length = 50)
    private String relation;

    @Column(length = 50)
    private String name;

    /** 工作单位 */
    @Column(length = 200)
    private String company;

    /** 职务 */
    @Column(length = 100)
    private String position;

    @Column(length = 30)
    private String phone;

    @Column(length = 100)
    private String email;

    /** 政治面貌 */
    @Column(name = "political_status", length = 50)
    private String politicalStatus;

    @Column(length = 300)
    private String address;

    @Column(length = 300)
    private String remark;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "enabled")
    private Boolean enabled = true;
}
