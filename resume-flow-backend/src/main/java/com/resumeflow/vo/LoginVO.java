package com.resumeflow.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录返回 VO
 */
@Data
@Builder
public class LoginVO {

    private String token;
    private Long userId;
    private String username;
}
