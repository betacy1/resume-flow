package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 紧急联系人表 emergency_contact（每个用户默认的标准字段结构，与家庭成员分别独立维护）
 */
@Entity
@Table(name = "emergency_contact")
@Data
@EqualsAndHashCode(callSuper = true)
public class EmergencyContact extends BaseEntity {

    @Column(length = 50)
    private String name;

    /** 与本人关系：母亲/父亲/配偶/朋友等 */
    @Column(length = 50)
    private String relation;

    @Column(length = 30)
    private String phone;

    /** 工作单位 */
    @Column(length = 200)
    private String company;

    /** 职务 */
    @Column(length = 100)
    private String position;

    @Column(length = 300)
    private String address;

    @Column(length = 300)
    private String remark;

    @Column(name = "enabled")
    private Boolean enabled = true;
}
