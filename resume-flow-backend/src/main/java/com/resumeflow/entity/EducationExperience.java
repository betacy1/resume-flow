package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 教育经历表 education_experience
 */
@Entity
@Table(name = "education_experience")
@Data
@EqualsAndHashCode(callSuper = true)
public class EducationExperience extends BaseEntity {

    @Column(length = 100)
    private String school;

    /** 学校标签，如 985、211、双一流 */
    @Column(name = "school_tags", length = 100)
    private String schoolTags;

    /** 学号 */
    @Column(name = "student_number", length = 50)
    private String studentNumber;

    /** 学历（硕士研究生/大学本科/高中） */
    @Column(name = "education_level", length = 50)
    private String educationLevel;

    /** 学位（硕士/学士） */
    @Column(length = 50)
    private String academicDegree;

    /** 学历类型 / 学习形式，如 普通全日制 */
    @Column(name = "study_mode", length = 100)
    private String studyMode;

    /** 主修课程及成绩 */
    @Column(name = "courses", columnDefinition = "TEXT")
    private String courses;

    /** 高考录取批次 */
    @Column(name = "admission_batch", length = 50)
    private String admissionBatch;

    /** 显示专业（与主修专业不同的对外展示专业名） */
    @Column(name = "display_major", length = 100)
    private String displayMajor;

    @Column(length = 100)
    private String major;

    @Column(length = 50)
    private String degree;

    /** 学院 */
    @Column(length = 100)
    private String college;

    @Column(name = "start_date", length = 20)
    private String startDate;

    @Column(name = "end_date", length = 20)
    private String endDate;

    @Column(length = 20)
    private String gpa;

    /** 成绩排名，如 前20%（列名避开 MySQL 保留字 rank） */
    @Column(name = "grade_rank", length = 30)
    private String rank;

    /** 导师 */
    @Column(length = 50)
    private String advisor;

    /** 实验室 */
    @Column(length = 200)
    private String lab;

    @Column(name = "research_direction", length = 300)
    private String researchDirection;

    /** 毕业论文 / 研究课题 */
    @Column(length = 300)
    private String thesis;

    /** 在校荣誉 */
    @Column(length = 500)
    private String honors;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
