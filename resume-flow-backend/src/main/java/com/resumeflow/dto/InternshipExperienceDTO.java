package com.resumeflow.dto;

import lombok.Data;

/**
 * 实习经历 DTO
 */
@Data
public class InternshipExperienceDTO {

    private Long id;
    private String company;
    private String department;
    /** 工作地点（城市） */
    private String city;
    private String position;
    private String startDate;
    private String endDate;
    private String techStack;
    private String highlights;
    private Boolean isDefault;
    private String shortName;
    private String description;
    private Integer sortOrder;
    /** 排除的受众场景（逗号分隔，如 big_tech） */
    private String audienceExclude;
    /** 模板优先级 JSON，如 {"bank":1,"state_owned":2} */
    private String templatePriority;
    /** 证明人姓名（可为空，字段始终存在） */
    private String certifierName;
    /** 证明人单位 */
    private String certifierCompany;
    /** 证明人职务 */
    private String certifierPosition;
    /** 证明人单位及职务 */
    private String certifierCompanyAndPosition;
    /** 证明人联系电话 */
    private String certifierPhone;
    /** 证明人邮箱 */
    private String certifierEmail;
    /** 证明人与本人关系 */
    private String certifierRelation;
    /** 证明人备注 */
    private String certifierRemark;
}
