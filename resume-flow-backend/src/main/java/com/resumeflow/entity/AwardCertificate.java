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

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
