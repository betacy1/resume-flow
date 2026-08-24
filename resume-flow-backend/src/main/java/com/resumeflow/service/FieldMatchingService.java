package com.resumeflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeflow.dto.AutofillMatchRequest;
import com.resumeflow.dto.AutofillMatchRequest.FieldInfo;
import com.resumeflow.entity.AnswerMaterial;
import com.resumeflow.entity.ApplicationTemplate;
import com.resumeflow.entity.ContentVariant;
import com.resumeflow.entity.EducationExperience;
import com.resumeflow.entity.InternshipExperience;
import com.resumeflow.entity.ProjectExperience;
import com.resumeflow.entity.TemplateExperienceConfig;
import com.resumeflow.entity.UserCustomField;
import com.resumeflow.entity.UserProfile;
import com.resumeflow.repository.AnswerMaterialRepository;
import com.resumeflow.repository.ApplicationTemplateRepository;
import com.resumeflow.repository.EducationExperienceRepository;
import com.resumeflow.repository.InternshipExperienceRepository;
import com.resumeflow.repository.ProjectExperienceRepository;
import com.resumeflow.repository.TemplateExperienceConfigRepository;
import com.resumeflow.repository.UserCustomFieldRepository;
import com.resumeflow.repository.UserProfileRepository;
import com.resumeflow.security.SecurityUtils;
import com.resumeflow.vo.AutofillMatchResponse;
import com.resumeflow.vo.AutofillMatchResponse.MatchResult;
import com.resumeflow.vo.AutofillMatchResponse.SkippedField;
import com.resumeflow.vo.AutofillMatchResponse.UnmatchedField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字段匹配服务（五级匹配 + 日期多格式 + 内容版本选择）
 * 匹配链：精确关键词 → 字段别名 → 字段类型 → 岗位模板 → 模糊相似度；
 * 日期字段单独走日期匹配（起止语义 + 页面格式探测）；
 * 长文本字段按 模板受众 × 页面字数限制 自动选择 content_variant 版本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "resumeflow")
public class FieldMatchingService {

    private final UserProfileRepository userProfileRepository;
    private final EducationExperienceRepository educationRepository;
    private final InternshipExperienceRepository internshipRepository;
    private final ProjectExperienceRepository projectRepository;
    private final ApplicationTemplateRepository templateRepository;
    private final AnswerMaterialRepository materialRepository;
    private final UserCustomFieldRepository userCustomFieldRepository;
    private final TemplateExperienceConfigRepository templateConfigRepository;
    private final ObjectMapper objectMapper;
    private final DateFormatService dateFormatService;
    private final ContentVariantService contentVariantService;

    private List<String> sensitiveKeywords;
    /** 敏感字段是否也自动填写（默认 true：匹配结果返回并标注 sensitive，由前端决定展示策略） */
    private boolean autoFillSensitive = true;

    public void setSensitiveKeywords(List<String> sensitiveKeywords) {
        this.sensitiveKeywords = sensitiveKeywords;
    }

    public void setAutoFillSensitive(boolean autoFillSensitive) {
        this.autoFillSensitive = autoFillSensitive;
    }

    /** 字数限制提取：500字以内 / 不超过300字 / 限200字 */
    private static final Pattern WORD_LIMIT_PATTERN =
            Pattern.compile("(?:不超过|限|最多)?\\s*(\\d{2,4})\\s*字(?:以内|内|左右)?");

    // ==================== 实习字段同义词（四类字段类型） ====================

    /** 实习整体描述类（段落格式） */
    private static final List<String> INTERNSHIP_OVERVIEW_KEYWORDS = List.of(
            "实习描述", "实习经历", "工作经历", "工作描述", "工作介绍", "岗位描述", "岗位介绍",
            "职责描述", "任职描述", "经历描述", "经历介绍", "请描述实习经历", "请描述工作经历",
            "请填写实习内容", "请填写工作内容", "实习期间主要工作", "在岗期间主要工作",
            "工作概述", "经历概述", "工作总结", "实习总结", "实践经历", "实践内容");

    /** 主要职责类（分条格式） */
    private static final List<String> INTERNSHIP_RESPONSIBILITY_KEYWORDS = List.of(
            "主要职责", "工作职责", "岗位职责", "实习职责", "任职职责", "职责内容", "负责内容",
            "主要工作", "工作任务", "承担工作", "参与工作", "负责事项", "工作事项",
            "工作职责描述", "岗位职责描述", "实习工作内容", "实习期间职责",
            "请描述你的主要职责", "请描述你承担的工作", "请填写主要工作内容");

    /** 工作成果类 */
    private static final List<String> INTERNSHIP_RESULT_KEYWORDS = List.of(
            "工作成果", "实习成果", "项目成果", "工作业绩", "实习收获", "主要成绩", "个人贡献",
            "产出成果", "成果描述", "取得成果", "工作成效", "项目成效", "业务价值", "实践成果",
            "达成效果", "请描述工作成果", "请填写项目成果", "请描述主要贡献");

    /** 技术栈类（仅技术关键词） */
    private static final List<String> INTERNSHIP_TECH_KEYWORDS = List.of(
            "技术栈", "使用技术", "开发环境", "开发工具", "技术工具", "技术框架", "主要技术",
            "相关技术", "项目技术", "编程语言", "技术关键词", "技能标签", "使用框架", "技术路线");

    /** 通用实习信号：模糊长文本字段归为合并型 */
    private static final List<String> INTERNSHIP_SIGNAL_KEYWORDS = List.of(
            "实习", "工作经历", "岗位", "职责", "工作内容", "实践", "成果", "技术栈");

    /** 专业技能字段匹配关键词：命中后按当前模板受众自动填入对应技能版本 */
    private static final List<String> SKILL_KEYWORDS = List.of(
            "专业技能", "技能", "个人技能", "技能特长", "技术能力", "技术栈", "掌握技能", "专业能力",
            "开发技能", "计算机技能", "编程技能", "熟悉技术", "软件技能", "核心技能", "技术关键词",
            "技能标签", "IT技能", "技术专长", "相关技能");

    /** 技能字段中优先填关键词形式的字段名信号 */
    private static final List<String> SKILL_KEYWORD_FORM_SIGNALS = List.of("技术栈", "技术关键词", "技能标签");

    /** 技能字段中优先填段落形式的字段名信号 */
    private static final List<String> SKILL_PARAGRAPH_FORM_SIGNALS = List.of("专业技能", "技术能力", "技能特长");

