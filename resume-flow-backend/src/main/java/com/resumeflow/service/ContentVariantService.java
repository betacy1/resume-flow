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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 内容版本 Service
 * 模板 = 场景风格(audienceType) × 岗位方向(jobDirection) × 字段类型(fieldType) × 字数限制(lengthType)。
 * 插件端根据当前模板、岗位方向、页面字段类型与字数限制自动选择最合适的内容版本；
 * 选择时绝不向上越档（不超过页面字数限制），缺失时按 方向 → 字段类型 → 受众 顺序回退。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentVariantService {

    private final ContentVariantRepository contentVariantRepository;
    private final ProfileVersionService versionService;

    /** 长度类型（按字数升序；full 为完整版，仅用于专业技能等不限字数场景） */
    public static final List<String> LENGTH_ORDER =
            List.of("within_100", "within_200", "within_300", "within_500", "within_1000", "full");

    /** 受众回退顺序：目标受众缺失时退回 general */
    private static final String FALLBACK_AUDIENCE = "general";

    /** 岗位方向回退顺序（未指定方向时依次尝试） */
    private static final List<String> DIRECTION_FALLBACK = List.of("backend", "ai", "fintech", "general");

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
     * 保存（新建或更新）版本；保存前校验内容不超过该长度档位字数上限
     */
    @Transactional
    public void save(ContentVariantDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        String content = dto.getContent() == null ? "" : dto.getContent();
        int limit = limitOf(dto.getLengthType());
        if (content.length() > limit) {
            throw new BusinessException("内容超出字数限制：当前 " + content.length() + " 字，" +
                    dto.getLengthType() + " 档位上限 " + limit + " 字，请压缩后重新保存");
        }
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
        entity.setJobDirection(hasText(dto.getJobDirection()) ? dto.getJobDirection() : "general");
        entity.setFieldType(hasText(dto.getFieldType()) ? dto.getFieldType() : "combined");
        entity.setLengthType(dto.getLengthType());
        entity.setContent(content);
        entity.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());
        contentVariantRepository.save(entity);
        versionService.bump(userId);
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
        versionService.bump(userId);
    }

    /**
     * 根据字数限制计算长度类型：取不小于限制的最小档位，超过 1000 用 full 完整版
     */
    public String lengthTypeForLimit(Integer wordLimit) {
        if (wordLimit == null || wordLimit <= 0) {
            return "within_1000";
        }
        if (wordLimit <= 100) return "within_100";
        if (wordLimit <= 200) return "within_200";
        if (wordLimit <= 300) return "within_300";
        if (wordLimit <= 500) return "within_500";
        if (wordLimit <= 1000) return "within_1000";
        return "full";
    }

    /**
     * 选择最合适的内容版本（完整维度：受众 × 岗位方向 × 字段类型 × 字数）
     * 回退链：受众(目标→general) × 方向(目标→通用顺序) × 字段类型(目标→合并型) × 长度(精确→更小档位)；
     * 绝不选择超过页面字数限制的档位，兜底取目标受众最小版本并截断到限制内。
     */
    public Optional<VariantPick> pickVariant(Long userId, String sourceType, Long sourceId,
                                             String audienceType, String jobDirection,
                                             String fieldType, Integer wordLimit) {
        List<ContentVariant> variants = contentVariantRepository
                .findByUserIdAndSourceTypeAndSourceIdAndDeletedFalseOrderByAudienceTypeAscLengthTypeAsc(
                        userId, sourceType, sourceId);
        return pickFromVariants(variants, audienceType, jobDirection, fieldType, wordLimit)
                .map(variant -> new VariantPick(
                        clipToLimit(variant.getContent(), wordLimit),
                        variant.getAudienceType(),
                        nz(variant.getJobDirection()),
                        nz(variant.getFieldType()),
                        variant.getLengthType()));
    }
    
    /**
     * 选择最合适的内容（仅返回文本）
     */
    public Optional<String> pickContent(Long userId, String sourceType, Long sourceId,
                                        String audienceType, String jobDirection,
                                        String fieldType, Integer wordLimit) {
        return pickVariant(userId, sourceType, sourceId, audienceType, jobDirection, fieldType, wordLimit)
                .map(VariantPick::content);
    }

    /**
     * 从给定版本集合中按回退链挑选最合适版本（供匹配服务缓存复用）
     */
    public Optional<ContentVariant> pickFromVariants(List<ContentVariant> all, String audienceType,
                                                     String jobDirection, String fieldType, Integer wordLimit) {
        String targetAudience = hasText(audienceType) ? audienceType : FALLBACK_AUDIENCE;
        String targetLength = lengthTypeForLimit(wordLimit);
        List<String> lengthChain = lengthChainUnder(targetLength);
        List<String> directions = directionChain(jobDirection);
        List<String> fieldTypes = fieldTypeChain(fieldType);

        List<String> audiences = FALLBACK_AUDIENCE.equals(targetAudience)
                ? List.of(FALLBACK_AUDIENCE)
                : List.of(targetAudience, FALLBACK_AUDIENCE);

        for (String audience : audiences) {
            for (String direction : directions) {
                for (String ft : fieldTypes) {
                    for (String length : lengthChain) {
                        Optional<ContentVariant> hit = find(all, audience, direction, ft, length);
                        if (hit.isPresent()) {
                            return hit;
                        }
                    }
                }
            }
        }

        // 兜底：目标受众下任意方向/字段类型的最小版本（最后截断到页面限制，保证不超字数）
        return all.stream()
                .filter(v -> Boolean.TRUE.equals(v.getEnabled()))
                .filter(v -> Objects.equals(v.getAudienceType(), targetAudience)
                        || Objects.equals(v.getAudienceType(), FALLBACK_AUDIENCE))
                .sorted((a, b) -> LENGTH_ORDER.indexOf(a.getLengthType()) - LENGTH_ORDER.indexOf(b.getLengthType()))
                .findFirst();
    }

    /** 长度回退链：目标档位 → 更小档位（不向上越档，避免超出页面字数限制） */
    private List<String> lengthChainUnder(String targetLength) {
        int idx = LENGTH_ORDER.indexOf(targetLength);
        if (idx < 0) {
            idx = LENGTH_ORDER.size() - 1;
        }
        List<String> chain = new ArrayList<>();
        for (int i = idx; i >= 0; i--) {
            chain.add(LENGTH_ORDER.get(i));
        }
        return chain;
    }

    /** 岗位方向回退链：指定方向 → general；未指定按内置顺序 */
    private List<String> directionChain(String jobDirection) {
        if (!hasText(jobDirection) || "general".equals(jobDirection)) {
            return DIRECTION_FALLBACK;
        }
        return List.of(jobDirection, "general");
    }

    /** 字段类型回退链：分类型缺失时退回同来源合并型，再退回通用 combined */
    private List<String> fieldTypeChain(String fieldType) {
        if (!hasText(fieldType) || "combined".equals(fieldType)) {
            return List.of("combined", "internship_combined", "project_combined");
        }
        if (fieldType.startsWith("internship_")) {
            return "internship_combined".equals(fieldType)
                    ? List.of(fieldType, "combined")
                    : List.of(fieldType, "internship_combined", "combined");
        }
        if (fieldType.startsWith("project_")) {
            return "project_combined".equals(fieldType)
                    ? List.of(fieldType, "combined")
                    : List.of(fieldType, "project_combined", "combined");
        }
        if (fieldType.startsWith("skill_")) {
            // 技能字段类型回退：目标类型 → 完整版 → 简短版 → 关键词 → 通用合并型
            return switch (fieldType) {
                case "skill_full" -> List.of(fieldType, "skill_short", "combined");
                case "skill_short" -> List.of(fieldType, "skill_full", "combined");
                case "skill_keywords" -> List.of(fieldType, "skill_short", "combined");
                default -> List.of(fieldType, "skill_full", "combined");
            };
        }
        return List.of(fieldType, "combined");
    }

    private Optional<ContentVariant> find(List<ContentVariant> all, String audience, String direction,
                                          String fieldType, String lengthType) {
        return all.stream()
                .filter(v -> Boolean.TRUE.equals(v.getEnabled()))
                .filter(v -> Objects.equals(v.getAudienceType(), audience))
                .filter(v -> Objects.equals(nz(v.getJobDirection()), direction))
                .filter(v -> Objects.equals(nz(v.getFieldType()), fieldType))
                .filter(v -> Objects.equals(v.getLengthType(), lengthType))
                .findFirst();
    }

    /** 字数档位对应的字符上限（full 完整版按 10000 处理） */
    public int limitOf(String lengthType) {
        if (lengthType == null) {
            return 1000;
        }
        return switch (lengthType) {
            case "within_100" -> 100;
            case "within_200" -> 200;
            case "within_300" -> 300;
            case "within_500" -> 500;
            case "full" -> 10000;
            default -> 1000;
        };
    }

    /** 页面限制存在时保证最终内容不超字数 */
    private String clipToLimit(String content, Integer wordLimit) {
        if (content == null) {
            return "";
        }
        if (wordLimit == null || wordLimit <= 0 || content.length() <= wordLimit) {
            return content;
        }
        return content.substring(0, wordLimit);
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 版本选择结果（内容已保证不超过页面字数限制） */
    public record VariantPick(String content, String audienceType, String jobDirection,
                              String fieldType, String lengthType) {
    }
}
