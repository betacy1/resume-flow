package com.resumeflow.init;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeflow.entity.*;
import com.resumeflow.repository.*;
import com.resumeflow.service.ProfileVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * demo 用户初始化器（仅 dev 环境）
 * 启动时检查 demo 用户：不存在则创建用户并初始化全部简历数据；
 * 已存在但缺少内容版本（旧库升级）则清理后重建业务数据；已完整初始化则跳过。
 */
@Slf4j
@Component
@Profile({"dev", "prod"})
@Order(10)
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    /**
     * prod 环境默认不初始化（避免覆盖线上已维护的数据）；
     * ECS 首次部署需要初始化/重建 demo 数据时，设置环境变量 DEMO_INIT_ENABLED=true 后重启。
     */
    @org.springframework.beans.factory.annotation.Value("${DEMO_INIT_ENABLED:${resumeflow.demo-init-enabled:true}}")
    private boolean initEnabled;

    private final SysUserRepository sysUserRepository;
    private final UserProfileRepository userProfileRepository;
    private final EducationExperienceRepository educationRepository;
    private final InternshipExperienceRepository internshipRepository;
    private final ProjectExperienceRepository projectRepository;
    private final SkillProfileRepository skillRepository;
    private final AwardCertificateRepository awardRepository;
    private final ApplicationTemplateRepository templateRepository;
    private final AnswerMaterialRepository materialRepository;
    private final UserCustomFieldRepository customFieldRepository;
    private final ContentVariantRepository contentVariantRepository;
    private final TemplateExperienceConfigRepository templateConfigRepository;
    private final ProfileSyncStateRepository syncStateRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final ProfileVersionService versionService;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /** 受众风格前缀：国央企/银行版本突出稳定合规，大厂/通用版本直接陈述 */
    private static final String PREFIX_STATE_OWNED = "注重稳定可靠与规范流程，强调协同落地、安全可控与可追溯：";
    private static final String PREFIX_BANK = "围绕风险控制与系统稳定，保障数据一致性、流程规范、审计留痕与生产变更安全：";

    // ==================== 简历文本素材 ====================

    private static final String SELF_EVALUATION = """
            本人具备较强的学习能力、责任意识和工程实践能力，研究生阶段两次获得北京理工大学研究生学业一等奖学金。\
            在实习和项目经历中，曾参与金融支付、机器人智能云平台等后端系统研发，熟悉 Java、Spring Boot、Go、Redis、\
            MySQL、gRPC、MQ 等技术，能够较快理解业务需求并落地为稳定可维护的系统功能。工作中注重代码质量、接口规范、\
            异常处理和团队协作，能够主动推进问题排查与方案优化。""";

    private static final String AI_COLLABORATION = """
            在实习和项目开发过程中，我会将 Claude Opus、GPT、Cursor、Codex 等 AI 工具作为研发辅助和效率提升工具，\
            主要用于需求拆解、技术方案梳理、代码生成辅助、问题排查、测试用例补充和技术文档整理。比如在京东科技机器人智能\
            云平台项目中，我参与 Cloud VLM 视觉语言模型服务和 Robot AIUI 智能对话系统建设，项目涉及图像识别、视觉问答、\
            智能对话、RAG 语义检索、Agent 插件化管理、多模型接入和流式接口等能力。在项目推进过程中，我使用 AI 工具辅助梳理\
            多模态服务链路和对话工作流，将请求解析、模型路由、插件调用、结果返回、异常处理和链路追踪等流程拆解为清晰模块，\
            并辅助生成部分接口代码、proto 协议结构、DTO、参数校验逻辑和测试用例思路。对于视觉推理链路和智能对话链路中的\
            边界问题，例如模型调用超时、返回结构不一致、上下文透传缺失、消息乱序、流式响应中断等，我也会借助 AI 进行排查思路\
            补充和方案对比。在实际使用中，我不会直接依赖 AI 生成结果，而是结合业务场景、项目规范和接口联调结果进行人工审查、\
            修改和验证。总体来看，AI 工具帮助我提升了复杂业务理解、技术方案完善、代码编写和文档整理效率，但核心设计判断、\
            业务校验和最终交付仍由自己负责。""";

    private static final String HOBBY = """
            我性格开朗，具备一定的表达和组织能力，曾参与主持、校园活动组织等工作，能够在团队协作和公开表达场景中保持良好的\
            沟通状态。日常喜欢羽毛球、乒乓球等运动，也学习过拉丁舞，注重通过运动和艺术活动保持良好的精神状态和执行力。""";

    private static final String CAREER_PLAN = """
            短期内，我希望扎根后端开发与 AI 应用工程方向，深入理解业务与系统架构，快速成长为能够独立负责核心模块的开发成员；\
            中长期来看，计划在金融科技或智能系统方向持续深耕，提升架构设计与技术方案主导能力，推动更复杂业务场景的落地与演进，\
            并成为兼具业务理解与工程能力的技术骨干。""";

    private static final String WHY_COMPANY = """
            贵公司在行业内具备领先的技术积累与业务规模，团队重视工程质量与个人成长，与我的职业发展方向高度契合。\
            我希望在贵平台参与核心系统建设，将实习与项目中积累的支付、云平台与 AI 应用经验与实际业务结合，与团队共同成长。""";

    private static final String WHY_POSITION = """
            该岗位与我的技术栈和职业规划高度匹配：我在 Java、Go 后端开发、分布式系统与 AI 应用工程方面的实习和项目经验，\
            可以直接支撑岗位工作内容；同时我希望通过该岗位继续深入业务理解，提升系统设计与复杂问题拆解能力。""";

    // ==================== 专业技能（七个分组，公共数据） ====================

    private static final String SKILL_BACKEND = "熟悉 Java、Go，熟悉 Spring Boot、MyBatis-Plus、JPA、Kitex、Gin、"
            + "RESTful API、gRPC / Proto、Thrift IDL，具备微服务分层开发、接口设计和后端业务建模经验。";
    private static final String SKILL_DATABASE_MIDDLEWARE = "熟悉 MySQL、Redis、Elasticsearch、Milvus、OSS，"
            + "具备数据建模、缓存设计、索引优化、向量检索链路接入和高频查询场景优化经验。";
    private static final String SKILL_DISTRIBUTED_STABILITY = "熟悉 RPC 调用、配置中心、异步消息、TCC 配置化规则、"
            + "重试补偿、幂等控制、链路日志、熔断降级、异常处理和服务解耦，具备复杂业务状态流转和系统可靠性建设经验。";
    private static final String SKILL_AI_ENGINEERING = "具备 Cloud VLM、Robot AIUI、RAG、Agent、多模态视觉处理、"
            + "多模型路由、流式响应和智能对话系统开发经验，熟悉 AI 能力接入、模型服务调用、上下文透传和工程化落地流程。";
    private static final String SKILL_DEVOPS_PLATFORM = "具备持续交付平台、自动化发布、镜像构建、模板部署、自动投验、"
            + "跨集群迁移、任务审计和失败恢复相关实践经验，熟悉 PaaS、Harbor、Apollo、ETCD、HAProxy 等基础设施能力封装。";
    private static final String SKILL_FRONTEND_TOOLS = "了解 Vue3、TypeScript、Vite、Element Plus，具备前后端联调、"
            + "接口文档编写、Postman 测试和 Git 协作经验；熟悉 Cursor、Codex、Claude Opus、GPT 等 AI 辅助开发工具。";
    private static final String SKILL_COMPUTER_BASIC = "具备数据结构与算法基础，熟悉操作系统、计算机网络、JVM 基本原理和"
            + "多线程并发，能够结合业务场景进行问题定位、性能分析和代码优化。";

    /** 七个技能分组：skillKey、标题、内容 */
    private static final String[][] SKILL_GROUPS = {
            {"skill_backend", "后端开发", SKILL_BACKEND},
            {"skill_database_middleware", "数据库与中间件", SKILL_DATABASE_MIDDLEWARE},
            {"skill_distributed_stability", "分布式与稳定性", SKILL_DISTRIBUTED_STABILITY},
            {"skill_ai_engineering", "AI 应用工程化", SKILL_AI_ENGINEERING},
            {"skill_devops_platform", "DevOps 与平台工程", SKILL_DEVOPS_PLATFORM},
            {"skill_frontend_tools", "前端与工程工具", SKILL_FRONTEND_TOOLS},
            {"skill_computer_basic", "计算机基础", SKILL_COMPUTER_BASIC},
    };

    // ==================== 各模板专业技能简短版（侧重不同） ====================

    private static final String SKILL_SHORT_BIG_TECH = "熟悉 Java、Go、Spring Boot、Kitex、Gin、RESTful API、"
            + "gRPC / Proto、Thrift IDL，具备微服务分层开发和复杂业务建模经验；熟悉 MySQL、Redis、Elasticsearch、"
            + "Milvus、MQ、TCC、幂等控制、重试补偿、熔断降级和链路日志；具备 Cloud VLM、Robot AIUI、RAG、Agent、"
            + "多模型路由、流式响应和 AI 应用工程化落地经验。";
    private static final String SKILL_SHORT_STATE_OWNED = "熟悉 Java、Go、Spring Boot、JPA、MyBatis-Plus、"
            + "RESTful API、gRPC / Proto，具备后端接口设计、领域建模和系统平台建设经验；熟悉 Redis、MySQL、异步消息、"
            + "重试补偿、幂等控制、链路日志和异常处理；具备持续交付、自动投验、任务审计、跨系统协同、Cloud IoT、"
            + "Cloud VLM、Robot AIUI 等智能系统工程化实践经验。";
    private static final String SKILL_SHORT_BANK = "熟悉 Java、Go、Spring Boot、JPA、Kitex、RESTful API、"
            + "gRPC / Proto、Thrift IDL，具备金融科技和支付账户类后端研发经验；熟悉 MySQL、Redis、MQ、TCC、RPC、"
            + "幂等控制、重试补偿、异常处理和链路日志；具备账户状态治理、资产安全校验、风险拦截、持续交付、自动投验、"
            + "发布审计和系统可靠性建设经验。";
    private static final String SKILL_SHORT_GENERAL = "熟悉 Java、Go、Spring Boot、MyBatis-Plus、JPA、Kitex、Gin、"
            + "RESTful API、gRPC / Proto，具备后端分层开发、接口设计和业务建模经验；熟悉 MySQL、Redis、"
            + "Elasticsearch、Milvus、MQ、TCC、幂等控制、重试补偿和异常处理；具备支付账户治理、智能云平台、"
            + "AI 应用工程化和 DevOps 平台研发实践经验。";

    // ==================== 各模板专业技能排序（侧重不同） ====================

    private static final String SKILL_ORDER_BIG_TECH = "skill_backend,skill_distributed_stability,"
            + "skill_database_middleware,skill_ai_engineering,skill_frontend_tools,skill_computer_basic,"
            + "skill_devops_platform";
    private static final String SKILL_ORDER_STATE_OWNED = "skill_backend,skill_devops_platform,"
            + "skill_ai_engineering,skill_distributed_stability,skill_database_middleware,skill_computer_basic,"
            + "skill_frontend_tools";
    private static final String SKILL_ORDER_BANK = "skill_backend,skill_distributed_stability,"
            + "skill_devops_platform,skill_database_middleware,skill_ai_engineering,skill_computer_basic,"
            + "skill_frontend_tools";
    private static final String SKILL_ORDER_GENERAL_BACKEND = "skill_backend,skill_database_middleware,"
            + "skill_distributed_stability,skill_ai_engineering,skill_devops_platform,skill_frontend_tools,"
            + "skill_computer_basic";

    /** 技能字段匹配关键词（招聘网站字段名命中即自动填入技能内容） */
    private static final List<String> SKILL_MATCH_KEYWORDS = List.of(
            "专业技能", "技能", "个人技能", "技能特长", "技术能力", "技术栈", "掌握技能", "专业能力", "开发技能",
            "计算机技能", "编程技能", "熟悉技术", "软件技能", "核心技能", "技术关键词", "技能标签", "IT技能",
            "技术专长", "相关技能");

    @Override
    public void run(String... args) {
        if (!initEnabled) {
            log.info("DEMO_INIT_ENABLED=false，跳过 demo 数据初始化");
            return;
        }
        transactionTemplate.executeWithoutResult(status -> init());
    }

    private void init() {
        SysUser demo = sysUserRepository.findByUsernameAndDeletedFalse("demo").orElseGet(() -> {
            SysUser user = new SysUser();
            user.setUsername("demo");
            user.setPassword(passwordEncoder.encode("123456"));
            user.setEmail("demo@resumeflow.com");
            user.setPhone("18813108802");
            return sysUserRepository.save(user);
        });
        Long userId = demo.getId();

        if (contentVariantRepository.countByUserIdAndDeletedFalse(userId) > 0
                && contentVariantRepository.countByUserIdAndJobDirectionNotNullAndDeletedFalse(userId) > 0
                && templateConfigRepository.countByUserIdAndDeletedFalse(userId) > 0
                && contentVariantRepository.countByUserIdAndSourceTypeAndDeletedFalse(userId, "skill") > 0
                && customFieldRepository.findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId).stream()
                        .anyMatch(f -> "language_type".equals(f.getFieldKey()))
                && familyMemberRepository.countByUserIdAndDeletedFalse(userId) >= 2) {
            log.info("demo 用户数据已初始化（含岗位方向维度版本、模板经历配置、专业技能版本、新增字段与家庭成员），跳过");
            return;
        }

        log.info("旧库升级（缺少新增字段/内容版本）：清理后重建 demo 用户业务数据");
        log.info("开始初始化 demo 用户简历数据 (userId={})", userId);
        cleanup(userId);
        initProfile(userId);
        initEducation(userId);
        List<InternshipExperience> internships = initInternships(userId);
        List<ProjectExperience> projects = initProjects(userId);
        initAwards(userId);
        initSkills(userId);
        initFamily(userId);
        initEmergencyContact(userId);
        Map<String, ApplicationTemplate> templates = initTemplates(userId);
        Map<String, AnswerMaterial> materials = initMaterials(userId);
        initCustomFields(userId, internships, projects, materials);
        initVariants(userId, internships, projects, materials);
        initSkillVariants(userId, templates);
        initTemplateConfigs(userId, templates, internships, projects);
        versionService.rebuild(userId);
        log.info("demo 用户数据初始化完成：3 段教育经历、3 条实习、6 个项目、4 个模板、内容版本 {} 条",
                contentVariantRepository.countByUserIdAndDeletedFalse(userId));
    }

    /** 旧库升级：清理历史业务数据后重建（保留用户与登录凭证） */
    private void cleanup(Long userId) {
        contentVariantRepository.deleteByUserId(userId);
        templateConfigRepository.deleteByUserId(userId);
        customFieldRepository.deleteByUserId(userId);
        materialRepository.deleteByUserId(userId);
        templateRepository.deleteByUserId(userId);
        awardRepository.deleteByUserId(userId);
        skillRepository.deleteByUserId(userId);
        projectRepository.deleteByUserId(userId);
        internshipRepository.deleteByUserId(userId);
        educationRepository.deleteByUserId(userId);
        familyMemberRepository.deleteByUserId(userId);
        emergencyContactRepository.deleteByUserId(userId);
        syncStateRepository.deleteByUserId(userId);
    }

    // ==================== 基础信息 ====================

    private void initProfile(Long userId) {
        UserProfile profile = userProfileRepository.findByUserIdAndDeletedFalse(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            return p;
        });
        profile.setName("胡宇欣");
        profile.setGender("女");
        profile.setPhone("18813108802");
        profile.setEmail("m15128278966@163.com");
        profile.setQq("2318402884");
        profile.setWechat("15128278966");
        profile.setCurrentLocation("中国大陆 / 北京 / 北京市");
        profile.setPoliticalStatus("共青团员");
        profile.setIdCard("130681200010281261");
        profile.setEmergencyContact("张巍");
        profile.setEmergencyPhone("13932241704");
        profile.setFamilyMembers("父亲：胡林喜，电话 13623225862；紧急联络人：张巍，电话 13932241704");
        profile.setApplicantType("境内院校中国籍2027届应届毕业生");
        profile.setTargetPosition("AI应用工程师");
        profile.setTargetCity("北京市");
        profile.setAcceptOtherCity("是");
        profile.setSchool("北京理工大学");
        profile.setMajor("新一代电子信息技术");
        profile.setDegree("硕士研究生");
        profile.setGraduationDate("2027-07-01");
        profile.setExpectedCity("北京市");
        profile.setExpectedPosition("后端开发 / AI应用工程师 / 金融科技");
        profile.setSelfIntroduction(SELF_EVALUATION);
        userProfileRepository.save(profile);
    }

    // ==================== 教育经历 ====================

    private void initEducation(Long userId) {
        EducationExperience master = new EducationExperience();
        master.setUserId(userId);
        master.setSchool("北京理工大学");
        master.setSchoolTags("985 / 211 / 双一流");
        master.setStudentNumber("3220242151");
        master.setEducationLevel("硕士研究生");
        master.setAcademicDegree("硕士");
        master.setDegree("硕士研究生");
        master.setStudyMode("全国普通高等院校全日制");
        master.setMajor("电子科学与技术");
        master.setDisplayMajor("新一代电子信息技术");
        master.setCollege("集成电路与电子学院");
        master.setStartDate("2025-09-01");
        master.setEndDate("2027-06-30");
        master.setGpa("3.5/4");
        master.setRank("前30%");
        master.setAdvisor("王业亮");
        master.setLab("电工电子国家级实验室教学示范中心");
        master.setResearchDirection("Java后端、分布式系统、云原生");
        master.setThesis("面向无线电池管理系统的监测数据异常检测与纠错方法研究");
        master.setCourses("群智感知技术与安全（92）；基于 ARM 的嵌入式系统基础与应用（92）；"
                + "人工智能与大数据综合实战（86）；医学信号处理（86）；集成电路设计实践（90）；"
                + "光电传感基础（97）；矩阵分析（87）；新时代中国特色社会主义理论与实践（90）");
        master.setHonors("北京理工大学研究生学业一等奖学金2次");
        master.setIsDefault(true);
        master.setSortOrder(0);
        educationRepository.save(master);

        EducationExperience bachelor = new EducationExperience();
        bachelor.setUserId(userId);
        bachelor.setSchool("河北工业大学");
        bachelor.setSchoolTags("211 / 双一流");
        bachelor.setStudentNumber("183995");
        bachelor.setEducationLevel("大学本科");
        bachelor.setAcademicDegree("学士");
        bachelor.setDegree("本科");
        bachelor.setStudyMode("普通全日制");
        bachelor.setMajor("电子信息工程");
        bachelor.setCollege("电子信息工程学院");
        bachelor.setStartDate("2018-09-01");
        bachelor.setEndDate("2022-06-30");
        bachelor.setGpa("3/4");
        bachelor.setAdmissionBatch("本科第一批");
        bachelor.setAdvisor("邱波");
        bachelor.setLab("电子与通信工程国家级实验教学示范中心");
        bachelor.setResearchDirection("软硬件协同时序数据处理");
        bachelor.setThesis("LAMOST光谱参数测量模式识别方法对比研究");
        bachelor.setCourses("深度学习（92）；智能传感器（88）；数学建模（96）；Python程序设计（96）；"
                + "电路与电子线路基础（92）；模式识别（90）；智能数据挖掘（98）；单片机应用系统综合实践（89）");
        bachelor.setHonors("校学生会优秀部长");
        bachelor.setIsDefault(false);
        bachelor.setSortOrder(1);
        educationRepository.save(bachelor);

        EducationExperience highSchool = new EducationExperience();
        highSchool.setUserId(userId);
        highSchool.setSchool("河北省高碑店市第一中学");
        highSchool.setEducationLevel("高中");
        highSchool.setDegree("高中");
        highSchool.setStartDate("2015-09-01");
        highSchool.setEndDate("2018-06-30");
        highSchool.setIsDefault(false);
        highSchool.setSortOrder(2);
        educationRepository.save(highSchool);
    }

    // ==================== 实习经历 ====================

    private record InternshipDef(String company, String department, String position, String start, String end,
                                 String techStack, String raw, String highlights, String shortName) {
    }

    private List<InternshipExperience> initInternships(Long userId) {
        List<InternshipDef> defs = List.of(
                new InternshipDef("中国工商银行北京市分行移动金融建设部", "移动金融建设部", "金融科技岗", "2026-07-01", "2026-08-31",
                        "Java / Spring Boot / JPA / Redis / Vue3 / TypeScript / Vite / PaaS / Harbor / Apollo / ETCD / HAProxy / RESTful API",
                        "参与研发企业级 DevOps 一体化交付平台，围绕持续交付、精准出版、自动投验、生产发布、环境路由切换、任务审计及存量系统跨集群批量迁移等场景，负责后端领域建模、接口设计、基础设施客户端封装及发布任务可靠性建设，推动交付流程标准化、自动化与可追溯。",
                        "形成统一一体化交付入口，通过异步任务持久化、Redis 发布互斥、熔断、失败恢复和操作审计机制，减少多平台切换与人工操作成本，提升发布任务可靠性与可追溯性。",
                        "工行"),
                new InternshipDef("字节跳动-国际支付", "国际支付", "AI应用后端开发", "2026-05-01", "2026-06-30",
                        "Go / Kitex / Thrift IDL / Redis / MySQL / TCC / MQ / RPC",
                        "参与 TikTok Pay / PIPO Wallet 国际支付账户体系后端研发，围绕越南区 P2P Transfer 用户间转账、多场景开户注册、账户状态治理、KYC 引导、账户关闭/注销拦截及资产安全校验等场景建设钱包用户域能力。参与账号侧状态校验、在途交易治理、账户关闭预检查、TCC 配置化规则、Redis 流程状态缓存、MQ 异步补偿、KYC 回调乱序处理和业务幂等机制建设，提升账户操作链路稳定性与异常场景处理一致性。",
                        "沉淀统一的账户状态校验与交易治理能力，支撑 P2P 转账、账户关闭预检查等多场景复用，提升账号侧链路稳定性与异常场景处理一致性。",
                        "字节跳动"),
                new InternshipDef("京东集团-京东科技", "应用及智能交互组", "软件开发工程师", "2025-11-17", "2026-03-09",
                        "Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / Elasticsearch / OSS / Go / Gin / Milvus / Docker / Kubernetes",
                        "参与机器人智能云平台后端研发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块，建设设备接入管理、多模态视觉处理、智能对话、语义检索和多模型调度等能力，支撑机器人配件与智能设备生态的云端服务化接入。参与设备全生命周期管理、gRPC/proto 通信、视觉语言模型服务、JSON Schema 校验、多级重试、异步处理、流式响应、RAG 语义检索、Agent 插件化管理、多模型路由、降级、熔断、链路追踪和 Kubernetes 容器化部署。",
                        "支撑机器人智能云平台多模块稳定运行，构建设备接入、多模态视觉、智能对话与多模型调度的云端服务化能力。",
                        "京东科技"));

        List<InternshipExperience> result = new ArrayList<>();
        int order = 0;
        // 三段实习全部完整保存；模板差异（展示/自动填充/优先级）由 template_experience_config 配置表控制，
        // 不再在经历实体上做受众排除。
        for (InternshipDef def : defs) {
            InternshipExperience entity = new InternshipExperience();
            entity.setUserId(userId);
            entity.setCompany(def.company);
            entity.setDepartment(def.department);
            entity.setPosition(def.position);
            entity.setStartDate(def.start);
            entity.setEndDate(def.end);
            entity.setTechStack(def.techStack);
            entity.setHighlights(def.highlights);
            entity.setDescription(def.raw);
            entity.setShortName(def.shortName);
            entity.setIsDefault(order == 0);
            entity.setSortOrder(order++);
            // 证明人信息：京东预填完整；工行/字节字段存在但内容为空，由用户后续补充，绝不用其他字段兑底
            if ("京东科技".equals(def.shortName)) {
                entity.setCertifierName("冯昱杰");
                entity.setCertifierCompany("京东集团-京东科技");
                entity.setCertifierPosition("软件开发工程师（正职）");
                entity.setCertifierCompanyAndPosition("京东集团-京东科技-软件开发工程师（正职）");
                entity.setCertifierPhone("18835068199");
                entity.setCertifierRelation("实习证明人");
            }
            result.add(internshipRepository.save(entity));
        }
        return result;
    }

    // ==================== 家庭成员与紧急联系人 ====================

    /** 家庭成员：父亲/母亲两条完整记录（family_member 结构化存储，与紧急联系人分别独立维护） */
    private void initFamily(Long userId) {
        FamilyMember father = new FamilyMember();
        father.setUserId(userId);
        father.setRelation("父亲");
        father.setName("胡林喜");
        father.setCompany("涿州市凌云股份有限公司");
        father.setPosition("技术员");
        father.setPhone("13623225862");
        father.setAddress("河北省保定市涿州市学校路育才家园2号楼一单元4层");
        father.setSortOrder(0);
        familyMemberRepository.save(father);

        FamilyMember mother = new FamilyMember();
        mother.setUserId(userId);
        mother.setRelation("母亲");
        mother.setName("张巍");
        mother.setCompany("涿州市松林店中学");
        mother.setPosition("教师");
        mother.setPhone("13932241704");
        mother.setAddress("河北省保定市涿州市学校路育才家园2号楼一单元4层");
        mother.setSortOrder(1);
        familyMemberRepository.save(mother);
    }

    /** 紧急联系人：母亲张巍（与 family_member 中的母亲记录分别维护） */
    private void initEmergencyContact(Long userId) {
        EmergencyContact contact = new EmergencyContact();
        contact.setUserId(userId);
        contact.setName("张巍");
        contact.setRelation("母亲");
        contact.setPhone("13932241704");
        contact.setCompany("涿州市松林店中学");
        contact.setPosition("教师");
        contact.setAddress("河北省保定市涿州市学校路育才家园2号楼一单元4层");
        emergencyContactRepository.save(contact);
    }

    // ==================== 项目经历 ====================

    private record ProjectDef(String name, String start, String end, String role, String techStack,
                              String intro, String responsibilities, String result, String shortName) {
    }

    private List<ProjectExperience> initProjects(Long userId) {
        List<ProjectDef> defs = List.of(
                new ProjectDef("研发提效：企业级 DevOps 一体化交付平台", "2026-07-01", "2026-08-31", "后端开发 / 平台研发",
                        "Java / Spring Boot / JPA / Redis / Vue3 / TypeScript / Vite / PaaS / Harbor / Apollo / ETCD / HAProxy",
                        "面向应用构建、跨集群迁移及生产发布场景，独立设计并研发集持续交付、精准出版、自动投验、环境路由切换和任务审计于一体的交付平台。",
                        "基于 Spring Boot、JPA 完成项目、集群、构建发布、部署任务及操作审计等领域建模与 RESTful API 设计；设计自动化持续交付链路，覆盖 Maven 打包、制品封装、镜像构建、应用及模板创建、滚动升级、应用启停与版本回滚；封装 PaaS、Harbor、Apollo、ETCD、HAProxy 等基础设施客户端及鉴权机制；实现精准出版、自动投验及跨集群批量迁移，支持资源选择、环境参数映射、配置转换、网络预检、资源导入、模板部署、健康检查和失败恢复。",
                        "形成统一一体化交付入口，通过异步任务持久化、Redis 发布互斥、熔断、失败恢复和操作审计机制，减少多平台切换与人工操作成本，提升发布任务可靠性与可追溯性。",
                        "DevOps交付平台"),
                new ProjectDef("TikTok Pay 钱包用户域账户治理与 P2P Transfer 能力建设", "2026-06-01", "2026-07-01", "AI 应用后端开发",
                        "Go / Kitex / Thrift IDL / Redis / TCC / MQ / RPC",
                        "面向越南区 P2P Transfer 用户间转账场景，建设账号侧状态校验、在途交易治理、账户关闭预检查与用户引导能力，解决多入口、多状态、多系统依赖下账户操作判断分散、重复开发和异常场景处理不一致的问题。",
                        "设计并实现转账在途交易统一校验能力，聚合交易系统与钱包产品 SDK 数据源，统一判断待收款、处理中、退款中等在途订单；将在途交易检查接入账户关闭预检查，命中风险项后返回拦截原因、文案 key、按钮类型及跳转链接；设计账户状态与交易方向匹配规则，按出金/入金方向判断账户 Normal、StopIn、StopOut 状态下的可操作性；通过 TCC 动态配置不同国家、入口、场景下的检查器、拦截优先级和返回结果；引入短周期 Redis 缓存并按 WalletUid 控制并发变更风险；状态变更事件通过 MQ 广播，消费侧结合业务唯一键进行幂等处理。",
                        "沉淀统一的账户状态校验与交易治理能力，支撑 P2P 转账、账户关闭预检查、交易预检查、账户权限校验等多场景复用，提升账号侧链路稳定性与可维护性。",
                        "P2P Transfer"),
                new ProjectDef("钱包用户域多场景开户与账户治理能力建设（BNPL / TTS）", "2026-05-11", "2026-05-27", "AI 应用后端开发",
                        "Go / Kitex / Thrift IDL / Redis / MySQL / TCC / MQ / RPC",
                        "面向 BNPL 先买后付和 TTS 多场景钱包建设统一开户注册与账户治理框架，将用户注册、KYC、PIN、开户、协议及通知等能力沉淀为可配置流程。",
                        "将 BNPL、TTS 等场景抽象为配置化流程，基于节点编排设计容灾策略与执行分离机制，采用“同步编排 + 异步重试”模型；完成 BNPL 从用户注册到账户开通、PIN 设置、协议签署、风险通知的完整流程编排；设计注册单缓存与流程状态共享机制，将流程中间态缓存至 Redis，通过请求参数 + Redis 注册信息合并实现跨请求流程推进；通过 client/domain 分层隔离风控、KYC、协议中心、用户核心、账户核心等下游依赖；接入用户创建、KYC 更新、协议签署、账户开通等消息通知，KYC 回调消息按时间戳排序，仅处理最新状态。",
                        "沉淀多场景开户注册与账户治理能力，降低新增业务场景接入与重复联调成本，通过 Redis 流程状态缓存、节点级重试、并发控制和消息通知机制，提升跨请求流程恢复能力与异常场景稳定性。",
                        "BNPL/TTS开户"),
                new ProjectDef("Cloud IoT（物联网云服务）", "2025-11-17", "2026-01-05", "后端开发",
                        "Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC",
                        "基于 Spring Boot 构建 IoT 云端管理平台，面向机器人配件与智能设备生态，提供设备接入、管理、数据采集、语音控制、场景联动等服务，支持多设备全生命周期管理和云云对接。",
                        "设计“品类-产品-设备”三层数据模型，实现设备全生命周期管理及核心 CRUD、绑定解绑、状态查询、批量操作与状态快照；基于 gRPC / JSF 实现设备通信、鉴权认证、数据订阅与消息推送能力；设计物模型属性、事件、方法与 Schema 规范，实现数据标准化；参与语音控制链路接入，完成意图结果到设备匹配、控制下发与统一返回码处理；基于事件感知机制实现属性变化触发、规则路由与智能消息下行。",
                        "支持设备管理、控制、监控等核心能力，构建 100ms 级事件响应与智能消息推送能力。",
                        "Cloud IoT"),
                new ProjectDef("Cloud VLM（视觉语言模型服务）", "2025-12-01", "2026-03-09", "后端开发",
                        "Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / Elasticsearch / OSS",
                        "基于 Spring Boot 构建多模态视觉处理服务，支持图像识别、视觉问答等能力，落地药盒检测、中医舌诊、题目识别、通用视觉问答等场景，系统采用策略模式设计图像处理框架，支持多场景动态扩展与服务化接入。",
                        "设计策略模式的图像处理框架，实现多业务场景解耦与动态扩展；结合规则引擎与配置中心，实现模型与插件的动态路由与热更新；完成 gRPC 接口设计和 proto 协议设计，实现云端、设备端高效通信；参与高并发推理服务链路建设，覆盖请求解析、模型调用与结果返回；实现 JSON Schema 数据校验与多级重试机制，并从模型推理、网络传输与业务处理三个维度拆解系统时延；基于异步处理与流式响应优化系统吞吐能力。",
                        "支持多类视觉处理场景稳定运行，核心接口响应耗时降低 30%+，吞吐量提升 50%。",
                        "Cloud VLM"),
                new ProjectDef("Robot AIUI（智能对话系统）", "2026-01-03", "2026-03-09", "后端开发",
                        "Go / gRPC / Gin / MySQL / Redis / Milvus / Docker / Kubernetes",
                        "基于 Go 构建智能交互平台，集成语音识别、自然语言理解与大模型能力，提供统一对话服务接口。",
                        "设计云端接入层与对话工作流，基于 Gin / gRPC 开发 RESTful 与流式接口，支持音频流接入与状态管理，实现流程编排与多模块协同处理；设计 gRPC / proto 协议与统一通信规范，完成元数据提取、上下文透传与跨模块调用规范设计；开发插件化 Agent 管理系统，采用工厂模式支持动态扩展；构建基于 Milvus 的语义检索模块 RAG，支撑知识问答与多轮对话场景；参与多模型接入与统一调度，设计模型适配、路由、降级与熔断机制，并完成链路追踪与 Kubernetes 容器化部署。",
                        "支撑高并发对话请求与多模块协同处理，系统可用性达 99.9%；完成全双工、Agent 路由与多模型接入等能力，支持智能交互场景持续扩展。",
                        "Robot AIUI"));

        List<ProjectExperience> result = new ArrayList<>();
        int order = 0;
        for (ProjectDef def : defs) {
            ProjectExperience entity = new ProjectExperience();
            entity.setUserId(userId);
            entity.setProjectName(def.name);
            entity.setRole(def.role);
            entity.setStartDate(def.start);
            entity.setEndDate(def.end);
            entity.setTechStack(def.techStack);
            entity.setProjectIntro(def.intro);
            entity.setResponsibilities(def.responsibilities);
            entity.setResult(def.result);
            entity.setDescription(def.intro() + def.responsibilities() + def.result());
            entity.setShortName(def.shortName());
            // 所有项目全部保留（含工行 DevOps 项目）；模板差异由 template_experience_config 配置表控制
            entity.setIsDefault(order == 0);
            entity.setSortOrder(order++);
            result.add(projectRepository.save(entity));
        }
        return result;
    }

    // ==================== 奖项与技能 ====================

    private void initAwards(Long userId) {
        String[][] awards = {
                // name, type, year, level, description
                {"北京理工大学研究生学业一等奖学金", "奖项", "2025.11", "院校级", ""},
                {"北京理工大学研究生学业一等奖学金", "奖项", "2024.11", "院校级", ""},
                {"校学生会优秀部长", "奖项", "2019.11", "院校级", "河北工业大学校学生会主持人联合会部长"},
                {"一种电池及电池内部芯片保护装置", "专利成果", "2025-03-26", "国家级",
                        "实用新型专利《一种电池及电池内部芯片保护装置》；专利类型：实用新型专利；申请时间：2025年03月26日；"
                        + "作者排名：发明人之一；专利权人：北京理工大学；授权机构：国家知识产权局；"
                        + "代理机构：北京高沃律师事务所；依托项目：智能电芯开发（项目编号：2023B0909050004）。"},
        };
        int order = 0;
        for (String[] award : awards) {
            AwardCertificate entity = new AwardCertificate();
            entity.setUserId(userId);
            entity.setAwardName(award[0]);
            entity.setAwardType(award[1]);
            entity.setAwardYear(award[2]);
            entity.setAwardLevel(award[3]);
            entity.setDescription(award[4]);
            entity.setSortOrder(order++);
            awardRepository.save(entity);
        }
    }

    private void initSkills(Long userId) {
        int order = 0;
        for (String[] group : SKILL_GROUPS) {
            SkillProfile entity = new SkillProfile();
            entity.setUserId(userId);
            entity.setSkillKey(group[0]);
            entity.setSkillName(group[1]);
            entity.setContent(group[2]);
            entity.setCategory("专业技能");
            entity.setSortOrder(order++);
            skillRepository.save(entity);
        }
    }

    // ==================== 岗位模板 ====================

    private Map<String, ApplicationTemplate> initTemplates(Long userId) {
        String[][] templates = {
                // name, audienceType, category, description, isDefault
                {"大厂互联网版", "big_tech", "大厂", "适用：腾讯、字节、阿里、美团、京东、小红书等。优先内容：京东 AI 工程化、字节支付后端、系统性能优化、AI 协作经历。语言风格：技术复杂度、工程效率、性能优化、业务规模、快速迭代。", "true"},
                {"国央企版", "state_owned", "国央企", "适用：航天院所、央企、国企、研究所、事业单位。优先内容：工行交付平台、京东 IoT/智能系统、科研经历、专利、奖学金。语言风格：稳定可靠、标准规范、系统工程、协同落地、安全可控。", "false"},
                {"银行金融科技版", "bank", "银行", "适用：银行、券商、基金、信托、金融科技公司。优先内容：工行金融科技、字节国际支付、账户治理、资产安全、发布审计。语言风格：风险控制、系统稳定、数据一致性、流程规范、审计留痕。", "false"},
                {"通用后端开发版", "general_backend", "通用", "适用：普通后端开发岗位。优先内容：Java、Go、Spring Boot、Redis、MySQL、gRPC、MQ、项目落地。语言风格：技术栈清晰、职责明确、结果可量化。", "false"},
        };
        Map<String, ApplicationTemplate> result = new LinkedHashMap<>();
        Map<String, String> skillOrders = Map.of(
                "big_tech", SKILL_ORDER_BIG_TECH,
                "state_owned", SKILL_ORDER_STATE_OWNED,
                "bank", SKILL_ORDER_BANK,
                "general_backend", SKILL_ORDER_GENERAL_BACKEND);
        Map<String, String> skillKeywordMap = Map.of(
                "big_tech", "Java / Go / Spring Boot / Kitex / gRPC / MQ / Redis / 微服务 / 分布式 / 幂等控制 / RAG / Agent / 多模型路由 / 性能优化",
                "state_owned", "Java / Go / Spring Boot / MySQL / Redis / 异步消息 / 持续交付 / 自动投验 / 任务审计 / 智能系统 / Cloud VLM / Robot AIUI",
                "bank", "Java / Spring Boot / MySQL / Redis / MQ / TCC / 幂等控制 / 重试补偿 / 风险控制 / 审计留痕 / 系统稳定 / 数据一致性",
                "general_backend", "Java / Go / Spring Boot / MyBatis-Plus / Redis / MySQL / gRPC / MQ / Elasticsearch / DevOps");
        for (String[] t : templates) {
            ApplicationTemplate entity = new ApplicationTemplate();
            entity.setUserId(userId);
            entity.setName(t[0]);
            entity.setAudienceType(t[1]);
            entity.setCategory(t[2]);
            entity.setDescription(t[3]);
            entity.setIsDefault(Boolean.parseBoolean(t[4]));
            entity.setSelfEvaluation(SELF_EVALUATION);
            entity.setCareerPlan(CAREER_PLAN);
            entity.setAiCollaboration(AI_COLLABORATION);
            entity.setSkillKeywords(skillKeywordMap.get(t[1]));
            entity.setSkillOrder(skillOrders.get(t[1]));
            result.put(t[1], templateRepository.save(entity));
        }
        return result;
    }

    /**
     * 专业技能内容版本：sourceType=skill、sourceId=0，受众按模板归一化（general_backend→general）。
     * skill_full 按模板技能排序拼接七分组；skill_short 保存各模板简短版并派生字数档位；
     * skill_keywords 取模板技能关键词。
     */
    private void initSkillVariants(Long userId, Map<String, ApplicationTemplate> templates) {
        Map<String, String> shortByAudience = Map.of(
                "big_tech", SKILL_SHORT_BIG_TECH,
                "state_owned", SKILL_SHORT_STATE_OWNED,
                "bank", SKILL_SHORT_BANK,
                "general", SKILL_SHORT_GENERAL);
        for (ApplicationTemplate template : templates.values()) {
            String audience = "general_backend".equals(template.getAudienceType())
                    ? "general" : template.getAudienceType();
            List<String> order = parseSkillOrder(template.getSkillOrder());
            String full = composeSkillFull(order);
            saveVariant(userId, "skill", 0L, audience, "general", "skill_full", "full", full);
            saveVariant(userId, "skill", 0L, audience, "general", "skill_full", "within_500", truncate(full, 500));
            saveVariant(userId, "skill", 0L, audience, "general", "skill_full", "within_300", truncate(full, 300));

            String shortText = shortByAudience.getOrDefault(audience, truncate(full, 300));
            saveVariant(userId, "skill", 0L, audience, "general", "skill_short", "full", shortText);
            saveVariant(userId, "skill", 0L, audience, "general", "skill_short", "within_500", truncate(shortText, 500));
            saveVariant(userId, "skill", 0L, audience, "general", "skill_short", "within_300", truncate(shortText, 300));
            saveVariant(userId, "skill", 0L, audience, "general", "skill_short", "within_200", truncate(shortText, 200));
            saveVariant(userId, "skill", 0L, audience, "general", "skill_short", "within_100", truncate(shortText, 100));

            saveVariant(userId, "skill", 0L, audience, "general", "skill_keywords", "within_200",
                    truncate(nz(template.getSkillKeywords()), 200));
        }
    }

    /** 按模板技能排序拼接完整专业技能：1、分组标题：内容 */
    private String composeSkillFull(List<String> order) {
        Map<String, String[]> byKey = new LinkedHashMap<>();
        for (String[] group : SKILL_GROUPS) {
            byKey.put(group[0], group);
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (String key : order) {
            String[] group = byKey.get(key);
            if (group == null) {
                continue;
            }
            sb.append(index++).append("、").append(group[1]).append("：").append(group[2]).append("\n");
        }
        return sb.toString().trim();
    }

    private List<String> parseSkillOrder(String skillOrder) {
        List<String> result = new ArrayList<>();
        if (skillOrder != null && !skillOrder.isBlank()) {
            for (String key : skillOrder.split(",")) {
                if (!key.isBlank() && !result.contains(key.trim())) {
                    result.add(key.trim());
                }
            }
        }
        for (String[] group : SKILL_GROUPS) {
            if (!result.contains(group[0])) {
                result.add(group[0]);
            }
        }
        return result;
    }

    // ==================== 模板-经历关系配置 ====================

    /**
     * 初始化各模板下的经历展示与自动填充配置：
     * 大厂版默认只展示字节/京东（工行经历与 DevOps 项目保留数据但默认不展示、不自动填充，可手动选择）；
     * 国央企/银行/通用版三段实习全部参与，仅优先级与侧重点不同。
     */
    private void initTemplateConfigs(Long userId, Map<String, ApplicationTemplate> templates,
                                   List<InternshipExperience> internships, List<ProjectExperience> projects) {
        InternshipExperience icbc = internships.get(0);
        InternshipExperience bytedance = internships.get(1);
        InternshipExperience jd = internships.get(2);
        // 项目顺序：0 DevOps 1 TikTok 2 BNPL 3 IoT 4 VLM 5 AIUI
        Long devOps = projects.get(0).getId();
        Long tiktok = projects.get(1).getId();
        Long bnpl = projects.get(2).getId();
        Long iot = projects.get(3).getId();
        Long vlm = projects.get(4).getId();
        Long aiui = projects.get(5).getId();

        // ---- 大厂互联网版：字节 > 京东；工行实习与 DevOps 项目不展示、不自动填充，仅保留可手选 ----
        Long bigTech = templates.get("big_tech").getId();
        saveConfig(userId, bigTech, "internship", bytedance.getId(), true, true, 1,
                "支付后端,分布式,性能优化", 1);
        saveConfig(userId, bigTech, "internship", jd.getId(), true, true, 2,
                "AI工程化,智能云平台,多模态", 2);
        saveConfig(userId, bigTech, "internship", icbc.getId(), false, false, 99,
                "金融科技（数据保留，默认不展示，可手动选择）", 3);
        saveConfig(userId, bigTech, "project", tiktok, true, true, 1, "账户治理,资产安全,P2P转账", 1);
        saveConfig(userId, bigTech, "project", bnpl, true, true, 2, "多场景开户,流程编排", 2);
        saveConfig(userId, bigTech, "project", iot, true, true, 3, "物联网,设备管理", 3);
        saveConfig(userId, bigTech, "project", vlm, true, true, 4, "视觉语言模型,多模态", 4);
        saveConfig(userId, bigTech, "project", aiui, true, true, 5, "智能对话,RAG,Agent", 5);
        saveConfig(userId, bigTech, "project", devOps, false, false, 99,
                "持续交付（数据保留，默认不展示，可手动选择）", 6);

        // ---- 国央企版：三段实习全部保留，工行 > 京东 > 字节 ----
        Long stateOwned = templates.get("state_owned").getId();
        saveConfig(userId, stateOwned, "internship", icbc.getId(), true, true, 1,
                "流程标准化,自动化,可审计,可追溯,跨系统协同", 1);
        saveConfig(userId, stateOwned, "internship", jd.getId(), true, true, 2,
                "智能系统,平台建设,工程落地,系统稳定性", 2);
        saveConfig(userId, stateOwned, "internship", bytedance.getId(), true, true, 3,
                "复杂业务治理,规则配置,异常处理一致性,系统可靠性", 3);
        saveConfig(userId, stateOwned, "project", devOps, true, true, 1,
                "流程标准化,可追溯,自动投验,跨集群迁移,系统协同", 1);
        saveConfig(userId, stateOwned, "project", tiktok, true, true, 2, "账户治理,一致性建设", 2);
        saveConfig(userId, stateOwned, "project", bnpl, true, true, 3, "流程编排,跨请求恢复", 3);
        saveConfig(userId, stateOwned, "project", iot, true, true, 4, "设备管理,数据标准化", 4);
        saveConfig(userId, stateOwned, "project", vlm, true, true, 5, "视觉处理,接口规范", 5);
        saveConfig(userId, stateOwned, "project", aiui, true, true, 6, "智能交互,多模块协同", 6);

        // ---- 银行金融科技版：三段实习全部保留，工行 > 字节 > 京东 ----
        Long bank = templates.get("bank").getId();
        saveConfig(userId, bank, "internship", icbc.getId(), true, true, 1,
                "金融科技,持续交付,生产变更安全,自动投验,审计留痕", 1);
        saveConfig(userId, bank, "internship", bytedance.getId(), true, true, 2,
                "支付账户治理,资产安全,风险拦截,账户状态校验", 2);
        saveConfig(userId, bank, "internship", jd.getId(), true, true, 3,
                "后端工程,智能系统,接口稳定性", 3);
        saveConfig(userId, bank, "project", devOps, true, true, 1,
                "发布可靠性,生产变更安全,审计留痕", 1);
        saveConfig(userId, bank, "project", tiktok, true, true, 2, "账户治理,资产安全,风险控制", 2);
        saveConfig(userId, bank, "project", bnpl, true, true, 3, "开户注册,流程一致性", 3);
        saveConfig(userId, bank, "project", iot, true, true, 4, "设备接入,数据订阅", 4);
        saveConfig(userId, bank, "project", vlm, true, true, 5, "视觉处理,链路稳定", 5);
        saveConfig(userId, bank, "project", aiui, true, true, 6, "智能系统,高并发对话", 6);

        // ---- 通用后端开发版：三段实习全部保留，字节 > 京东 > 工行 ----
        Long generalBackend = templates.get("general_backend").getId();
        String generalTags = "Java,Go,Spring Boot,Redis,MySQL,gRPC,MQ,分布式";
        saveConfig(userId, generalBackend, "internship", bytedance.getId(), true, true, 1, generalTags, 1);
        saveConfig(userId, generalBackend, "internship", jd.getId(), true, true, 2, generalTags, 2);
        saveConfig(userId, generalBackend, "internship", icbc.getId(), true, true, 3, generalTags, 3);
        saveConfig(userId, generalBackend, "project", devOps, true, true, 1, generalTags, 1);
        saveConfig(userId, generalBackend, "project", tiktok, true, true, 2, generalTags, 2);
        saveConfig(userId, generalBackend, "project", bnpl, true, true, 3, generalTags, 3);
        saveConfig(userId, generalBackend, "project", iot, true, true, 4, generalTags, 4);
        saveConfig(userId, generalBackend, "project", vlm, true, true, 5, generalTags, 5);
        saveConfig(userId, generalBackend, "project", aiui, true, true, 6, generalTags, 6);
    }

    private void saveConfig(Long userId, Long templateId, String sourceType, Long sourceId,
                            boolean included, boolean autoFill, int priority, String tags, int order) {
        TemplateExperienceConfig config = new TemplateExperienceConfig();
        config.setUserId(userId);
        config.setTemplateId(templateId);
        config.setSourceType(sourceType);
        config.setSourceId(sourceId);
        config.setIncludedInResume(included);
        config.setAutoFillEnabled(autoFill);
        config.setAutoFillPriority(priority);
        config.setManualSelectable(true);
        config.setEmphasisTags(tags);
        config.setDisplayOrder(order);
        templateConfigRepository.save(config);
    }

    // ==================== 开放题素材 ====================

    private Map<String, AnswerMaterial> initMaterials(Long userId) {
        Map<String, AnswerMaterial> map = new LinkedHashMap<>();
        String[][] materials = {
                {"自我评价", "SELF_EVALUATION", SELF_EVALUATION},
                {"AI 协作经历", "AI_COLLABORATION", AI_COLLABORATION},
                {"兴趣特长", "HOBBY", HOBBY},
                {"职业规划", "CAREER_PLAN", CAREER_PLAN},
                {"为什么选择本公司", "WHY_COMPANY", WHY_COMPANY},
                {"为什么选择该岗位", "WHY_POSITION", WHY_POSITION},
        };
        int order = 0;
        for (String[] m : materials) {
            AnswerMaterial entity = new AnswerMaterial();
            entity.setUserId(userId);
            entity.setTitle(m[0]);
            entity.setMaterialType(m[1]);
            entity.setContent(m[2]);
            entity.setEnabled(true);
            entity.setSortOrder(order++);
            AnswerMaterial saved = materialRepository.save(entity);
            map.put(m[1], saved);
        }
        return map;
    }

    // ==================== 字段匹配规则 ====================

    private void initCustomFields(Long userId,
                                  List<InternshipExperience> internships,
                                  List<ProjectExperience> projects,
                                  Map<String, AnswerMaterial> materials) {
        List<UserCustomField> fields = new ArrayList<>();
        int order = 0;

        // ---- 基础信息 / 个人信息 ----
        fields.add(field(userId, "name", "姓名", "input", "基础信息", "胡宇欣",
                List.of("姓名", "真实姓名", "中文姓名", "申请人姓名", "候选人姓名", "本人姓名",
                        "full name", "chinese name", "name"), null));
        fields.add(field(userId, "gender", "性别", "input", "基础信息", "女",
                List.of("性别", "gender", "sex"), null));
        fields.add(field(userId, "age", "年龄", "input", "基础信息", "25岁",
                List.of("年龄", "age"), null));
        fields.add(field(userId, "birth_date", "出生日期", "input", "基础信息", "2000-10-28",
                List.of("出生日期", "出生年月", "出生年月日", "生日", "birth date", "birthday",
                        "date of birth", "dob"), null));
        fields.add(field(userId, "phone", "手机号", "input", "基础信息", "18813108802",
                List.of("手机", "手机号", "手机号码", "联系电话", "联系手机", "联系方式", "移动电话",
                        "电话", "本人手机", "中国大陆手机号", "phone", "mobile", "mobile phone",
                        "telephone", "tel"), null));
        fields.add(field(userId, "phone_type", "手机号类型", "input", "基础信息", "中国大陆",
                List.of("手机号类型", "手机类型", "电话类型", "phone type"), null));
        fields.add(field(userId, "email", "邮箱", "input", "基础信息", "m15128278966@163.com",
                List.of("邮箱", "电子邮箱", "常用邮箱", "联系邮箱", "邮件地址", "e-mail", "email",
                        "email address", "mail"), null));
        fields.add(field(userId, "id_type", "证件类型", "input", "基础信息", "身份证",
                List.of("证件类型", "证件类别", "身份证件类型", "证件种类", "id type", "document type"), null));
        fields.add(field(userId, "id_card", "证件号码", "input", "基础信息", "130681200010281261",
                List.of("证件号码", "证件号", "身份证号", "身份证号码", "身份证件号码", "身份证件号",
                        "居民身份证号码", "身份证", "id number", "document number",
                        "certificate number", "id card"), null));
        fields.add(field(userId, "nationality", "国籍", "input", "基础信息", "中国",
                List.of("国籍", "国籍/地区", "国家", "国家或地区", "nationality", "country", "region"), null));
        fields.add(field(userId, "overseas_identity", "是否具有境外身份", "input", "基础信息", "否",
                List.of("是否具有境外身份", "是否有境外身份", "境外身份", "是否港澳台侨",
                        "是否拥有海外身份", "overseas identity"), null));
        fields.add(field(userId, "native_place", "籍贯", "input", "基础信息", "河北省/保定市/涿州市",
                List.of("籍贯", "祖籍", "native place", "hometown"), null));
        fields.add(field(userId, "origin_place", "生源地", "input", "基础信息", "河北省 / 保定市 / 涿州市",
                List.of("生源地", "生源所在地", "高考生源地", "生源省市", "source of student"), null));
        fields.add(field(userId, "ethnicity", "民族", "input", "基础信息", "汉族",
                List.of("民族", "ethnicity", "nation"), null));
        fields.add(field(userId, "political_status", "政治面貌", "input", "基础信息", "共青团员",
                List.of("政治面貌", "政治身份", "政治状态", "political status"), null));
        fields.add(field(userId, "marital_status", "婚姻状况", "input", "基础信息", "未婚",
                List.of("婚姻状况", "婚姻状态", "婚否", "marital status"), null));
        fields.add(field(userId, "height", "身高", "input", "基础信息", "170",
                List.of("身高", "身高cm", "height"), null));
        fields.add(field(userId, "household_address", "当前户籍所在地", "input", "基础信息", "北京市 / 北京市 / 海淀区",
                List.of("当前户籍所在地", "户籍所在地", "户口所在地", "户籍地址", "户口地址", "户籍省市区",
                        "registered residence", "household registration"), null));
        fields.add(field(userId, "household_type", "当前户籍类型", "input", "基础信息", "学校集体户",
                List.of("当前户籍类型", "户籍类型", "户口类型", "户口性质", "household type"), null));
        fields.add(field(userId, "home_address", "家庭地址", "textarea", "基础信息",
                "河北省保定市涿州市学校路育才家园2号楼一单元4层",
                List.of("家庭地址", "家庭住址", "通讯地址", "现居住地址", "联系地址",
                        "home address", "address"), null));
        fields.add(field(userId, "qq", "QQ", "input", "基础信息", "2318402884",
                List.of("QQ", "qq号"), null));
        fields.add(field(userId, "wechat", "微信", "input", "基础信息", "15128278966",
                List.of("微信", "微信号", "wechat"), null));
        fields.add(field(userId, "current_location", "当前所在地", "input", "基础信息", "中国大陆 / 北京 / 北京市",
                List.of("当前所在地", "现居住地", "所在地", "current location"), null));

        // ---- 家庭情况 / 紧急联系人 ----
        fields.add(field(userId, "emergency_contact", "紧急联系人", "input", "家庭情况", "张巍",
                List.of("紧急联系人", "紧急联系人姓名", "紧急联络人", "紧急联络人姓名", "应急联系人",
                        "emergency contact", "emergency contact name"), null));
        fields.add(field(userId, "emergency_phone", "紧急联系电话", "input", "家庭情况", "13932241704",
                List.of("紧急联系电话", "紧急联系人电话", "紧急联系人手机", "紧急联络人电话",
                        "应急联系人电话", "emergency phone", "emergency contact phone"), null));
        fields.add(field(userId, "relatives_in_company", "是否有近亲属在集团任职", "input", "家庭情况", "否",
                List.of("是否有近亲属在集团任职", "是否有亲属在本单位任职", "是否有亲属在集团工作",
                        "是否存在亲属回避", "集团内亲属", "亲属关系", "relatives in company"), null));
        fields.add(field(userId, "father_name", "父亲姓名", "input", "家庭情况", "胡林喜",
                List.of("父亲", "父亲姓名", "家庭成员姓名", "father", "father name"), null));
        fields.add(field(userId, "father_phone", "父亲手机号", "input", "家庭情况", "13623225862",
                List.of("父亲手机号", "父亲电话", "家庭成员电话", "father phone"), null));
        fields.add(field(userId, "family_members", "家庭成员", "textarea", "家庭情况",
                "1. 父亲：胡林喜，电话 13623225862；\n2. 紧急联络人：张巍，电话 13932241704",
                List.of("家庭成员", "家庭情况", "家属信息"), null));
        fields.add(field(userId, "reference_phone", "证明人电话", "input", "家庭情况", "",
                List.of("证明人电话", "证明人联系方式", "推荐人电话"), null));
        fields.add(field(userId, "bank_card", "银行卡号", "input", "基础信息", "",
                List.of("银行卡", "银行卡号", "银行账号"), null));

        // ---- 应聘信息 / 求职意向 ----
        fields.add(field(userId, "applicant_type", "应聘类别", "input", "应聘信息", "境内院校中国籍2027届应届毕业生",
                List.of("应聘类别", "应聘类型", "报名类别", "招聘类别", "招聘对象", "人员类别", "考生类别",
                        "生源类别", "毕业生类别", "应届生类别", "学生类别", "申请类别", "应届生", "应届毕业生",
                        "candidate type", "applicant type"), null));
        fields.add(field(userId, "expected_work_city", "意向工作地点", "input", "应聘信息", "北京市",
                List.of("意向工作地点", "期望工作地点", "期望工作城市", "意向城市", "期望城市", "工作地点",
                        "工作城市", "求职城市", "首选城市", "第一意向城市", "第二意向城市", "可接受城市",
                        "目标城市", "目标工作地", "期望办公地点", "工作所在地", "希望工作地点",
                        "preferred city", "expected city", "preferred location", "work location"), null));
        fields.add(field(userId, "target_position", "目标岗位", "input", "应聘信息", "AI应用工程师",
                List.of("目标岗位", "意向岗位", "应聘岗位"), null));
        fields.add(field(userId, "accept_other_city", "是否接受其他城市", "input", "应聘信息", "是",
                List.of("接受其他城市", "是否接受调剂", "接受工作地点调剂"), null));

        // ---- 教育（最高学历维度） ----
        fields.add(field(userId, "highest_education", "最高学历", "input", "教育经历", "硕士研究生",
                List.of("最高学历", "当前学历", "最高教育程度", "education level", "degree level"), null));
        fields.add(field(userId, "highest_degree", "最高学位", "input", "教育经历", "硕士",
                List.of("最高学位", "degree"), null));
        fields.add(field(userId, "study_mode", "学习形式", "input", "教育经历", "全国普通高等院校全日制",
                List.of("学习形式", "学历类型", "培养方式", "学习方式", "是否全日制", "全日制",
                        "education type", "study mode"), null));
        fields.add(field(userId, "education_type", "学历类型", "input", "教育经历", "普通全日制",
                List.of("学历类型", "学历性质"), null));
        fields.add(field(userId, "graduation_class", "毕业届别", "input", "教育经历", "2027届",
                List.of("毕业届别", "届别", "毕业年份", "graduation year"), null));

        // ---- 教育经历 ----
        fields.add(field(userId, "graduate_school", "毕业院校", "input", "教育经历", "北京理工大学",
                List.of("毕业院校", "最高学历学校", "university", "college"), null));
        fields.add(field(userId, "graduate_major", "专业", "input", "教育经历", "新一代电子信息技术",
                List.of("主修专业", "所学专业", "第一专业", "major"), null));
        fields.add(field(userId, "graduate_degree", "学历", "input", "教育经历", "硕士研究生",
                List.of("学历", "学位", "degree", "education"), null));
        fields.add(field(userId, "graduation_date", "毕业时间", "input", "教育经历", "2027-07-01",
                List.of("毕业时间", "毕业日期", "预计毕业时间", "预计毕业日期", "毕业年月", "graduation date",
                        "graduate date"), null));
        fields.add(field(userId, "gpa", "GPA", "input", "教育经历", "3.5/4",
                List.of("GPA", "绩点"), null));
        fields.add(field(userId, "grade_rank", "成绩排名", "input", "教育经历", "前30%",
                List.of("成绩排名", "年级排名", "专业排名", "综合排名", "ranking"), null));
        fields.add(field(userId, "thesis", "毕业论文", "input", "教育经历", "面向无线电池管理系统的监测数据异常检测与纠错方法研究",
                List.of("论文", "毕业论文", "研究课题"), null));
        fields.add(field(userId, "research_direction", "研究方向", "input", "教育经历", "Java后端、分布式系统、云原生",
                List.of("研究方向", "研究内容"), null));

        // ---- 实习经历（长文本，带来源引用，按模板+字数自动选版本） ----
        InternshipExperience icbc = internships.get(0);
        InternshipExperience bytedance = internships.get(1);
        InternshipExperience jd = internships.get(2);
        fields.add(field(userId, "icbc_internship", "工行实习经历", "textarea", "实习经历",
                variantPreview(icbc.getDescription(), icbc.getHighlights()),
                List.of("工行实习", "工商银行", "移动金融建设部", "金融科技", "一体化交付平台", "DevOps"),
                "internship:" + icbc.getId()));
        fields.add(field(userId, "byte_internship", "字节实习经历", "textarea", "实习经历",
                variantPreview(bytedance.getDescription(), bytedance.getHighlights()),
                List.of("字节实习", "字节跳动", "TikTok Pay", "PIPO Wallet", "国际支付", "P2P Transfer"),
                "internship:" + bytedance.getId()));
        fields.add(field(userId, "jd_internship", "京东实习经历", "textarea", "实习经历",
                variantPreview(jd.getDescription(), jd.getHighlights()),
                List.of("京东实习", "京东科技", "机器人智能云平台", "Cloud IoT", "Cloud VLM", "Robot AIUI"),
                "internship:" + jd.getId()));

        // ---- 项目经历 ----
        String[] projectKeys = {"devops_project", "p2p_project", "bnpl_tts_project",
                "cloud_iot_project", "cloud_vlm_project", "robot_aiui_project"};
        String[] projectNames = {"DevOps 一体化交付平台", "P2P Transfer 账户治理", "BNPL/TTS 开户注册",
                "Cloud IoT 物联网", "Cloud VLM 视觉语言模型", "Robot AIUI 智能对话"};
        List<List<String>> projectKeywords = List.of(
                List.of("一体化交付平台", "DevOps", "持续交付", "自动投验", "精准出版", "跨集群迁移"),
                List.of("P2P Transfer", "用户间转账", "账户治理", "账户状态校验", "在途交易"),
                List.of("BNPL", "TTS", "开户注册", "账户治理框架", "KYC", "PIN"),
                List.of("Cloud IoT", "物联网云服务", "设备接入", "设备管理"),
                List.of("Cloud VLM", "视觉语言模型", "图像识别", "视觉问答", "多模态"),
                List.of("Robot AIUI", "智能对话", "RAG", "Agent", "多模型", "语义检索"));
        for (int i = 0; i < projects.size() && i < projectKeys.length; i++) {
            ProjectExperience project = projects.get(i);
            fields.add(field(userId, projectKeys[i], projectNames[i], "textarea", "项目经历",
                    variantPreview(project.getProjectIntro(), project.getResult()),
                    projectKeywords.get(i),
                    "project:" + project.getId()));
        }

        // ---- 语言能力（语言块内字段由块匹配从这些候选取值，绝不填姓名） ----
        fields.add(field(userId, "language_type", "语言类型", "input", "语言能力", "英语",
                List.of("语言类型", "语种", "外语语种", "语言名称", "语言", "language"), null));
        fields.add(field(userId, "language_proficiency", "掌握程度", "input", "语言能力", "良好",
                List.of("掌握程度", "熟练程度", "语言水平", "英语水平", "水平", "proficiency", "level"), null));
        fields.add(field(userId, "language_listening_speaking", "听说", "input", "语言能力", "良好",
                List.of("听说", "听力口语", "听力与口语", "listening and speaking"), null));
        fields.add(field(userId, "language_reading_writing", "读写", "input", "语言能力", "良好",
                List.of("读写", "阅读写作", "阅读与写作", "reading and writing"), null));
        fields.add(field(userId, "language_certificate", "语言证书", "input", "语言能力", "CET-6丨435分",
                List.of("英语证书", "外语证书", "证书", "cet-6", "cet", "六级", "certificate"), null));
        fields.add(field(userId, "language_score", "英语成绩", "input", "语言能力", "435分",
                List.of("英语成绩", "考试成绩", "六级成绩", "分数"), null));
        fields.add(field(userId, "language_skill_display", "技能水平展示", "input", "语言能力", "CET-6丨435分",
                List.of("技能水平展示", "语言技能展示"), null));

        // ---- 专利成果（专利块字段命中子字段关键词时填对应值，否则填完整描述） ----
        fields.add(field(userId, "patent_full", "专利成果", "textarea", "专利成果",
                "实用新型专利《一种电池及电池内部芯片保护装置》；专利类型：实用新型专利；申请时间：2025年03月26日；"
                        + "作者排名：发明人之一；专利权人：北京理工大学；授权机构：国家知识产权局；"
                        + "代理机构：北京高沃律师事务所；依托项目：智能电芯开发（项目编号：2023B0909050004）。",
                List.of("专利成果", "科研成果", "论文专利", "知识产权", "专利", "专利名称", "成果名称",
                        "专利题目", "patent", "research output", "patent name"), null));
        fields.add(field(userId, "patent_type", "专利类型", "input", "专利成果", "实用新型专利",
                List.of("专利类型", "成果类型", "patent type"), null));
        fields.add(field(userId, "patent_apply_date", "专利申请时间", "input", "专利成果", "2025年03月26日",
                List.of("申请时间", "申请日期", "patent application date"), null));
        fields.add(field(userId, "patent_author_rank", "作者排名", "input", "专利成果", "发明人之一",
                List.of("作者排名", "发明人排名", "patent author ranking", "author ranking"), null));
        fields.add(field(userId, "patent_owner", "专利权人", "input", "专利成果", "北京理工大学",
                List.of("专利权人", "权利人", "patent owner"), null));
        fields.add(field(userId, "patent_grant_org", "授权机构", "input", "专利成果", "国家知识产权局",
                List.of("授权机构", "授权单位", "颁发机构"), null));
        fields.add(field(userId, "patent_agency", "代理机构", "input", "专利成果", "北京高沃律师事务所",
                List.of("代理机构", "patent agency"), null));
        fields.add(field(userId, "patent_project", "依托项目", "input", "专利成果",
                "智能电芯开发（项目编号：2023B0909050004）",
                List.of("依托项目", "项目来源", "supporting project"), null));

        // ---- 个人简介 / 科研经历 / 校园经历 ----
        fields.add(field(userId, "personal_summary", "个人简介", "textarea", "开放题",
                "具备 Java 后端与 Go 微服务研发经验，拥有工商银行北京市分行金融科技岗、字节跳动国际支付后端、"
                        + "京东科技后端研发经历。熟悉 Spring Boot、Kitex、MySQL、Redis、gRPC、TCC、MQ，"
                        + "具备持续交付平台、支付账户治理、AI 应用工程化及复杂业务状态建模经验。",
                List.of("个人简介", "个人介绍", "个人概述", "个人描述", "自我介绍", "简介",
                        "personal summary", "self introduction"), null));
        fields.add(field(userId, "research_experience", "科研经历", "textarea", "科研经历",
                "硕士期间参与导师课题组“智能电芯开发”项目，聚焦电池及内部芯片保护技术研发，参与系统方案设计与实现，"
                        + "作为发明人之一申请实用新型专利《一种电池及电池内部芯片保护装置》（2025.03），"
                        + "具备一定科研能力与工程实践经验。",
                List.of("科研经历", "科研情况", "科研能力", "研究经历", "research experience"), null));
        fields.add(field(userId, "campus_experience", "校园经历", "textarea", "校园经历",
                "1、担任河北工业大学校学生会主持人联合会部长：主持十佳大学生、红旗团支部评比、十佳社团评比等校级重点活动，"
                        + "统筹活动现场流程把控与主持工作；负责跨部门对接、沟通活动流程、协商执行方案；主导部门纳新工作，"
                        + "策划面试考核方案，完成候选人筛选及新人培训工作。\n"
                        + "2、担任河北工业大学电子信息工程学院院学生会学研部部员：策划并组织学科竞赛专题讲座，统筹讲师对接、"
                        + "宣传推广、主持等工作，助力同学备赛；负责学院期中考试统筹工作，参与考场安排、考务人员调配、试卷整理；"
                        + "策划并举办图书沙龙活动，组织同学交流，营造学术文化氛围。\n"
                        + "3、志愿服务：疫情期间参与社区志愿服务，协助信息登记及核酸检测工作；参与社会实践基地植树活动，"
                        + "具备良好的责任意识与团队协作精神。\n"
                        + "4、文体活动：参加北京理工大学校园马拉松长跑比赛，位次前十；参与校运会开幕式方阵，完成举旗礼仪任务；"
                        + "日常热爱羽毛球、乒乓球等运动，经常参与学院组织的球类交流活动；拉丁舞三级（银牌），具备较强的自律意识、"
                        + "组织纪律性和抗压能力。",
                List.of("校园经历", "校园活动", "在校经历", "社团经历", "学生工作经历", "课外经历",
                        "campus experience"), null));

        // ---- 开放题 ----
        fields.add(field(userId, "self_evaluation", "自我评价", "textarea", "开放题",
                SELF_EVALUATION, List.of("自我评价", "个人评价", "综合评价", "个人优势", "个人总结"),
                ref(materials.get("SELF_EVALUATION"))));
        fields.add(field(userId, "ai_collaboration", "AI 协作经历", "textarea", "开放题",
                AI_COLLABORATION, List.of("AI协作", "AI工具", "人工智能工具", "AI辅助开发", "请描述一个或多个你与AI协作完成的项目或任务"),
                ref(materials.get("AI_COLLABORATION"))));
        fields.add(field(userId, "career_plan", "职业规划", "textarea", "开放题",
                CAREER_PLAN, List.of("职业规划", "未来规划", "发展方向"),
                ref(materials.get("CAREER_PLAN"))));
        fields.add(field(userId, "hobbies", "兴趣特长", "textarea", "开放题",
                "羽毛球、乒乓球、校园马拉松、主持、拉丁舞。",
                List.of("兴趣特长", "爱好特长", "个人特长", "兴趣爱好"),
                ref(materials.get("HOBBY"))));
        fields.add(field(userId, "additional_info", "补充信息", "textarea", "开放题",
                "", List.of("补充信息", "其他信息", "其他相关信息"), null));
        fields.add(field(userId, "why_company", "为什么选择本公司", "textarea", "开放题",
                WHY_COMPANY, List.of("为什么选择本公司", "选择我们的原因", "为什么投递本公司"),
                ref(materials.get("WHY_COMPANY"))));
        fields.add(field(userId, "why_position", "为什么选择该岗位", "textarea", "开放题",
                WHY_POSITION, List.of("为什么选择该岗位", "岗位理解", "应聘原因"),
                ref(materials.get("WHY_POSITION"))));

        // ---- 专业技能（供插件自动填写；字段名命中技能关键词时优先走技能版本匹配） ----
        for (String[] group : SKILL_GROUPS) {
            fields.add(field(userId, group[0], group[1], "textarea", "专业技能", group[2],
                    SKILL_MATCH_KEYWORDS, null));
        }
        fields.add(field(userId, "skill_full", "专业技能（完整版）", "textarea", "专业技能",
                composeSkillFull(parseSkillOrder(SKILL_ORDER_BIG_TECH)), SKILL_MATCH_KEYWORDS, null));
        fields.add(field(userId, "skill_short", "专业技能（简短版）", "textarea", "专业技能",
                SKILL_SHORT_BIG_TECH, SKILL_MATCH_KEYWORDS, null));
        fields.add(field(userId, "skill_keywords", "技能关键词", "input", "专业技能",
                "Java / Go / Spring Boot / Redis / MySQL / gRPC / MQ / RAG / Agent", SKILL_MATCH_KEYWORDS, null));

        // 幂等：已存在的 fieldKey 不重复创建（旧库补建场景），全量重建时表已清空不受影响
        java.util.Set<String> existingKeys = customFieldRepository
                .findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(userId).stream()
                .map(UserCustomField::getFieldKey)
                .collect(java.util.stream.Collectors.toSet());
        for (UserCustomField f : fields) {
            if (existingKeys.contains(f.getFieldKey())) {
                continue;
            }
            f.setSortOrder(order++);
            customFieldRepository.save(f);
        }
    }

    private UserCustomField field(Long userId, String key, String name, String type, String category,
                                  String value, List<String> keywords, String sourceRef) {
        UserCustomField entity = new UserCustomField();
        entity.setUserId(userId);
        entity.setFieldKey(key);
        entity.setFieldName(name);
        entity.setFieldType(type);
        entity.setFieldCategory(category);
        entity.setFieldValue(value);
        entity.setMatchKeywords(toJson(keywords));
        entity.setSourceRef(sourceRef);
        entity.setSensitive(false);
        entity.setEnabled(true);
        return entity;
    }

    private String ref(AnswerMaterial material) {
        return material == null ? null : "material:" + material.getId();
    }

    private String variantPreview(String... parts) {
        return truncate(String.join("", parts), 500);
    }

    // ==================== 内容版本生成 ====================

    private static final List<String> LENGTHS = List.of("within_200", "within_300", "within_500", "within_1000");

    private void initVariants(Long userId,
                              List<InternshipExperience> internships,
                              List<ProjectExperience> projects,
                              Map<String, AnswerMaterial> materials) {
        // 实习经历：按 场景风格 × 岗位方向 × 字段类型 × 字数 生成内容版本（大厂版不含工行）
        for (InternshipExperience internship : internships) {
            InternshipTpl tpl = INTERN_TPL.get(nz(internship.getCompany()));
            if (tpl == null) {
                log.warn("实习「{}」无预置内容模板，跳过版本生成", internship.getCompany());
                continue;
            }
            saveInternVariants(userId, internship, tpl);
        }
        // 项目经历：每个受众生成 描述/职责/成果/技术栈/合并型 字段版本；
        // 命中预置文案模板（PROJECT_TPL）时使用模板全文，否则回退实体字段合成（全部受众全量生成，不做排除）
        for (ProjectExperience project : projects) {
            ProjectTpl pt = PROJECT_TPL.get(nz(project.getProjectName()));
            for (String audience : AUDIENCES_ALL) {
                saveVariant(userId, "project", project.getId(), audience, "general", "project_overview",
                        "within_300", truncate(firstNonBlank(pt == null ? null : pt.ov300(),
                                nz(project.getProjectIntro())), 300));
                saveVariant(userId, "project", project.getId(), audience, "general", "project_responsibility",
                        "within_500", truncate(firstNonBlank(pt == null ? null : pt.resp500(),
                                nz(project.getResponsibilities())), 500));
                saveVariant(userId, "project", project.getId(), audience, "general", "project_result",
                        "within_200", truncate(firstNonBlank(pt == null ? null : pt.res200(),
                                nz(project.getResult())), 200));
                saveVariant(userId, "project", project.getId(), audience, "general", "project_tech_stack",
                        "within_200", truncate(firstNonBlank(pt == null ? null : pt.techStack(),
                                nz(project.getTechStack())), 200));
                for (String length : LENGTHS) {
                    String combined;
                    if (pt != null) {
                        combined = switch (length) {
                            case "within_200" -> pt.comb200();
                            case "within_300" -> pt.comb300();
                            case "within_500" -> pt.comb500();
                            default -> pt.comb1000();
                        };
                    } else {
                        combined = nz(project.getProjectIntro()) + "\n主要职责：\n" + nz(project.getResponsibilities())
                                + "\n项目成果：" + nz(project.getResult());
                    }
                    saveVariant(userId, "project", project.getId(), audience, "general", "project_combined",
                            length, truncate(combined, limitOf(length)));
                }
            }
        }
        // 开放题素材：通用方向合并型字段，四个长度档位（超长自动按句截断）
        for (AnswerMaterial material : materials.values()) {
            for (String length : LENGTHS) {
                saveVariant(userId, "material", material.getId(), "general", "general", "combined",
                        length, truncate(nz(material.getContent()), limitOf(length)));
            }
        }
    }

    private void saveVariant(Long userId, String sourceType, Long sourceId, String audience,
                             String direction, String fieldType, String length, String content) {
        ContentVariant variant = new ContentVariant();
        variant.setUserId(userId);
        variant.setSourceType(sourceType);
        variant.setSourceId(sourceId);
        variant.setAudienceType(audience);
        variant.setJobDirection(direction);
        variant.setFieldType(fieldType);
        variant.setLengthType(length);
        variant.setContent(content);
        variant.setEnabled(true);
        contentVariantRepository.save(variant);
    }

    /** 为一段实习生成各受众 × 字段类型 × 字数的内容版本 */
    private void saveInternVariants(Long userId, InternshipExperience internship, InternshipTpl tpl) {
        Long id = internship.getId();
        // 200 字档：合并/职责/成果/技术栈 四类字段（模板提供受众级文案时按受众取，否则共用）
        for (String audience : tpl.audiences) {
            InternshipTpl.Aud aud = tpl.aud(audience);
            saveVariant(userId, "internship", id, audience, "general", "internship_combined", "within_200",
                    firstNonBlank(aud == null ? null : aud.comb200(), tpl.comb200));
            saveVariant(userId, "internship", id, audience, "general", "internship_responsibility", "within_200",
                    firstNonBlank(aud == null ? null : aud.resp200(), tpl.resp200));
            saveVariant(userId, "internship", id, audience, "general", "internship_result", "within_200",
                    firstNonBlank(aud == null ? null : aud.res200(), tpl.res200));
            saveVariant(userId, "internship", id, audience, "general", "internship_tech_stack", "within_200",
                    truncate(firstNonBlank(aud == null ? null : aud.techStack(), nz(internship.getTechStack())), 200));
        }
        // 300/500/1000 字档：按受众 × 岗位方向（backend / ai）生成；
        // 受众级方向文案优先，未提供时回退共用方向文案/受众维度/截断兜底（绝不超限）
        for (String audience : tpl.audiences) {
            for (String direction : List.of("backend", "ai")) {
                saveVariant(userId, "internship", id, audience, direction, "internship_overview", "within_300",
                        truncate(firstNonBlank(tpl.ov300For(audience, direction), tpl.comb200), 300));
                String comb500 = firstNonBlank(tpl.comb500For(audience, direction), tpl.comb200);
                saveVariant(userId, "internship", id, audience, direction, "internship_combined", "within_500",
                        truncate(comb500, 500));
                saveVariant(userId, "internship", id, audience, direction, "internship_combined", "within_1000",
                        truncate(firstNonBlank(tpl.comb1000For(audience, direction), comb500), 1000));
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (hasText(v)) {
                return v;
            }
        }
        return "";
    }

    private int limitOf(String lengthType) {
        return switch (lengthType) {
            case "within_200" -> 200;
            case "within_300" -> 300;
            case "within_500" -> 500;
            default -> 1000;
        };
    }

    /** 按句边界截断到指定字数以内 */
    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        String cut = text.substring(0, limit);
        int idx = Math.max(cut.lastIndexOf('。'), Math.max(cut.lastIndexOf('；'), cut.lastIndexOf(';')));
        if (idx > limit / 2) {
            return cut.substring(0, idx + 1);
        }
        return cut;
    }

    private String toJson(List<String> keywords) {
        try {
            return objectMapper.writeValueAsString(keywords);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // ==================== 实习内容模板（场景风格 × 岗位方向 × 字数） ====================
    // 三段实习均生成全部受众的内容版本（含工行 big_tech 版本）；版本保留供手动选择，
    // 是否默认展示/自动填充由 template_experience_config 配置表决定。

    private static final List<String> AUDIENCES_ALL = List.of("big_tech", "state_owned", "bank", "general");

    /**
     * 实习内容模板。两类供给方式：
     * 1）共用字段（comb200/resp200/res200 + ov300Backend 等方向文案）：四受众共用（工行）；
     * 2）受众级文案（audienceTpls）：每个受众一整套 10 类文案（字节/京东），优先于共用字段。
     */
    private record InternshipTpl(List<String> audiences, String comb200, String resp200, String res200,
                                 String ov300, String ov300Bank, String ov300State, String ov300BigTech,
                                 String comb500Bank, String comb500BigTech, String comb500State,
                                 String comb1000Bank, String comb1000BigTech,
                                 String ov300Backend, String ov300Ai, String ov300General,
                                 String comb500Backend, String comb500Ai, String comb500General,
                                 String comb1000Backend, String comb1000Ai, String comb1000General,
                                 Map<String, Aud> audienceTpls) {

        /** 受众级一整套文案：200 档四类 + 300/500/1000 档按岗位方向（后端开发 / AI 应用工程化） */
        record Aud(String comb200, String resp200, String res200, String techStack,
                   String ov300Backend, String ov300Ai,
                   String comb500Backend, String comb500Ai,
                   String comb1000Backend, String comb1000Ai) {
        }

        Aud aud(String audience) {
            return audienceTpls == null ? null : audienceTpls.get(audience);
        }

        String ov300For(String audience, String direction) {
            Aud aud = aud(audience);
            if (aud != null) {
                return "backend".equals(direction) ? aud.ov300Backend() : aud.ov300Ai();
            }
            return switch (direction) {
                case "backend" -> firstOf(ov300Backend, ov300General);
                case "ai" -> firstOf(ov300Ai, ov300General);
                default -> ov300General;
            };
        }

        String comb500For(String audience, String direction) {
            Aud aud = aud(audience);
            if (aud != null) {
                return "backend".equals(direction) ? aud.comb500Backend() : aud.comb500Ai();
            }
            return switch (direction) {
                case "backend" -> firstOf(comb500Backend, comb500General);
                case "ai" -> firstOf(comb500Ai, comb500General);
                default -> comb500General;
            };
        }

        String comb1000For(String audience, String direction) {
            Aud aud = aud(audience);
            if (aud != null) {
                return "backend".equals(direction) ? aud.comb1000Backend() : aud.comb1000Ai();
            }
            return switch (direction) {
                case "backend" -> firstOf(comb1000Backend, comb1000General);
                case "ai" -> firstOf(comb1000Ai, comb1000General);
                default -> comb1000General;
            };
        }

        String comb1000ByAudience(String a) {
            return switch (a) {
                case "bank" -> comb1000Bank;
                case "big_tech" -> firstOf(comb1000BigTech, comb1000Bank);
                default -> null;
            };
        }

        private static String firstOf(String... vs) {
            for (String v : vs) {
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
            return null;
        }
    }

    // ---------- 工行（全部受众；大厂版版本保留供手动选择，默认不展示/不自动填充） ----------
    // 四受众（大厂/国央企/银行/通用）共用同一套文案；300/500/1000 字档按岗位方向区分（后端开发 / AI 应用工程化）。
    private static final String ICBC_COMB200 = """
            1、企业级一体化交付平台研发；
            2、基于 Java、Spring Boot、JPA、Redis 完成任务建模、接口设计和平台能力封装；
            3、建设持续交付、精准出版、自动投验和环境路由切换流程；
            4、提升发布投验标准化、可靠性和可追溯性。""";
    private static final String ICBC_RESP200 = """
            1、企业级一体化交付平台研发，负责后端领域建模、接口设计和任务状态管理；
            2、封装 PaaS、Harbor、Apollo、ETCD、HAProxy 等平台能力；
            3、持续交付、精准出版、自动投验和环境路由切换流程建设；
            4、完善步骤日志、异常处理和操作审计能力，提升交付流程规范性。""";
    private static final String ICBC_RES200 = """
            1、沉淀覆盖开发构建、模板升级、综测出版、生产导入、健康检查和投验通知的一体化交付入口；
            2、减少多平台切换、人工改参和投产检查遗漏风险；
            3、提升发布投验流程的标准化、可靠性、可恢复性和审计可追溯性。""";
    // 后端开发｜实习描述｜300字以内（四受众共用）
    private static final String ICBC_OV300_BACKEND = """
            1、企业级一体化交付平台研发；
            2、围绕应用构建、模板升级、精准出版、自动投验、环境路由切换和跨集群迁移等场景开展后端开发；
            3、基于 Java、Spring Boot、JPA、Redis 完成项目、应用、模板、发布任务、出版任务、投验任务和操作审计等领域建模与接口设计；
            4、封装 PaaS、Harbor、Apollo、ETCD、HAProxy 等基础设施能力，推动交付流程标准化、自动化和可追溯。""";
    // AI 应用工程化｜实习描述｜300字以内（四受众共用）
    private static final String ICBC_OV300_AI = """
            1、企业级一体化交付平台研发，重点围绕交付流程自动化、任务编排、状态追踪和投验检查能力建设开展工作；
            2、将 Maven 打包、镜像构建、模板升级、配置迁移、健康检查和投验通知等分散人工操作抽象为标准化流程；
            3、基于 Spring Boot、JPA、Redis 实现任务状态、步骤日志、异常原因和操作审计管理；
            4、通过流程编排、日志追踪和检查规则建设，提升平台自动化执行、过程可观测和异常定位能力。""";
    // 后端开发｜合并型｜500字以内（四受众共用）
    private static final String ICBC_COMB500_BACKEND = """
            1、企业级一体化交付平台研发，面向行内应用从开发、综测到生产投验的交付流程，建设持续交付、精准出版、自动投验、环境路由切换和任务审计能力；
            2、基于 Java、Spring Boot、JPA、Redis 完成项目、应用、模板、集群、发布任务、出版任务、投验任务和步骤日志等领域建模与 RESTful API 设计；
            3、封装 PaaS、Harbor、Apollo、ETCD、HAProxy 等基础设施客户端，统一处理登录鉴权、接口调用、参数转换和异常返回；
            4、持续交付链路建设，支持 Maven 打包、启动文件等组zip包、镜像构建、模板 Tag 更新、滚动升级、Pod/JVM 检查；
            5、精准出版和自动投验流程建设，支持将综测资源转换为生产出版包，并在生产侧完成资源导入、健康检查和投验通知，提升流程规范性与可追溯性。""";
    // AI 应用工程化｜合并型｜500字以内（四受众共用）
    private static final String ICBC_COMB500_AI = """
            1、企业级一体化交付平台研发，主要围绕交付流程自动化、任务编排、状态追踪和投验检查能力建设开展工作；
            2、将 Maven 打包、镜像构建、模板 Tag 更新、滚动升级、综测资源出版、生产资源导入、健康检查和投验通知等步骤抽象为可执行的标准化任务链路；
            3、基于 Java、Spring Boot、JPA、Redis 完成任务模型、步骤日志、操作审计、异常记录和失败恢复等能力建设；
            4、封装 PaaS、Harbor、Apollo、ETCD、HAProxy 等平台接口，统一处理鉴权、参数转换、接口调用和异常返回；
            5、通过流程编排、状态管理、日志追踪和检查规则，将复杂发布投验过程变成可配置、可追踪、可恢复的平台能力，提升交付效率和异常定位能力。""";
    // 后端开发｜合并型｜1000字以内（四受众共用）
    private static final String ICBC_COMB1000_BACKEND = """
            1、企业级一体化交付平台研发，面向行内应用从开发、综测到生产投验的交付流程，解决多平台切换、人工改参、重复发布、状态难追踪和投产检查依赖人工等问题；
            2、围绕持续交付、精准出版、自动投验、环境路由切换和任务审计等场景开展后端开发工作，基于 Java、Spring Boot、JPA、Redis 完成项目、应用、模板、集群、发布任务、出版任务、投验任务、步骤日志和操作审计等领域建模；
            3、设计 RESTful API，统一沉淀任务状态、执行步骤、异常原因和操作留痕，提升发布投验过程的可观测性和可追溯性；
            4、封装 PaaS、Harbor、Apollo、ETCD、HAProxy 等基础设施客户端，统一处理登录鉴权、接口调用、参数映射、异常返回和失败重试；
            5、持续交付链路建设，实现 Maven 打包、Jar/Dockerfile/appstartup.sh 文件组包、构建包上传、镜像构建、模板镜像 Tag 更新、滚动升级、Pod 状态查询和 JVM 日志检查等能力；
            6、精准出版能力建设，将综测环境应用、模板、镜像、HA、Apollo 配置转换为可确认、可审计、可导入生产的标准出版包；
            7、自动投验能力建设，在生产侧完成资源导入、Apollo 发布、模板启动、Pod/JVM/ETCD/HA 校验、健康检查和投验平台通知；
            8、通过异步任务持久化、Redis 发布互斥、步骤日志和失败恢复机制，提升发布投验流程的标准化、可靠性和可恢复性。""";
    // AI 应用工程化｜合并型｜1000字以内（四受众共用）
    private static final String ICBC_COMB1000_AI = """
            1、企业级一体化交付平台研发，工作重点是将分散在 PaaS、Harbor、Apollo、ETCD、HAProxy 和投验平台中的人工操作抽象为可执行、可追踪、可恢复的标准化任务流程；
            2、围绕持续交付、精准出版、自动投验和环境路由切换等场景开展工程化建设，将复杂发布投验流程拆分为可配置步骤、可观测状态和可恢复任务；
            3、持续交付侧，将 Maven 打包、Jar/Dockerfile/appstartup.sh 文件组包、镜像构建、模板 Tag 更新、滚动升级、Pod 状态查询和 JVM 日志检查编排为自动化链路；
            4、精准出版侧，将综测环境应用、模板、镜像、HA、Apollo 配置等资源转换为生产出版包，并按生产规则完成副本、CPU、内存、镜像 Tag、测试 IP 到生产 IP 等参数映射；
            5、自动投验侧，承接出版包完成生产资源导入、Apollo 发布、模板启动、Pod/JVM/ETCD/HA 校验、健康检查和投验平台通知；
            6、后端基于 Java、Spring Boot、JPA、Redis 完成任务、步骤日志、操作审计、异常记录、失败恢复等模型与接口设计；
            7、封装外部平台客户端，统一处理鉴权、接口调用、参数转换和异常返回，提升多平台协同过程中的接入效率和异常定位能力；
            8、通过任务编排、状态管理、日志追踪和失败恢复机制，提升平台自动化执行能力和发布投验流程的可靠性。""";
    private static final InternshipTpl ICBC_TPL = new InternshipTpl(
            AUDIENCES_ALL,
            ICBC_COMB200, ICBC_RESP200, ICBC_RES200,
            null, null, null, null,
            null, null, null,
            null, null,
            ICBC_OV300_BACKEND, ICBC_OV300_AI, null,
            ICBC_COMB500_BACKEND, ICBC_COMB500_AI, null,
            ICBC_COMB1000_BACKEND, ICBC_COMB1000_AI, null,
            null);

    // ---------- 字节（大厂/国央企/银行/通用 四受众各自独立文案） ----------
    // ---- 大厂版 ----
    private static final String BYTE_BT_RESP200 = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、参与 P2P 转账、账户开通、KYC 引导、冻结/解冻、关闭/注销拦截等链路建设；
            3、参与账户状态治理、TCC 规则配置、RPC 封装和单元测试；
            4、提升账户操作链路稳定性与异常场景处理一致性。""";
    private static final String BYTE_BT_RES200 = """
            1、沉淀统一的账户状态校验、操作权限判断和在途交易治理能力；
            2、支撑 P2P 转账、账户关闭预检查、注销拦截、交易预检查等多场景复用；
            3、减少重复校验逻辑和硬编码分支，提升账号侧链路稳定性与可维护性。""";
    private static final String BYTE_BT_COMB200 = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、围绕 P2P 转账、账户开通、KYC 引导、账户关闭/注销拦截等场景建设账号侧能力；
            3、参与状态治理、TCC 配置、RPC 封装、Redis 缓存和 MQ 通知；
            4、提升账户链路稳定性和异常处理一致性。""";
    private static final String BYTE_BT_OV300_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截等链路建设账号侧能力；
            3、参与越南区 P2P Transfer 账号侧状态治理、资产安全校验、TCC 规则配置、RPC 封装和单元测试；
            4、提升账户操作链路稳定性与异常场景处理一致性。""";
    private static final String BYTE_BT_OV300_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，围绕支付账户治理和复杂状态判断开展工程化建设；
            2、参与将 P2P 转账、账户关闭预检查、KYC 引导、冻结/解冻等场景中的账号侧判断逻辑抽象为可复用能力；
            3、通过 TCC 配置、RPC 封装、Redis 缓存、MQ 通知和幂等处理，提升多场景账户链路的稳定性；
            4、配合前端、产品、风控、KYC、交易等团队完成接口联调和上线支持。""";
    private static final String BYTE_BT_COMB500_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截和站内信通知等链路建设；
            2、重点参与越南区 P2P Transfer 账号侧能力建设，梳理付款方、收款方在未开通、KYC 中间态、账户冻结、CA 异常、转账过期等状态下的业务分支；
            3、参与账户状态治理、资产安全校验、TCC 规则配置、RPC 调用封装和错误码处理；
            4、结合 Redis 缓存、MQ 通知和业务幂等机制，提升高频账户预检查和状态变更链路的稳定性。""";
    private static final String BYTE_BT_COMB500_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，重点围绕复杂账户状态治理、支付链路拦截和用户引导能力建设；
            2、将 P2P 转账、账户关闭、注销拦截、KYC 引导、冻结/解冻等业务入口中的重复判断逻辑抽象为账号侧统一校验能力；
            3、通过 TCC 配置控制不同国家、场景、入口下的检查器、返回文案和跳转路径，支持策略快速调整；
            4、参与 RPC 调用封装、错误码处理、Redis 缓存、MQ 状态广播和业务幂等处理，降低重复查询和重复消费风险；
            5、参与核心 Handler 单元测试建设，对外部依赖进行 Mock，覆盖参数异常、状态异常、风控拦截和正常链路等场景。""";
    private static final String BYTE_BT_COMB1000_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，基于 Go、Kitex、Thrift IDL、RPC、TCC、Redis、MQ 等技术，围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截、站内信通知等场景建设钱包用户域账号侧能力；
            2、重点参与越南区 P2P Transfer 用户间转账账号侧能力建设，将转账链路拆分为付款方发起、确认付款、收款方进入、点击收款和 24 小时超时退款等关键节点；
            3、梳理付款方、收款方在未开通、KYC 审核中、KYC 驳回、钱包账户冻结、CA 状态异常、风控限制、转账过期等状态下的业务分支，并通过分层校验逻辑减少硬编码判断；
            4、参与转账在途交易统一校验、账户关闭预检查、注销拦截和账户权限校验能力建设，统一返回拦截原因、文案 key、按钮类型和跳转链接；
            5、通过 TCC 配置不同国家、入口和场景下的检查器、拦截优先级和返回结果，提升规则调整效率；
            6、结合 Redis 短周期缓存、MQ 状态广播、业务唯一键幂等和单元测试建设，提升支付账户链路稳定性、可维护性和异常处理一致性。""";
    private static final String BYTE_BT_COMB1000_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，工作重点是把多入口、多状态、多系统依赖下的支付账户判断逻辑沉淀为标准化、可配置、可复用的账号侧能力；
            2、围绕越南区 P2P Transfer、账户开通、KYC 引导、账户冻结/解冻、账户关闭/注销拦截、站内信通知等场景，参与用户账户状态治理和操作权限控制能力建设；
            3、在 P2P 转账场景中，梳理付款方和收款方在未开通、KYC 审核中、KYC 驳回、钱包账户冻结、CA 状态异常、风控限制、转账过期等状态下的分支逻辑；
            4、参与将是否在途订单、是否风险账户等状态抽象为分层校验逻辑，避免各业务入口重复编写大量 if else；
            5、通过 TCC 动态配置不同国家、入口、场景下的检查器、拦截优先级、文案 key 和跳转链接，使账户状态校验能力可在 P2P 转账、账户关闭、注销拦截、交易预检查等场景复用；
            6、参与 Redis 短周期缓存、MQ 状态广播、业务唯一键幂等、RPC 封装和单元测试建设，提升支付账户链路的稳定性、可维护性和异常处理一致性。""";
    // ---- 国央企版 ----
    private static final String BYTE_ST_RESP200 = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、参与账户开通、P2P 转账、KYC 引导、冻结/解冻、关闭/注销拦截等链路建设；
            3、参与账户状态治理、资产安全校验、TCC 规则配置和 RPC 封装；
            4、提升账户操作流程规范性与异常处理一致性。""";
    private static final String BYTE_ST_RES200 = """
            1、沉淀统一的账户状态校验和交易治理能力；
            2、支撑 P2P 转账、账户关闭预检查、交易预检查和账户权限校验等多场景复用；
            3、减少重复开发和异常处理不一致问题，提升账号侧链路稳定性与可维护性。""";
    private static final String BYTE_ST_COMB200 = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、围绕账户开通、P2P 转账、账户状态校验、KYC 引导、关闭/注销拦截等场景建设账号侧能力；
            3、参与 TCC 配置、RPC 封装、Redis 缓存、MQ 通知和幂等处理；
            4、提升账户治理流程稳定性。""";
    private static final String BYTE_ST_OV300_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截等场景建设账号侧能力；
            3、参与账户状态治理、资产安全校验、TCC 规则配置和 RPC 封装；
            4、通过统一校验、消息通知和幂等处理机制，提升多场景账户操作流程的规范性、稳定性和可维护性。""";
    private static final String BYTE_ST_OV300_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，围绕支付账户治理和复杂状态判断开展工程化建设；
            2、将 P2P 转账、账户关闭、注销拦截、KYC 引导等场景中的账号侧判断逻辑抽象为统一校验能力；
            3、通过 TCC 配置、RPC 封装、Redis 缓存、MQ 通知和幂等处理，提升流程稳定性；
            4、支撑账户操作链路规范化、可配置化和异常处理一致性。""";
    private static final String BYTE_ST_COMB500_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截等链路建设；
            2、参与越南区 P2P Transfer 账号侧能力建设，梳理付款方、收款方在未开通、KYC 中间态、账户冻结、资金账户异常、转账过期等状态下的处理规则；
            3、参与账户状态治理、资产安全校验、TCC 配置化规则、RPC 调用封装和错误码处理；
            4、通过统一校验逻辑、标准化返回结构、Redis 缓存、MQ 通知和业务幂等机制，减少重复开发和异常处理不一致问题；
            5、提升账户操作链路的规范性、稳定性和可追溯性。""";
    private static final String BYTE_ST_COMB500_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，重点围绕账户状态治理、风险拦截和用户引导能力建设；
            2、将 P2P 转账、账户关闭、注销拦截、KYC 引导、冻结/解冻等多入口判断逻辑沉淀为账号侧统一校验能力；
            3、通过 TCC 配置化管理不同国家、入口和场景下的检查器、拦截优先级、文案 key 和跳转链接，提升规则调整效率；
            4、参与 Redis 缓存、MQ 状态广播、RPC 封装、错误码治理和消息幂等处理，减少重复查询和重复消费风险；
            5、提升支付账户治理流程的规范性、稳定性和可维护性。""";
    private static final String BYTE_ST_COMB1000_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，围绕账户开通、P2P 转账、账户状态校验、KYC 引导、账户冻结/解冻、关闭/注销拦截、站内信通知等场景建设账号侧能力；
            2、参与越南区 P2P Transfer 用户间转账账号侧状态治理，梳理付款方、收款方在未开通、KYC 审核中、KYC 驳回、钱包账户冻结、CA 资金账户异常、风控限制、转账过期等状态下的业务处理规则；
            3、参与账户状态与操作权限校验能力建设，将是否在途订单、是否风险账户等状态拆分为分层校验逻辑，并统一返回状态结果、拦截原因、文案 key 和跳转链接；
            4、参与账户关闭预检查和注销拦截能力建设，对余额、冻结金额、在途交易、未收款订单等风险项进行校验，避免资金未结清情况下执行高风险账户操作；
            5、通过 TCC 配置不同国家、入口、场景下的校验项、拦截优先级和返回结果，减少多入口重复开发成本；
            6、参与 Redis 缓存、MQ 状态通知、业务唯一键幂等和单元测试建设，提升账户治理流程的稳定性、规范性和异常处理一致性。""";
    private static final String BYTE_ST_COMB1000_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，工作重点是将多入口、多状态、多系统依赖下的账户判断逻辑沉淀为可配置、可复用、可维护的账号侧治理能力；
            2、围绕 P2P 转账、账户开通、KYC 引导、账户冻结/解冻、账户关闭/注销拦截和站内信通知等场景，参与账户状态治理和操作权限控制能力建设；
            3、在 P2P 场景中，将付款方发起、确认付款、收款方进入、点击收款和超时退款等节点拆分，并按不同主体判断开户状态、KYC 状态、钱包用户状态、资金账户状态和风控状态；
            4、通过 TCC 配置不同国家、业务入口、产品场景下启用的校验项、拦截优先级和返回结果，减少多入口重复开发；
            5、参与在途交易校验、账户关闭预检查、注销拦截等能力建设，对余额、冻结金额、未完成交易、未收款订单等风险项进行校验；
            6、参与 Redis 缓存、MQ 通知、RPC 封装、错误码处理和业务幂等建设，提升账户治理链路的稳定性、规范性和异常处理一致性。""";
    // ---- 银行版 ----
    private static final String BYTE_BK_RESP200 = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、参与 P2P 转账、账户开通、KYC 引导、冻结/解冻、关闭/注销拦截等链路建设；
            3、参与账户状态治理、资产安全校验和交易预检查；
            4、通过 TCC、Redis、MQ、RPC 和幂等机制提升支付链路稳定性。""";
    private static final String BYTE_BK_RES200 = """
            1、沉淀统一的支付账户状态校验、操作权限判断和在途交易治理能力；
            2、支撑 P2P 转账、账户关闭预检查、交易预检查和账户权限校验等场景；
            3、提升账户链路稳定性、资金安全和异常处理一致性。""";
    private static final String BYTE_BK_COMB200 = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、围绕 P2P 转账、账户状态校验、KYC 引导、关闭/注销拦截和资产安全校验建设账号侧能力；
            3、通过 TCC、Redis、MQ、RPC 和幂等机制提升支付账户链路稳定性。""";
    private static final String BYTE_BK_OV300_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截等支付账户链路建设；
            3、参与账户状态治理、资产安全校验、交易预检查、账户关闭预检查和 TCC 规则配置；
            4、通过 Redis、MQ、RPC 和幂等机制提升支付账户链路稳定性和风险拦截一致性。""";
    private static final String BYTE_BK_OV300_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，围绕支付账户治理、资产安全校验和风险拦截开展工程化建设；
            2、将 P2P 转账、账户关闭、注销拦截、KYC 引导等场景中的账户状态判断抽象为统一校验能力；
            3、通过 TCC 配置、Redis 缓存、MQ 通知、RPC 封装和幂等处理提升链路稳定性；
            4、支撑支付账户操作权限判断和异常场景一致处理。""";
    private static final String BYTE_BK_COMB500_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截等链路建设；
            2、参与越南区 P2P Transfer 账号侧状态治理，按付款方、收款方和退款场景校验开户状态、KYC 状态、WalletUid 状态、CA 资金账户状态和风控状态；
            3、参与账户关闭预检查和注销拦截能力建设，对余额、冻结金额、在途交易、未收款订单等风险项进行校验，避免资金未结清情况下执行高风险账户操作；
            4、通过 TCC 配置、RPC 封装、Redis 缓存、MQ 通知和业务幂等机制，提升账户操作链路稳定性、资金安全和异常处理一致性。""";
    private static final String BYTE_BK_COMB500_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，重点围绕支付账户状态治理、资产安全校验和风险拦截能力建设；
            2、在 P2P 转账、账户关闭、注销拦截等场景中，参与统一判断用户开户状态、KYC 状态、钱包用户状态、资金账户状态、风控状态和在途交易状态；
            3、通过 TCC 配置化规则控制不同国家、入口、场景下的检查器和拦截优先级，提升风险策略调整效率；
            4、参与 Redis 短周期缓存、MQ 状态广播、RPC 封装和业务幂等处理，降低重复查询和重复消费风险；
            5、提升支付账户链路稳定性、资金安全校验能力和异常处理一致性。""";
    private static final String BYTE_BK_COMB1000_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，围绕账户开通、P2P 转账、账户状态校验、KYC 引导、账户冻结/解冻、关闭/注销拦截、站内信通知等金融支付场景建设账号侧能力；
            2、参与越南区 P2P Transfer 用户间转账账号侧能力建设，梳理付款方、收款方在未开通、KYC 审核中、KYC 驳回、钱包账户冻结、CA 资金账户异常、风控限制、转账过期等状态下的处理规则；
            3、参与账户状态与交易方向匹配规则设计，按出金/入金方向判断账户正常、止入、止出等状态下的可操作性，避免仅按账户异常状态一刀切拦截；
            4、参与账户关闭预检查和注销拦截链路建设，校验余额、冻结金额、在途交易、未收款订单等资产风险项，避免资金未结清情况下执行高风险账户操作；
            5、通过 TCC 配置化管理不同国家、入口和场景下的检查器、拦截优先级、文案 key 和跳转链接，提升风险拦截规则的可维护性；
            6、结合 Redis 缓存、MQ 状态广播、RPC 封装、错误码处理、业务唯一键幂等和单元测试，提升支付账户链路稳定性、资金安全和异常处理一致性。""";
    private static final String BYTE_BK_COMB1000_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，围绕支付账户治理、资产安全校验、风险拦截和用户引导等场景开展工程化建设；
            2、在越南区 P2P Transfer 用户间转账场景中，参与付款方发起、确认付款、收款方进入、点击收款、超时退款等关键节点的账号侧状态判断；
            3、参与将用户开通状态、KYC 状态、WalletUid 状态、CA 资金账户状态、风控状态和在途交易状态抽象为分层校验能力，统一输出是否允许继续操作、拦截原因、文案 key 和跳转路径；
            4、参与账户关闭预检查和注销拦截能力建设，对余额、冻结金额、未完成交易、未收款订单等资产风险项进行校验，避免资金未结清情况下继续高风险账户操作；
            5、通过 TCC 配置化规则支持不同国家、入口、场景下的检查器、拦截优先级和返回结果快速调整；
            6、结合 Redis 缓存、MQ 通知、RPC 封装、错误码治理、业务幂等和单元测试，提升支付账户治理链路的稳定性、资金安全和异常场景处理一致性。""";
    // ---- 通用版 ----
    private static final String BYTE_GE_RESP200 = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、参与账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截等链路建设；
            3、参与 TCC 配置、RPC 封装、Redis 缓存、MQ 通知和单元测试；
            4、提升账户链路稳定性。""";
    private static final String BYTE_GE_RES200 = """
            1、沉淀账户状态校验、交易治理和账户权限判断能力；
            2、支撑 P2P 转账、账户关闭预检查、交易预检查、注销拦截等多场景复用；
            3、减少重复逻辑和异常处理不一致问题，提升账号侧链路稳定性与可维护性。""";
    private static final String BYTE_GE_COMB200 = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截等链路建设；
            3、参与 TCC 配置、RPC 封装、Redis 缓存、MQ 通知和单元测试，提升账户链路稳定性。""";
    private static final String BYTE_GE_OV300_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截、站内信通知等链路建设；
            3、参与账户状态治理、资产安全校验、TCC 规则配置、RPC 封装、错误码处理和单元测试；
            4、提升账户操作链路稳定性、可维护性和异常场景处理一致性。""";
    private static final String BYTE_GE_OV300_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发；
            2、围绕支付账户治理、多状态判断、用户引导和异常拦截等场景开展工程化建设；
            3、通过 TCC 配置、RPC 封装、Redis 缓存、MQ 通知和幂等处理沉淀可复用账号侧能力；
            4、支撑 P2P 转账、账户关闭、注销拦截和账户权限校验等多场景复用。""";
    private static final String BYTE_GE_COMB500_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截和站内信通知等链路建设；
            2、参与越南区 P2P Transfer 账号侧能力建设，梳理付款方、收款方在未开通、KYC 中间态、账户冻结、CA 异常、转账过期等场景下的状态路由；
            3、参与账户关闭预检查、注销拦截、资产安全校验和交易预检查能力建设；
            4、基于 Go、Kitex、Thrift IDL 完成 RPC 接口接入、TCC 配置读取、错误码处理和业务分层开发；
            5、结合 Redis、MQ、幂等处理和单元测试提升账户链路稳定性。""";
    private static final String BYTE_GE_COMB500_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，将多入口、多状态、多系统依赖下的账户判断逻辑抽象为可复用账号侧能力；
            2、围绕 P2P 转账、账户开通、KYC 引导、冻结/解冻、关闭/注销拦截等场景，参与账户状态治理和操作权限控制；
            3、通过 TCC 配置不同国家、入口和场景下的检查器、拦截优先级和返回结果；
            4、参与 RPC 调用封装、Redis 缓存、MQ 状态广播、错误码处理和业务幂等建设；
            5、提升账户操作链路稳定性、可维护性和异常场景处理一致性。""";
    private static final String BYTE_GE_COMB1000_BACKEND = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，基于 Go、Kitex、Thrift IDL、Redis、TCC、MQ、RPC 等技术建设钱包用户域账号侧能力；
            2、围绕账户开通、P2P 转账、账户状态校验、KYC 引导、冻结/解冻、关闭/注销拦截、站内信通知等链路参与需求开发和接口联调；
            3、参与越南区 P2P Transfer 用户间转账能力建设，将付款方发起、确认付款、收款方进入、点击收款和超时退款等节点拆分，并按不同主体判断账户状态；
            4、参与账号侧状态治理能力建设，覆盖未开通、KYC 审核中、KYC 驳回、钱包账户冻结、CA 异常、风控限制、转账过期等场景；
            5、参与账户关闭预检查和注销拦截链路建设，对余额、冻结金额、在途交易、未收款订单等风险项进行校验；
            6、通过 TCC 配置化规则、Redis 缓存、MQ 状态通知、RPC 封装、错误码处理、业务唯一键幂等和单元测试，提升账户操作链路稳定性、可维护性和异常处理一致性。""";
    private static final String BYTE_GE_COMB1000_AI = """
            1、参与TikTok 支付/ PIPO Wallet 国际支付账户体系后端研发，工作内容覆盖账户开通、P2P 转账、账户状态校验、KYC 引导、账户冻结/解冻、关闭/注销拦截、站内信通知等链路；
            2、参与越南区 P2P Transfer 账号侧能力建设，将转账链路拆分为付款方发起、确认付款、收款方进入、点击收款和超时退款等关键节点；
            3、参与账户状态治理和操作权限控制能力建设，按不同主体和业务节点判断开户状态、KYC 状态、WalletUid 状态、CA 状态、风控状态和在途交易状态；
            4、通过 TCC 配置不同国家、入口、场景下的检查器、拦截优先级、文案 key 和跳转链接，减少重复开发；
            5、参与 RPC 封装、错误码处理、Redis 缓存、MQ 状态通知、业务幂等和单元测试建设；
            6、通过上述工作，将复杂账户状态判断和异常拦截能力沉淀为可复用后端能力，支撑 P2P 转账、账户关闭预检查、交易预检查和账户权限校验等多场景复用。""";
    /** 字节技术栈：四受众一致 */
    private static final String BYTE_TECH_STACK = "Go / Kitex / Thrift IDL / Redis / MySQL / TCC / MQ / RPC";
    private static final InternshipTpl BYTE_TPL = new InternshipTpl(
            AUDIENCES_ALL,
            null, null, null,
            null, null, null, null,
            null, null, null,
            null, null,
            null, null, null,
            null, null, null,
            null, null, null,
            Map.of(
                    "big_tech", new InternshipTpl.Aud(BYTE_BT_COMB200, BYTE_BT_RESP200, BYTE_BT_RES200, BYTE_TECH_STACK,
                            BYTE_BT_OV300_BACKEND, BYTE_BT_OV300_AI, BYTE_BT_COMB500_BACKEND, BYTE_BT_COMB500_AI,
                            BYTE_BT_COMB1000_BACKEND, BYTE_BT_COMB1000_AI),
                    "state_owned", new InternshipTpl.Aud(BYTE_ST_COMB200, BYTE_ST_RESP200, BYTE_ST_RES200, BYTE_TECH_STACK,
                            BYTE_ST_OV300_BACKEND, BYTE_ST_OV300_AI, BYTE_ST_COMB500_BACKEND, BYTE_ST_COMB500_AI,
                            BYTE_ST_COMB1000_BACKEND, BYTE_ST_COMB1000_AI),
                    "bank", new InternshipTpl.Aud(BYTE_BK_COMB200, BYTE_BK_RESP200, BYTE_BK_RES200, BYTE_TECH_STACK,
                            BYTE_BK_OV300_BACKEND, BYTE_BK_OV300_AI, BYTE_BK_COMB500_BACKEND, BYTE_BK_COMB500_AI,
                            BYTE_BK_COMB1000_BACKEND, BYTE_BK_COMB1000_AI),
                    "general", new InternshipTpl.Aud(BYTE_GE_COMB200, BYTE_GE_RESP200, BYTE_GE_RES200, BYTE_TECH_STACK,
                            BYTE_GE_OV300_BACKEND, BYTE_GE_OV300_AI, BYTE_GE_COMB500_BACKEND, BYTE_GE_COMB500_AI,
                            BYTE_GE_COMB1000_BACKEND, BYTE_GE_COMB1000_AI)));

    // ---------- 京东（大厂/国央企/银行/通用 四受众各自独立文案） ----------
    // ---- 大厂版 ----
    private static final String JD_BT_RESP200 = """
            1、参与机器人相关后端系统开发，负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块；
            2、参与设备管理、多模态视觉处理、智能对话、RAG 检索和多模型调度链路建设；
            3、基于 gRPC / proto 统一接口规范；
            4、通过异步处理、缓存优化、流式返回和链路日志提升系统稳定性。""";
    private static final String JD_BT_RES200 = """
            1、支撑智能家居设备管理、多模态视觉处理和智能对话等场景稳定运行；
            2、通过策略扩展、配置路由、异步处理和流式返回提升多场景接入效率；
            3、核心接口响应耗时降低 30%+，吞吐量提升 50%，系统可用性达 99.9%。""";
    private static final String JD_BT_COMB200 = """
            1、参与机器人相关后端系统开发；
            2、负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块，覆盖设备管理、多模态视觉处理和智能对话场景；
            3、基于 gRPC / proto 构建多端通信体系；
            4、通过异步处理、缓存优化、流式返回和链路日志提升系统稳定性。""";
    private static final String JD_BT_OV300_BACKEND = """
            1、参与机器人相关后端系统开发，基于 Java / Spring Boot 和 Go 构建微服务能力；
            2、负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块开发，覆盖智能家居设备管理、多模态视觉处理与智能对话场景；
            3、基于 gRPC / proto 构建多端通信体系，统一数据模型与接口规范；
            4、通过异步处理、缓存优化、流式返回和链路日志优化接口响应与系统稳定性。""";
    private static final String JD_BT_OV300_AI = """
            1、参与机器人智能云平台后端研发，围绕 Cloud VLM、Robot AIUI、Cloud IoT 等模块建设 AI 应用工程化能力；
            2、参与多模态视觉处理、智能对话、RAG 语义检索、Agent 路由和多模型调度等链路开发；
            3、通过 gRPC / proto、策略模式、规则引擎、流式响应和链路追踪提升 AI 能力接入效率；
            4、支撑机器人视觉问答、语音交互和智能设备控制等场景稳定运行。""";
    private static final String JD_BT_COMB500_BACKEND = """
            1、参与机器人相关后端系统开发，基于 Java / Spring Boot 和 Go 构建 Cloud IoT、Cloud VLM、Robot AIUI 等模块能力；
            2、参与 IoT 云端管理平台建设，完成设备接入、设备管理、状态查询、数据采集、控制下发和场景联动等能力；
            3、参与 Cloud VLM 多模态视觉服务建设，通过策略模式、规则引擎和配置中心解耦场景逻辑与模型路由；
            4、参与 Robot AIUI 智能对话系统建设，支持音频流接入、上下文透传、Agent 路由、RAG 检索和多模型调度；
            5、基于 gRPC / proto、异步处理、缓存优化、流式返回和链路日志提升接口响应与系统稳定性。""";
    private static final String JD_BT_COMB500_AI = """
            1、参与机器人智能云平台后端研发，围绕多模态视觉服务、智能对话系统和 IoT 云服务建设 AI 应用工程化能力；
            2、将图片输入、文本上下文、场景规则、模型调用和结果返回抽象为统一服务链路，支持药盒检测、中医舌诊、题目识别、通用视觉问答等场景；
            3、参与音频流接入、对话工作流编排、Agent 路由、RAG 语义检索和多模型调度能力建设；
            4、基于 gRPC / proto、策略模式、规则引擎、配置中心、异步处理和流式返回提升多场景接入效率；
            5、参与链路日志、降级熔断和 Kubernetes 容器化部署，提升 AI 应用链路稳定性和可维护性。""";
    private static final String JD_BT_COMB1000_BACKEND = """
            1、参与机器人相关后端系统开发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块，建设设备接入管理、多模态视觉处理、智能对话、语义检索和多模型调度能力；
            2、IoT服务中参与 IoT 云端管理平台建设，设计“品类-产品-设备”三层数据模型，支持设备注册、绑定解绑、状态查询、批量操作、分页查询和状态快照等能力；
            3、参与多模态视觉处理服务建设，将图片输入、文本上下文、场景规则、模型调用和结果返回抽象为统一服务链路；
            4、基于策略模式和规则引擎实现多业务场景解耦与动态扩展，通过配置中心完成模型路由、插件选择和策略执行的动态配置；
            5、参与云端智能交互网关建设，支持音频流接入、会话上下文管理、ASR、意图识别、LLM / Agent、TTS 等模块协同处理；
            6、基于 gRPC / proto 设计多端通信规范，通过异步处理、缓存优化、流式返回、链路日志和 Kubernetes 容器化部署提升接口响应、系统稳定性和可维护性。""";
    private static final String JD_BT_COMB1000_AI = """
            1、参与机器人智能云平台后端研发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块，建设智能设备接入、多模态视觉处理、智能对话、RAG 语义检索、Agent 路由和多模型调度能力；
            2、参与统一多模态视觉服务建设，将“图片输入 + 文本上下文 + 场景规则 + 模型调用 + 结果返回”抽象为标准化服务链路；
            3、基于策略模式设计图像处理框架，通过工厂模式按业务 order 路由到对应场景处理逻辑，并结合规则引擎与配置中心实现模型路由、插件选择和策略执行的动态配置；
            4、完成 gRPC 接口和 proto 协议设计，统一请求 header、图片内容、文本上下文和响应结构，支撑云端与设备端高效通信；
            5、参与云端智能交互网关建设，接入 ASR、意图识别、LLM、TTS、Agent 和 RAG 等能力，支持音频流接入、上下文透传、全双工交互和多轮对话；
            6、构建基于 Milvus 的 RAG 语义检索模块，完成 query 向量化、TopK 召回、结果重排和上下文拼接；
            7、通过异步处理、流式响应、链路日志、降级熔断和 Kubernetes 容器化部署，提升 AI 应用链路稳定性和扩展能力。""";
    private static final String JD_BT_TECH_STACK = "Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / proto / Elasticsearch / OSS / Go / Gin / Milvus / Docker / Kubernetes / OpenTelemetry / RAG / Agent";
    // ---- 国央企版 ----
    private static final String JD_ST_RESP200 = """
            1、参与机器人相关后端系统开发；
            2、负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块建设；
            3、参与设备管理、多模态视觉处理、智能对话和 RAG 检索链路开发；
            4、通过接口规范、异步处理、缓存优化和链路日志提升系统稳定性。""";
    private static final String JD_ST_RES200 = """
            1、支撑机器人配件、智能设备、多模态视觉处理和智能对话场景稳定运行；
            2、沉淀统一设备模型、视觉服务链路和智能对话编排能力；
            3、提升智能系统服务化接入、接口规范化和多模块协同处理能力。""";
    private static final String JD_ST_COMB200 = """
            1、参与机器人相关后端系统开发；
            2、基于 Java / Spring Boot 和 Go 负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块；
            3、覆盖设备管理、多模态视觉处理和智能对话场景；
            4、通过 gRPC / proto、异步处理、缓存优化和链路日志提升系统稳定性。""";
    private static final String JD_ST_OV300_BACKEND = """
            1、参与机器人相关后端系统开发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块开展平台建设；
            2、基于 Java / Spring Boot 和 Go 完成设备管理、多模态视觉处理、智能对话等后端能力开发；
            3、基于 gRPC / proto 统一多端通信规范和数据模型；
            4、通过异步处理、缓存优化、流式返回和链路日志提升接口稳定性与系统可维护性。""";
    private static final String JD_ST_OV300_AI = """
            1、参与机器人智能云平台研发，围绕多模态视觉服务、智能对话系统和 IoT 云服务开展 AI 应用工程化建设；
            2、参与 Cloud VLM、Robot AIUI 等模块的服务端链路开发和能力接入；
            3、通过策略模式、规则引擎、gRPC / proto、RAG 检索和多模型路由支撑智能交互场景；
            4、提升智能系统服务化接入、接口规范化和链路稳定性。""";
    private static final String JD_ST_COMB500_BACKEND = """
            1、参与机器人相关后端系统开发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块建设智能系统平台能力；
            2、参与 IoT 云端管理平台建设，实现设备接入、设备管理、状态查询、数据采集、语音控制和场景联动能力；
            3、参与多模态视觉服务建设，通过策略模式、规则引擎和配置中心解耦场景逻辑与模型路由；
            4、参与智能对话系统建设，支持音频流接入、上下文透传、Agent 路由、RAG 检索和多模型接入；
            5、通过统一接口规范、异步处理、缓存优化、流式响应和链路日志提升系统稳定性、扩展性和可维护性。""";
    private static final String JD_ST_COMB500_AI = """
            1、参与机器人智能云平台研发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块开展 AI 应用工程化建设；
            2、参与多模态视觉服务建设，将图像识别、视觉问答等能力统一接入服务端链路；
            3、通过策略模式、规则引擎和配置中心实现模型路由、插件选择和场景处理逻辑的配置化管理；
            4、参与音频流接入、对话工作流编排、Agent 路由、RAG 检索和多模型接入；
            5、通过 gRPC / proto、异步处理、流式返回、链路日志和容器化部署提升智能系统稳定性和可维护性。""";
    private static final String JD_ST_COMB1000_BACKEND = """
            1、参与机器人相关后端系统开发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块建设智能设备接入、多模态视觉处理和智能对话能力；
            2、参与 Cloud IoT 云端管理平台建设，设计“品类-产品-设备”三层数据模型，实现设备注册、绑定解绑、状态查询、批量操作、分页查询和状态快照等全生命周期管理能力；
            3、参与设备通信、鉴权认证、数据订阅和消息推送能力建设，通过 gRPC / JSF 支撑云端服务、设备端和第三方平台稳定交互；
            4、参与 Cloud VLM 多模态视觉服务建设，通过策略模式、规则引擎和配置中心实现多场景视觉处理能力的解耦和配置化扩展；
            5、参与 Robot AIUI 智能对话系统建设，支持音频流接入、上下文透传、Agent 路由、RAG 检索、多模型接入和流式返回；
            6、通过统一通信规范、数据模型标准化、异步处理、缓存优化、链路日志和容器化部署，提升智能系统平台的稳定性、可维护性和工程化落地能力。""";
    private static final String JD_ST_COMB1000_AI = """
            1、参与机器人智能云平台研发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块，建设智能设备接入、多模态视觉处理、智能对话、语义检索和多模型调度能力；
            2、参与统一视觉服务层建设，将图像识别、视觉问答等能力以服务化方式接入，支持药盒检测、中医舌诊、题目识别、通用视觉问答等场景；
            3、通过策略模式、规则引擎和配置中心将场景逻辑与模型路由解耦，支持多场景配置化扩展；
            4、参与云端智能交互网关建设，统一接入 ASR、意图识别、LLM、TTS、Agent、RAG 等能力；
            5、参与 Agent 路由、RAG 语义检索、多模型接入、降级熔断和流式响应能力建设，支撑知识问答、多轮对话和全双工交互场景；
            6、通过 gRPC / proto 统一通信规范，结合异步处理、缓存优化、链路追踪和 Kubernetes 容器化部署，提升智能系统工程化落地、稳定运行和持续扩展能力。""";
    private static final String JD_ST_TECH_STACK = "Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / proto / JSF / JMQ / Elasticsearch / OSS / Go / Gin / Milvus / Docker / Kubernetes / OpenTelemetry";
    // ---- 银行版 ----
    private static final String JD_BK_RESP200 = """
            1、参与机器人相关后端系统开发；
            2、负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块，覆盖设备管理、多模态视觉处理和智能对话场景；
            3、参与 gRPC / proto 通信规范、异步处理、缓存优化和流式返回建设；
            4、提升接口稳定性与系统可维护性。""";
    private static final String JD_BK_RES200 = """
            1、支撑设备管理、多模态视觉处理和智能对话等场景稳定运行；
            2、沉淀统一数据模型、接口规范、视觉服务链路和对话编排能力；
            3、核心接口响应耗时降低 30%+，吞吐量提升 50%，系统可用性达 99.9%。""";
    private static final String JD_BK_COMB200 = """
            1、参与机器人相关后端系统开发；
            2、负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块建设；
            3、覆盖设备管理、多模态视觉处理、智能对话和 RAG 检索场景；
            4、通过 gRPC / proto、异步处理、缓存优化和链路日志提升系统稳定性。""";
    private static final String JD_BK_OV300_BACKEND = """
            1、参与机器人相关后端系统开发，基于 Java / Spring Boot 和 Go 构建微服务能力；
            2、负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块开发，覆盖设备管理、多模态视觉处理和智能对话场景；
            3、基于 gRPC / proto 统一数据模型和接口规范；
            4、通过异步处理、缓存优化、流式返回、链路日志和容器化部署提升系统稳定性。""";
    private static final String JD_BK_OV300_AI = """
            1、参与机器人智能云平台后端研发，围绕 AI 能力接入、智能对话和多模态视觉处理开展工程化建设；
            2、参与 Cloud VLM、Robot AIUI 等模块开发，支持视觉问答、RAG 检索、Agent 路由和多模型调度；
            3、通过 gRPC / proto、策略模式、规则引擎、流式响应和链路日志提升 AI 服务稳定性；
            4、支撑智能交互场景持续扩展。""";
    private static final String JD_BK_COMB500_BACKEND = """
            1、参与机器人相关后端系统开发，基于 Java / Spring Boot 和 Go 负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块能力建设；
            2、参与设备接入、设备管理、状态查询、数据采集、语音控制和场景联动等后端链路开发；
            3、参与多模态视觉服务建设，通过策略模式、规则引擎和配置中心实现模型路由和插件选择；
            4、参与智能对话系统建设，支持音频流接入、上下文透传、RAG 检索、Agent 路由和多模型接入；
            5、通过 gRPC / proto、异步处理、缓存优化、流式响应和链路日志提升接口稳定性和系统可维护性。""";
    private static final String JD_BK_COMB500_AI = """
            1、参与机器人智能云平台后端研发，围绕多模态视觉服务、智能对话系统和 IoT 云服务建设 AI 应用工程化能力；
            2、参与 Cloud VLM 视觉语言模型服务建设，将图像识别、视觉问答等能力封装为统一服务链路；
            3、参与 Robot AIUI 智能对话系统建设，支持音频流接入、上下文透传、Agent 路由、RAG 检索和多模型调度；
            4、通过 gRPC / proto、策略模式、规则引擎、配置中心、异步处理和流式返回提升接口稳定性；
            5、该经历可作为金融科技智能客服、智能运营、智能风控辅助等 AI 工程化场景的后端系统能力补充。""";
    private static final String JD_BK_COMB1000_BACKEND = """
            1、参与机器人相关后端系统开发，基于 Java / Spring Boot 和 Go 构建 Cloud IoT、Cloud VLM、Robot AIUI 等模块能力；
            2、IoT服务中参与设备接入、设备管理、状态查询、数据采集、控制下发和场景联动等后端链路开发，设计“品类-产品-设备”三层数据模型，提升多类型设备管理的一致性；
            3、参与多模态视觉服务建设，将图像识别、视觉问答等能力以统一服务形式接入，支持药盒检测、中医舌诊、题目识别和通用视觉问答等场景；
            4、参与智能对话系统建设，支持音频流接入、上下文透传、Agent 路由、RAG 检索、多模型接入和流式返回；
            5、基于 gRPC / proto 构建多端通信体系，统一请求字段、上下文字段和响应结构，提升云端、设备端和第三方服务之间的调用稳定性；
            6、通过异步处理、缓存优化、流式返回、链路日志、降级熔断和 Kubernetes 容器化部署提升系统可用性和问题定位效率。""";
    private static final String JD_BK_COMB1000_AI = """
            1、参与机器人智能云平台后端研发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块建设多模态视觉处理、智能对话、语义检索和多模型调度能力；
            2、参与统一视觉服务层建设，将图片输入、文本上下文、场景规则、模型调用和结果返回抽象为标准化服务链路，支持多类视觉处理场景；
            3、参与云端智能交互网关建设，统一接入 ASR、意图识别、LLM、TTS、Agent 和 RAG 等能力；
            4、参与 gRPC / proto 通信规范设计，完成元数据提取、上下文透传和跨模块调用规范建设，保障对话链路数据一致性和可追踪性；
            5、参与基于 Milvus 的 RAG 语义检索模块建设，完成 query 向量化、TopK 召回、结果重排和上下文拼接，支撑知识问答和多轮对话；
            6、通过异步处理、流式响应、链路日志、降级熔断和 Kubernetes 容器化部署提升智能系统稳定性。该经历可迁移到金融科技场景中的智能客服、智能运营、智能助手和知识检索系统建设。""";
    private static final String JD_BK_TECH_STACK = "Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / proto / Elasticsearch / OSS / Go / Gin / Milvus / Docker / Kubernetes / RAG / Agent";
    // ---- 通用版 ----
    private static final String JD_GE_RESP200 = """
            1、参与机器人相关后端系统开发；
            2、负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块；
            3、参与设备管理、多模态视觉处理、智能对话、RAG 检索和多模型调度链路建设；
            4、通过 gRPC / proto、异步处理、缓存优化和流式返回提升系统稳定性。""";
    private static final String JD_GE_RES200 = """
            1、支撑智能家居设备管理、多模态视觉处理和智能对话等场景稳定运行；
            2、沉淀统一设备模型、视觉服务链路、对话工作流和 RAG 检索能力；
            3、核心接口响应耗时降低 30%+，吞吐量提升 50%，系统可用性达 99.9%。""";
    private static final String JD_GE_COMB200 = """
            1、参与机器人相关后端系统开发；
            2、负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块，覆盖设备管理、多模态视觉处理和智能对话场景；
            3、基于 gRPC / proto 构建多端通信体系；
            4、通过异步处理、缓存优化、流式返回和链路日志提升系统稳定性。""";
    private static final String JD_GE_OV300_BACKEND = """
            1、参与机器人相关后端系统开发；
            2、基于 Java / Spring Boot 和 Go 负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块开发；
            3、覆盖智能家居设备管理、多模态视觉处理、智能对话、RAG 检索和多模型调度等场景；
            4、基于 gRPC / proto 构建多端通信体系，通过异步处理、缓存优化、流式返回和链路日志提升系统稳定性。""";
    private static final String JD_GE_OV300_AI = """
            1、参与机器人智能云平台后端研发；
            2、围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块建设设备管理、多模态视觉处理、智能对话和 RAG 检索能力；
            3、参与 ASR、LLM、TTS、Agent、多模型路由和流式响应等能力接入；
            4、通过 gRPC / proto、策略模式、规则引擎和链路日志提升 AI 应用工程化落地能力。""";
    private static final String JD_GE_COMB500_BACKEND = """
            1、参与机器人相关后端系统开发，基于 Java / Spring Boot 和 Go 构建 Cloud IoT、Cloud VLM、Robot AIUI 等模块能力；
            2、IoT服务中参与设备接入、设备管理、状态查询、数据采集、语音控制和场景联动能力建设；
            3、参与多模态视觉服务建设，通过策略模式、规则引擎和配置中心支持多业务场景动态扩展；
            4、参与音频流接入、对话工作流、Agent 路由、RAG 检索和多模型接入；
            5、基于 gRPC / proto、异步处理、缓存优化、流式返回、链路日志和容器化部署提升接口响应与系统稳定性。""";
    private static final String JD_GE_COMB500_AI = """
            1、参与机器人智能云平台后端研发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块建设 AI 应用工程化能力；
            2、参与多模态视觉服务建设，将图像识别、视觉问答等能力抽象为统一服务链路，支持多场景动态扩展；
            3、参与智能对话系统建设，支持音频流接入、上下文透传、Agent 路由、RAG 检索和多模型接入；
            4、基于 Milvus 构建语义检索链路，完成 query 向量化、TopK 召回、结果重排和上下文拼接；
            5、通过 gRPC / proto、异步处理、流式返回、链路追踪和 Kubernetes 部署提升系统稳定性。""";
    private static final String JD_GE_COMB1000_BACKEND = """
            1、参与机器人相关后端系统开发，基于 Java / Spring Boot 和 Go 构建微服务能力，负责 Cloud IoT、Cloud VLM、Robot AIUI 等模块开发；
            2、IoT服务中参与 IoT 云端管理平台建设，提供设备接入、设备管理、状态查询、数据采集、语音控制、场景联动和云云对接能力；
            3、设计“品类-产品-设备”三层数据模型，实现设备注册、绑定解绑、状态查询、批量操作、分页查询和状态快照等能力；
            4、参与多模态视觉服务建设，通过策略模式、规则引擎和配置中心解耦场景逻辑与模型路由；
            5、参与智能对话系统建设，支持音频流接入、上下文透传、Agent 路由、RAG 检索和多模型调度；
            6、基于 gRPC / proto 构建多端通信体系，统一数据模型和接口规范；
            7、通过异步处理、缓存优化、流式返回、链路日志、降级熔断和 Kubernetes 容器化部署，提升接口响应、系统稳定性和可维护性。""";
    private static final String JD_GE_COMB1000_AI = """
            1、参与机器人智能云平台后端研发，围绕 Cloud IoT、Cloud VLM、Robot AIUI 等模块，建设设备接入管理、多模态视觉处理、智能对话、语义检索和多模型调度能力；
            2、参与 Cloud VLM 视觉语言模型服务建设，将图像识别、视觉问答等能力统一接入服务端链路，支持药盒检测、中医舌诊、题目识别、通用视觉问答等业务场景；
            3、基于策略模式设计图像处理框架，结合规则引擎与配置中心实现模型路由、插件选择和策略执行的动态配置；
            4、参与 Robot AIUI 智能对话系统建设，支持音频流接入、会话上下文管理、ASR、意图识别、LLM / Agent、TTS 等模块协同处理；
            5、构建基于 Milvus 的 RAG 语义检索模块，完成 query 向量化、TopK 召回、结果重排和上下文拼接；
            6、参与多模型接入与统一调度，设计模型适配、路由、降级和熔断机制；
            7、通过 gRPC / proto、异步处理、流式响应、链路追踪和 Kubernetes 容器化部署提升系统稳定性和扩展能力。""";
    private static final String JD_GE_TECH_STACK = "Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / proto / JSF / JMQ / Elasticsearch / OSS / Go / Gin / Milvus / Docker / Kubernetes / OpenTelemetry / RAG / Agent";
    private static final InternshipTpl JD_TPL = new InternshipTpl(
            AUDIENCES_ALL,
            null, null, null,
            null, null, null, null,
            null, null, null,
            null, null,
            null, null, null,
            null, null, null,
            null, null, null,
            Map.of(
                    "big_tech", new InternshipTpl.Aud(JD_BT_COMB200, JD_BT_RESP200, JD_BT_RES200, JD_BT_TECH_STACK,
                            JD_BT_OV300_BACKEND, JD_BT_OV300_AI, JD_BT_COMB500_BACKEND, JD_BT_COMB500_AI,
                            JD_BT_COMB1000_BACKEND, JD_BT_COMB1000_AI),
                    "state_owned", new InternshipTpl.Aud(JD_ST_COMB200, JD_ST_RESP200, JD_ST_RES200, JD_ST_TECH_STACK,
                            JD_ST_OV300_BACKEND, JD_ST_OV300_AI, JD_ST_COMB500_BACKEND, JD_ST_COMB500_AI,
                            JD_ST_COMB1000_BACKEND, JD_ST_COMB1000_AI),
                    "bank", new InternshipTpl.Aud(JD_BK_COMB200, JD_BK_RESP200, JD_BK_RES200, JD_BK_TECH_STACK,
                            JD_BK_OV300_BACKEND, JD_BK_OV300_AI, JD_BK_COMB500_BACKEND, JD_BK_COMB500_AI,
                            JD_BK_COMB1000_BACKEND, JD_BK_COMB1000_AI),
                    "general", new InternshipTpl.Aud(JD_GE_COMB200, JD_GE_RESP200, JD_GE_RES200, JD_GE_TECH_STACK,
                            JD_GE_OV300_BACKEND, JD_GE_OV300_AI, JD_GE_COMB500_BACKEND, JD_GE_COMB500_AI,
                            JD_GE_COMB1000_BACKEND, JD_GE_COMB1000_AI)));

    private static final Map<String, InternshipTpl> INTERN_TPL = Map.of(
            "中国工商银行北京市分行移动金融建设部", ICBC_TPL,
            "字节跳动-国际支付", BYTE_TPL,
            "京东集团-京东科技", JD_TPL);

    // ==================== 项目内容模板（通用受众；描述/职责/成果/技术栈 + 合并型四档） ====================
    // key = 项目实体名称；命中时使用模板全文生成内容版本，四受众共用。
    private record ProjectTpl(String ov300, String resp500, String res200, String techStack,
                              String comb200, String comb300, String comb500, String comb1000) {
    }

    // ---------- 项目 1：研发提效一体化交付平台 ----------
    private static final ProjectTpl PROJ_TPL_DEVOPS = new ProjectTpl(
            """
            面向应用构建、跨集群迁移及生产发布场景，独立设计并研发集持续交付、精准出版、自动投验、环境路由切换和任务审计于一体的交付平台。平台围绕 Maven 打包、构建包上传、镜像构建、模板升级、综测出版、生产导入、健康检查和投验通知等流程，减少人工改参、跨平台切换和投产检查遗漏风险，提升交付流程标准化、自动化和可追溯性。""",
            """
            1、完成项目、应用、模板、集群、发布任务、出版任务、投验任务、步骤日志和操作审计等领域建模与 RESTful API 设计；
            2、设计持续交付链路，自动完成 Maven 打包、构建包上传、镜像构建、模板镜像 Tag 更新、滚动升级、Pod 状态查询和 JVM 日志检查；
            3、实现精准出版能力，以综测环境应用/模板为入口，拉取 PaaS 模板、镜像、HA、Apollo 配置，按生产规则预填副本、CPU、内存和镜像 Tag，并完成测试 IP、数据库、Apollo、ETCD、HA 等环境参数映射；
            4、实现自动投验能力，承接出版包完成生产资源导入、Apollo 发布、镜像/模板更新、模板启动、Pod/JVM/ETCD 校验、HA 部署、健康检查和投验平台通知。""",
            """
            形成覆盖“开发构建—模板升级—综测出版—生产导入—健康检查—投验通知”的一体化交付入口，减少人工改参、跨平台切换和投产检查遗漏风险，提升发布投验流程的标准化、可靠性和可追溯性。""",
            "Java / Spring Boot / JPA / Redis / Vue 3 / TypeScript / Vite / PaaS / Harbor / Apollo / ETCD / HAProxy",
            """
            面向应用构建、跨集群迁移及生产发布场景，研发一体化交付平台。技术栈为 Java / Spring Boot / JPA / Redis / Vue 3 / TypeScript / Vite / PaaS / Harbor / Apollo / ETCD / HAProxy。负责领域建模、接口设计、持续交付、精准出版、自动投验和任务审计，提升交付流程标准化与可追溯性。""",
            """
            项目面向应用构建、跨集群迁移及生产发布场景，研发集持续交付、精准出版、自动投验、环境路由切换和任务审计于一体的交付平台。技术栈为 Java / Spring Boot / JPA / Redis / Vue 3 / TypeScript / Vite / PaaS / Harbor / Apollo / ETCD / HAProxy。主要负责项目、应用、模板、发布任务、出版任务、投验任务等领域建模与接口设计，建设 Maven 打包、镜像构建、模板升级、综测出版、生产导入、健康检查和投验通知链路，提升发布投验流程可靠性。""",
            """
            项目面向应用构建、跨集群迁移及生产发布场景，研发集持续交付、精准出版、自动投验、环境路由切换和任务审计于一体的交付平台。技术栈为 Java / Spring Boot / JPA / Redis / Vue 3 / TypeScript / Vite / PaaS / Harbor / Apollo / ETCD / HAProxy。

            主要工作：1、完成项目、应用、模板、集群、发布任务、出版任务、投验任务、步骤日志和操作审计等领域建模与 RESTful API 设计；2、设计持续交付链路，自动完成 Maven 打包、构建包上传、镜像构建、模板镜像 Tag 更新、滚动升级、Pod 状态查询和 JVM 日志检查；3、实现精准出版能力，完成综测资源拉取、生产参数预填和环境参数映射；4、实现自动投验能力，完成生产资源导入、Apollo 发布、模板启动、健康检查和投验通知。项目提升了交付流程的标准化、可靠性和可追溯性。""",
            """
            项目面向应用构建、跨集群迁移及生产发布场景，研发集持续交付、精准出版、自动投验、环境路由切换和任务审计于一体的交付平台，解决原流程中人工打包、跨平台切换、重复改参、状态难追踪和投产检查依赖人工的问题。技术栈为 Java / Spring Boot / JPA / Redis / Vue 3 / TypeScript / Vite / PaaS / Harbor / Apollo / ETCD / HAProxy。

            主要工作：1、完成项目、应用、模板、集群、发布任务、出版任务、投验任务、步骤日志和操作审计等领域建模与 RESTful API 设计；2、设计持续交付链路，自动完成 Maven 打包、构建包上传、镜像构建、模板镜像 Tag 更新、滚动升级、Pod 状态查询和 JVM 日志检查；3、实现精准出版能力，以综测环境应用/模板为入口，拉取 PaaS 模板、镜像、HA、Apollo 配置，按生产规则预填副本、CPU、内存和镜像 Tag，并完成测试 IP、数据库、Apollo、ETCD、HA 等环境参数映射，生成可导入生产的标准出版包；4、实现自动投验能力，承接出版包完成生产资源导入、Apollo 发布、镜像/模板更新、模板启动、Pod/JVM/ETCD 校验、HA 部署、健康检查和投验平台通知，失败时支持问题定位、人工处理和二次健康检查。

            项目形成覆盖“开发构建—模板升级—综测出版—生产导入—健康检查—投验通知”的一体化交付入口，减少人工改参、跨平台切换和投产检查遗漏风险。""");

    // ---------- 项目 2：TikTok Pay 账户治理与 P2P Transfer ----------
    private static final ProjectTpl PROJ_TPL_P2P = new ProjectTpl(
            """
            面向越南区 P2P Transfer 用户间转账场景，建设账号侧状态校验、在途交易治理、账户关闭预检查与用户引导能力，解决多入口、多状态、多系统依赖下账户操作判断分散、重复开发和异常场景处理不一致的问题。项目覆盖待收款、处理中、退款中等在途订单，以及账户开通、KYC、账户冻结、资金账户异常、风控拦截等异常场景。""",
            """
            1、设计并实现转账在途交易统一校验能力，聚合交易系统与钱包产品 SDK 数据源，识别待收款、处理中、退款中等在途订单，并按国家、商户和交易类型维度过滤，避免跨区域历史交易或非目标业务订单造成误拦截；
            2、将在途交易检查接入账户关闭预检查和拦截链路，命中风险项后返回拦截原因、文案 key、跳转链接，前端按协议渲染，实现后端校验与前端展示解耦；
            3、设计账户状态与交易方向匹配规则，针对出金/入金方向判断账户正常、止入、止出状态下的可操作性；
            4、通过 TCC 动态配置不同国家、入口、场景下的检查器、拦截优先级和返回结果；
            5、参与账户冻结/解冻、出金记账码白名单、历史交易补偿和账户状态变更通知链路建设，并通过 Redis 缓存、MQ 广播和业务幂等提升稳定性。""",
            """
            沉淀统一的账户状态校验与交易治理能力，支撑 P2P 转账、账户关闭预检查、交易预检查、账户权限校验等多场景复用，覆盖未开通、KYC 中间态、账户冻结、资金账户异常、风控拦截、存在未完成交易等异常场景。""",
            "Go / Kitex / Thrift IDL / Redis / TCC / MQ / RPC",
            """
            面向越南区 P2P Transfer 用户间转账场景，建设账号侧状态校验、在途交易治理和账户关闭预检查能力。技术栈为 Go / Kitex / Thrift IDL / Redis / TCC / MQ / RPC。负责在途交易校验、状态方向匹配、TCC 规则配置、Redis 缓存和 MQ 幂等处理，提升账户链路稳定性。""",
            """
            项目面向越南区 P2P Transfer 用户间转账场景，建设账号侧状态校验、在途交易治理、账户关闭预检查与用户引导能力。技术栈为 Go / Kitex / Thrift IDL / Redis / TCC / MQ / RPC。主要工作包括：聚合交易系统与钱包产品 SDK 数据源，识别待收款、处理中、退款中等在途订单；将在途交易检查接入账户关闭预检查和拦截链路；设计出金/入金方向下账户正常、止入、止出状态的可操作性规则；通过 TCC 配置、Redis 缓存、MQ 广播和幂等处理提升链路稳定性。""",
            """
            项目面向越南区 P2P Transfer 用户间转账场景，建设账号侧状态校验、在途交易治理、账户关闭预检查与用户引导能力，解决多入口、多状态、多系统依赖下账户操作判断分散、重复开发和异常处理不一致的问题。技术栈为 Go / Kitex / Thrift IDL / Redis / TCC / MQ / RPC。

            主要工作：1、设计并实现转账在途交易统一校验能力，聚合交易系统与钱包产品 SDK 数据源，识别待收款、处理中、退款中等在途订单，并按国家、商户和交易类型过滤；2、将在途交易检查接入账户关闭预检查和拦截链路，命中风险项后返回拦截原因、文案 key 和跳转链接；3、设计账户状态与交易方向匹配规则，针对出金/入金方向判断账户正常、止入、止出状态下的可操作性；4、通过 TCC 配置检查器、拦截优先级和返回结果，并结合 Redis 缓存、MQ 广播和业务幂等提升稳定性。""",
            """
            项目面向越南区 P2P Transfer 用户间转账场景，建设账号侧状态校验、在途交易治理、账户关闭预检查与用户引导能力，解决多入口、多状态、多系统依赖下账户操作判断分散、重复开发和异常场景处理不一致的问题。技术栈为 Go / Kitex / Thrift IDL / Redis / TCC / MQ / RPC。

            主要工作：1、设计并实现转账在途交易统一校验能力，聚合交易系统与钱包产品 SDK 数据源，识别待收款、处理中、退款中等在途订单，并按国家、商户和交易类型维度过滤，避免跨区域历史交易或非目标业务订单造成误拦截；2、将在途交易检查接入账户关闭预检查和拦截链路，命中风险项后返回拦截原因、文案 key、跳转链接，前端按协议渲染，实现后端校验与前端展示解耦；3、设计账户状态与交易方向匹配规则，针对出金/入金方向判断账户正常、止入、止出状态下的可操作性，付款时重点校验出金能力，收款时重点校验入金能力，避免仅按账户异常状态一刀切拦截；4、通过 TCC 动态配置不同国家、入口、场景下的检查器、拦截优先级和返回结果，减少多场景下重复开发成本，并支持策略快速调整与灰度发布；5、参与账户冻结/解冻、出金记账码白名单、历史交易补偿和账户状态变更通知链路建设；对高频账户预检查场景引入短周期 Redis 缓存，降低重复查询压力；账户状态变更事件通过 MQ 广播，消费侧结合业务唯一键进行幂等处理，避免重复消费和状态回退。

            项目沉淀统一的账户状态校验与交易治理能力，支撑 P2P 转账、账户关闭预检查、交易预检查、账户权限校验等多场景复用。""");

    // ---------- 项目 3：钱包用户域多场景开户（BNPL / TTS） ----------
    private static final ProjectTpl PROJ_TPL_BNPL = new ProjectTpl(
            """
            面向 BNPL 先买后付和 TTS 多场景钱包建设统一开户与账户治理框架，将用户注册、KYC、PIN、开户、协议及通知等能力沉淀为可配置流程。项目通过节点编排、TCC 配置、Redis 注册单缓存、MQ 异步补偿和消息幂等机制，降低新增业务场景接入与重复联调成本，提升流程稳定性和用户状态一致性。""",
            """
            1、将 BNPL、TTS 等场景抽象为配置化流程，基于节点编排设计容灾策略与执行分离机制，由 TCC 配置控制执行链路；
            2、采用“同步编排 + 异步重试”模型，核心节点如用户创建、账户开通在主流程中同步执行并支持有限重试，非核心节点如风险通知失败后由 MQ 驱动异步补偿；
            3、BNPL 场景完成从用户注册到账户开通、PIN 设置、协议签署、风险通知的完整流程；TTS 场景接入多种钱包用户创建与账户治理链路；
            4、设计注册单缓存与流程状态共享机制，将流程中间态缓存至 Redis，通过请求参数 + Redis 注册信息合并推进请求流程；
            5、前移身份生成与上下文准备逻辑，并通过下游适配层 / 领域模型层分层隔离风控、KYC、协议中心、用户核心、账户核心等下游依赖。""",
            """
            沉淀多场景开户与账户治理能力，降低新增业务场景接入与重复联调成本；通过 Redis 流程状态缓存、节点级重试、MQ 异步补偿、消息幂等和分层适配机制，支持开户注册流程在页面跳转、异步回调或异常中断后继续推进。""",
            "Go / Kitex / Thrift IDL / Redis / MySQL / TCC / MQ / RPC",
            """
            面向 BNPL 先买后付和 TTS 多场景钱包建设统一开户与账户治理框架。技术栈为 Go / Kitex / Thrift IDL / Redis / MySQL / TCC / MQ / RPC。负责配置化流程、同步编排、异步重试、Redis 注册单缓存、消息通知和 KYC 回调处理，提升开户链接稳定性。""",
            """
            项目面向 BNPL 先买后付和 TTS 多场景钱包建设统一开户与账户治理框架，将用户注册、KYC、PIN、开户、协议及通知等能力沉淀为可配置流程。技术栈为 Go / Kitex / Thrift IDL / Redis / MySQL / TCC / MQ / RPC。主要工作包括：将 BNPL、TTS 抽象为配置化流程；采用“同步编排 + 异步重试”模型；设计注册单缓存与流程状态共享机制；前移身份生成与上下文准备逻辑；接入 KYC 更新、协议签署、账户开通等消息通知。""",
            """
            项目面向 BNPL 先买后付和 TTS 多场景钱包建设统一开户与账户治理框架，将用户注册、KYC、PIN、开户、协议及通知等能力沉淀为可配置流程。技术栈为 Go / Kitex / Thrift IDL / Redis / MySQL / TCC / MQ / RPC。

            主要工作：1、将 BNPL、TTS 等场景抽象为配置化流程，基于节点编排设计容灾策略与执行分离机制，由 TCC 配置控制执行链路；2、采用“同步编排 + 异步重试”模型，核心节点在主流程中同步执行并支持有限重试，非核心节点失败后由 MQ 驱动异步补偿；3、BNPL 完成从用户注册到账户开通、PIN 设置、协议签署、风险通知的完整流程，TTS 接入多种钱包用户创建与账户治理链路；4、设计注册单缓存与流程状态共享机制，将流程中间态缓存至 Redis，通过请求参数 + Redis 注册信息合并推进请求流程；5、接入 KYC 更新、协议签署、账户开通等消息通知，KYC 回调按时间戳排序处理。""",
            """
            项目面向 BNPL 先买后付和 TTS 多场景钱包建设统一开户与账户治理框架，将用户注册、KYC、PIN、开户、协议及通知等能力沉淀为可配置流程。技术栈为 Go / Kitex / Thrift IDL / Redis / MySQL / TCC / MQ / RPC。

            主要工作：1、将 BNPL、TTS 等场景抽象为配置化流程，基于节点编排设计容灾策略与执行分离机制，由 TCC 配置控制执行链路；采用“同步编排 + 异步重试”模型，核心节点如用户创建、账户开通在主流程中同步执行并支持有限重试，非核心节点如风险通知失败后由 MQ 驱动异步补偿，避免非核心节点阻塞主链路；2、BNPL 场景完成从用户注册到账户开通、PIN 设置、协议签署、风险通知的完整流程；TTS 场景接入多种钱包用户创建与账户治理链路，支撑多地区多业务场景；3、设计注册单缓存与流程状态共享机制，将流程中间态缓存至 Redis，通过请求参数 + Redis 注册信息合并推进请求流程，支持手机号等关键字段复用与补齐；4、前移身份生成与上下文准备逻辑，如提前补齐用户身份、国家地区等，保证 KYC URL、协议信息、开户参数等下游依赖在决策阶段正确生成；5、通过下游适配层 / 领域模型层分层隔离风控、KYC、协议中心、用户核心、账户核心等下游依赖，并接入用户创建、KYC 更新、协议签署、账户开通等消息通知；KYC 回调消息按时间戳排序，仅处理最新状态，避免乱序导致状态回退。

            项目沉淀多场景开户与账户治理能力，降低新增业务场景接入与重复联调成本。""");

    // ---------- 项目 4：Cloud IoT（物联网云服务） ----------
    private static final ProjectTpl PROJ_TPL_IOT = new ProjectTpl(
            """
            面向机器人配件与智能设备生态，参与建设 IoT 云端管理平台，提供设备接入、设备管理、状态查询、数据采集、语音控制、场景联动和云云对接等能力，解决多类型设备接入后数据模型不统一、设备状态难管理和事件处理链路分散等问题。项目支撑机器人配件与智能设备的接入、管理、控制和监控。""",
            """
            1、设计“品类-产品-设备”三层数据模型，实现设备注册、绑定解绑、状态查询、批量操作、分页查询和状态快照等全生命周期管理能力；
            2、构建设备控制与数据采集接口体系，通过 gRPC / JSF 对接内部服务和第三方云平台，支撑设备状态查询、控制下发、数据订阅和消息推送；
            3、设计物模型（属性/事件/方法）与 Schema 规范，实现数据标准化；
            4、参与语音控制链路接入，完成意图结果到设备匹配、控制下发与统一返回码处理；
            5、基于事件感知机制实现属性变化触发、规则路由与智能消息下行，支撑场景联动与设备告警处理。""",
            """
            支持设备管理、控制、监控等核心能力；构建 100ms 级事件响应与智能消息推送能力，沉淀统一设备模型、物模型规范和事件感知链路，提升设备事件处理与消息下行的实时性和可维护性。""",
            "Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / JSF / JMQ",
            """
            面向机器人配件与智能设备生态，参与建设 IoT 云端管理平台。技术栈为 Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / JSF / JMQ。负责三层设备模型、设备控制与采集接口、物模型规范、语音控制链路和事件感知机制建设，支撑设备管理和场景联动。""",
            """
            项目面向机器人配件与智能设备生态，参与建设 IoT 云端管理平台，提供设备接入、设备管理、状态查询、数据采集、语音控制、场景联动和云云对接等能力。技术栈为 Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / JSF / JMQ。主要工作包括：设计“品类-产品-设备”三层数据模型；构建设备控制与数据采集接口体系；设计物模型与 Schema 规范；参与语音控制链路接入；基于事件感知机制实现属性变化触发、规则路由与智能消息下行。""",
            """
            项目面向机器人配件与智能设备生态，参与建设 IoT 云端管理平台，提供设备接入、设备管理、状态查询、数据采集、语音控制、场景联动和云云对接等能力，解决多类型设备接入后数据模型不统一、设备状态难管理和事件处理链路分散等问题。技术栈为 Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / JSF / JMQ。

            主要工作：1、设计“品类-产品-设备”三层数据模型，实现设备注册、绑定解绑、状态查询、批量操作、分页查询和状态快照等全生命周期管理能力；2、构建设备控制与数据采集接口体系，通过 gRPC / JSF 对接内部服务和第三方云平台，支撑设备状态查询、控制下发、数据订阅和消息推送；3、设计物模型（属性/事件/方法）与 Schema 规范，实现数据标准化；4、参与语音控制链路接入，完成意图结果到设备匹配、控制下发与统一返回码处理；5、基于事件感知机制实现属性变化触发、规则路由与智能消息下行，支撑场景联动与设备告警处理。""",
            """
            项目面向机器人配件与智能设备生态，参与建设 IoT 云端管理平台，提供设备接入、设备管理、状态查询、数据采集、语音控制、场景联动和云云对接等能力，解决多类型设备接入后数据模型不统一、设备状态难管理和事件处理链路分散等问题。技术栈为 Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / JSF / JMQ。

            主要工作：1、设计“品类-产品-设备”三层数据模型，实现设备注册、绑定解绑、状态查询、批量操作、分页查询和状态快照等全生命周期管理能力，将设备类别、产品能力和具体设备实例分层管理，提升多类型设备接入的一致性；2、构建设备控制与数据采集接口体系，通过 gRPC / JSF 对接内部服务和第三方云平台，支撑设备状态查询、控制下发、数据订阅和消息推送；3、设计物模型（属性/事件/方法）与 Schema 规范，实现设备属性、事件和方法的数据标准化，降低设备差异带来的适配成本；4、参与语音控制链路接入，完成意图结果到设备匹配、控制下发与统一返回码处理；5、基于事件感知机制实现属性变化触发、规则路由与智能消息下行，支撑场景联动与设备告警处理。

            项目支持设备管理、控制、监控等核心能力，构建 100ms 级事件响应与智能消息推送能力。""");

    // ---------- 项目 5：Cloud VLM（视觉语言模型服务） ----------
    private static final ProjectTpl PROJ_TPL_VLM = new ProjectTpl(
            """
            面向机器人视觉交互场景构建多模态视觉处理服务，支持图像识别、视觉问答等能力，落地药盒检测、中医舌诊、题目识别、通用视觉问答等场景。项目将“图片输入 + 文本上下文 + 场景规则 + 模型调用 + 结果返回”抽象为统一服务链路，提升多场景接入效率和视觉能力服务化扩展能力。""",
            """
            1、设计策略模式的图像处理框架，实现多业务场景解耦与动态扩展；
            2、结合规则引擎与配置中心，实现模型路由、插件选择和策略执行的动态配置；
            3、完成 gRPC 接口设计和 proto 协议设计，实现多端（云端、设备端）高效通信；
            4、参与视觉推理服务链路建设，覆盖请求解析、模型调用与结果返回；
            5、实现 JSON Schema 数据校验与多级重试机制，并从模型推理、网络传输与业务处理三个维度拆解系统时延，定位瓶颈并优化；
            6、基于异步处理与流式响应优化系统吞吐能力。""",
            """
            支持多类视觉处理场景稳定运行，新增场景可通过策略扩展和配置路由接入；通过链路拆解、异步处理、流式返回和校验前置等优化，核心接口响应耗时降低 30%+，吞吐量提升 50%。""",
            "Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / Elasticsearch / OSS",
            """
            面向机器人视觉交互场景构建多模态视觉处理服务，支持图像识别、视觉问答等能力。技术栈为 Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / Elasticsearch / OSS。负责策略模式框架、gRPC/proto、JSON Schema 校验、多级重试、异步处理和流式响应，核心接口耗时降低 30%+。""",
            """
            项目面向机器人视觉交互场景构建多模态视觉处理服务，支持图像识别、视觉问答等能力，落地药盒检测、中医舌诊、题目识别、通用视觉问答等场景。技术栈为 Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / Elasticsearch / OSS。主要工作包括：设计策略模式的图像处理框架；结合规则引擎与配置中心实现模型路由、插件选择和策略执行动态配置；完成 gRPC 接口和 proto 协议设计；实现 JSON Schema 校验、多级重试、异步处理和流式响应优化。""",
            """
            项目面向机器人视觉交互场景构建多模态视觉处理服务，支持图像识别、视觉问答等能力，落地药盒检测、中医舌诊、题目识别、通用视觉问答等场景。项目将“图片输入 + 文本上下文 + 场景规则 + 模型调用 + 结果返回”抽象为统一服务链路，提升多场景接入效率。技术栈为 Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / Elasticsearch / OSS。

            主要工作：1、设计策略模式的图像处理框架，实现多业务场景解耦与动态扩展；2、结合规则引擎与配置中心，实现模型路由、插件选择和策略执行的动态配置；3、完成 gRPC 接口设计和 proto 协议设计，实现多端高效通信；4、参与视觉推理服务链路建设，覆盖请求解析、模型调用与结果返回；5、实现 JSON Schema 数据校验与多级重试机制，并从模型推理、网络传输与业务处理三个维度拆解系统时延，定位瓶颈并优化；6、基于异步处理与流式响应优化系统吞吐能力。""",
            """
            项目面向机器人视觉交互场景构建多模态视觉处理服务，支持图像识别、视觉问答等能力，落地药盒检测、中医舌诊、题目识别、通用视觉问答等场景。项目将“图片输入 + 文本上下文 + 场景规则 + 模型调用 + 结果返回”抽象为统一服务链路，提升多场景接入效率。技术栈为 Java / Spring Boot / MyBatis-Plus / MySQL / Redis / gRPC / Elasticsearch / OSS。

            主要工作：1、设计策略模式的图像处理框架，实现多业务场景解耦与动态扩展，将不同视觉场景抽象为策略接口，并通过工厂模式按业务场景路由到对应处理逻辑；2、结合规则引擎与配置中心，实现模型路由、插件选择和策略执行的动态配置，支持新增场景低成本接入；3、完成 gRPC 接口设计和 proto 协议设计，实现云端、设备端高效通信，并统一请求字段、图片内容、文本上下文和响应结构；4、参与视觉推理服务链路建设，覆盖请求解析、模型调用与结果返回；5、实现 JSON Schema 数据校验与多级重试机制，对格式问题和字段缺失提前拦截，对网络波动和下游短暂异常进行有限重试；6、从模型推理、网络传输与业务处理三个维度拆解系统时延，定位瓶颈并优化；7、基于异步处理与流式响应优化系统吞吐能力。

            项目支持多类视觉处理场景稳定运行，新增场景可通过策略扩展和配置路由接入，核心接口响应耗时降低 30%+，吞吐量提升 50%。""");

    // ---------- 项目 6：Robot AIUI（智能对话系统） ----------
    private static final ProjectTpl PROJ_TPL_AIUI = new ProjectTpl(
            """
            基于 Go 构建面向机器人的智能交互平台，集成语音识别、自然语言理解与大模型能力，支撑多轮对话、全双工交互和多模型能力扩展。项目通过云端接入层、对话工作流、Agent 路由、RAG 语义检索、多模型调度和链路追踪能力，支撑高并发对话请求与多模块协同处理。""",
            """
            1、设计云端接入层与对话工作流，接收音频流及 robotId、traceId 等元数据，构建会话上下文并驱动 ASR、意图识别、LLM/Agent、TTS 等模块协同处理；
            2、设计 gRPC / proto 协议与统一通信规范，完成元数据提取、上下文透传与跨模块调用规范设计，保障对话链路数据一致性和可追踪性；
            3、参与插件化 Agent 管理与路由体系建设，基于工厂模式支持天气、新闻、健康、知识问答、IoT 控制等领域能力动态扩展；
            4、构建基于 Milvus 的 RAG 语义检索模块，完成 query 向量化、TopK 召回、结果重排和上下文拼接链路；
            5、参与多模型接入与统一调度，设计模型适配、路由、降级与熔断机制，并完成链路追踪与 Kubernetes 容器化部署。""",
            """
            支撑高并发对话请求与多模块协同处理，系统可用性达 99.9%；完成全双工、Agent 路由、RAG 检索和多模型接入等核心链路建设，支持智能交互场景持续扩展。""",
            "Go / gRPC / Gin / MySQL / Redis / Milvus / Docker / Kubernetes",
            """
            基于 Go 构建面向机器人的智能交互平台，集成语音识别、自然语言理解与大模型能力。技术栈为 Go / gRPC / Gin / MySQL / Redis / Milvus / Docker / Kubernetes。负责云端接入层、对话工作流、gRPC/proto、Agent 路由、RAG 检索、多模型调度、链路追踪和容器化部署。""",
            """
            项目基于 Go 构建面向机器人的智能交互平台，集成语音识别、自然语言理解与大模型能力，支撑多轮对话、全双工交互和多模型能力扩展。技术栈为 Go / gRPC / Gin / MySQL / Redis / Milvus / Docker / Kubernetes。主要工作包括：设计云端接入层与对话工作流，驱动 ASR、意图识别、LLM/Agent、TTS 等模块协同处理；设计 gRPC / proto 协议与统一通信规范；参与插件化 Agent 管理与路由体系建设；构建基于 Milvus 的 RAG 语义检索模块；参与多模型接入、降级熔断、链路追踪与容器化部署。""",
            """
            项目基于 Go 构建面向机器人的智能交互平台，集成语音识别、自然语言理解与大模型能力，支撑多轮对话、全双工交互和多模型能力扩展。技术栈为 Go / gRPC / Gin / MySQL / Redis / Milvus / Docker / Kubernetes。

            主要工作：1、设计云端接入层与对话工作流，接收音频流及 robotId、traceId 等元数据，构建会话上下文并驱动 ASR、意图识别、LLM/Agent、TTS 等模块协同处理；2、设计 gRPC / proto 协议与统一通信规范，完成元数据提取、上下文透传与跨模块调用规范设计，保障对话链路数据一致性和可追踪性；3、参与插件化 Agent 管理与路由体系建设，基于工厂模式支持天气、新闻、健康、知识问答、IoT 控制等领域能力动态扩展；4、构建基于 Milvus 的 RAG 语义检索模块；5、参与多模型接入与统一调度，设计模型适配、路由、降级与熔断机制，并完成链路追踪与 Kubernetes 容器化部署。""",
            """
            项目基于 Go 构建面向机器人的智能交互平台，集成语音识别、自然语言理解与大模型能力，支撑多轮对话、全双工交互和多模型能力扩展。技术栈为 Go / gRPC / Gin / MySQL / Redis / Milvus / Docker / Kubernetes。

            主要工作：1、设计云端接入层与对话工作流，接收音频流及 robotId、traceId 等元数据，构建会话上下文并驱动 ASR、意图识别、LLM/Agent、TTS 等模块协同处理；2、设计 gRPC / proto 协议与统一通信规范，完成元数据提取、上下文透传与跨模块调用规范设计，保障对话链路数据一致性和可追踪性；3、参与插件化 Agent 管理与路由体系建设，基于工厂模式支持天气、新闻、健康、知识问答、IoT 控制等领域能力动态扩展；4、构建基于 Milvus 的 RAG 语义检索模块，完成 query 向量化、TopK 召回、结果重排和上下文拼接链路，支撑知识问答与多轮对话；5、参与多模型接入与统一调度，设计模型适配、路由、降级与熔断机制，并完成链路追踪与 Kubernetes 容器化部署。

            项目支撑高并发对话请求与多模块协同处理，系统可用性达 99.9%；完成全双工、Agent 路由、RAG 检索和多模型接入等核心链路建设，支持智能交互场景持续扩展。""");

    /** 项目内容模板映射：key = 项目实体名称 */
    private static final Map<String, ProjectTpl> PROJECT_TPL = Map.of(
            "研发提效：企业级 DevOps 一体化交付平台", PROJ_TPL_DEVOPS,
            "TikTok Pay 钱包用户域账户治理与 P2P Transfer 能力建设", PROJ_TPL_P2P,
            "钱包用户域多场景开户与账户治理能力建设（BNPL / TTS）", PROJ_TPL_BNPL,
            "Cloud IoT（物联网云服务）", PROJ_TPL_IOT,
            "Cloud VLM（视觉语言模型服务）", PROJ_TPL_VLM,
            "Robot AIUI（智能对话系统）", PROJ_TPL_AIUI);
}