    /** 实习默认优先级：受众:岗位方向 → 公司关键词顺序（大厂版内置排除银行类实习） */
    private static final Map<String, List<String>> INTERNSHIP_PRIORITY_TABLE = Map.of(
            "state_owned:backend", List.of("工商银行", "京东"),
            "state_owned:ai", List.of("京东", "工商银行"),
            "big_tech:backend", List.of("字节跳动", "京东"),
            "big_tech:ai", List.of("京东"),
            "bank", List.of("工商银行", "字节跳动"),
            "general", List.of("字节跳动", "京东", "工商银行"));

    private static final Map<String, List<String>> BUILTIN_KEYWORDS = new LinkedHashMap<>();

    static {
        BUILTIN_KEYWORDS.put("name", Arrays.asList("姓名", "name", "full name", "真实姓名"));
        BUILTIN_KEYWORDS.put("gender", Arrays.asList("性别", "gender"));
        BUILTIN_KEYWORDS.put("phone", Arrays.asList("手机", "手机号", "电话", "联系方式", "phone", "mobile", "tel"));
        BUILTIN_KEYWORDS.put("email", Arrays.asList("邮箱", "电子邮箱", "email", "mail"));
        BUILTIN_KEYWORDS.put("qq", Arrays.asList("qq", "qq号"));
        BUILTIN_KEYWORDS.put("wechat", Arrays.asList("微信", "微信号", "wechat"));
        BUILTIN_KEYWORDS.put("currentLocation", Arrays.asList("当前所在地", "现居住地", "所在地", "current location"));
        BUILTIN_KEYWORDS.put("school", Arrays.asList("学校", "院校", "毕业院校", "university", "college", "school"));
        BUILTIN_KEYWORDS.put("degree", Arrays.asList("学历", "学位", "degree", "education", "最高学历"));
        BUILTIN_KEYWORDS.put("major", Arrays.asList("专业", "所学专业", "major"));
        BUILTIN_KEYWORDS.put("graduationDate", Arrays.asList("毕业时间", "毕业年份", "graduation", "预计毕业", "graduate date"));
        BUILTIN_KEYWORDS.put("gpa", Arrays.asList("gpa", "绩点"));
        BUILTIN_KEYWORDS.put("rank", Arrays.asList("成绩排名", "排名", "rank"));
        BUILTIN_KEYWORDS.put("thesis", Arrays.asList("论文", "毕业论文", "研究课题"));
        BUILTIN_KEYWORDS.put("researchDirection", Arrays.asList("研究方向", "研究内容"));
        BUILTIN_KEYWORDS.put("expectedCity", Arrays.asList("期望城市", "工作城市", "意向城市", "工作地点", "expected city"));
        BUILTIN_KEYWORDS.put("expectedPosition", Arrays.asList("期望岗位", "应聘岗位", "意向岗位", "投递岗位", "position", "job title"));
        BUILTIN_KEYWORDS.put("applicantType", Arrays.asList("应届生", "应聘类型", "毕业生类别", "身份类型"));
        BUILTIN_KEYWORDS.put("selfEvaluation", Arrays.asList("自我评价", "个人评价", "综合评价", "个人优势", "个人总结"));
        BUILTIN_KEYWORDS.put("careerPlan", Arrays.asList("职业规划", "未来规划", "职业发展", "发展方向"));
        BUILTIN_KEYWORDS.put("hobby", Arrays.asList("兴趣特长", "兴趣爱好", "爱好特长", "个人特长"));
        BUILTIN_KEYWORDS.put("supplement", Arrays.asList("补充信息", "其他信息", "其他相关信息", "备注"));
        BUILTIN_KEYWORDS.put("whyCompany", Arrays.asList("为什么选择本公司", "选择我们的原因", "为什么投递本公司"));
        BUILTIN_KEYWORDS.put("whyPosition", Arrays.asList("为什么选择该岗位", "岗位理解", "应聘原因"));
        BUILTIN_KEYWORDS.put("aiCollaboration", Arrays.asList("ai协作", "ai工具", "人工智能工具", "ai辅助开发"));
    }

    /** 内置日期关键词 → 字段 key（毕业时间走 graduationDate 特殊逻辑） */
    private static final Map<String, String> DATE_KEYWORD_FIELDS = new LinkedHashMap<>();

    static {
        DATE_KEYWORD_FIELDS.put("入学时间", "eduStartDate");
        DATE_KEYWORD_FIELDS.put("毕业时间", "graduationDate");
        DATE_KEYWORD_FIELDS.put("毕业年月", "graduationDate");
        DATE_KEYWORD_FIELDS.put("毕业年份", "graduationDate");
        DATE_KEYWORD_FIELDS.put("预计毕业", "graduationDate");
        DATE_KEYWORD_FIELDS.put("开始时间", "startDate");
        DATE_KEYWORD_FIELDS.put("起始时间", "startDate");
        DATE_KEYWORD_FIELDS.put("入职时间", "startDate");
        DATE_KEYWORD_FIELDS.put("start date", "startDate");
        DATE_KEYWORD_FIELDS.put("结束时间", "endDate");
        DATE_KEYWORD_FIELDS.put("离职时间", "endDate");
        DATE_KEYWORD_FIELDS.put("end date", "endDate");
    }

    public AutofillMatchResponse match(AutofillMatchRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        ApplicationTemplate selectedTemplate = resolveTemplate(userId, request.getTemplateId());
        String audience = resolveAudience(request, selectedTemplate);
        Map<String, TemplateExperienceConfig> experienceConfigs = loadExperienceConfigs(userId, selectedTemplate);
        List<FieldCandidate> candidates = buildCandidates(userId, request.getTemplateId(), selectedTemplate, experienceConfigs);
        List<InternshipExperience> internshipList = internshipRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);

        List<MatchResult> matches = new ArrayList<>();
        List<SkippedField> skipped = new ArrayList<>();
        List<UnmatchedField> unmatched = new ArrayList<>();

        if (request.getFields() == null || request.getFields().isEmpty()) {
            AutofillMatchResponse response = new AutofillMatchResponse();
            response.setMatches(matches);
            response.setSkipped(skipped);
            response.setUnmatched(unmatched);
            return response;
        }

