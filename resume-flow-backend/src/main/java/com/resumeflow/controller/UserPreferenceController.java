package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.entity.UserPreference;
import com.resumeflow.repository.UserPreferenceRepository;
import com.resumeflow.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

/**
 * 用户偏好接口：插件侧最近使用、收藏内容、站点默认模板/方向。
 * 偏好数据不影响简历内容版本号（不触发全量同步）。
 */
@Tag(name = "用户偏好接口")
@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceRepository preferenceRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Operation(summary = "获取当前用户偏好")
    @GetMapping
    public Result<Map<String, Object>> get() {
        Long userId = SecurityUtils.getCurrentUserId();
        UserPreference pref = preferenceRepository.findByUserIdAndDeletedFalse(userId).orElse(null);
        if (pref == null || pref.getContent() == null || pref.getContent().isBlank()) {
            return Result.success(Collections.emptyMap());
        }
        try {
            Map<String, Object> map = objectMapper.readValue(pref.getContent(), Map.class);
            return Result.success(map);
        } catch (Exception e) {
            return Result.success(Collections.emptyMap());
        }
    }

    @Operation(summary = "保存当前用户偏好（整体覆盖）")
    @PutMapping
    public Result<Boolean> save(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        try {
            String json = objectMapper.writeValueAsString(body);
            UserPreference pref = preferenceRepository.findByUserIdAndDeletedFalse(userId)
                    .orElseGet(() -> {
                        UserPreference p = new UserPreference();
                        p.setUserId(userId);
                        return p;
                    });
            pref.setContent(json);
            preferenceRepository.save(pref);
            return Result.success(true);
        } catch (Exception e) {
            return Result.error(400, "偏好数据格式错误", null);
        }
    }
}
