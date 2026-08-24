package com.resumeflow.dto;

import lombok.Data;

/**
 * 紧急联系人 DTO（与家庭成员分别独立维护）
 */
@Data
public class EmergencyContactDTO {

    private Long id;
    private String name;
    private String relation;
    private String phone;
    private String company;
    private String position;
    private String address;
    private String remark;
    private Boolean enabled;
}