        for (FieldInfo field : request.getFields()) {
            if (Boolean.FALSE.equals(field.getVisible())) {
                skipped.add(new SkippedField(field.getFieldId(), "字段不可见，跳过", false));
                continue;
            }
            if (Boolean.TRUE.equals(field.getDisabled())) {
                skipped.add(new SkippedField(field.getFieldId(), "字段已禁用，跳过", false));
                continue;
            }

            String text = combineFieldText(field);

            // 日期字段优先走日期匹配（起止语义 + 页面格式动态格式化）；
            // 仅当文本含明确日期关键词或 input type 为 date/month 时触发，避免普通字段误判。
            // 纯"年"/"月"拆分字段（如"入职年"）不走此分支，由自定义字段规则匹配。
            if (isDateControl(field) || hasExplicitDateKeyword(text)) {
                MatchPick datePick = datePick(text, field, candidates);
                if (datePick != null) {
                    if (!hasText(datePick.candidate.value)) {
                        skipped.add(new SkippedField(field.getFieldId(), "日期字段已匹配但日期为空", false));
                        continue;
                    }
                    matches.add(new MatchResult(
                            field.getFieldId(),
                            datePick.candidate.fieldKey,
                            datePick.candidate.fieldName,
                            datePick.candidate.value,
                            datePick.confidence,
                            false,
                            datePick.reason,
                            null
                    ));
                    continue;
                }
            }

            // 专业技能字段：优先于实习/项目分类，按当前模板受众选择技能内容版本（关键词/简短/完整）
            SkillFieldPlan skillPlan = classifySkillField(text, field);
            if (skillPlan != null) {
                MatchResult skillMatch = skillVariantMatch(userId, field, skillPlan,
                        audience, request.getJobDirection());
                if (skillMatch != null) {
                    matches.add(skillMatch);
                    continue;
                }
            }

            MatchPick pick = pickCandidate(text, field, candidates, request.getTemplateId());
            boolean longText = isLongTextField(field);

            // 实习类字段分类（整体描述/主要职责/成果/技术栈/合并型）
            String internFieldType = longText ? classifyInternshipFieldType(text) : null;
            boolean internExplicit = internFieldType != null;
            if (internFieldType == null && longText && hasInternshipSignal(text)
                    && !text.contains("项目") && !text.contains("自我评价")) {
                internFieldType = "internship_combined";
            }

            // 实习类字段优先走实习推荐（命中同义词的长文本框优先于模糊匹配的低置信度通用候选）；
            // 实习无可用版本时回退常规候选。模糊信号（非显式命中）仅在常规候选缺失时兜底。
            if (internFieldType != null && (internExplicit || pick == null)) {
                MatchResult internMatch = internshipVariantMatch(userId, field, internFieldType,
                        audience, request.getJobDirection(), request.getPreferredInternshipId(), internshipList,
                        experienceConfigs);
                if (internMatch != null) {
                    matches.add(internMatch);
                    continue;
                }
            }
            if (pick == null) {
                unmatched.add(new UnmatchedField(field.getFieldId(), "未匹配到可用字段"));
                continue;
            }

            boolean sensitive = pick.candidate.sensitive || isSensitive(text);
            if (sensitive && !autoFillSensitive) {
                skipped.add(new SkippedField(field.getFieldId(), "敏感字段，需手动确认", true));
                continue;
            }
            if (!hasText(pick.candidate.value)) {
                skipped.add(new SkippedField(field.getFieldId(), "字段已匹配但内容为空", false));
                continue;
            }

            // 长文本字段：按模板受众 × 岗位方向 × 字段类型 × 字数限制选择内容版本
            String value = pick.candidate.value;
            String variantDesc = null;
            if (pick.candidate.sourceType != null && pick.candidate.sourceId != null && longText) {
                Integer wordLimit = field.getWordLimit() != null ? field.getWordLimit() : parseWordLimit(text);
                String fieldType = resolveVariantFieldType(pick.candidate.sourceType, internFieldType, text);
                Optional<ContentVariantService.VariantPick> variant = contentVariantService.pickVariant(
                        userId, pick.candidate.sourceType, pick.candidate.sourceId,
                        audience, request.getJobDirection(), fieldType, wordLimit);
                if (variant.isPresent()) {
                    value = variant.get().content();
                    variantDesc = variant.get().audienceType() + "/" + variant.get().jobDirection() + "/"
                            + variant.get().lengthType() + "/" + variant.get().fieldType();
                }
            }

            matches.add(new MatchResult(
                    field.getFieldId(),
                    pick.candidate.fieldKey,
                    pick.candidate.fieldName,
                    value,
                    pick.confidence,
                    sensitive,
                    pick.reason,
                    variantDesc
            ));
        }

