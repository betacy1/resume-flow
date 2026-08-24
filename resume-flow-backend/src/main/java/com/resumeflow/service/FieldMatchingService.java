package com.resumeflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeflow.dto.AutofillMatchRequest;
import com.resumeflow.dto.AutofillMatchRequest.FieldInfo;
import com.resumeflow.entity.AnswerMaterial;
import com.resumeflow.entity.ApplicationTemplate;
import com.resumeflow.entity.AwardCertificate;
import com.resumeflow.entity.ContentVariant;
import com.resumeflow.entity.EducationExperience;
import com.resumeflow.entity.EmergencyContact;
import com.resumeflow.entity.FamilyMember;
import com.resumeflow.entity.InternshipExperience;
import com.resumeflow.entity.ProjectExperience;
import com.resumeflow.entity.TemplateExperienceConfig;
import com.resumeflow.entity.UserCustomField;
import com.resumeflow.entity.UserProfile;
import com.resumeflow.repository.AnswerMaterialRepository;
import com.resumeflow.repository.ApplicationTemplateRepository;
import com.resumeflow.repository.AwardCertificateRepository;
import com.resumeflow.repository.EducationExperienceRepository;
import com.resumeflow.repository.EmergencyContactRepository;
import com.resumeflow.repository.FamilyMemberRepository;
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
    private final AwardCertificateRepository awardRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final ApplicationTemplateRepository templateRepository;
    private final AnswerMaterialRepository materialRepository;
    private final UserCustomFieldRepository userCustomFieldRepository;
    private final TemplateExperienceConfigRepository templateConfigRepository;
    private final ObjectMapper objectMapper;
    private final DateFormatService dateFormatService;
    private final ContentVariantService contentVariantService;

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

    // ==================== 经历块字段映射关键词（同一块内绑定同一条记录） ====================

    /** 单位名称 → internship.company */
    private static final List<String> BLOCK_COMPANY_KEYWORDS = List.of(
            "单位名称", "公司名称", "企业名称", "组织名称", "工作单位", "实习单位", "任职单位",
            "雇主名称", "所在公司", "雇主", "单位", "公司", "employer", "company", "organization");

    /** 职位名称 → internship.position */
    private static final List<String> BLOCK_POSITION_KEYWORDS = List.of(
            "职位名称", "岗位名称", "实习岗位", "任职岗位", "工作岗位", "担任职务", "职务", "岗位", "职位",
            "job title", "position", "role");

    /** 开始时间 → internship.startDate */
    private static final List<String> BLOCK_START_DATE_KEYWORDS = List.of(
            "开始时间", "入职时间", "开始日期", "起始时间", "起始日期", "工作开始时间", "实习开始时间",
            "任职开始时间", "from", "start date");

    /** 结束时间 → internship.endDate */
    private static final List<String> BLOCK_END_DATE_KEYWORDS = List.of(
            "结束时间", "离职时间", "结束日期", "截止时间", "截止日期", "工作结束时间", "实习结束时间",
            "任职结束时间", "end date");

    /** 至今 → internship.isPresent */
    private static final List<String> BLOCK_PRESENT_KEYWORDS = List.of(
            "至今", "目前", "当前仍在职", "仍在职", "present", "current");

    /** 部门 → internship.department */
    private static final List<String> BLOCK_DEPARTMENT_KEYWORDS = List.of(
            "所在部门", "实习部门", "工作部门", "任职部门", "部门", "department");

    // ==================== 实习/工作证明人字段关键词（必须优先于单位/职位判断，避免“证明人单位”误匹配公司） ====================

    /** 证明人/联系人/推荐人信号：命中后所有取值均来自当前块绑定的同一条实习记录 */
    private static final List<String> BLOCK_CERTIFIER_SIGNALS = List.of(
            "证明人", "实习证明人", "工作证明人", "经历证明人", "单位证明人", "联系人", "推荐人", "介绍人",
            "指导老师", "指导人", "直接主管", "主管", "supervisor", "reference", "referee", "certifier",
            "contact person");

    /** 证明人单位及职务（合并字段，先于单位/职务单独判断） */
    private static final List<String> CERTIFIER_COMBINED_SIGNALS = List.of("单位及职务", "单位和职务");

    /** 证明人电话信号 */
    private static final List<String> CERTIFIER_PHONE_SIGNALS = List.of(
            "电话", "手机", "联系方式", "phone", "mobile");

    /** 证明人邮箱信号 */
    private static final List<String> CERTIFIER_EMAIL_SIGNALS = List.of("邮箱", "email");

    /** 证明人单位信号 */
    private static final List<String> CERTIFIER_COMPANY_SIGNALS = List.of("单位", "所在单位", "company");

    /** 证明人职务信号 */
    private static final List<String> CERTIFIER_POSITION_SIGNALS = List.of("职务", "职位", "岗位", "position");

    /** 证明人关系信号 */
    private static final List<String> CERTIFIER_RELATION_SIGNALS = List.of("关系", "与本人关系", "relationship");

    // ==================== 家庭成员块字段映射关键词 ====================

    private static final List<String> BLOCK_FAMILY_RELATION_KEYWORDS = List.of(
            "关系", "与本人关系", "成员关系", "亲属关系", "家庭关系", "成员身份", "relation", "relationship");

    private static final List<String> BLOCK_FAMILY_NAME_KEYWORDS = List.of(
            "姓名", "成员姓名", "亲属姓名", "家属姓名", "家庭成员姓名", "father name", "mother name", "name");

    private static final List<String> BLOCK_FAMILY_COMPANY_KEYWORDS = List.of(
            "单位", "工作单位", "所在单位", "任职单位", "就职单位", "工作机构", "家庭成员单位",
            "父亲单位", "母亲单位", "company", "organization", "employer");

    private static final List<String> BLOCK_FAMILY_POSITION_KEYWORDS = List.of(
            "职务", "职位", "岗位", "工作职务", "任职职务", "家庭成员职务", "父亲职务", "母亲职务",
            "position", "job title", "role");

    private static final List<String> BLOCK_FAMILY_PHONE_KEYWORDS = List.of(
            "联系电话", "手机号", "手机", "电话", "联系方式", "家庭成员联系电话", "父亲联系电话",
            "母亲联系电话", "phone", "mobile", "telephone");

    private static final List<String> BLOCK_FAMILY_EMAIL_KEYWORDS = List.of("邮箱", "电子邮箱", "email");

    private static final List<String> BLOCK_FAMILY_ADDRESS_KEYWORDS = List.of("地址", "住址", "家庭地址", "address");

    private static final List<String> BLOCK_FAMILY_POLITICAL_KEYWORDS = List.of("政治面貌", "政治身份");

    /** 城市/行业：经历实体无此数据源，命中时标记未匹配，绝不用姓名兜底 */
    private static final List<String> BLOCK_NO_DATA_KEYWORDS = List.of(
            "工作城市", "实习城市", "所在城市", "工作地点", "实习地点", "地点", "城市", "city", "location",
            "所属行业", "公司行业", "现从事行业", "从事行业", "行业", "industry",
            "工作年限", "现从事职业", "期望职业", "月薪", "薪资");

    // ==================== 教育经历块字段映射关键词 ====================

    private static final List<String> BLOCK_EDU_SCHOOL_KEYWORDS = List.of(
            "学校", "学校名称", "毕业院校", "院校", "就读学校", "university", "school", "college");

    private static final List<String> BLOCK_EDU_LEVEL_KEYWORDS = List.of(
            "学历", "学历层次", "教育层次", "education level");

    private static final List<String> BLOCK_EDU_DEGREE_KEYWORDS = List.of("学位", "degree");

    private static final List<String> BLOCK_EDU_MAJOR_KEYWORDS = List.of(
            "专业", "专业名称", "主修专业", "所学专业", "第一专业", "major");

    private static final List<String> BLOCK_EDU_COLLEGE_KEYWORDS = List.of(
            "学院", "院系", "所在学院", "所在院系", "department");

    private static final List<String> BLOCK_EDU_STUDENT_ID_KEYWORDS = List.of(
            "学号", "学生号", "student id", "student number");

    private static final List<String> BLOCK_EDU_RANK_KEYWORDS = List.of(
            "年级排名", "专业排名", "成绩排名", "综合排名", "ranking");

    private static final List<String> BLOCK_EDU_STUDY_MODE_KEYWORDS = List.of(
            "学习形式", "学历类型", "培养方式", "学习方式", "是否全日制", "全日制",
            "education type", "study mode");

    private static final List<String> BLOCK_EDU_COURSES_KEYWORDS = List.of(
            "主修课程及成绩", "主修课程", "主要课程", "核心课程", "课程成绩", "课程",
            "relevant courses", "major courses");

    private static final List<String> BLOCK_EDU_TAGS_KEYWORDS = List.of("学校标签", "院校标签");

    private static final List<String> BLOCK_EDU_BATCH_KEYWORDS = List.of("高考录取批次", "录取批次");

    private static final List<String> BLOCK_EDU_GPA_KEYWORDS = List.of("gpa", "绩点");

    // ==================== 荣誉奖项块字段映射关键词 ====================

    private static final List<String> BLOCK_AWARD_NAME_KEYWORDS = List.of(
            "奖项名称", "荣誉名称", "获奖名称", "奖励名称", "专利名称", "成果名称", "名称",
            "award name", "honor name");

    private static final List<String> BLOCK_AWARD_DATE_KEYWORDS = List.of(
            "获奖时间", "获奖日期", "获得时间", "取得时间", "申请时间", "时间", "日期", "award date");

    private static final List<String> BLOCK_AWARD_LEVEL_KEYWORDS = List.of(
            "奖项级别", "荣誉级别", "级别", "level");

    private static final List<String> BLOCK_AWARD_TYPE_KEYWORDS = List.of(
            "奖项类型", "类型", "类别", "category");

    /** 项目名称 → project.projectName */
    private static final List<String> BLOCK_PROJECT_NAME_KEYWORDS = List.of(
            "项目名称", "项目名", "项目", "project name", "project");

    /** 项目角色 → project.role */
    private static final List<String> BLOCK_PROJECT_ROLE_KEYWORDS = List.of(
            "担任角色", "项目角色", "承担角色", "角色", "担任职位", "role");

    /** 实习默认优先级：受众:岗位方向 → 公司关键词顺序；优先级只决定排序与默认集合，不决定只填一条 */
    private static final Map<String, List<String>> INTERNSHIP_PRIORITY_TABLE = Map.of(
            "state_owned", List.of("工商银行", "京东", "字节跳动"),
            "state_owned:backend", List.of("工商银行", "京东", "字节跳动"),
            "state_owned:ai", List.of("工商银行", "京东", "字节跳动"),
            "big_tech", List.of("字节跳动", "京东"),
            "big_tech:backend", List.of("字节跳动", "京东"),
            "big_tech:ai", List.of("字节跳动", "京东"),
            "bank", List.of("工商银行", "字节跳动", "京东"),
            "bank:fintech", List.of("工商银行", "字节跳动", "京东"),
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

    /** 内置日期关键词 → 字段 key（毕业时间/出生日期为固定值字段，起止时间走经历日期） */
    private static final Map<String, String> DATE_KEYWORD_FIELDS = new LinkedHashMap<>();

    static {
        DATE_KEYWORD_FIELDS.put("出生日期", "birth_date");
        DATE_KEYWORD_FIELDS.put("出生年月", "birth_date");
        DATE_KEYWORD_FIELDS.put("生日", "birth_date");
        DATE_KEYWORD_FIELDS.put("birth date", "birth_date");
        DATE_KEYWORD_FIELDS.put("birthday", "birth_date");
        DATE_KEYWORD_FIELDS.put("date of birth", "birth_date");
        DATE_KEYWORD_FIELDS.put("dob", "birth_date");
        DATE_KEYWORD_FIELDS.put("入学时间", "eduStartDate");
        DATE_KEYWORD_FIELDS.put("毕业时间", "graduationDate");
        DATE_KEYWORD_FIELDS.put("毕业日期", "graduationDate");
        DATE_KEYWORD_FIELDS.put("毕业年月", "graduationDate");
        DATE_KEYWORD_FIELDS.put("毕业年份", "graduationDate");
        DATE_KEYWORD_FIELDS.put("预计毕业", "graduationDate");
        DATE_KEYWORD_FIELDS.put("开始时间", "startDate");
        DATE_KEYWORD_FIELDS.put("开始日期", "startDate");
        DATE_KEYWORD_FIELDS.put("起始时间", "startDate");
        DATE_KEYWORD_FIELDS.put("入职时间", "startDate");
        DATE_KEYWORD_FIELDS.put("start date", "startDate");
        DATE_KEYWORD_FIELDS.put("结束时间", "endDate");
        DATE_KEYWORD_FIELDS.put("结束日期", "endDate");
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
        List<ProjectExperience> projectList = projectRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
        List<EducationExperience> educationList = educationRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
        List<AwardCertificate> awardList = awardRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
        List<FamilyMember> familyList = familyMemberRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId);
        List<EmergencyContact> emergencyList = emergencyContactRepository
                .findByUserIdAndDeletedFalseOrderByIdAsc(userId);
    
        AutofillMatchResponse response = new AutofillMatchResponse();
        List<MatchResult> matches = new ArrayList<>();
        List<SkippedField> skipped = new ArrayList<>();
        List<UnmatchedField> unmatched = new ArrayList<>();
        response.setMatches(matches);
        response.setSkipped(skipped);
        response.setUnmatched(unmatched);
    
        // 当前模板的有序经历计划（优先级只决定排序与默认集合，不决定只填一条）：
        // 插件据此判断需新增多少个经历块，并逐块绑定记录填充。
        List<InternshipExperience> orderedInternships = orderInternshipsForTemplate(
                internshipList, audience, request.getJobDirection(), request.getPreferredInternshipId(), experienceConfigs);
        List<ProjectExperience> orderedProjects = orderProjectsForTemplate(projectList, experienceConfigs);
        response.setExperiencePlan(buildExperiencePlan(orderedInternships, orderedProjects, educationList,
                awardList, familyList));
    
        if (request.getFields() == null || request.getFields().isEmpty()) {
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
    
            // 证据优先级：中文 label/问题文本/附近文本为主证据（权重最高）；
            // input.name/id/className 仅在完全无中文证据时作弱证据，且结果降级为“需确认”。
            String text = primaryEvidence(field);
            boolean weakOnly = !hasText(text);
            if (weakOnly) {
                text = weakEvidence(field);
            }
    
            // 重复块内字段（工作/实习经历、项目经历、教育经历、荣誉奖项、语言能力）：
            // 同一块内所有字段绑定同一条记录，禁止串块取值；无数据源时留待手动，绝不用姓名兜底。
            if (hasText(field.getBlockType()) && field.getBlockIndex() != null) {
                if ("language".equals(field.getBlockType())) {
                    MatchResult langMatch = matchLanguageBlockField(field, text, candidates);
                    if (langMatch != null) {
                        matches.add(langMatch);
                        continue;
                    }
                    unmatched.add(new UnmatchedField(field.getFieldId(), "语言能力块内无可填数据，需手动选择"));
                    continue;
                }
                MatchResult blockMatch = matchBlockField(userId, field, text, audience, request.getJobDirection(),
                        orderedInternships, orderedProjects, educationList, awardList, familyList);
                if (blockMatch != null) {
                    matches.add(blockMatch);
                    continue;
                }
                // 证明人字段在对应实习记录中为空：明确提示“字段存在但未填写”，绝不用其他字段兑底
                if ("internship".equals(field.getBlockType()) && containsAny(text, BLOCK_CERTIFIER_SIGNALS)) {
                    unmatched.add(new UnmatchedField(field.getFieldId(),
                            "证明人字段存在但当前记录未填写证明人信息，需手动补充"));
                    continue;
                }
                unmatched.add(new UnmatchedField(field.getFieldId(),
                        "块内无可填数据（对应记录缺失或字段无数据来源），需手动选择"));
                continue;
            }
    
            // 日期字段优先走日期匹配（起止语义 + 页面格式动态格式化）；
            // 仅当文本含明确日期关键词或 input type 为 date/month 时触发，避免普通字段误判。
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
    
            // 实习类字段优先走实习推荐；实习无可用版本时回退常规候选。
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
            // 内容类型强约束：禁止把姓名填入单位/职位/城市等字段，禁止把开放题素材填入职责/描述字段；
            // 违反时标记为未匹配，由用户手动选择，绝不做错误兕底。
            if (isForbiddenPick(text, pick.candidate)) {
                unmatched.add(new UnmatchedField(field.getFieldId(),
                        "内容类型约束：该字段不允许填入\"" + pick.candidate.fieldName + "\"，未匹配，需手动选择"));
                continue;
            }
            // 值类型强校验：邮箱/手机/姓名/语言类型等字段语义与候选值格式冲突时标记疑似错误（置信度归零，默认不勾选）
            String typeConflict = typeConflictReason(text, field, pick.candidate);
            boolean suspicious = typeConflict != null;

            // 已移除敏感字段跳过逻辑：个人自用场景，所有字段均按普通字段正常匹配与填充。
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
    
            MatchResult result = new MatchResult(
                    field.getFieldId(),
                    pick.candidate.fieldKey,
                    pick.candidate.fieldName,
                    value,
                    pick.confidence,
                    false,
                    pick.reason,
                    variantDesc
            );
            // 仅弱证据（无中文 label）时降级为需人工确认，避免 name/id 误判直接自动填入；
            // 类型冲突时置信度强制归零并标记疑似错误。
            if (weakOnly) {
                result.setConfidence(Math.min(result.getConfidence(), 0.55));
                result.setReason(result.getReason() + "（仅 name/id 弱证据，请确认）");
            }
            if (suspicious) {
                result.setSuspicious(true);
                result.setSuspiciousReason(typeConflict);
                result.setConfidence(0);
                result.setReason("类型校验失败：" + typeConflict);
            }
            result.setGroup(groupOf(pick.candidate));
            matches.add(result);
        }
    
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
     * 日期字段匹配：先按日期关键词定位目标字段（出生日期/毕业时间/经历起止），再按页面格式输出；
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

        // 固定值日期字段（出生日期/毕业时间）：按 fieldKey 定位候选值，绝不拿姓名等非日期内容兜底
        if ("graduationDate".equals(target) || "birth_date".equals(target)) {
            final String targetKey = target;
            FieldCandidate dated = candidates.stream()
                    .filter(c -> targetKey.equals(c.fieldKey) && hasText(c.value))
                    .findFirst().orElse(null);
            if (dated != null) {
                String fmt = dateFormatService.detectFormat(field.getType(), field.getPlaceholder(), field.getLabel());
                String value = dateFormatService.format(dated.value, fmt);
                String label = "graduationDate".equals(target) ? "毕业时间" : "出生日期";
                FieldCandidate formatted = new FieldCandidate(dated.fieldKey, label, value,
                        dated.keywords, field.getType(), null, null, null, null, null);
                return new MatchPick(formatted, 0.88, "日期匹配: " + label + " → " + fmt);
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
        // 未定位到具体经历：绝不随意取第一个经历的日期（会导致不同经历日期串数据），交给未匹配由用户手动选择
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
                value, chosen.keywords, field.getType(), null, null, null, null, null);
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

        // 注意：已移除“岗位模板优先匹配”无差别兕底（会把 AI 协作等素材填入任意长文本），
        // 也移除“普通 input 默认填 name”兕底；内容类型必须强约束。
        return fuzzyPick(text, field, candidates, templateId);
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
        // 仅保留邮箱/电话的强语义类型匹配；普通 input 不再默认兕底为 name，
        // 长文本也不再默认兕底为自我评价 —— 无明确证据时返回 null 交给未匹配。
        String preferredKey = null;
        if ("input".equals(type)) {
            if (text.contains("邮箱") || text.contains("email")) {
                preferredKey = "email";
            } else if (text.contains("手机") || text.contains("电话") || text.contains("phone")) {
                preferredKey = "phone";
            }
        }
        if (preferredKey == null) return null;

        for (FieldCandidate candidate : candidates) {
            if (preferredKey.equals(candidate.fieldKey) || preferredKey.equalsIgnoreCase(candidate.fieldKey)) {
                double score = 0.72 + templateBoost(templateId, candidate.templateId);
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

    private MatchPick fuzzyPick(String text, FieldInfo field, List<FieldCandidate> candidates, Long templateId) {
        // 短字段（input/select）绝不模糊匹配：字符重叠相似度对短标签极不可靠，
        // 是“姓名/邮箱/手机号被填成英语”类误填的主要来源；模糊匹配仅限长文本（开放题/描述）。
        if (!isLongTextField(field)) {
            return null;
        }
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
        if (bestScore < 0.50) {
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
        MatchResult result = new MatchResult(field.getFieldId(), "internship", name + "实习经历",
                value, 0.88, false,
                "实习字段匹配: " + name + " → " + internFieldType, variantDesc);
        result.setRecordRef("internship:" + chosen.getId());
        result.setRecordName(name);
        result.setGroup("work_experience");
        return result;
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
        MatchResult result = new MatchResult(
                field.getFieldId(), "skill", "专业技能", variant.get().content(),
                0.92, false, "技能字段匹配: " + plan.fieldType(), variantDesc);
        result.setGroup("skill");
        return result;
    }

    // ==================== 证据优先级 / 内容类型约束 / 经历块匹配 ====================

    /** 姓名禁止兜底信号：字段文本命中任一信号时，绝不允许填入姓名 */
    private static final List<String> NAME_FORBIDDEN_SIGNALS = List.of(
            "单位", "公司", "企业", "雇主", "岗位", "职位", "职务", "城市", "地点", "日期", "时间",
            "月薪", "薪资", "薪酬", "年薪", "语言", "掌握程度", "听说", "读写", "行业", "职业",
            "工作年限", "出生", "部门", "学校", "院校", "专业", "学历", "学位", "学号", "项目", "技术栈",
            "证件", "身份证", "紧急", "联系人", "家庭", "父亲", "母亲", "亲属", "地址", "户籍", "户口",
            "身高", "婚姻", "民族", "国籍", "籍贯", "生源", "政治", "课程", "学院", "排名", "届别",
            "类别", "批次", "专利", "奖项", "荣誉", "级别", "证书", "成绩",
            "证明人", "推荐人", "介绍人", "主管");

    /** 职责/描述类信号：命中时禁止填入开放题素材（AI 协作/自我评价/职业规划等） */
    private static final List<String> RESPONSIBILITY_TYPE_SIGNALS = List.of(
            "职责", "工作内容", "工作描述", "实习描述", "项目描述", "项目介绍", "技术栈", "主要工作");

    /** 主证据：中文 label/问题文本/模块标题/附近文本，权重最高；input.name/id/className 不参与 */
    private String primaryEvidence(FieldInfo field) {
        StringBuilder sb = new StringBuilder();
        append(sb, field.getLabel());
        append(sb, field.getQuestionText());
        append(sb, field.getSectionTitle());
        append(sb, field.getNearbyText());
        append(sb, field.getAriaLabel());
        append(sb, field.getPlaceholder());
        append(sb, field.getParentText());
        return sb.toString().toLowerCase();
    }

    /** 弱证据：仅在完全无中文证据时使用，命中结果置信度封顶 0.55（需人工确认） */
    private String weakEvidence(FieldInfo field) {
        StringBuilder sb = new StringBuilder();
        append(sb, field.getName());
        append(sb, field.getId());
        append(sb, field.getClassName());
        return sb.toString().toLowerCase();
    }

    /** 内容类型强约束：禁止姓名填入非姓名字段，禁止开放题素材填入职责/描述字段 */
    private boolean isForbiddenPick(String text, FieldCandidate candidate) {
        if ("name".equals(candidate.fieldKey) && containsAny(text, NAME_FORBIDDEN_SIGNALS)) {
            return true;
        }
        if ("material".equals(candidate.sourceType) && containsAny(text, RESPONSIBILITY_TYPE_SIGNALS)) {
            String key = nz(candidate.fieldKey);
            // 实习/项目类素材允许进描述字段；AI 协作/自我评价/职业规划等一律禁止
            return !"internship".equals(key) && !"project".equals(key);
        }
        return false;
    }

    /** 预览分组：基础信息/教育经历/工作经历/项目经历/专业技能/家庭/语言/专利/科研/校园/开放题 */
    private String groupOf(FieldCandidate candidate) {
        if ("material".equals(candidate.sourceType)) {
            return "material";
        }
        String key = nz(candidate.fieldKey);
        if (key.startsWith("language_")) {
            return "language";
        }
        if (key.startsWith("patent_")) {
            return "patent";
        }
        if (key.startsWith("emergency_")) {
            return "emergency";
        }
        if (key.startsWith("father_") || key.startsWith("family_")
                || key.startsWith("relatives_")) {
            return "family";
        }
        if ("research_experience".equals(key)) {
            return "research";
        }
        if (key.startsWith("campus_")) {
            return "campus";
        }
        return switch (key) {
            case "school", "degree", "major", "gpa", "rank", "thesis", "researchDirection",
                 "graduationDate", "eduStartDate", "highest_education", "highest_degree",
                 "study_mode", "education_type", "graduation_class" -> "education";
            case "expected_work_city", "expectedCity", "applicant_type", "applicantType",
                 "expectedPosition" -> "intent";
            case "internship" -> "work_experience";
            case "project" -> "project_experience";
            case "skill" -> "skill";
            default -> "basic";
        };
    }

    /**
     * 当前模板应填实习经历完整列表（优先级只决定排序，不决定只填一条）：
     * 手动指定经历置顶 → 模板经历配置（仅 autoFillEnabled 参与，按 autoFillPriority 排序）→
     * 内置受众推荐表（公司关键词）→ 全量回退。
     */
    private List<InternshipExperience> orderInternshipsForTemplate(List<InternshipExperience> internships,
            String audience, String jobDirection, Long preferredId,
            Map<String, TemplateExperienceConfig> experienceConfigs) {
        List<InternshipExperience> result = new ArrayList<>();
        if (internships.isEmpty()) {
            return result;
        }
        if (preferredId != null) {
            internships.stream().filter(i -> preferredId.equals(i.getId())).findFirst().ifPresent(result::add);
        }
        // 1. 模板经历配置：显式关闭（autoFillEnabled=false）的不默认填，其余按优先级排序
        List<InternshipExperience> enabled = new ArrayList<>();
        boolean hasConfig = false;
        for (InternshipExperience internship : internships) {
            TemplateExperienceConfig config = experienceConfigs.get("internship:" + internship.getId());
            if (config == null) {
                continue;
            }
            hasConfig = true;
            if (!Boolean.FALSE.equals(config.getAutoFillEnabled())) {
                enabled.add(internship);
            }
        }
        if (hasConfig && !enabled.isEmpty()) {
            enabled.sort(Comparator.<InternshipExperience, Integer>comparing(i -> {
                TemplateExperienceConfig config = experienceConfigs.get("internship:" + i.getId());
                return config != null && config.getAutoFillPriority() != null
                        ? config.getAutoFillPriority() : Integer.MAX_VALUE;
            }).thenComparing(i -> i.getSortOrder() == null ? Integer.MAX_VALUE : i.getSortOrder()));
            for (InternshipExperience internship : enabled) {
                if (!result.contains(internship)) {
                    result.add(internship);
                }
            }
            return result;
        }
        // 2. 内置受众推荐表：大厂版默认不含银行类，银行/国央企版工行优先；命中的全部填入而非只填第一条
        List<String> preferred = INTERNSHIP_PRIORITY_TABLE.get(audience + ":" + nz(jobDirection));
        if (preferred == null) {
            preferred = INTERNSHIP_PRIORITY_TABLE.getOrDefault(audience, INTERNSHIP_PRIORITY_TABLE.get("general"));
        }
        if (preferred != null) {
            for (String keyword : preferred) {
                for (InternshipExperience internship : internships) {
                    boolean hit = nz(internship.getShortName()).contains(keyword)
                            || nz(internship.getCompany()).contains(keyword);
                    if (hit && !result.contains(internship)) {
                        result.add(internship);
                    }
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        // 3. 回退：全量按原排序
        for (InternshipExperience internship : internships) {
            if (!result.contains(internship)) {
                result.add(internship);
            }
        }
        return result;
    }

    /** 当前模板应填项目列表：配置控制启停与顺序，无配置时全部保留 */
    private List<ProjectExperience> orderProjectsForTemplate(List<ProjectExperience> projects,
            Map<String, TemplateExperienceConfig> experienceConfigs) {
        List<ProjectExperience> result = new ArrayList<>();
        if (projects.isEmpty()) {
            return result;
        }
        List<ProjectExperience> enabled = new ArrayList<>();
        boolean hasConfig = false;
        for (ProjectExperience project : projects) {
            TemplateExperienceConfig config = experienceConfigs.get("project:" + project.getId());
            if (config == null) {
                continue;
            }
            hasConfig = true;
            if (!Boolean.FALSE.equals(config.getAutoFillEnabled())) {
                enabled.add(project);
            }
        }
        if (hasConfig && !enabled.isEmpty()) {
            enabled.sort(Comparator.<ProjectExperience, Integer>comparing(p -> {
                TemplateExperienceConfig config = experienceConfigs.get("project:" + p.getId());
                return config != null && config.getAutoFillPriority() != null
                        ? config.getAutoFillPriority() : Integer.MAX_VALUE;
            }).thenComparing(p -> p.getSortOrder() == null ? Integer.MAX_VALUE : p.getSortOrder()));
            result.addAll(enabled);
            return result;
        }
        result.addAll(projects);
        return result;
    }

    /** 经历计划：插件据此判断需要新增多少个经历块并按序绑定（含教育/奖项/家庭成员 repeatable section） */
    private List<AutofillMatchResponse.ExperiencePlanItem> buildExperiencePlan(
            List<InternshipExperience> orderedInternships, List<ProjectExperience> orderedProjects,
            List<EducationExperience> educationList, List<AwardCertificate> awardList,
            List<FamilyMember> familyList) {
        List<AutofillMatchResponse.ExperiencePlanItem> plan = new ArrayList<>();
        for (InternshipExperience internship : orderedInternships) {
            plan.add(new AutofillMatchResponse.ExperiencePlanItem("internship", internship.getId(),
                    internRecordName(internship), internship.getStartDate(), internship.getEndDate()));
        }
        for (ProjectExperience project : orderedProjects) {
            plan.add(new AutofillMatchResponse.ExperiencePlanItem("project", project.getId(),
                    projectRecordName(project), project.getStartDate(), project.getEndDate()));
        }
        for (EducationExperience edu : educationList) {
            plan.add(new AutofillMatchResponse.ExperiencePlanItem("education", edu.getId(),
                    nz(edu.getSchool()), edu.getStartDate(), edu.getEndDate()));
        }
        for (AwardCertificate award : awardList) {
            plan.add(new AutofillMatchResponse.ExperiencePlanItem("award", award.getId(),
                    nz(award.getAwardName()), award.getAwardYear(), null));
        }
        for (FamilyMember member : familyList) {
            String name = hasText(member.getName()) ? nz(member.getRelation()) + "：" + member.getName()
                    : nz(member.getRelation());
            plan.add(new AutofillMatchResponse.ExperiencePlanItem("family", member.getId(), name, null, null));
        }
        return plan;
    }

    private String internRecordName(InternshipExperience internship) {
        return hasText(internship.getShortName()) ? internship.getShortName() : nz(internship.getCompany());
    }

    private String projectRecordName(ProjectExperience project) {
        return hasText(project.getShortName()) ? project.getShortName() : nz(project.getProjectName());
    }

    /**
     * 经历块字段匹配：同一块内所有字段从同一条记录取值（公司/职位/起止时间/部门/职责），禁止串块。
     * blockIndex 超出记录数量时返回 null（需先新增经历块）。
     */
    private MatchResult matchBlockField(Long userId, FieldInfo field, String text, String audience,
                                        String jobDirection, List<InternshipExperience> orderedInternships,
                                        List<ProjectExperience> orderedProjects,
                                        List<EducationExperience> educationList,
                                        List<AwardCertificate> awardList,
                                        List<FamilyMember> familyList) {
        if ("project".equals(field.getBlockType())) {
            return matchProjectBlockField(userId, field, text, audience, jobDirection, orderedProjects);
        }
        if ("education".equals(field.getBlockType())) {
            return matchEducationBlockField(field, text, educationList);
        }
        if ("award".equals(field.getBlockType())) {
            return matchAwardBlockField(field, text, awardList);
        }
        if ("family".equals(field.getBlockType())) {
            return matchFamilyBlockField(field, text, familyList);
        }
        int index = field.getBlockIndex();
        if (index < 0 || index >= orderedInternships.size()) {
            return null;
        }
        InternshipExperience record = orderedInternships.get(index);
        String recordName = internRecordName(record);
        String recordRef = "internship:" + record.getId();

        // 证明人字段：必须优先于单位/职位/电话判断（“证明人单位”含“单位”字样），
        // 取值始终来自当前块绑定的同一条实习记录，禁止跨记录串用，禁止用紧急联系人/父母兑底。
        if (containsAny(text, BLOCK_CERTIFIER_SIGNALS)) {
            if (containsAny(text, CERTIFIER_COMBINED_SIGNALS)) {
                return blockSimpleResult(field, record.getCertifierCompanyAndPosition(), recordName, recordRef,
                        "证明人单位及职务", "work_experience");
            }
            if (containsAny(text, CERTIFIER_PHONE_SIGNALS)) {
                return blockSimpleResult(field, record.getCertifierPhone(), recordName, recordRef,
                        "证明人联系电话", "work_experience");
            }
            if (containsAny(text, CERTIFIER_EMAIL_SIGNALS)) {
                return blockSimpleResult(field, record.getCertifierEmail(), recordName, recordRef,
                        "证明人邮箱", "work_experience");
            }
            if (containsAny(text, CERTIFIER_COMPANY_SIGNALS)) {
                return blockSimpleResult(field, record.getCertifierCompany(), recordName, recordRef,
                        "证明人单位", "work_experience");
            }
            if (containsAny(text, CERTIFIER_POSITION_SIGNALS)) {
                return blockSimpleResult(field, record.getCertifierPosition(), recordName, recordRef,
                        "证明人职务", "work_experience");
            }
            if (containsAny(text, CERTIFIER_RELATION_SIGNALS)) {
                return blockSimpleResult(field, record.getCertifierRelation(), recordName, recordRef,
                        "证明人与本人关系", "work_experience");
            }
            // 默认：证明人姓名（字段存在但当前记录未填写时返回 null，提示手动补充）
            return blockSimpleResult(field, record.getCertifierName(), recordName, recordRef,
                    "证明人姓名", "work_experience");
        }

        // 短字段：单位/职位/部门/日期/至今（结束时间先判，避免与开始时间关键词冲突）
        if (containsAny(text, BLOCK_END_DATE_KEYWORDS)) {
            return blockDateResult(field, record.getEndDate(), recordName, recordRef, "结束时间", "work_experience");
        }
        if (containsAny(text, BLOCK_START_DATE_KEYWORDS)) {
            return blockDateResult(field, record.getStartDate(), recordName, recordRef, "开始时间", "work_experience");
        }
        if (containsAny(text, BLOCK_PRESENT_KEYWORDS)) {
            if (hasText(record.getEndDate())) {
                return null; // 已结束的经历不勾“至今”，留待手动
            }
            MatchResult result = new MatchResult(field.getFieldId(), "internship.isPresent", recordName + "(至今)",
                    "是", 0.90, false, "经历块字段: " + recordName + " → 至今", null);
            result.setRecordRef(recordRef);
            result.setRecordName(recordName);
            result.setGroup("work_experience");
            return result;
        }
        if (containsAny(text, BLOCK_COMPANY_KEYWORDS)) {
            return blockSimpleResult(field, record.getCompany(), recordName, recordRef, "单位名称", "work_experience");
        }
        if (containsAny(text, BLOCK_POSITION_KEYWORDS)) {
            return blockSimpleResult(field, record.getPosition(), recordName, recordRef, "职位名称", "work_experience");
        }
        if (containsAny(text, BLOCK_DEPARTMENT_KEYWORDS)) {
            return blockSimpleResult(field, record.getDepartment(), recordName, recordRef, "部门", "work_experience");
        }
        // 城市/行业/月薪等：经历实体无数据源，标记未匹配，绝不用姓名兕底
        if (containsAny(text, BLOCK_NO_DATA_KEYWORDS)) {
            return null;
        }

        // 长文本：职责/描述/成果/技术栈，从绑定记录取内容版本（模板受众 × 字数限制）
        String fieldType = classifyInternshipFieldType(text);
        if (fieldType == null) {
            fieldType = isLongTextField(field) ? "internship_combined" : null;
        }
        if (fieldType == null) {
            if (containsAny(text, INTERNSHIP_TECH_KEYWORDS)) {
                return blockSimpleResult(field, record.getTechStack(), recordName, recordRef, "技术栈", "work_experience");
            }
            return null;
        }
        if ("internship_tech_stack".equals(fieldType)) {
            return blockSimpleResult(field, record.getTechStack(), recordName, recordRef, "技术栈", "work_experience");
        }
        Optional<ContentVariantService.VariantPick> variant = contentVariantService.pickVariant(
                userId, "internship", record.getId(), audience, jobDirection, fieldType, field.getWordLimit());
        String value;
        String variantDesc = null;
        if (variant.isPresent()) {
            value = variant.get().content();
            variantDesc = variant.get().audienceType() + "/" + variant.get().jobDirection() + "/"
                    + variant.get().lengthType() + "/" + variant.get().fieldType();
        } else {
            value = switch (fieldType) {
                case "internship_responsibility" -> nz(record.getDescription());
                case "internship_result" -> nz(record.getHighlights());
                default -> nz(record.getDescription()) + nz(record.getHighlights());
            };
        }
        if (!hasText(value)) {
            return null;
        }
        MatchResult result = new MatchResult(field.getFieldId(), "internship", recordName + "实习经历",
                value, 0.90, false, "经历块字段: " + recordName + " → " + fieldType, variantDesc);
        result.setRecordRef(recordRef);
        result.setRecordName(recordName);
        result.setGroup("work_experience");
        return result;
    }

    /** 教育块字段匹配：同一块内学校/学历/学位/专业/起止时间/课程等来自同一条教育记录 */
    private MatchResult matchEducationBlockField(FieldInfo field, String text,
                                                 List<EducationExperience> educationList) {
        int index = field.getBlockIndex();
        if (index < 0 || index >= educationList.size()) {
            return null;
        }
        EducationExperience record = educationList.get(index);
        String recordName = nz(record.getSchool());
        String recordRef = "education:" + record.getId();

        // 结束时间（含毕业时间叫法）先判，避免与开始时间关键词冲突
        if (containsAny(text, BLOCK_END_DATE_KEYWORDS) || text.contains("毕业时间") || text.contains("毕业日期")) {
            return blockDateResult(field, record.getEndDate(), recordName, recordRef, "结束时间", "education");
        }
        if (containsAny(text, BLOCK_START_DATE_KEYWORDS) || text.contains("入学时间") || text.contains("入学日期")) {
            return blockDateResult(field, record.getStartDate(), recordName, recordRef, "开始时间", "education");
        }
        if (containsAny(text, BLOCK_EDU_STUDENT_ID_KEYWORDS)) {
            return blockSimpleResult(field, record.getStudentNumber(), recordName, recordRef, "学号", "education");
        }
        if (containsAny(text, BLOCK_EDU_RANK_KEYWORDS)) {
            return blockSimpleResult(field, record.getRank(), recordName, recordRef, "年级排名", "education");
        }
        if (containsAny(text, BLOCK_EDU_STUDY_MODE_KEYWORDS)) {
            return blockSimpleResult(field, record.getStudyMode(), recordName, recordRef, "学习形式", "education");
        }
        if (containsAny(text, BLOCK_EDU_COURSES_KEYWORDS)) {
            return blockSimpleResult(field, record.getCourses(), recordName, recordRef, "主修课程及成绩", "education");
        }
        if (containsAny(text, BLOCK_EDU_TAGS_KEYWORDS)) {
            return blockSimpleResult(field, record.getSchoolTags(), recordName, recordRef, "学校标签", "education");
        }
        if (containsAny(text, BLOCK_EDU_BATCH_KEYWORDS)) {
            return blockSimpleResult(field, record.getAdmissionBatch(), recordName, recordRef, "录取批次", "education");
        }
        if (containsAny(text, BLOCK_EDU_COLLEGE_KEYWORDS)) {
            return blockSimpleResult(field, record.getCollege(), recordName, recordRef, "学院", "education");
        }
        if (containsAny(text, BLOCK_EDU_GPA_KEYWORDS)) {
            return blockSimpleResult(field, record.getGpa(), recordName, recordRef, "GPA", "education");
        }
        // 显示专业：明确要求展示专业时优先 displayMajor
        if (text.contains("显示专业") && hasText(record.getDisplayMajor())) {
            return blockSimpleResult(field, record.getDisplayMajor(), recordName, recordRef, "显示专业", "education");
        }
        if (containsAny(text, BLOCK_EDU_MAJOR_KEYWORDS)) {
            return blockSimpleResult(field, record.getMajor(), recordName, recordRef, "专业", "education");
        }
        if (containsAny(text, BLOCK_EDU_LEVEL_KEYWORDS)) {
            return blockSimpleResult(field, record.getEducationLevel(), recordName, recordRef, "学历", "education");
        }
        if (containsAny(text, BLOCK_EDU_DEGREE_KEYWORDS)) {
            return blockSimpleResult(field, record.getAcademicDegree(), recordName, recordRef, "学位", "education");
        }
        if (containsAny(text, BLOCK_EDU_SCHOOL_KEYWORDS)) {
            return blockSimpleResult(field, record.getSchool(), recordName, recordRef, "学校", "education");
        }
        return null;
    }

    /** 奖项块字段匹配：同一块内名称/时间/级别/类型来自同一条奖项记录 */
    private MatchResult matchAwardBlockField(FieldInfo field, String text, List<AwardCertificate> awardList) {
        int index = field.getBlockIndex();
        if (index < 0 || index >= awardList.size()) {
            return null;
        }
        AwardCertificate record = awardList.get(index);
        String recordName = nz(record.getAwardName());
        String recordRef = "award:" + record.getId();

        if (containsAny(text, BLOCK_AWARD_LEVEL_KEYWORDS)) {
            return blockSimpleResult(field, record.getAwardLevel(), recordName, recordRef, "级别", "award");
        }
        if (containsAny(text, BLOCK_AWARD_TYPE_KEYWORDS)) {
            return blockSimpleResult(field, record.getAwardType(), recordName, recordRef, "类型", "award");
        }
        if (containsAny(text, BLOCK_AWARD_DATE_KEYWORDS)) {
            return blockSimpleResult(field, record.getAwardYear(), recordName, recordRef, "获得时间", "award");
        }
        if (isLongTextField(field) && hasText(record.getDescription())) {
            return blockSimpleResult(field, record.getDescription(), recordName, recordRef, "成果描述", "award");
        }
        if (containsAny(text, BLOCK_AWARD_NAME_KEYWORDS)) {
            return blockSimpleResult(field, record.getAwardName(), recordName, recordRef, "名称", "award");
        }
        return null;
    }

    /** 家庭成员块字段匹配：同一块内关系/姓名/单位/职务/电话等来自同一条 family_member 记录 */
    private MatchResult matchFamilyBlockField(FieldInfo field, String text, List<FamilyMember> familyList) {
        int index = field.getBlockIndex();
        if (index < 0 || index >= familyList.size()) {
            return null;
        }
        FamilyMember record = familyList.get(index);
        String recordName = hasText(record.getName()) ? nz(record.getRelation()) + "：" + record.getName()
                : nz(record.getRelation());
        String recordRef = "family:" + record.getId();

        // 单位/职务/电话先判（“父亲单位”等含明确信号）；“姓名”最后判避免误吸“关系”等字段
        if (containsAny(text, BLOCK_FAMILY_COMPANY_KEYWORDS)) {
            return blockSimpleResult(field, record.getCompany(), recordName, recordRef, "家庭成员单位", "family");
        }
        if (containsAny(text, BLOCK_FAMILY_POSITION_KEYWORDS)) {
            return blockSimpleResult(field, record.getPosition(), recordName, recordRef, "家庭成员职务", "family");
        }
        if (containsAny(text, BLOCK_FAMILY_PHONE_KEYWORDS)) {
            return blockSimpleResult(field, record.getPhone(), recordName, recordRef, "家庭成员联系电话", "family");
        }
        if (containsAny(text, BLOCK_FAMILY_EMAIL_KEYWORDS)) {
            return blockSimpleResult(field, record.getEmail(), recordName, recordRef, "家庭成员邮箱", "family");
        }
        if (containsAny(text, BLOCK_FAMILY_ADDRESS_KEYWORDS)) {
            return blockSimpleResult(field, record.getAddress(), recordName, recordRef, "家庭地址", "family");
        }
        if (containsAny(text, BLOCK_FAMILY_POLITICAL_KEYWORDS)) {
            return blockSimpleResult(field, record.getPoliticalStatus(), recordName, recordRef, "政治面貌", "family");
        }
        if (containsAny(text, BLOCK_FAMILY_RELATION_KEYWORDS)) {
            return blockSimpleResult(field, record.getRelation(), recordName, recordRef, "与本人关系", "family");
        }
        if (containsAny(text, BLOCK_FAMILY_NAME_KEYWORDS)) {
            return blockSimpleResult(field, record.getName(), recordName, recordRef, "家庭成员姓名", "family");
        }
        return null;
    }

    /** 语言块字段匹配：语言类型/掌握程度/听说/读写/证书从 language_* 候选取值，绝不填姓名 */
    private MatchResult matchLanguageBlockField(FieldInfo field, String text, List<FieldCandidate> candidates) {
        FieldCandidate best = null;
        int bestLen = 0;
        for (FieldCandidate candidate : candidates) {
            if (candidate.fieldKey() == null || !candidate.fieldKey().startsWith("language_")) {
                continue;
            }
            for (String keyword : candidate.keywords()) {
                if (hasText(keyword) && text.contains(keyword.toLowerCase()) && keyword.length() > bestLen) {
                    best = candidate;
                    bestLen = keyword.length();
                }
            }
        }
        if (best == null || !hasText(best.value())) {
            return null;
        }
        MatchResult result = new MatchResult(field.getFieldId(), best.fieldKey(), best.fieldName(),
                best.value(), 0.90, false, "语言块字段: " + best.fieldName(), null);
        result.setRecordRef("language:0");
        result.setRecordName("英语");
        result.setGroup("language");
        return result;
    }

    /** 项目块字段匹配：同一块内项目名称/角色/时间/描述/成果/技术栈来自同一个 projectRecord */
    private MatchResult matchProjectBlockField(Long userId, FieldInfo field, String text, String audience,
                                               String jobDirection, List<ProjectExperience> orderedProjects) {
        int index = field.getBlockIndex();
        if (index < 0 || index >= orderedProjects.size()) {
            return null;
        }
        ProjectExperience record = orderedProjects.get(index);
        String recordName = projectRecordName(record);
        String recordRef = "project:" + record.getId();

        if (containsAny(text, BLOCK_END_DATE_KEYWORDS)) {
            return blockDateResult(field, record.getEndDate(), recordName, recordRef, "结束时间", "project_experience");
        }
        if (containsAny(text, BLOCK_START_DATE_KEYWORDS)) {
            return blockDateResult(field, record.getStartDate(), recordName, recordRef, "开始时间", "project_experience");
        }
        if (containsAny(text, BLOCK_PROJECT_ROLE_KEYWORDS)) {
            return blockSimpleResult(field, record.getRole(), recordName, recordRef, "项目角色", "project_experience");
        }
        if (containsAny(text, BLOCK_PROJECT_NAME_KEYWORDS)) {
            return blockSimpleResult(field, record.getProjectName(), recordName, recordRef, "项目名称", "project_experience");
        }

        String fieldType = projectFieldType(text);
        Optional<ContentVariantService.VariantPick> variant = contentVariantService.pickVariant(
                userId, "project", record.getId(), audience, jobDirection, fieldType, field.getWordLimit());
        String value;
        String variantDesc = null;
        if (variant.isPresent()) {
            value = variant.get().content();
            variantDesc = variant.get().audienceType() + "/" + variant.get().jobDirection() + "/"
                    + variant.get().lengthType() + "/" + variant.get().fieldType();
        } else {
            value = switch (fieldType) {
                case "project_overview" -> hasText(record.getProjectIntro())
                        ? record.getProjectIntro() : nz(record.getDescription());
                case "project_responsibility" -> nz(record.getResponsibilities());
                case "project_result" -> nz(record.getResult());
                case "project_tech_stack" -> nz(record.getTechStack());
                default -> nz(record.getDescription()) + nz(record.getResponsibilities()) + nz(record.getResult());
            };
        }
        if (!hasText(value)) {
            return null;
        }
        MatchResult result = new MatchResult(field.getFieldId(), "project", recordName + "项目",
                value, 0.90, false, "项目块字段: " + recordName + " → " + fieldType, variantDesc);
        result.setRecordRef(recordRef);
        result.setRecordName(recordName);
        result.setGroup("project_experience");
        return result;
    }

    /** 块内短字段结果（单位/职位/部门等）；值为空时返回 null 交给未匹配 */
    private MatchResult blockSimpleResult(FieldInfo field, String value, String recordName,
                                          String recordRef, String fieldLabel, String groupType) {
        if (!hasText(value)) {
            return null;
        }
        MatchResult result = new MatchResult(field.getFieldId(), recordRef, recordName + "(" + fieldLabel + ")",
                value, 0.93, false, "经历块字段: " + recordName + " → " + fieldLabel, null);
        result.setRecordRef(recordRef);
        result.setRecordName(recordName);
        result.setGroup(groupType);
        return result;
    }

    /** 块内日期字段：从绑定记录取起止时间并按页面控件格式转换（month→yyyy-MM，date→yyyy-MM-dd 等） */
    private MatchResult blockDateResult(FieldInfo field, String stdDate, String recordName,
                                        String recordRef, String dateLabel, String groupType) {
        if (!hasText(stdDate)) {
            return null;
        }
        String fmt = dateFormatService.detectFormat(field.getType(), field.getPlaceholder(), field.getLabel());
        String value = dateFormatService.format(stdDate, fmt);
        MatchResult result = new MatchResult(field.getFieldId(), recordRef + "." + dateLabel,
                recordName + "(" + dateLabel + ")", value, 0.92, false,
                "经历块字段: " + recordName + " → " + dateLabel + " → " + fmt, null);
        result.setRecordRef(recordRef);
        result.setRecordName(recordName);
        result.setGroup(groupType);
        return result;
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
                    "input", null, sourceType, sourceId, startDate, endDate
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
                    "textarea", material.getTemplateId(),
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
                    field.getFieldType(),
                    field.getTemplateId(),
                    sourceType, sourceId, startDate, endDate
            ));
        }

        // 4. 紧急联系人实体（姓名/关系/电话）：含手机号也按普通字段正常参与匹配，不做脱敏或跳过；
        //    与家庭成员分别独立维护，不允许与家庭成员字段混填。
        EmergencyContact emergency = emergencyContactRepository.findByUserIdAndDeletedFalseOrderByIdAsc(userId)
                .stream()
                .filter(c -> !Boolean.FALSE.equals(c.getEnabled()))
                .findFirst().orElse(null);
        if (emergency != null) {
            addEmergencyCandidate(candidates, "emergency_contact", emergency.getName(), Arrays.asList(
                    "紧急联系人", "紧急联系人姓名", "紧急联络人", "紧急联络人姓名", "应急联系人",
                    "emergency contact", "emergency contact name"));
            addEmergencyCandidate(candidates, "emergency_phone", emergency.getPhone(), Arrays.asList(
                    "紧急联系电话", "紧急联系人电话", "紧急联系人手机", "紧急联络人电话", "应急联系人电话",
                    "emergency phone", "emergency contact phone"));
            addEmergencyCandidate(candidates, "emergency_relation", emergency.getRelation(), Arrays.asList(
                    "紧急联系人关系", "与紧急联系人关系", "联系人关系", "紧急联络人关系"));
        }
        return candidates;
    }

    /** 紧急联系人实体候选：值为空时不生成候选（空字段不参与自动填充） */
    private void addEmergencyCandidate(List<FieldCandidate> candidates, String key, String value,
                                       List<String> keywords) {
        if (!hasText(value)) {
            return;
        }
        candidates.add(new FieldCandidate(key, key, value, keywords, "input", null, null, null, null, null));
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

    // ==================== 值类型强校验（finalValidation） ====================

    private static final java.util.regex.Pattern EMAIL_PATTERN = java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final java.util.regex.Pattern PHONE_PATTERN = java.util.regex.Pattern.compile("^1\\d{10}$");
    private static final java.util.regex.Pattern DATE_LIKE_PATTERN = java.util.regex.Pattern.compile("(19|20)?\\d{2}[年./-]\\d{1,2}");
    private static final List<String> LANGUAGE_WORDS = List.of("英语", "日语", "韩语", "法语", "德语", "俄语", "西班牙语", "english", "japanese");
    private static final List<String> LANGUAGE_CONTENT_SIGNALS = List.of("cet-4", "cet-6", "四六级", "雅思", "托福", "ielts", "toefl", "tem-4", "tem-8");

    private boolean isLanguageWordValue(String value) {
        String v = lower(value);
        return LANGUAGE_WORDS.stream().anyMatch(v::contains);
    }

    /** 值是否为语言能力类内容（语种/等级/证书分数），禁止流入姓名/邮箱/手机/单位/职位等字段 */
    private boolean isLanguageLikeValue(String value) {
        if (!hasText(value) || value.length() > 30) return false;
        String v = lower(value);
        if (isLanguageWordValue(value)) return true;
        return LANGUAGE_CONTENT_SIGNALS.stream().anyMatch(v::contains);
    }

    /**
     * 值类型强校验：字段语义与候选值格式冲突时返回原因（该结果标记为疑似错误）。
     * 仅对短字段校验；长文本（职责/描述/开放题）不校验。
     */
    private String typeConflictReason(String text, FieldInfo field, FieldCandidate candidate) {
        if (isLongTextField(field)) return null;
        String value = candidate.value;
        if (!hasText(value)) return null;
        String type = lower(field.getType());
        boolean emailLike = text.contains("邮箱") || text.contains("email") || "email".equals(type);
        boolean phoneLike = text.contains("手机") || text.contains("电话") || "tel".equals(type);
        boolean nameLike = (text.contains("姓名") || text.contains("真实姓名"))
                && !text.contains("证明人") && !text.contains("联系人") && !text.contains("推荐人")
                && !text.contains("亲属") && !text.contains("成员");
        boolean langField = text.contains("语言类型") || text.contains("语种") || text.contains("外语")
                || text.contains("语言名称") || (text.contains("语言") && text.contains("类型"));

        if (emailLike && !EMAIL_PATTERN.matcher(value.trim()).matches()) {
            return "邮箱字段推荐值\"" + value + "\"不是邮箱格式";
        }
        if (phoneLike && !PHONE_PATTERN.matcher(value.trim()).matches()) {
            return "手机号字段推荐值\"" + value + "\"不是 11 位手机号";
        }
        if (nameLike && !isPersonNameValue(value)) {
            return "姓名字段推荐值\"" + value + "\"不符合姓名类型，疑似将其他分类内容误填到姓名字段";
        }
        if (langField && !isLanguageLikeValue(value)) {
            return "语言类型字段推荐值\"" + value + "\"不是语种/语言能力内容";
        }
        // 语种与证书类值只能进语言类字段，绝不允许出现在基础信息/经历字段中（“英语”填到姓名的根因防线）
        if (isLanguageLikeValue(value) && !langField
                && !text.contains("水平") && !text.contains("听说") && !text.contains("读写")
                && !text.contains("掌握") && !text.contains("成绩") && !text.contains("证书")) {
            return "语言能力内容\"" + value + "\"不允许填入该字段（" + truncate(text, 20) + "）";
        }
        return null;
    }

    /** 中文姓名：2-4 个汉字（少数民族姓名放宽到 2-15 个汉字，不含数字/字母/符号） */
    private boolean isPersonNameValue(String value) {
        if (!hasText(value)) return false;
        String v = value.trim();
        if (v.length() < 2 || v.length() > 15) return false;
        return v.matches("[\\u4e00-\\u9fa5·]+") || v.matches("[A-Za-z\\s]{2,30}");
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
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
