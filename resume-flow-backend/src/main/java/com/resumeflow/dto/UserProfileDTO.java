package com.resumeflow.dto;

import lombok.Data;

/**
 * 用户简历基础信息 DTO
 */
@Data
public class UserProfileDTO {

    private String name;
    private String gender;
    private String phone;
    private String email;
    private String qq;
    private String wechat;
    private String currentLocation;
    private String politicalStatus;
    private String idCard;
    private String emergencyContact;
    private String emergencyPhone;
    private String referencePhone;
    private String bankCard;
    private String familyMembers;
    private String applicantType;
    private String targetPosition;
    private String targetCity;
    private String acceptOtherCity;
    private String school;
    private String major;
    private String degree;
    private String graduationDate;
    private String expectedCity;
    private String expectedPosition;
    private String selfIntroduction;
}
