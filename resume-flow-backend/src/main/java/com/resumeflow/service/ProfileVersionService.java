package com.resumeflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeflow.entity.*;
import com.resumeflow.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历数据版本 Service
 * 所有用户简历数据（基础信息、教育、实习、项目、技能、素材、自定义字段、模板、经历配置、内容版本）
 * 发生变更后必须调用 {@link #bump(Long)} 使版本号 +1；
 * dataHash 在读取同步状态时惰性重算，保证与当前数据严格一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileVersionService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProfileSyncStateRepository syncStateRepository;
    private final UserProfileRepository userProfileRepository;
    private final EducationExperienceRepository educationRepository;
    private final InternshipExperienceRepository internshipRepository;
    private final ProjectExperienceRepository projectRepository;
    private final SkillProfileRepository skillRepository;
    private final AwardCertificateRepository awardRepository;
    private final AnswerMaterialRepository materialRepository;
    private final UserCustomFieldRepository customFieldRepository;
    private final ApplicationTemplateRepository templateRepository;
    private final TemplateExperienceConfigRepository templateConfigRepository;
    private final ContentVariantRepository contentVariantRepository;
    private final ObjectMapper objectMapper;

    /**
     * 用户数据变更后调用：版本号 +1，刷新更新时间
     */
    @Transactional
    public void bump(Long userId) {
        ProfileSyncState state = stateOf(userId);
        state.setProfileVersion(state.getProfileVersion() + 1);
        syncStateRepository.save(state);
    }

    /**
     * 同步状态：版本号 + 内容哈希（惰性重算）+ 最后更新时间
     */
    @Transactional
    public Map<String, Object> status(Long userId) {
        ProfileSyncState state = stateOf(userId);
        String hash = computeDataHash(userId);
        if (!hash.equals(state.getDataHash())) {
            state.setDataHash(hash);
            syncStateRepository.save(state);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profileVersion", state.getProfileVersion());
        result.put("dataHash", hash);
        result.put("updatedAt", state.getUpdateTime() == null ? null : state.getUpdateTime().format(TIME_FORMAT));
        return result;
    }

    /**
     * demo 初始化等全量重建后调用：重置版本号并立即计算哈希
     */
    @Transactional
    public void rebuild(Long userId) {
        ProfileSyncState state = stateOf(userId);
        state.setProfileVersion(1L);
        state.setDataHash(computeDataHash(userId));
        syncStateRepository.save(state);
    }

    private ProfileSyncState stateOf(Long userId) {
        return syncStateRepository.findByUserIdAndDeletedFalse(userId).orElseGet(() -> {
            ProfileSyncState state = new ProfileSyncState();
            state.setUserId(userId);
            state.setProfileVersion(0L);
            return syncStateRepository.save(state);
        });
    }

    /**
     * 计算当前用户全部简历数据的内容哈希（SHA-256）
     */
    public String computeDataHash(Long userId) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("profile", userProfileRepository.findByUserIdAndDeletedFalse(userId).orElse(null));
            snapshot.put("education", educationRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId));
            snapshot.put("internships", internshipRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId));
            snapshot.put("projects", projectRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId));
            snapshot.put("skills", skillRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId));
            snapshot.put("awards", awardRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId));
            snapshot.put("materials", materialRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId));
            snapshot.put("customFields", customFieldRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId));
            snapshot.put("templates", templateRepository.findByUserIdAndDeletedFalseOrderByIsDefaultDescIdAsc(userId));
            snapshot.put("templateConfigs", templateConfigRepository.findByUserIdAndDeletedFalse(userId));
            snapshot.put("variants", contentVariantRepository
                    .findByUserIdAndDeletedFalseOrderBySourceTypeAscSourceIdAscAudienceTypeAscLengthTypeAsc(userId));
            Map<String, Object> generic = objectMapper.convertValue(snapshot,
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            String json = objectMapper.writeValueAsString(stripTimeFields(generic));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算数据哈希失败，回退为时间戳哈希: {}", e.getMessage());
            return "fallback-" + System.currentTimeMillis();
        }
    }

    /**
     * 剔除实体中的创建/更新时间字段：哈希只反映业务内容，
     * 避免 bump 更新状态行或保存实体带来的时间戳变化导致哈希自反波动。
     */
    private Object stripTimeFields(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if ("createTime".equals(key) || "updateTime".equals(key)) {
                    continue;
                }
                cleaned.put(key, stripTimeFields(entry.getValue()));
            }
            return cleaned;
        }
        if (node instanceof List<?> list) {
            return list.stream().map(this::stripTimeFields).toList();
        }
        return node;
    }
}
