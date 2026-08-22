package com.resumeflow.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 用户信息 VO
 */
@Data
@Builder
public class UserVO {

    private Long id;
    private String username;
    private String email;
    private String phone;
}
