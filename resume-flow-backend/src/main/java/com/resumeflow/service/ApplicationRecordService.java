package com.resumeflow.service;

import com.resumeflow.common.BusinessException;
import com.resumeflow.dto.*;
import com.resumeflow.entity.ApplicationRecord;
import com.resumeflow.entity.ApplicationStageRecord;
import com.resumeflow.init.ApplicationInitData;
import com.resumeflow.repository.ApplicationRecordRepository;
import com.resumeflow.repository.ApplicationStageRecordRepository;
import com.resumeflow.security.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 投递信息表服务（秋招投递记录 / Application Tracker）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationRecordService {

    public static final List<String> APPLY_STATUSES = List.of(
            "未投", "准备中", "已投", "已截止", "简历挂", "测评", "笔试",
            "一面", "二面", "三面", "HR面", "终面", "offer", "已拒", "已放弃", "待确认", "其他");

    public static final List<String> SOURCE_TYPES = List.of(
            "企业", "体制", "基金", "互联网", "银行", "券商", "国央企", "其他");

    public static final List<String> DEFAULT_CHANNELS = List.of(
            "官网", "公众号", "官网 / 公众号", "内推", "第三方系统", "手动添加", "插件采集", "Excel初始化", "Excel导入");

    public static final List<String> DEFAULT_STAGES = List.of(
            "初筛", "测评", "笔试", "一面", "二面", "三面", "HR面", "终面", "offer");

    public static final List<String> PRIORITIES = List.of(
            "重点关注", "高", "中", "低", "暂不投", "不合适", "待补充岗位");

    /** 插件采集置信度低于该值且用户未确认时不自动入库 */
    private static final double CONFIRM_THRESHOLD = 0.6;

    private final ApplicationRecordRepository recordRepository;
    private final ApplicationStageRecordRepository stageRepository;

    // ==================== 查询 ====================

    @Data
    public static class QueryParams {
        private Integer page = 1;
        private Integer size = 50;
        private String batchName;
        private String applyStatus;
        private String sourceType;
        private String companyName;
        private String organizationName;
        private String positionName;
        private String companyNature;
        private String applicationChannel;
        private String currentStage;
        /** true=仅插件采集，false=仅非插件采集 */
        private Boolean pluginCollected;
        /** 搜索：公司/机构/岗位/官网/公众号/备注 */
        private String keyword;
        private String sortBy;
        private String sortDir;
    }

    @Transactional
    public Map<String, Object> list(QueryParams q) {
        Long userId = SecurityUtils.getCurrentUserId();
        ensureInitialized(userId);

        Specification<ApplicationRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.equal(root.get("deleted"), false));
            if (notEmpty(q.getBatchName())) {
                predicates.add(cb.equal(root.get("batchName"), q.getBatchName().trim()));
            }
            if (notEmpty(q.getApplyStatus())) {
                predicates.add(cb.equal(root.get("applyStatus"), q.getApplyStatus().trim()));
            }
            if (notEmpty(q.getSourceType())) {
                predicates.add(cb.equal(root.get("sourceType"), q.getSourceType().trim()));
            }
            if (notEmpty(q.getCompanyName())) {
                predicates.add(cb.like(root.get("companyName"), "%" + q.getCompanyName().trim() + "%"));
            }
            if (notEmpty(q.getOrganizationName())) {
                predicates.add(cb.like(root.get("organizationName"), "%" + q.getOrganizationName().trim() + "%"));
            }
            if (notEmpty(q.getPositionName())) {
                predicates.add(cb.like(root.get("positionName"), "%" + q.getPositionName().trim() + "%"));
            }
            if (notEmpty(q.getCompanyNature())) {
                predicates.add(cb.like(root.get("companyNature"), "%" + q.getCompanyNature().trim() + "%"));
            }
            if (notEmpty(q.getApplicationChannel())) {
                predicates.add(cb.equal(root.get("applicationChannel"), q.getApplicationChannel().trim()));
            }
            if (notEmpty(q.getCurrentStage())) {
                predicates.add(cb.equal(root.get("currentStage"), q.getCurrentStage().trim()));
            }
            if (q.getPluginCollected() != null) {
                if (Boolean.TRUE.equals(q.getPluginCollected())) {
                    predicates.add(cb.equal(root.get("applicationChannel"), "插件采集"));
                } else {
                    predicates.add(cb.notEqual(root.get("applicationChannel"), "插件采集"));
                }
            }
            if (notEmpty(q.getKeyword())) {
                String kw = "%" + q.getKeyword().trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("companyName"), kw),
                        cb.like(root.get("organizationName"), kw),
                        cb.like(root.get("positionName"), kw),
                        cb.like(root.get("officialWebsite"), kw),
                        cb.like(root.get("publicAccount"), kw),
                        cb.like(root.get("remark"), kw)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = buildSort(q);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        int size = Math.min(500, Math.max(1, q.getSize() == null ? 50 : q.getSize()));
        Page<ApplicationRecord> result = recordRepository.findAll(spec, PageRequest.of(page - 1, size, sort));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", result.getTotalElements());
        data.put("page", page);
        data.put("size", size);
        data.put("records", result.getContent().stream().map(ApplicationRecordDTO::fromEntity).toList());
        return data;
    }

    /** 查询当前用户全部记录（导出等场景） */
    @Transactional
    public List<ApplicationRecord> listAllOfCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        ensureInitialized(userId);
        return recordRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
    }

    private Sort buildSort(QueryParams q) {
        String sortBy = q.getSortBy();
        boolean desc = "desc".equalsIgnoreCase(q.getSortDir());
        Sort.Direction dir = desc ? Sort.Direction.DESC : Sort.Direction.ASC;
        if (sortBy == null) {
            return Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return switch (sortBy) {
            case "lastVisitedAt", "appliedAt", "updateTime", "applyStatus", "priority",
                 "deadlineAt", "resumeModifiedAt", "createTime", "sortOrder" -> Sort.by(dir, sortBy);
            default -> Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.ASC, "id"));
        };
    }

    // ==================== 新增 / 编辑 / 删除 / 复制 ====================

    @Transactional
    public ApplicationRecordDTO create(ApplicationRecordDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        ApplicationRecord record = new ApplicationRecord();
        record.setUserId(userId);
        dto.applyTo(record);
        if (!notEmpty(record.getApplyStatus())) {
            record.setApplyStatus("未投");
        }
        if (!notEmpty(record.getBatchName())) {
            record.setBatchName(ApplicationInitData.DEFAULT_BATCH);
        }
        if (!notEmpty(record.getApplicationChannel())) {
            record.setApplicationChannel("手动添加");
        }
        if (record.getSortOrder() == null || record.getSortOrder() == 0) {
            Integer max = recordRepository.findMaxSortOrder(userId);
            record.setSortOrder((max == null ? 0 : max) + 1);
        }
        return ApplicationRecordDTO.fromEntity(recordRepository.save(record));
    }

    @Transactional
    public ApplicationRecordDTO update(Long id, ApplicationRecordDTO dto) {
        ApplicationRecord record = getOwnedRecord(id);
        dto.applyTo(record);
        return ApplicationRecordDTO.fromEntity(recordRepository.save(record));
    }

    /** 快速修改状态（表格内标记已投/未投等） */
    @Transactional
    public ApplicationRecordDTO updateStatus(Long id, String applyStatus) {
        ApplicationRecord record = getOwnedRecord(id);
        record.setApplyStatus(applyStatus);
        if ("已投".equals(applyStatus) && record.getAppliedAt() == null) {
            record.setAppliedAt(LocalDateTime.now());
        }
        return ApplicationRecordDTO.fromEntity(recordRepository.save(record));
    }

    /** 批量修改状态 */
    @Transactional
    public int batchUpdateStatus(List<Long> ids, String applyStatus) {
        int count = 0;
        for (Long id : ids) {
            try {
                updateStatus(id, applyStatus);
                count++;
            } catch (BusinessException ignored) {
                // 跳过无权限/不存在的记录
            }
        }
        return count;
    }

    @Transactional
    public void delete(Long id) {
        ApplicationRecord record = getOwnedRecord(id);
        record.setDeleted(true);
        recordRepository.save(record);
    }

    @Transactional
    public ApplicationRecordDTO copy(Long id) {
        ApplicationRecord source = getOwnedRecord(id);
        Long userId = SecurityUtils.getCurrentUserId();
        ApplicationRecord copy = new ApplicationRecord();
        copy.setUserId(userId);
        copy.setBatchName(source.getBatchName());
        copy.setSourceType(source.getSourceType());
        copy.setCategoryType(source.getCategoryType());
        copy.setCompanyName(source.getCompanyName());
        copy.setOrganizationName(source.getOrganizationName());
        copy.setPositionName(source.getPositionName());
        copy.setPositionDirection(source.getPositionDirection());
        copy.setCompanyNature(source.getCompanyNature());
        copy.setApplyStatus(source.getApplyStatus());
        copy.setCurrentStage(source.getCurrentStage());
        copy.setPriority(source.getPriority());
        copy.setCity(source.getCity());
        copy.setApplicationChannel(source.getApplicationChannel());
        copy.setOfficialWebsite(source.getOfficialWebsite());
        copy.setPublicAccount(source.getPublicAccount());
        copy.setRecruitmentUrl(source.getRecruitmentUrl());
        copy.setApplicationUrl(source.getApplicationUrl());
        copy.setRemark(source.getRemark());
        copy.setWarningNote(source.getWarningNote());
        Integer max = recordRepository.findMaxSortOrder(userId);
        copy.setSortOrder((max == null ? 0 : max) + 1);
        return ApplicationRecordDTO.fromEntity(recordRepository.save(copy));
    }

    private ApplicationRecord getOwnedRecord(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        ApplicationRecord record = recordRepository.findById(id)
                .filter(r -> userId.equals(r.getUserId()) && !Boolean.TRUE.equals(r.getDeleted()))
                .orElseThrow(() -> new BusinessException(404, "投递记录不存在或无权访问"));
        return record;
    }

    // ==================== 插件采集 ====================

    /**
     * 插件自动采集写入/更新投递记录。
     * 去重：优先 公司+机构+岗位；岗位为空时 公司+机构+域名；机构为空时 公司+岗位+域名；兜底 公司+域名（含包含关系）。
     * 已有记录仅更新访问类字段，不覆盖用户手动编辑过的公司/机构/岗位。
     */
    @Transactional
    public ApplicationCaptureResult capture(ApplicationCaptureRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        ensureInitialized(userId);

        String company = trimToEmpty(req.getCompanyName());
        String org = trimToEmpty(req.getOrganizationName());
        String position = trimToEmpty(req.getPositionName());
        String domain = trimToEmpty(req.getDomain());
        LocalDateTime now = LocalDateTime.now();
        double confidence = req.getConfidenceScore() == null ? 1.0 : req.getConfidenceScore();

        List<ApplicationRecord> candidates = recordRepository.findByUserIdAndDeletedFalse(userId);
        ApplicationRecord matched = findMatch(candidates, company, org, position, domain);

        // 低置信度且未确认：不自动入库，返回候选供插件提示
        if (matched == null && confidence < CONFIRM_THRESHOLD && !Boolean.TRUE.equals(req.getConfirmed())) {
            String summary = company.isEmpty() ? "未识别到公司名称" : "公司：" + company
                    + (position.isEmpty() ? "" : "，岗位：" + position);
            return ApplicationCaptureResult.needConfirm(summary);
        }

        if (matched != null) {
            matched.setLastVisitedAt(now);
            if (notEmpty(req.getPageUrl())) {
                matched.setPageUrl(req.getPageUrl());
            }
            if (notEmpty(req.getPageTitle())) {
                matched.setPageTitle(req.getPageTitle());
            }
            if (notEmpty(domain)) {
                matched.setDomain(domain);
            }
            if (notEmpty(req.getResumeEditUrl())) {
                matched.setResumeEditUrl(req.getResumeEditUrl());
            }
            if (notEmpty(req.getRecruitmentUrl()) && !notEmpty(matched.getRecruitmentUrl())) {
                matched.setRecruitmentUrl(req.getRecruitmentUrl());
            }
            if (req.getResumeModifiedAt() != null) {
                matched.setResumeModifiedAt(req.getResumeModifiedAt());
                matched.setResumeModifiedSource(notEmpty(req.getResumeModifiedSource())
                        ? req.getResumeModifiedSource() : "detected_time");
            }
            matched.setConfidenceScore(confidence);
            // 岗位为空时允许插件补充岗位（仅在用户未手动编辑过名称时）
            if (!Boolean.TRUE.equals(matched.getNameManuallyEdited())) {
                if (!notEmpty(matched.getPositionName()) && notEmpty(position)) {
                    matched.setPositionName(position);
                }
                if (!notEmpty(matched.getOrganizationName()) && notEmpty(org)) {
                    matched.setOrganizationName(org);
                }
            }
            recordRepository.save(matched);
            return ApplicationCaptureResult.updated(matched.getId(), matched.getApplyStatus());
        }

        // 新记录
        ApplicationRecord record = new ApplicationRecord();
        record.setUserId(userId);
        record.setBatchName(ApplicationInitData.DEFAULT_BATCH);
        record.setCompanyName(company);
        record.setOrganizationName(org);
        record.setPositionName(position);
        record.setApplyStatus("准备中");
        record.setApplicationChannel("插件采集");
        record.setPageUrl(req.getPageUrl());
        record.setPageTitle(req.getPageTitle());
        record.setDomain(domain);
        record.setRecruitmentUrl(req.getRecruitmentUrl());
        record.setResumeEditUrl(req.getResumeEditUrl());
        record.setFirstDetectedAt(now);
        record.setLastVisitedAt(now);
        if (req.getResumeModifiedAt() != null) {
            record.setResumeModifiedAt(req.getResumeModifiedAt());
            record.setResumeModifiedSource(notEmpty(req.getResumeModifiedSource())
                    ? req.getResumeModifiedSource() : "detected_time");
        }
        record.setConfidenceScore(confidence);
        Integer max = recordRepository.findMaxSortOrder(userId);
        record.setSortOrder((max == null ? 0 : max) + 1);
        recordRepository.save(record);
        return ApplicationCaptureResult.created(record.getId(), record.getApplyStatus());
    }

    private ApplicationRecord findMatch(List<ApplicationRecord> candidates, String company,
                                        String org, String position, String domain) {
        if (company.isEmpty() && domain.isEmpty()) {
            return null;
        }
        for (ApplicationRecord r : candidates) {
            boolean companyMatch = !company.isEmpty() && nameMatch(company, r.getCompanyName());
            boolean domainMatch = !domain.isEmpty() && domain.equalsIgnoreCase(trimToEmpty(r.getDomain()));
            if (!companyMatch && !domainMatch) {
                continue;
            }
            boolean orgMatch = org.isEmpty() || !notEmpty(r.getOrganizationName())
                    || org.equalsIgnoreCase(trimToEmpty(r.getOrganizationName()));
            boolean posMatch = position.isEmpty() || !notEmpty(r.getPositionName())
                    || position.equalsIgnoreCase(trimToEmpty(r.getPositionName()));
            if (companyMatch && orgMatch && posMatch) {
                // 公司+机构+岗位 完全对应（空值视为通配）
                return r;
            }
            if (position.isEmpty() && companyMatch && orgMatch && domainMatch) {
                return r;
            }
            if (org.isEmpty() && companyMatch && posMatch && domainMatch) {
                return r;
            }
        }
        return null;
    }

    /** 名称匹配：忽略大小写，支持包含关系（如"易方达"与"易方达基金"） */
    private boolean nameMatch(String a, String b) {
        String x = trimToEmpty(a).toLowerCase(Locale.ROOT);
        String y = trimToEmpty(b).toLowerCase(Locale.ROOT);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        return x.equals(y) || x.contains(y) || y.contains(x);
    }

    // ==================== 下拉选项 ====================

    @Transactional
    public Map<String, Object> options() {
        Long userId = SecurityUtils.getCurrentUserId();
        ensureInitialized(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applyStatuses", APPLY_STATUSES);
        data.put("sourceTypes", SOURCE_TYPES);
        data.put("channels", mergeDistinct(DEFAULT_CHANNELS, recordRepository.findDistinctChannels(userId)));
        data.put("currentStages", mergeDistinct(DEFAULT_STAGES, recordRepository.findDistinctCurrentStages(userId)));
        data.put("priorities", PRIORITIES);
        data.put("batchNames", mergeDistinct(List.of(ApplicationInitData.DEFAULT_BATCH),
                recordRepository.findDistinctBatchNames(userId)));
        data.put("companyNatures", mergeDistinct(List.of(), recordRepository.findDistinctCompanyNatures(userId)));
        data.put("categoryTypes", mergeDistinct(List.of(), recordRepository.findDistinctCategoryTypes(userId)));
        data.put("stageNames", DEFAULT_STAGES);
        data.put("stageStatuses", List.of("待开始", "已完成", "通过", "未通过", "放弃", "未参加"));
        data.put("stageResults", List.of("通过", "挂", "拒", "offer"));
        return data;
    }

    private List<String> mergeDistinct(List<String> defaults, List<String> dbValues) {
        LinkedHashSet<String> set = new LinkedHashSet<>(defaults);
        set.addAll(dbValues);
        return new ArrayList<>(set);
    }

    // ==================== 流程记录 ====================

    @Transactional(readOnly = true)
    public List<ApplicationStageRecordDTO> listStages(Long recordId) {
        getOwnedRecord(recordId);
        Long userId = SecurityUtils.getCurrentUserId();
        return stageRepository
                .findByApplicationRecordIdAndUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(recordId, userId)
                .stream().map(ApplicationStageRecordDTO::fromEntity).toList();
    }

    @Transactional
    public ApplicationStageRecordDTO createStage(Long recordId, ApplicationStageRecordDTO dto) {
        getOwnedRecord(recordId);
        Long userId = SecurityUtils.getCurrentUserId();
        ApplicationStageRecord stage = new ApplicationStageRecord();
        stage.setUserId(userId);
        stage.setApplicationRecordId(recordId);
        dto.applyTo(stage);
        long count = stageRepository.countByApplicationRecordIdAndUserIdAndDeletedFalse(recordId, userId);
        if (stage.getSortOrder() == null || stage.getSortOrder() == 0) {
            stage.setSortOrder((int) count + 1);
        }
        ApplicationStageRecord saved = stageRepository.save(stage);
        syncCurrentStage(recordId, saved);
        return ApplicationStageRecordDTO.fromEntity(saved);
    }

    @Transactional
    public ApplicationStageRecordDTO updateStage(Long stageId, ApplicationStageRecordDTO dto) {
        ApplicationStageRecord stage = getOwnedStage(stageId);
        dto.applyTo(stage);
        ApplicationStageRecord saved = stageRepository.save(stage);
        syncCurrentStage(saved.getApplicationRecordId(), saved);
        return ApplicationStageRecordDTO.fromEntity(saved);
    }

    @Transactional
    public void deleteStage(Long stageId) {
        ApplicationStageRecord stage = getOwnedStage(stageId);
        stage.setDeleted(true);
        stageRepository.save(stage);
    }

    private void syncCurrentStage(Long recordId, ApplicationStageRecord latest) {
        if (latest.getStageName() == null) {
            return;
        }
        recordRepository.findById(recordId).ifPresent(record -> {
            record.setCurrentStage(latest.getStageName());
            // 主表状态与阶段联动（仅在主表仍为早期状态时推进）
            String status = latest.getStageStatus();
            if ("通过".equals(latest.getStageResult()) || "通过".equals(status)) {
                record.setApplyStatus(mapStageToStatus(latest.getStageName(), record.getApplyStatus()));
            }
            recordRepository.save(record);
        });
    }

    private String mapStageToStatus(String stageName, String current) {
        if ("offer".equalsIgnoreCase(stageName)) {
            return "offer";
        }
        int idx = DEFAULT_STAGES.indexOf(stageName);
        int curIdx = APPLY_STATUSES.indexOf(current);
        return idx >= 0 ? APPLY_STATUSES.get(Math.min(4 + idx, 12))
                : (curIdx >= 0 ? current : "其他");
    }

    private ApplicationStageRecord getOwnedStage(Long stageId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return stageRepository.findById(stageId)
                .filter(s -> userId.equals(s.getUserId()) && !Boolean.TRUE.equals(s.getDeleted()))
                .orElseThrow(() -> new BusinessException(404, "流程记录不存在或无权访问"));
    }

    // ==================== 初始化 ====================

    /**
     * 首次使用时初始化投递信息表（企业清单 170 条 + 体制清单 6 条 + 3 条已投手动记录）。
     * 以用户维度幂等：用户名下已有记录（含逻辑删除）则跳过。
     */
    @Transactional
    public void ensureInitialized(Long userId) {
        if (recordRepository.countByUserId(userId) > 0) {
            return;
        }
        log.info("初始化用户 {} 的投递信息表数据", userId);
        int order = 1;
        LocalDateTime now = LocalDateTime.now();
        for (String item : ApplicationInitData.INIT_COMPANIES) {
            String[] parts = item.split("\\|", -1);
            String company = parts[0].trim();
            String org = parts.length > 1 && !"空".equals(parts[1].trim()) ? parts[1].trim() : "";
            ApplicationRecord r = new ApplicationRecord();
            r.setUserId(userId);
            r.setBatchName(ApplicationInitData.DEFAULT_BATCH);
            r.setSourceType("企业");
            r.setCategoryType("");
            r.setCompanyName(company);
            r.setOrganizationName(org);
            r.setPositionName("");
            r.setApplyStatus(ApplicationInitData.STATUS_NOT_APPLIED);
            r.setApplicationChannel(ApplicationInitData.CHANNEL_INIT);
            r.setSortOrder(order++);
            r.setLastVisitedAt(now);
            recordRepository.save(r);
        }
        for (String item : ApplicationInitData.INIT_CATEGORIES) {
            String[] parts = item.split("\\|", -1);
            ApplicationRecord r = new ApplicationRecord();
            r.setUserId(userId);
            r.setBatchName(ApplicationInitData.DEFAULT_BATCH);
            r.setSourceType("体制");
            r.setCategoryType(parts[0].trim());
            r.setCompanyName(parts.length > 1 ? parts[1].trim() : "");
            r.setOrganizationName("");
            r.setPositionName("");
            r.setApplyStatus(ApplicationInitData.STATUS_NOT_APPLIED);
            r.setApplicationChannel(ApplicationInitData.CHANNEL_INIT);
            r.setSortOrder(order++);
            r.setLastVisitedAt(now);
            recordRepository.save(r);
        }
        // 手动指定的已投记录：已存在同名/近似公司则更新，否则新增
        for (ApplicationInitData.ManualRecord m : ApplicationInitData.MANUAL_APPLIED_RECORDS) {
            ApplicationRecord target = recordRepository.findByUserIdAndDeletedFalse(userId).stream()
                    .filter(r -> nameMatch(m.companyName, r.getCompanyName()))
                    .filter(r -> !notEmpty(r.getOrganizationName()))
                    .findFirst().orElse(null);
            if (target == null) {
                target = new ApplicationRecord();
                target.setUserId(userId);
                target.setBatchName(ApplicationInitData.DEFAULT_BATCH);
                target.setSourceType("企业");
                target.setCompanyName(m.companyName);
                target.setOrganizationName("");
                target.setPositionName("");
                target.setSortOrder(order++);
            }
            target.setApplyStatus(ApplicationInitData.STATUS_APPLIED);
            target.setOfficialWebsite(m.url);
            target.setRecruitmentUrl(m.url);
            target.setPublicAccount(m.publicAccount);
            target.setCompanyNature(m.companyNature);
            target.setApplicationChannel("官网 / 公众号");
            target.setRemark(m.remark);
            if (notEmpty(m.warningNote)) {
                target.setWarningNote(m.warningNote);
            }
            target.setAppliedAt(target.getAppliedAt() == null ? now : target.getAppliedAt());
            recordRepository.save(target);
        }
        log.info("用户 {} 投递信息表初始化完成：企业 {} 条 + 体制 {} 条 + 手动已投 {} 条",
                userId, ApplicationInitData.INIT_COMPANIES.length,
                ApplicationInitData.INIT_CATEGORIES.length, ApplicationInitData.MANUAL_APPLIED_RECORDS.size());
    }

    // ==================== 工具 ====================

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
