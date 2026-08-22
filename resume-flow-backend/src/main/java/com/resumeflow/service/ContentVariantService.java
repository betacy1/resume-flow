package com.resumeflow.service;

import com.resumeflow.common.BusinessException;
import com.resumeflow.dto.ContentVariantDTO;
import com.resumeflow.entity.ContentVariant;
import com.resumeflow.repository.ContentVariantRepository;
import com.resumeflow.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 内容版本 Service
 * 每条实习/项目/素材支持 受众类型(4) × 长度类型(4) 共 16 个版本，
 * 插件端根据当前模板与页面字数限制自动选择最合适的内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentVariantService {

    private final ContentVariantRepository contentVariantRepository;

    /** 长度类型（按字数升序） */
    public static final List<String> LENGTH_ORDER =
            List.of("within_200", "within_300", "within_500", "within_1000");

    /** 受众回退顺序：目标受众缺失时退回 general */
    private static final String FALLBACK_AUDIENCE = "general";

    /**
     * 查询某来源的全部版本
     */
    public List<ContentVariant> listBySource(String sourceType, Long sourceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return contentVariantRepository
                .findByUserIdAndSourceTypeAndSourceIdAndDeletedFalseOrderByAudienceTypeAscLengthTypeAsc(
                        userId, sourceType, sourceId);
    }

    /**
     * 查询当前用户全部版本
     */
    public List<ContentVariant> listAll() {
        Long userId = SecurityUtils.getCurrentUserId();
        return contentVariantRepository
                .findByUserIdAndDeletedFalseOrderBySourceTypeAscSourceIdAscAudienceTypeAscLengthTypeAsc(userId);
    }

    /**
     * 保存（新建或更新）版本
     */
    @Transactional
    public void save(ContentVariantDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        ContentVariant entity;
        if (dto.getId() != null) {
            entity = contentVariantRepository.findById(dto.getId())
                    .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                    .orElseThrow(() -> new BusinessException("内容版本不存在"));
        } else {
            entity = new ContentVariant();
            entity.setUserId(userId);
            entity.setSourceType(dto.getSourceType());
            entity.setSourceId(dto.getSourceId());
        }
        entity.setAudienceType(dto.getAudienceType());
        entity.setLengthType(dto.getLengthType());
        entity.setContent(dto.getContent());
        entity.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());
        contentVariantRepository.save(entity);
    }

    /**
     * 删除版本
     */
    @Transactional
    public void delete(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        ContentVariant entity = contentVariantRepository.findById(id)
                .filter(e -> e.getUserId().equals(userId) && !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BusinessException("内容版本不存在"));
        entity.setDeleted(true);
        contentVariantRepository.save(entity);
    }

    /**
     * 根据字数限制计算长度类型：取不小于限制的最小档位，超过 1000 用 within_1000
     */
    public String lengthTypeForLimit(Integer wordLimit) {
        if (wordLimit == null || wordLimit <= 0) {
            return "within_500";
        }
        if (wordLimit <= 200) return "within_200";
        if (wordLimit <= 300) return "within_300";
        if (wordLimit <= 500) return "within_500";
        return "within_1000";
    }

    /**
     * 选择最合适的内容版本
     * 优先级：目标受众+精确长度 → 目标受众+更小长度（最大者）→ 目标受众+1000 → general+精确长度 → null
     *
     * @param sourceType   internship / project / material
     * @param sourceId     来源记录 id
     * @param audienceType 受众类型（可为空，默认 general）
     * @param wordLimit    页面字数限制（可为空，默认 within_500）
     */
    public Optional<String> pickContent(Long userId, String sourceType, Long sourceId,
                                        String audienceType, Integer wordLimit) {
        String audience = hasText(audienceType) ? audienceType : FALLBACK_AUDIENCE;
        String targetLength = lengthTypeForLimit(wordLimit);

        // 1. 目标受众 + 精确长度
        Optional<ContentVariant> exact = findEnabled(userId, sourceType, sourceId, audience, targetLength);
        if (exact.isPresent()) {
            return exact.map(ContentVariant::getContent);
        }

        // 2. 目标受众 + 不超过目标长度的最大档位
        int targetIdx = LENGTH_ORDER.indexOf(targetLength);
        for (int i = targetIdx - 1; i >= 0; i--) {
            Optional<ContentVariant> smaller = findEnabled(userId, sourceType, sourceId, audience, LENGTH_ORDER.get(i));
            if (smaller.isPresent()) {
                return smaller.map(ContentVariant::getContent);
            }
        }

        // 3. 目标受众 + 最长版本
        Optional<ContentVariant> longest = findEnabled(userId, sourceType, sourceId, audience, "within_1000");
        if (longest.isPresent()) {
            return longest.map(ContentVariant::getContent);
        }

        // 4. 回退 general 受众
        if (!FALLBACK_AUDIENCE.equals(audience)) {
            return pickContent(userId, sourceType, sourceId, FALLBACK_AUDIENCE, wordLimit);
        }
        return Optional.empty();
    }

    private Optional<ContentVariant> findEnabled(Long userId, String sourceType, Long sourceId,
                                                 String audience, String lengthType) {
        return contentVariantRepository
                .findByUserIdAndSourceTypeAndSourceIdAndAudienceTypeAndLengthTypeAndEnabledTrueAndDeletedFalse(
                        userId, sourceType, sourceId, audience, lengthType);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
