package com.resumeflow.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserCustomFieldDTO {

    private Long id;
    private Long templateId;
    private String fieldKey;
    private String fieldName;
    private String fieldType;
    private String fieldCategory;
    private String fieldValue;
    private List<String> matchKeywords;
    /** 适用模板 id 列表；为空表示全部模板适用 */
    private List<Long> templateIds;
    /** 字数档位：within_200 / within_300 / within_500 / within_1000 / full */
    private String lengthType;
    /** 是否参与一键自动填充 */
    private Boolean autoFillEnabled;
    /** 是否允许插件手动点选填充 */
    private Boolean manualFillEnabled;
    /** 乐观锁版本号：插件保存时提交，用于冲突检测 */
    private Long version;
    /** 来源引用，如 internship:1 / project:3 / material:5 */
    private String sourceRef;
    private Boolean sensitive;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime updateTime;
}
