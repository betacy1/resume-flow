package com.resumeflow.vo;

import lombok.Data;

import java.util.List;

/**
 * 自动填充匹配响应 VO
 */
@Data
public class AutofillMatchResponse {

    private List<MatchResult> matches;
    private List<SkippedField> skipped;
    private List<UnmatchedField> unmatched;
    /** 当前模板应填经历计划（有序）：插件据此判断需要新增多少个经历块 */
    private List<ExperiencePlanItem> experiencePlan;

    /**
     * 经历计划项：当前模板应填的一段经历/项目（含顺序）
     */
    @Data
    public static class ExperiencePlanItem {
        /** internship / project */
        private String type;
        private Long id;
        private String name;
        private String startDate;
        private String endDate;

        public ExperiencePlanItem(String type, Long id, String name, String startDate, String endDate) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    /**
     * 匹配结果
     */
    @Data
    public static class MatchResult {
        private String fieldId;
        private String matchedFieldKey;
        private String matchedFieldName;
        private String value;
        private double confidence;
        /** 已废弃：敏感字段概念已移除，恒为 false，仅为兼容旧版插件保留 */
        private boolean sensitive;
        private String reason;
        /** 命中的内容版本描述，如 big_tech/within_300 */
        private String variantDesc;
        /** 绑定的经历记录引用，如 internship:2 / project:5；非经历字段为 null */
        private String recordRef;
        /** 绑定记录名称（如“京东集团-京东科技实习”），预览分组展示用 */
        private String recordName;
        /** 预览分组：work_experience / project_experience / skill / material / education / basic */
        private String group;
        /** 疑似错误：值类型与字段语义冲突（如邮箱字段推荐“英语”），默认不勾选 */
        private boolean suspicious;
        /** 疑似错误原因（suspicious=true 时说明冲突点） */
        private String suspiciousReason;

        public MatchResult(String fieldId,
                           String matchedFieldKey,
                           String matchedFieldName,
                           String value,
                           double confidence,
                           boolean sensitive,
                           String reason) {
            this(fieldId, matchedFieldKey, matchedFieldName, value, confidence, sensitive, reason, null);
        }

        public MatchResult(String fieldId,
                           String matchedFieldKey,
                           String matchedFieldName,
                           String value,
                           double confidence,
                           boolean sensitive,
                           String reason,
                           String variantDesc) {
            this.fieldId = fieldId;
            this.matchedFieldKey = matchedFieldKey;
            this.matchedFieldName = matchedFieldName;
            this.value = value;
            this.confidence = confidence;
            this.sensitive = sensitive;
            this.reason = reason;
            this.variantDesc = variantDesc;
        }
    }

    /**
     * 跳过字段
     */
    @Data
    public static class SkippedField {
        private String fieldId;
        private String reason;
        /** 已废弃：敏感字段概念已移除，恒为 false/null，仅为兼容旧版插件保留 */
        private Boolean sensitive;

        public SkippedField(String fieldId, String reason, Boolean sensitive) {
            this.fieldId = fieldId;
            this.reason = reason;
            this.sensitive = sensitive;
        }
    }

    @Data
    public static class UnmatchedField {
        private String fieldId;
        private String reason;

        public UnmatchedField(String fieldId, String reason) {
            this.fieldId = fieldId;
            this.reason = reason;
        }
    }
}
