package com.resumeflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeflow.common.BusinessException;
import com.resumeflow.dto.UserCustomFieldDTO;
import com.resumeflow.entity.UserCustomField;
import com.resumeflow.repository.UserCustomFieldRepository;
import com.resumeflow.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCustomFieldService {

    private final UserCustomFieldRepository userCustomFieldRepository;
    private final ObjectMapper objectMapper;
    private final ProfileVersionService versionService;

    public List<UserCustomFieldDTO> list(String keyword, String category, Boolean enabled, Long templateId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return userCustomFieldRepository.findByConditions(
                userId,
                hasText(category) ? category : null,
                enabled,
                templateId,
                hasText(keyword) ? keyword.trim() : null
        ).stream().map(this::toDTO).toList();
    }

    @Transactional
    public Long create(UserCustomFieldDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserCustomField entity = new UserCustomField();
        entity.setUserId(userId);
        applyDTO(entity, dto);
        userCustomFieldRepository.save(entity);
        versionService.bump(userId);
        return entity.getId();
    }

    @Transactional
    public void update(Long id, UserCustomFieldDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserCustomField entity = getById(id, userId);
        applyDTO(entity, dto);
        entity.setVersion(entity.getVersion() == null ? 1L : entity.getVersion() + 1);
        userCustomFieldRepository.save(entity);
        versionService.bump(userId);
    }

    @Transactional
    public void delete(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserCustomField entity = getById(id, userId);
        entity.setDeleted(true);
        userCustomFieldRepository.save(entity);
        versionService.bump(userId);
    }

    @Transactional
    public void setEnabled(Long id, Boolean enabled) {
        Long userId = SecurityUtils.getCurrentUserId();
        UserCustomField entity = getById(id, userId);
        entity.setEnabled(Boolean.TRUE.equals(enabled));
        userCustomFieldRepository.save(entity);
        versionService.bump(userId);
    }

    public UserCustomField getById(Long id, Long userId) {
        return userCustomFieldRepository.findById(id)
                .filter(f -> f.getUserId().equals(userId) && !Boolean.TRUE.equals(f.getDeleted()))
                .orElseThrow(() -> new BusinessException("字段不存在"));
    }

    /** 实体 → DTO（供插件字段服务复用） */
    public UserCustomFieldDTO toDTO(UserCustomField entity) {
        UserCustomFieldDTO dto = new UserCustomFieldDTO();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setFieldKey(entity.getFieldKey());
        dto.setFieldName(entity.getFieldName());
        dto.setFieldType(entity.getFieldType());
        dto.setFieldCategory(entity.getFieldCategory());
        dto.setFieldValue(entity.getFieldValue());
        dto.setMatchKeywords(fromJson(entity.getMatchKeywords()));
        dto.setTemplateIds(fromLongJson(entity.getTemplateIds()));
        dto.setLengthType(entity.getLengthType());
        dto.setAutoFillEnabled(entity.getAutoFillEnabled());
        dto.setManualFillEnabled(entity.getManualFillEnabled());
        dto.setVersion(entity.getVersion());
        dto.setSourceRef(entity.getSourceRef());
        // 已废弃敏感字段概念：个人自用场景，统一返回 false，仅保留字段兼容旧版插件/后台协议
        dto.setSensitive(false);
        dto.setEnabled(entity.getEnabled());
        dto.setSortOrder(entity.getSortOrder());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    /**
     * 为字段追加匹配关键词（插件手动绑定页面字段时形成新匹配规则）
     */
    @Transactional
    public void addMatchKeyword(Long id, String keyword) {
        if (!hasText(keyword)) {
            throw new BusinessException("关键词不能为空");
        }
        Long userId = SecurityUtils.getCurrentUserId();
        UserCustomField entity = getById(id, userId);
        List<String> keywords = new java.util.ArrayList<>(fromJson(entity.getMatchKeywords()));
        String trimmed = keyword.trim();
        boolean exists = keywords.stream().anyMatch(k -> k.equalsIgnoreCase(trimmed));
        if (!exists) {
            keywords.add(trimmed);
            entity.setMatchKeywords(toJson(keywords));
            userCustomFieldRepository.save(entity);
            versionService.bump(userId);
        }
    }

    public void applyDTO(UserCustomField entity, UserCustomFieldDTO dto) {
        if (!hasText(dto.getFieldKey())) {
            throw new BusinessException("fieldKey 不能为空");
        }
        if (!hasText(dto.getFieldName())) {
            throw new BusinessException("fieldName 不能为空");
        }
        if (!hasText(dto.getFieldType())) {
            throw new BusinessException("fieldType 不能为空");
        }
        entity.setTemplateId(dto.getTemplateId());
        entity.setFieldKey(dto.getFieldKey().trim());
        entity.setFieldName(dto.getFieldName().trim());
        entity.setFieldType(dto.getFieldType().trim());
        entity.setFieldCategory(dto.getFieldCategory());
        entity.setFieldValue(dto.getFieldValue());
        entity.setMatchKeywords(toJson(dto.getMatchKeywords()));
        entity.setTemplateIds(toLongJson(dto.getTemplateIds()));
        entity.setLengthType(dto.getLengthType());
        entity.setAutoFillEnabled(dto.getAutoFillEnabled() == null || dto.getAutoFillEnabled());
        entity.setManualFillEnabled(dto.getManualFillEnabled() == null || dto.getManualFillEnabled());
        entity.setSourceRef(dto.getSourceRef());
        // 已废弃敏感字段概念：无论前端传什么，一律按普通字段保存（数据库列保留，恒为 false）
        entity.setSensitive(false);
        entity.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
    }

    private String toJson(List<String> keywords) {
        List<String> safeKeywords = keywords == null ? Collections.emptyList() : keywords.stream()
                .filter(this::hasText)
                .map(String::trim)
                .toList();
        try {
            return objectMapper.writeValueAsString(safeKeywords);
        } catch (JsonProcessingException e) {
            throw new BusinessException("匹配关键词格式错误");
        }
    }

    private String toLongJson(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new BusinessException("模板 id 列表格式错误");
        }
    }

    private List<Long> fromLongJson(String json) {
        if (!hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private List<String> fromJson(String json) {
        if (!hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException("字段关键词数据损坏，请重新编辑该字段");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
