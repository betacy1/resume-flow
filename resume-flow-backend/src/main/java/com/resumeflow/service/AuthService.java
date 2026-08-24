package com.resumeflow.service;

import com.resumeflow.common.BusinessException;
import com.resumeflow.dto.ChangePasswordRequest;
import com.resumeflow.dto.LoginRequest;
import com.resumeflow.dto.RegisterRequest;
import com.resumeflow.entity.SysUser;
import com.resumeflow.repository.SysUserRepository;
import com.resumeflow.security.JwtUtils;
import com.resumeflow.security.SecurityUtils;
import com.resumeflow.vo.LoginVO;
import com.resumeflow.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户认证 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserRepository sysUserRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final DefaultStructureService defaultStructureService;

    /**
     * 用户注册
     */
    @Transactional
    public void register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (sysUserRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());

        sysUserRepository.save(user);
        // 注册后即创建标准字段结构（家庭成员/紧急联系人/基础信息），内容由用户自己填写
        defaultStructureService.initDefaultStructure(user.getId());
        log.info("用户注册成功: {}", request.getUsername());
    }

    /**
     * 用户登录
     */
    public LoginVO login(LoginRequest request) {
        SysUser user = sysUserRepository.findByUsernameAndDeletedFalse(request.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername());
        log.info("用户登录成功: {}", user.getUsername());

        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    /**
     * 获取当前登录用户信息
     */
    public UserVO getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));

        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        SysUser user = sysUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(401, "用户不存在"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        sysUserRepository.save(user);
        log.info("用户修改密码成功: {}", user.getUsername());
    }

    /**
     * 退出登录（JWT 无状态，前端清除 Token 即可，后端仅记录日志）
     */
    public void logout() {
        String username = SecurityUtils.getCurrentUsername();
        log.info("用户退出登录: {}", username);
    }
}
