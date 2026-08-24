package com.resumeflow.dto;

import lombok.Data;

/**
 * 教育经历 DTO
 */
@Data
public class EducationExperienceDTO {

    private Long id;
    private String school;
    private String schoolTags;
    private String major;
    private String degree;
    private String college;
    private String startDate;
    private String endDate;
    /** 学号 */
    private String studentNumber;
    /** 学历（硕士研究生/大学本科/高中） */
    private String educationLevel;
    /** 学位（硕士/学士） */
    private String academicDegree;
    /** 学习形式（全国普通高等院校全日制） */
    private String studyMode;
    /** 主修课程及成绩 */
    private String courses;
    /** 高考录取批次 */
    private String admissionBatch;
    /** 显示专业 */
    private String displayMajor;
    private String gpa;
    private String rank;
    private String advisor;
    private String lab;
    private String researchDirection;
    private String thesis;
    private String honors;
    private Boolean isDefault;
    private String description;
    private Integer sortOrder;
}
