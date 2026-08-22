package com.resumeflow.dto;

import lombok.Data;

/**
 * 奖项证书 DTO
 */
@Data
public class AwardCertificateDTO {

    private Long id;
    private String awardName;
    private String awardType;
    private String awardYear;
    private String description;
    private Integer sortOrder;
}
