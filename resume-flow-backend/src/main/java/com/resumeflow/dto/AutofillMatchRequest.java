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
    /** 填充方式：auto 一键自动填充 / manual 手动点选填充，缺省 auto */
    private String fillType;
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
        /** 所属重复块类型：internship（工作/实习经历）/ project（项目经历）/ language（语言能力），无块为 null */
        private String blockType;
        /** 所属块序号（0 起）；同一块内字段绑定同一条经历记录 */
        private Integer blockIndex;
        /** 所属模块标题（如“工作经历”），辅助分组与语义判定 */
        private String sectionTitle;
    }
}
