package com.resumeflow.service;

import com.resumeflow.common.BusinessException;
import com.resumeflow.common.ConflictException;
import com.resumeflow.dto.UserCustomFieldDTO;
import com.resumeflow.entity.UserCustomField;
import com.resumeflow.repository.UserCustomFieldRepository;
import com.resumeflow.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件字段 Service：插件侧新增 / 编辑 / 删除 / 启停自定义字段。
 * 所有写操作成功后版本号 +1，响应携带 profileVersion / dataHash / updatedAt 供插件本地缓存对齐；
 * 编辑时提交乐观锁版本号，落后于服务端则抛出 409 冲突并携带服务端最新字段。
 */
@Service
@RequiredArgsConstructor
public class PluginFieldService {

    /** 插件提交的英文分类别名 → 库内中文分类（与初始化数据分类保持一致） */
    private static final Map<String, String> CATEGORY_ALIAS = new HashMap<>(Map.of(
            "basic", "基础信息",
            "education", "教育经历",
            "skill", "专业技能",
            "internship", "实习经历",
            "project", "项目经历",
            "self_evaluation", "自我评价",
            "ai_collaboration", "AI 协作经历",
            "award", "获奖经历",
            "interest", "兴趣特长",
            "open_question", "开放题"));
    static {
        CATEGORY_ALIAS.put("custom", "自定义字段");
    }

    private final UserCustomFieldService customFieldService;
    private final UserCustomFieldRepository userCustomFieldRepository;
    private final ProfileVersionService versionService;

    @Transactional
    public Map<String, Object> create(UserCustomFieldDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        checkContentNotEmpty(dto);
        UserCustomField entity = new UserCustomField();
        entity.setUserId(userId);
        normalizeCategory(dto);
        customFieldService.applyDTO(entity, dto);
        entity.setVersion(0L);
        userCustomFieldRepository.save(entity);
        versionService.bump(userId);
        Map<String, Object> result = syncResult(userId);
        result.put("id", entity.getId());
        result.put("field", customFieldService.toDTO(entity));
        result.put("warnings", ContentQualityService.check(dto.getFieldValue(), dto.getLengthType()));
        return result;
    }

    @Transactional
    public Map<String, Object> update(Long id, UserCustomFieldDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserCustomField entity = customFieldService.getById(id, userId);
        checkVersion(entity, dto.getVersion());
        checkContentNotEmpty(dto);
        normalizeCategory(dto);
        customFieldService.applyDTO(entity, dto);
        entity.setVersion(entity.getVersion() == null ? 1L : entity.getVersion() + 1);
        userCustomFieldRepository.save(entity);
        versionService.bump(userId);
        Map<String, Object> result = syncResult(userId);
        result.put("field", customFieldService.toDTO(entity));
        result.put("warnings", ContentQualityService.check(dto.getFieldValue(), dto.getLengthType()));
        return result;
    }

    /** 逻辑删除 */
    @Transactional
    public Map<String, Object> delete(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserCustomField entity = customFieldService.getById(id, userId);
        entity.setDeleted(true);
        userCustomFieldRepository.save(entity);
        versionService.bump(userId);
        return syncResult(userId);
    }

    /** 启用 / 禁用 */
    @Transactional
    public Map<String, Object> toggle(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserCustomField entity = customFieldService.getById(id, userId);
        entity.setEnabled(!Boolean.TRUE.equals(entity.getEnabled()));
        entity.setVersion(entity.getVersion() == null ? 1L : entity.getVersion() + 1);
        userCustomFieldRepository.save(entity);
        versionService.bump(userId);
        Map<String, Object> result = syncResult(userId);
        result.put("field", customFieldService.toDTO(entity));
        return result;
    }

    /** 插件提交的版本号落后于服务端 → 409，携带服务端最新字段 */
    private void checkVersion(UserCustomField entity, Long submitted) {
        if (submitted == null) {
            return;
        }
        long server = entity.getVersion() == null ? 0L : entity.getVersion();
        if (submitted < server) {
            throw new ConflictException("该内容已在网页端更新，请拉取最新内容或选择覆盖保存",
                    customFieldService.toDTO(entity));
        }
    }

    /** 内容为空阻断保存（质量检查规则：仅空内容强制拦截） */
    private void checkContentNotEmpty(UserCustomFieldDTO dto) {
        if (dto.getFieldValue() == null || dto.getFieldValue().trim().isEmpty()) {
            throw new BusinessException("内容正文不能为空");
        }
    }

    private void normalizeCategory(UserCustomFieldDTO dto) {
        if (dto.getFieldCategory() != null) {
            dto.setFieldCategory(CATEGORY_ALIAS.getOrDefault(dto.getFieldCategory().trim(), dto.getFieldCategory().trim()));
        }
    }

    /** 写操作后的同步状态：版本号 / 哈希 / 更新时间 */
    private Map<String, Object> syncResult(Long userId) {
        Map<String, Object> status = versionService.status(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("profileVersion", status.get("profileVersion"));
        result.put("dataHash", status.get("dataHash"));
        result.put("updatedAt", status.get("updatedAt"));
        return result;
    }
}
