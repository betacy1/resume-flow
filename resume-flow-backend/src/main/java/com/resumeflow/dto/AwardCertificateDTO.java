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
    /** 奖项级别（院校级/省部级/国家级） */
    private String awardLevel;
    private String description;
    private Integer sortOrder;
}