        AutofillMatchResponse response = new AutofillMatchResponse();
        response.setMatches(matches);
        response.setSkipped(skipped);
        response.setUnmatched(unmatched);
        return response;
    }

    // ==================== 日期匹配 ====================

    private boolean isDateControl(FieldInfo field) {
        String type = lower(field.getType());
        return "date".equals(type) || "month".equals(type);
    }

    private boolean hasExplicitDateKeyword(String text) {
        if (!hasText(text)) return false;
        for (String keyword : DATE_KEYWORD_FIELDS.keySet()) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 日期字段匹配：先按日期关键词定位目标字段（毕业时间/经历起止），再按页面格式输出；
     * 字段上下文出现某经历关键词时，优先取该经历的日期。
     */
    private MatchPick datePick(String text, FieldInfo field, List<FieldCandidate> candidates) {
        String target = null;
        for (Map.Entry<String, String> entry : DATE_KEYWORD_FIELDS.entrySet()) {
            if (text.contains(entry.getKey().toLowerCase())) {
                target = entry.getValue();
                break;
            }
        }

        // 毕业时间：使用 profile.graduationDate
        if ("graduationDate".equals(target)) {
            FieldCandidate graduation = candidates.stream()
                    .filter(c -> "graduationDate".equals(c.fieldKey) && hasText(c.value))
                    .findFirst().orElse(null);
            if (graduation != null) {
                String fmt = dateFormatService.detectFormat(field.getType(), field.getPlaceholder(), field.getLabel());
                String value = dateFormatService.format(graduation.value, fmt);
                FieldCandidate dated = new FieldCandidate("graduationDate", "毕业时间", value,
                        graduation.keywords, false, field.getType(), null, null, null, null, null);
                return new MatchPick(dated, 0.88, "日期匹配: 毕业时间 → " + fmt);
            }
            return null;
        }

        // 经历起止时间：优先选择字段上下文中出现关键词的经历（公司名/项目名）
        FieldCandidate chosen = null;
        for (FieldCandidate candidate : candidates) {
            if (candidate.startDate == null && candidate.endDate == null) {
                continue;
            }
            for (String keyword : candidate.keywords) {
                if (hasText(keyword) && text.contains(keyword.toLowerCase())) {
                    chosen = candidate;
                    break;
                }
            }
            if (chosen != null) {
                break;
            }
        }
        // 未定位到具体经历：取第一个带日期的经历（按排序，通常为最近一段）
        if (chosen == null) {
            chosen = candidates.stream()
                    .filter(c -> c.startDate != null || c.endDate != null)
                    .findFirst().orElse(null);
        }
        if (chosen == null) {
            return null;
        }

        // 关键词未明确起止时，默认按开始时间处理（页面多数先填开始时间）
        boolean end = "endDate".equals(target);
        String std = end ? chosen.endDate : chosen.startDate;
        if (!hasText(std)) {
            return null;
        }
        String fmt = dateFormatService.detectFormat(field.getType(), field.getPlaceholder(), field.getLabel());
        String value = dateFormatService.format(std, fmt);
        String key = chosen.fieldKey + (end ? ".endDate" : ".startDate");
        FieldCandidate dated = new FieldCandidate(key, chosen.fieldName + (end ? "(结束时间)" : "(开始时间)"),
                value, chosen.keywords, false, field.getType(), null, null, null, null, null);
        return new MatchPick(dated, 0.85, "日期匹配: " + chosen.fieldName + " " + (end ? "结束" : "开始") + "时间 → " + fmt);
    }

    // ==================== 五级匹配 ====================

    private MatchPick pickCandidate(String text, FieldInfo field, List<FieldCandidate> candidates, Long templateId) {
        MatchPick precise = preciseKeywordPick(text, candidates, templateId);
        if (precise != null) {
            return precise;
        }

        MatchPick alias = aliasPick(text, candidates, templateId);
        if (alias != null) {
            return alias;
        }

        MatchPick typePick = fieldTypePick(text, field, candidates, templateId);
        if (typePick != null) {
            return typePick;
        }

        MatchPick templatePick = templatePriorityPick(field, candidates, templateId);
        if (templatePick != null) {
            return templatePick;
        }

        return fuzzyPick(text, candidates, templateId);
    }

    private MatchPick preciseKeywordPick(String text, List<FieldCandidate> candidates, Long templateId) {
        MatchPick best = null;
        double bestScore = 0;
        int bestKeywordLength = 0;
        for (FieldCandidate candidate : candidates) {
            for (String keyword : candidate.keywords) {
                if (!hasText(keyword)) continue;
                String normalizedKeyword = keyword.toLowerCase();
                if (text.contains(normalizedKeyword)) {
                    // 短关键词（<4 字符）略降权，避免子串误命中；同分时保留关键词更长的结果，确定性更强
                    double score = 0.90 + Math.min(normalizedKeyword.length() * 0.01, 0.08);
                    if (normalizedKeyword.length() < 4) {
                        score -= 0.02;
                    }
                    score += templateBoost(templateId, candidate.templateId);
                    if (score > bestScore || (score == bestScore && normalizedKeyword.length() > bestKeywordLength)) {
                        bestScore = score;
                        bestKeywordLength = normalizedKeyword.length();
                        best = new MatchPick(candidate, clamp(score), "精确关键词匹配: " + keyword);
                    }
                }
            }
        }
        return best;
    }

    private MatchPick aliasPick(String text, List<FieldCandidate> candidates, Long templateId) {
        MatchPick best = null;
        double bestScore = 0;
        for (FieldCandidate candidate : candidates) {
            List<String> aliases = List.of(candidate.fieldName, candidate.fieldKey);
            for (String alias : aliases) {
                if (!hasText(alias)) continue;
                String lowerAlias = alias.toLowerCase();
                if (text.contains(lowerAlias)) {
                    double score = 0.82 + Math.min(lowerAlias.length() * 0.008, 0.10);
                    score += templateBoost(templateId, candidate.templateId);
                    if (score > bestScore) {
                        bestScore = score;
                        best = new MatchPick(candidate, clamp(score), "字段别名匹配: " + alias);
                    }
                }
            }
        }
        return best;
    }

    private MatchPick fieldTypePick(String text, FieldInfo field, List<FieldCandidate> candidates, Long templateId) {
        String type = lower(field.getType());
        boolean isLongText = isLongTextField(field);
        String preferredKey = null;
        if ("input".equals(type)) {
            if (text.contains("邮箱") || text.contains("email")) {
                preferredKey = "email";
            } else if (text.contains("手机") || text.contains("电话") || text.contains("phone")) {
                preferredKey = "phone";
            } else {
                preferredKey = "name";
            }
        } else if (isLongText) {
            preferredKey = "selfEvaluation";
        }
        if (preferredKey == null) return null;

        for (FieldCandidate candidate : candidates) {
            if (preferredKey.equals(candidate.fieldKey) || preferredKey.equalsIgnoreCase(candidate.fieldKey)) {
                double score = (isLongText ? 0.75 : 0.72) + templateBoost(templateId, candidate.templateId);
                return new MatchPick(candidate, clamp(score), "字段类型匹配: " + field.getType());
            }
        }
        return null;
    }

    private MatchPick templatePriorityPick(FieldInfo field, List<FieldCandidate> candidates, Long templateId) {
        if (templateId == null) return null;
        if (!isLongTextField(field)) {
            return null;
        }
        for (FieldCandidate candidate : candidates) {
            if (templateId.equals(candidate.templateId) && hasText(candidate.value)) {
                return new MatchPick(candidate, 0.70, "岗位模板优先匹配");
            }
        }
        return null;
    }

    private MatchPick fuzzyPick(String text, List<FieldCandidate> candidates, Long templateId) {
        MatchPick best = null;
        double bestScore = 0;
        for (FieldCandidate candidate : candidates) {
            for (String keyword : candidate.keywords) {
                if (!hasText(keyword)) continue;
                double similarity = similarity(text, keyword.toLowerCase());
                if (similarity > bestScore) {
                    bestScore = similarity;
                    double confidence = 0.45 + similarity * 0.45 + templateBoost(templateId, candidate.templateId);
                    best = new MatchPick(candidate, clamp(confidence), "模糊匹配: " + keyword);
                }
            }
        }
        if (bestScore < 0.30) {
            return null;
        }
        return best;
    }

    // ==================== 实习字段分类与自动推荐 ====================

    /**
     * 实习字段类型分类：按最长命中关键词判定（避免“实习期间主要工作”被“主要工作”误判为职责类）
     * 返回 internship_overview / internship_responsibility / internship_result / internship_tech_stack，未命中返回 null
     */
    private String classifyInternshipFieldType(String text) {
        if (!hasText(text)) {
            return null;
        }
        String result = null;
        int bestLen = 0;
        Map<String, List<String>> groups = Map.of(
                "internship_tech_stack", INTERNSHIP_TECH_KEYWORDS,
                "internship_result", INTERNSHIP_RESULT_KEYWORDS,
                "internship_responsibility", INTERNSHIP_RESPONSIBILITY_KEYWORDS,
                "internship_overview", INTERNSHIP_OVERVIEW_KEYWORDS);
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword.toLowerCase()) && keyword.length() > bestLen) {
                    bestLen = keyword.length();
                    result = entry.getKey();
                }
            }
        }
        return result;
    }

    /** 模糊实习信号：单独的“描述/经历/工作内容”等大文本框归为合并型 */
    private boolean hasInternshipSignal(String text) {
        for (String keyword : INTERNSHIP_SIGNAL_KEYWORDS) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 实习类字段自动推荐：选定实习经历后按 字段类型 × 岗位方向 × 字数限制 选内容版本；
     * 默认选择由模板经历配置（included/autoFillEnabled/autoFillPriority）控制，手动指定经历不受限。
     */
    private MatchResult internshipVariantMatch(Long userId, FieldInfo field, String internFieldType,
                                              String audience, String jobDirection,
                                              Long preferredInternshipId,
                                              List<InternshipExperience> internshipList,
                                              Map<String, TemplateExperienceConfig> experienceConfigs) {
        InternshipExperience chosen = selectInternship(internshipList, audience, jobDirection,
                preferredInternshipId, experienceConfigs);
        if (chosen == null) {
            return null;
        }
        Integer wordLimit = field.getWordLimit() != null ? field.getWordLimit() : null;
        Optional<ContentVariantService.VariantPick> variant = contentVariantService.pickVariant(
                userId, "internship", chosen.getId(), audience, jobDirection, internFieldType, wordLimit);
        String value;
        String variantDesc = null;
        if (variant.isPresent()) {
            value = variant.get().content();
            variantDesc = variant.get().audienceType() + "/" + variant.get().jobDirection() + "/"
                    + variant.get().lengthType() + "/" + variant.get().fieldType();
        } else {
            // 无版本时回退实习原始内容（合并型用描述+亮点，职责/成果用对应字段）
            value = switch (internFieldType) {
                case "internship_responsibility" -> nz(chosen.getDescription());
                case "internship_result" -> nz(chosen.getHighlights());
                case "internship_tech_stack" -> nz(chosen.getTechStack());
                default -> nz(chosen.getDescription()) + nz(chosen.getHighlights());
            };
        }
        if (!hasText(value)) {
            return null;
        }
        String name = nz(chosen.getShortName()).isEmpty() ? nz(chosen.getCompany()) : chosen.getShortName();
        return new MatchResult(field.getFieldId(), "internship", name + "实习经历",
                value, 0.88, false,
                "实习字段匹配: " + name + " → " + internFieldType, variantDesc);
    }

    /**
     * 选择实习经历：用户手动指定优先经历（不受模板配置限制，插件端已确认）→
     * 模板经历配置（仅 autoFillEnabled 参与，按 autoFillPriority 排序）→
     * 后台 templatePriority → 内置推荐表 → 排序第一。不删除任何经历，仅控制默认选择。
     */
    private InternshipExperience selectInternship(List<InternshipExperience> internships, String audience,
                                                  String jobDirection, Long preferredId,
                                                  Map<String, TemplateExperienceConfig> experienceConfigs) {
        if (internships.isEmpty()) {
            return null;
        }
        if (preferredId != null) {
            return internships.stream().filter(i -> preferredId.equals(i.getId())).findFirst().orElse(null);
        }
        InternshipExperience byConfig = selectByExperienceConfig(internships, "internship", experienceConfigs);
        if (byConfig != null) {
            return byConfig;
        }
        InternshipExperience byPriority = sortByTemplatePriority(internships, audience, jobDirection);
        if (byPriority != null) {
            return byPriority;
        }
        List<String> preferred = INTERNSHIP_PRIORITY_TABLE.get(audience + ":" + nz(jobDirection));
        if (preferred == null) {
            preferred = INTERNSHIP_PRIORITY_TABLE.getOrDefault(audience, INTERNSHIP_PRIORITY_TABLE.get("general"));
        }
        if (preferred != null) {
            for (String keyword : preferred) {
                for (InternshipExperience internship : internships) {
                    if (nz(internship.getShortName()).contains(keyword) || nz(internship.getCompany()).contains(keyword)) {
                        return internship;
                    }
                }
            }
        }
        return internships.get(0);
    }

    /** 加载当前模板的经历配置，key = sourceType:sourceId；无模板时返回空表 */
    private Map<String, TemplateExperienceConfig> loadExperienceConfigs(Long userId, ApplicationTemplate template) {
        Map<String, TemplateExperienceConfig> map = new LinkedHashMap<>();
        if (template == null) {
            return map;
        }
        List<TemplateExperienceConfig> configs = templateConfigRepository
                .findByUserIdAndTemplateIdAndDeletedFalse(userId, template.getId());
        for (TemplateExperienceConfig config : configs) {
            map.put(config.getSourceType() + ":" + config.getSourceId(), config);
        }
        return map;
    }

    /**
     * 按模板经历配置选择：仅 autoFillEnabled 的经历参与，按 autoFillPriority 升序（同优先级按 sortOrder）；
     * 该类型无配置时返回 null 交由后续策略兜底。
     */
    private <T> T selectByExperienceConfig(List<T> sources, String sourceType,
                                           Map<String, TemplateExperienceConfig> experienceConfigs,
                                           java.util.function.Function<T, Long> idGetter,
                                           java.util.function.Function<T, Integer> orderGetter) {
        List<T> enabled = new ArrayList<>();
        boolean hasConfig = false;
        for (T source : sources) {
            TemplateExperienceConfig config = experienceConfigs.get(sourceType + ":" + idGetter.apply(source));
            if (config != null) {
                hasConfig = true;
                if (Boolean.FALSE.equals(config.getAutoFillEnabled())) {
                    continue;
                }
            }
            enabled.add(source);
        }
        if (!hasConfig || enabled.isEmpty()) {
            return null;
        }
        enabled.sort(Comparator.<T, Integer>comparing(source -> {
            TemplateExperienceConfig config = experienceConfigs.get(sourceType + ":" + idGetter.apply(source));
            return config != null && config.getAutoFillPriority() != null
                    ? config.getAutoFillPriority() : Integer.MAX_VALUE;
        }).thenComparing(source -> {
            Integer order = orderGetter.apply(source);
            return order == null ? Integer.MAX_VALUE : order;
        }));
        return enabled.get(0);
    }

    /** 实习按模板经历配置选择 */
    private InternshipExperience selectByExperienceConfig(List<InternshipExperience> internships, String sourceType,
                                                          Map<String, TemplateExperienceConfig> experienceConfigs) {
        return selectByExperienceConfig(internships, sourceType, experienceConfigs,
                InternshipExperience::getId, InternshipExperience::getSortOrder);
    }

    /** 后台配置的模板优先级（templatePriority JSON）命中当前受众时按其排序；支持 受众:方向 复合键 */
    private InternshipExperience sortByTemplatePriority(List<InternshipExperience> internships, String audience,
                                                        String jobDirection) {
        List<InternshipExperience> configured = new ArrayList<>();
        for (InternshipExperience internship : internships) {
            Integer priority = templatePriorityOf(internship.getTemplatePriority(), audience, jobDirection);
            if (priority != null) {
                configured.add(internship);
            }
        }
        if (configured.isEmpty()) {
            return null;
        }
        configured.sort(Comparator.comparingInt(i ->
                templatePriorityOf(i.getTemplatePriority(), audience, jobDirection) == null
                        ? Integer.MAX_VALUE
                        : templatePriorityOf(i.getTemplatePriority(), audience, jobDirection)));
        return configured.get(0);
    }

    private Integer templatePriorityOf(String json, String audience, String jobDirection) {
        if (!hasText(json) || !hasText(audience)) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            Object value = hasText(jobDirection) ? map.get(audience + ":" + jobDirection) : null;
            if (value == null) {
                value = map.get(audience);
            }
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    /** 版本字段类型：实习用分类结果（默认合并型），项目按描述/职责/成果拆分，素材用通用合并型 */
    private String resolveVariantFieldType(String sourceType, String internFieldType, String text) {
        if ("internship".equals(sourceType)) {
            return internFieldType != null ? internFieldType : "internship_combined";
        }
        if ("project".equals(sourceType)) {
            return projectFieldType(text);
        }
        return "combined";
    }

    private String projectFieldType(String text) {
        if (containsAny(text, INTERNSHIP_TECH_KEYWORDS)) {
            return "project_tech_stack";
        }
        if (text.contains("项目描述") || text.contains("项目介绍") || text.contains("项目背景")) {
            return "project_overview";
        }
        if (containsAny(text, INTERNSHIP_RESULT_KEYWORDS)) {
            return "project_result";
        }
        if (containsAny(text, INTERNSHIP_RESPONSIBILITY_KEYWORDS)) {
            return "project_responsibility";
        }
        return "project_combined";
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 专业技能匹配 ====================

    /** 技能字段匹配方案：内容版本字段类型 + 页面字数限制 */
    private record SkillFieldPlan(String fieldType, Integer wordLimit) {
    }

    /**
     * 识别专业技能字段并确定填充方案：
     * 1. 字段名含 技术栈/技术关键词/技能标签 → 关键词形式；
     * 2. 有字数限制 → 500 字以内用简短版，超过用完整版；
     * 3. 无字数限制 → 单行 input 用关键词、富文本用完整版、其余（textarea）用简短版；
     * 4. 字段名含 专业技能/技术能力/技能特长 → 优先段落形式。
     */
    private SkillFieldPlan classifySkillField(String text, FieldInfo field) {
        if (!hasText(text) || !containsAny(lower(text), SKILL_KEYWORDS)) {
            return null;
        }
        String lower = lower(text);
        Integer wordLimit = field.getWordLimit() != null ? field.getWordLimit() : parseWordLimit(text);

        String fieldType;
        if (containsAny(lower, SKILL_KEYWORD_FORM_SIGNALS)) {
            fieldType = "skill_keywords";
        } else if (wordLimit != null) {
            fieldType = wordLimit > 500 ? "skill_full" : "skill_short";
        } else {
            String type = lower(field.getType());
            String tag = lower(field.getTagName());
            if ("contenteditable".equals(type) || "richeditor".equals(type)) {
                fieldType = "skill_full";
            } else if ("input".equals(tag) || "select".equals(tag)) {
                fieldType = "skill_keywords";
            } else {
                fieldType = "skill_short";
            }
        }
        // 字段名明确要求段落形式（专业技能/技术能力/技能特长）时，关键词形式升级为简短段落；
        // 内容版本按字数档位自动选择，不会超出字段限制。
        if ("skill_keywords".equals(fieldType) && containsAny(lower, SKILL_PARAGRAPH_FORM_SIGNALS)
                && !containsAny(lower, SKILL_KEYWORD_FORM_SIGNALS)) {
            fieldType = "skill_short";
        }
        return new SkillFieldPlan(fieldType, wordLimit);
    }

    /** 按当前模板受众与岗位方向选择技能内容版本 */
    private MatchResult skillVariantMatch(Long userId, FieldInfo field, SkillFieldPlan plan,
                                          String audience, String jobDirection) {
        Integer limit = plan.wordLimit();
        if (limit == null && "skill_full".equals(plan.fieldType())) {
            limit = 10000;
        }
        Optional<ContentVariantService.VariantPick> variant = contentVariantService.pickVariant(
                userId, "skill", 0L, audience, jobDirection, plan.fieldType(), limit);
        if (variant.isEmpty() || !hasText(variant.get().content())) {
            return null;
        }
        String variantDesc = variant.get().audienceType() + "/" + variant.get().jobDirection() + "/"
                + variant.get().lengthType() + "/" + variant.get().fieldType();
        return new MatchResult(
                field.getFieldId(), "skill", "专业技能", variant.get().content(),
                0.92, false, "技能字段匹配: " + plan.fieldType(), variantDesc);
    }

    // ==================== 候选构建 ====================

    private List<FieldCandidate> buildCandidates(Long userId, Long templateId, ApplicationTemplate template,
                                                 Map<String, TemplateExperienceConfig> experienceConfigs) {
        UserProfile profile = userProfileRepository.findByUserIdAndDeletedFalse(userId).orElse(null);
        List<EducationExperience> educationList = educationRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
        List<InternshipExperience> internshipList = internshipRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
        List<ProjectExperience> projectList = projectRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
        List<UserCustomField> customFields = userCustomFieldRepository.findByConditions(userId, null, true, templateId, null);
        List<AnswerMaterial> materials = materialRepository.findByUserIdAndEnabledTrueAndDeletedFalseOrderBySortOrderAscIdAsc(userId);

        List<FieldCandidate> candidates = new ArrayList<>();

        // 1. 内置结构化数据；默认实习/项目优先取模板经历配置中参与自动填充的最高优先级经历，
        //    无配置时回退 isDefault。
        InternshipExperience defaultInternship = selectByExperienceConfig(internshipList, "internship", experienceConfigs);
        if (defaultInternship == null) {
            defaultInternship = internshipList.stream()
                    .filter(i -> Boolean.TRUE.equals(i.getIsDefault())).findFirst()
                    .or(() -> internshipList.stream().findFirst()).orElse(null);
        }
        ProjectExperience defaultProject = selectByExperienceConfig(projectList, "project", experienceConfigs,
                ProjectExperience::getId, ProjectExperience::getSortOrder);
        if (defaultProject == null) {
            defaultProject = projectList.stream()
                    .filter(p -> Boolean.TRUE.equals(p.getIsDefault())).findFirst()
                    .or(() -> projectList.stream().findFirst()).orElse(null);
        }
        Map<String, String> valueMap = buildBuiltinValueMap(profile, educationList, defaultInternship,
                defaultProject, template, templateId);

        for (Map.Entry<String, String> entry : valueMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!hasText(value)) continue;
            String sourceType = null;
            Long sourceId = null;
            String startDate = null;
            String endDate = null;
            if ("internship".equals(key) && defaultInternship != null) {
                sourceType = "internship";
                sourceId = defaultInternship.getId();
                startDate = defaultInternship.getStartDate();
                endDate = defaultInternship.getEndDate();
            } else if ("project".equals(key) && defaultProject != null) {
                sourceType = "project";
                sourceId = defaultProject.getId();
                startDate = defaultProject.getStartDate();
                endDate = defaultProject.getEndDate();
            }
            candidates.add(new FieldCandidate(
                    key, key, value,
                    BUILTIN_KEYWORDS.getOrDefault(key, Collections.emptyList()),
                    false, "input", null, sourceType, sourceId, startDate, endDate
            ));
        }

        // 2. 素材库（开放题），附带来源引用用于版本选择
        for (AnswerMaterial material : materials) {
            if (!hasText(material.getContent())) {
                continue;
            }
            if (material.getTemplateId() != null && templateId != null && !templateId.equals(material.getTemplateId())) {
                continue;
            }
            String fieldKey = materialTypeToFieldKey(material.getMaterialType());
            if (!hasText(fieldKey)) {
                continue;
            }
            candidates.add(new FieldCandidate(
                    fieldKey,
                    hasText(material.getTitle()) ? material.getTitle() : fieldKey,
                    material.getContent(),
                    BUILTIN_KEYWORDS.getOrDefault(fieldKey, Collections.emptyList()),
                    false, "textarea", material.getTemplateId(),
                    "material", material.getId(), null, null
            ));
        }

        // 3. 用户自定义字段（含字段匹配规则与来源引用）
        for (UserCustomField field : customFields) {
            String sourceType = null;
            Long sourceId = null;
            String startDate = null;
            String endDate = null;
            if (hasText(field.getSourceRef()) && field.getSourceRef().contains(":")) {
                String[] parts = field.getSourceRef().split(":", 2);
                sourceType = parts[0];
                try {
                    sourceId = Long.parseLong(parts[1]);
                } catch (NumberFormatException ignored) {
                    sourceType = null;
                }
                if ("internship".equals(sourceType)) {
                    InternshipExperience internship = internshipRepository.findById(sourceId).orElse(null);
                    if (internship != null) {
                        startDate = internship.getStartDate();
                        endDate = internship.getEndDate();
                    }
                } else if ("project".equals(sourceType)) {
                    ProjectExperience project = projectRepository.findById(sourceId).orElse(null);
                    if (project != null) {
                        startDate = project.getStartDate();
                        endDate = project.getEndDate();
                    }
                }
            }
            candidates.add(new FieldCandidate(
                    field.getFieldKey(),
                    field.getFieldName(),
                    field.getFieldValue(),
                    parseKeywords(field.getMatchKeywords()),
                    Boolean.TRUE.equals(field.getSensitive()),
                    field.getFieldType(),
                    field.getTemplateId(),
                    sourceType, sourceId, startDate, endDate
            ));
        }
        return candidates;
    }

    private Map<String, String> buildBuiltinValueMap(UserProfile profile,
                                                     List<EducationExperience> educationList,
                                                     InternshipExperience defaultInternship,
                                                     ProjectExperience defaultProject,
                                                     ApplicationTemplate template,
                                                     Long templateId) {
        Map<String, String> map = new LinkedHashMap<>();
        if (profile != null) {
            map.put("name", profile.getName());
            map.put("gender", profile.getGender());
            map.put("phone", profile.getPhone());
            map.put("email", profile.getEmail());
            map.put("qq", profile.getQq());
            map.put("wechat", profile.getWechat());
            map.put("currentLocation", profile.getCurrentLocation());
            map.put("school", profile.getSchool());
            map.put("major", profile.getMajor());
            map.put("degree", profile.getDegree());
            map.put("graduationDate", profile.getGraduationDate());
            map.put("expectedCity", profile.getExpectedCity());
            map.put("expectedPosition", profile.getExpectedPosition());
            map.put("applicantType", profile.getApplicantType());
            map.put("selfEvaluation", profile.getSelfIntroduction());
        }

        EducationExperience defaultEducation = educationList.stream()
                .filter(e -> Boolean.TRUE.equals(e.getIsDefault()))
                .findFirst()
                .or(() -> educationList.stream().findFirst())
                .orElse(null);
        if (defaultEducation != null) {
            putIfEmpty(map, "school", defaultEducation.getSchool());
            putIfEmpty(map, "major", defaultEducation.getMajor());
            putIfEmpty(map, "degree", defaultEducation.getDegree());
            map.put("gpa", defaultEducation.getGpa());
            map.put("rank", defaultEducation.getRank());
            map.put("thesis", defaultEducation.getThesis());
            map.put("researchDirection", defaultEducation.getResearchDirection());
            putIfEmpty(map, "graduationDate", defaultEducation.getEndDate());
        }

        if (defaultInternship != null) {
            putIfEmpty(map, "internship", defaultInternship.getDescription());
        }
        if (defaultProject != null) {
            putIfEmpty(map, "project", defaultProject.getDescription());
        }

        if (template != null) {
            map.put("selfEvaluation", coalesce(template.getSelfEvaluation(), map.get("selfEvaluation")));
            map.put("internship", coalesce(template.getInternshipDescription(), map.get("internship")));
            map.put("project", coalesce(template.getProjectDescription(), map.get("project")));
            map.put("careerPlan", coalesce(template.getCareerPlan(), map.get("careerPlan")));
            map.put("aiCollaboration", coalesce(template.getAiCollaboration(), map.get("aiCollaboration")));
        }
        return map;
    }

    // ==================== 工具方法 ====================

    private String resolveAudience(AutofillMatchRequest request, ApplicationTemplate template) {
        if (hasText(request.getAudienceType())) {
            // 模板表 audienceType 为 general_backend 时，内容版本受众对应 general
            String audience = request.getAudienceType();
            return "general_backend".equals(audience) ? "general" : audience;
        }
        if (template != null && hasText(template.getAudienceType())) {
            String audience = template.getAudienceType();
            return "general_backend".equals(audience) ? "general" : audience;
        }
        return "general";
    }

    private ApplicationTemplate resolveTemplate(Long userId, Long templateId) {
        if (templateId == null) {
            return null;
        }
        Optional<ApplicationTemplate> templateOpt = templateRepository.findById(templateId)
                .filter(t -> t.getUserId().equals(userId) && !Boolean.TRUE.equals(t.getDeleted()));
        return templateOpt.orElse(null);
    }

    private boolean isLongTextField(FieldInfo field) {
        String type = lower(field.getType());
        if ("textarea".equals(type) || "contenteditable".equals(type) || "richeditor".equals(type)) {
            return true;
        }
        // 部分页面扫描时 tagName=TEXTAREA 但 input type 为 text，兜底按标签名判定
        return "textarea".equals(lower(field.getTagName()));
    }

    /**
     * 从页面文本中提取字数限制：200字以内 / 不超过300字 / 限500字
     */
    private Integer parseWordLimit(String text) {
        if (!hasText(text)) {
            return null;
        }
        Matcher matcher = WORD_LIMIT_PATTERN.matcher(text);
        Integer min = null;
        while (matcher.find()) {
            try {
                int limit = Integer.parseInt(matcher.group(1));
                if (limit >= 50 && limit <= 5000 && (min == null || limit < min)) {
                    min = limit;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return min;
    }

    private String combineFieldText(FieldInfo field) {
        StringBuilder sb = new StringBuilder();
        append(sb, field.getLabel());
        append(sb, field.getPlaceholder());
        append(sb, field.getTagName());
        append(sb, field.getName());
        append(sb, field.getId());
        append(sb, field.getClassName());
        append(sb, field.getAriaLabel());
        append(sb, field.getParentText());
        append(sb, field.getQuestionText());
        append(sb, field.getNearbyText());
        return sb.toString().toLowerCase();
    }

    private boolean isSensitive(String text) {
        if (!hasText(text) || sensitiveKeywords == null) return false;
        String lower = text.toLowerCase();
        for (String keyword : sensitiveKeywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseKeywords(String json) {
        if (!hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析字段关键词失败: {}", json);
            return Collections.emptyList();
        }
    }

    private String materialTypeToFieldKey(String materialType) {
        if (materialType == null) return null;
        return switch (materialType) {
            case "SELF_EVALUATION" -> "selfEvaluation";
            case "INTERNSHIP" -> "internship";
            case "PROJECT" -> "project";
            case "AI_COLLABORATION" -> "aiCollaboration";
            case "CAREER_PLAN" -> "careerPlan";
            case "HOBBY" -> "hobby";
            case "WHY_COMPANY" -> "whyCompany";
            case "WHY_POSITION" -> "whyPosition";
            case "SUPPLEMENT" -> "supplement";
            default -> null;
        };
    }

    private double similarity(String a, String b) {
        if (!hasText(a) || !hasText(b)) return 0;
        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() > b.length() ? a : b;
        if (longer.contains(shorter)) {
            return (double) shorter.length() / longer.length();
        }
        int matchCount = 0;
        for (int i = 0; i < shorter.length(); i++) {
            if (longer.indexOf(shorter.charAt(i)) >= 0) {
                matchCount++;
            }
        }
        return (double) matchCount / longer.length();
    }

    private double templateBoost(Long selectedTemplateId, Long candidateTemplateId) {
        if (selectedTemplateId != null && selectedTemplateId.equals(candidateTemplateId)) {
            return 0.03;
        }
        return 0;
    }

    private double clamp(double score) {
        return Math.min(0.99, Math.max(0.01, score));
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private void append(StringBuilder sb, String value) {
        if (hasText(value)) {
            sb.append(value).append(' ');
        }
    }

    private String coalesce(String first, String second) {
        return hasText(first) ? first : second;
    }

    private void putIfEmpty(Map<String, String> map, String key, String value) {
        if (!hasText(map.get(key)) && hasText(value)) {
            map.put(key, value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }

    private record FieldCandidate(String fieldKey,
                                  String fieldName,
                                  String value,
                                  List<String> keywords,
                                  boolean sensitive,
                                  String fieldType,
                                  Long templateId,
                                  String sourceType,
                                  Long sourceId,
                                  String startDate,
                                  String endDate) {
    }

    private record MatchPick(FieldCandidate candidate, double confidence, String reason) {
    }
}
