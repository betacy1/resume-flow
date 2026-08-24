package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户简历基础信息表 user_profile
 * 存储姓名、手机、邮箱、学校、专业、学历、毕业时间、期望城市、期望岗位等
 */
@Entity
@Table(name = "user_profile")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserProfile extends BaseEntity {

    @Column(length = 50)
    private String name;

    /** 性别：男 / 女 */
    @Column(length = 10)
    private String gender;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 30)
    private String qq;

    @Column(length = 50)
    private String wechat;

    /** 当前所在地，如 中国大陆 / 北京 / 北京市 */
    @Column(name = "current_location", length = 100)
    private String currentLocation;

    /** 政治面貌 */
    @Column(name = "political_status", length = 50)
    private String politicalStatus;

    /** 身份证号（自动填写字段，按普通字段处理） */
    @Column(name = "id_card", length = 30)
    private String idCard;

    @Column(name = "emergency_contact", length = 50)
    private String emergencyContact;

    @Column(name = "emergency_phone", length = 30)
    private String emergencyPhone;

    @Column(name = "reference_phone", length = 30)
    private String referencePhone;

    @Column(name = "bank_card", length = 50)
    private String bankCard;

    @Column(name = "family_members", length = 500)
    private String familyMembers;

    /** 应聘类型：应届毕业生 / 社招 */
    @Column(name = "applicant_type", length = 50)
    private String applicantType;

    @Column(name = "target_position", length = 100)
    private String targetPosition;

    @Column(name = "target_city", length = 50)
    private String targetCity;

    /** 是否接受其他城市：是 / 否 */
    @Column(name = "accept_other_city", length = 10)
    private String acceptOtherCity;

    @Column(name = "school", length = 100)
    private String school;

    @Column(name = "major", length = 100)
    private String major;

    @Column(name = "degree", length = 50)
    private String degree;

    @Column(name = "graduation_date", length = 20)
    private String graduationDate;

    @Column(name = "expected_city", length = 50)
    private String expectedCity;

    @Column(name = "expected_position", length = 100)
    private String expectedPosition;

    @Column(name = "self_introduction", columnDefinition = "TEXT")
    private String selfIntroduction;
}
