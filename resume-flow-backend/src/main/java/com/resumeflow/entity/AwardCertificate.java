package com.resumeflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖项证书表 award_certificate
 */
@Entity
@Table(name = "award_certificate")
@Data
@EqualsAndHashCode(callSuper = true)
public class AwardCertificate extends BaseEntity {

    @Column(name = "award_name", length = 200)
    private String awardName;

    @Column(name = "award_type", length = 50)
    private String awardType;

    @Column(name = "award_year", length = 20)
    private String awardYear;

    /** 奖项级别，如 院校级 / 省部级 / 国家级 */
    @Column(name = "award_level", length = 50)
    private String awardLevel;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
