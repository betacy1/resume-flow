package com.resumeflow.dto;

import lombok.Data;

/**
 * 家庭成员 DTO（relation/name/company/position/phone/email/politicalStatus/address/remark）
 */
@Data
public class FamilyMemberDTO {

    private Long id;
    private String relation;
    private String name;
    private String company;
    private String position;
    private String phone;
    private String email;
    private String politicalStatus;
    private String address;
    private String remark;
    private Integer sortOrder;
    private Boolean enabled;
}
