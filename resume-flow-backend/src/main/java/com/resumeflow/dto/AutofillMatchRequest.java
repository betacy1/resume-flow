package com.resumeflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 自动填充匹配请求 DTO
 */
@Data
public class AutofillMatchRequest {

    private Long templateId;
    /** 受众类型（可覆盖模板上的 audienceType）：big_tech / state_owned / bank / general_backend */
    private String audienceType;
    /** 岗位方向：backend / ai / fintech，为空时按内置顺序回退 */
    private String jobDirection;
    /** 优先实习经历 id；为空时按模板+岗位方向自动推荐 */
    private Long preferredInternshipId;
    private String pageUrl;
    private String pageTitle;
    private List<FieldInfo> fields;

    /**
     * 字段信息
     */
    @Data
    public static class FieldInfo {
        private String fieldId;
        private String tagName;
        private String label;
        private String placeholder;
        private String type;
        private String name;
        private String id;
        private String className;
        private String ariaLabel;
        private String parentText;
        private String questionText;
        private String nearbyText;
        /** 页面识别到的字数限制（如 500字以内），优先级高于后端从文本中提取 */
        private Integer wordLimit;
        private Boolean visible;
        private Boolean disabled;
    }
}
