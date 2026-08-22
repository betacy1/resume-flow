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
        private boolean sensitive;
        private String reason;
        /** 命中的内容版本描述，如 big_tech/within_300 */
        private String variantDesc;

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
