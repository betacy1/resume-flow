package com.resumeflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 DTO
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度 3~50")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度 6~50")
    private String password;

    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过 20")
    private String phone;
}
