package com.resumeflow.init;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeflow.entity.*;
import com.resumeflow.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
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
@Profile("dev")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

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

    @Override
    public void run(String... args) {
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

        if (contentVariantRepository.countByUserIdAndDeletedFalse(userId) > 0) {
            log.info("demo 用户数据已初始化，跳过");
            return;
        }

        log.info("开始初始化 demo 用户简历数据 (userId={})", userId);
        cleanup(userId);
        initProfile(userId);
        initEducation(userId);
        List<InternshipExperience> internships = initInternships(userId);
        List<ProjectExperience> projects = initProjects(userId);
        initAwards(userId);
        initSkills(userId);
        initTemplates(userId);
        Map<String, AnswerMaterial> materials = initMaterials(userId);
        initCustomFields(userId, internships, projects, materials);
        initVariants(userId, internships, projects, materials);
        log.info("demo 用户数据初始化完成：2 段教育经历、3 条实习、6 个项目、4 个模板、内容版本 {} 条",
                contentVariantRepository.countByUserIdAndDeletedFalse(userId));
    }

    /** 旧库升级：清理历史业务数据后重建（保留用户与登录凭证） */
    private void cleanup(Long userId) {
        contentVariantRepository.deleteByUserId(userId);
        customFieldRepository.deleteByUserId(userId);
        materialRepository.deleteByUserId(userId);
        templateRepository.deleteByUserId(userId);
        awardRepository.deleteByUserId(userId);
        skillRepository.deleteByUserId(userId);
        projectRepository.deleteByUserId(userId);
        internshipRepository.deleteByUserId(userId);
        educationRepository.deleteByUserId(userId);
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
        profile.setPoliticalStatus(null);
        profile.setApplicantType("应届毕业生");
        profile.setTargetPosition("AI应用工程师");
        profile.setTargetCity("北京");
        profile.setAcceptOtherCity("是");
        profile.setSchool("北京理工大学");
        profile.setMajor("新一代电子信息技术");
        profile.setDegree("硕士研究生");
        profile.setGraduationDate("2027-06-30");
        profile.setExpectedCity("北京");
        profile.setExpectedPosition("后端开发 / AI应用工程师 / 金融科技");
        profile.setSelfIntroduction(SELF_EVALUATION);
        userProfileRepository.save(profile);
    }

    // ==================== 教育经历 ====================

    private void initEducation(Long userId) {
        EducationExperience master = new EducationExperience();
        master.setUserId(userId);
        master.setSchool("北京理工大学");
        master.setSchoolTags("985、211、双一流");
        master.setDegree("硕士研究生");
        master.setMajor("新一代电子信息技术");
        master.setCollege("集成电路与电子学院");
        master.setStartDate("2024-09-01");
        master.setEndDate("2027-06-30");
        master.setGpa("3.5/4");
        master.setRank("前20%");
        master.setAdvisor("王业亮");
        master.setLab("电工电子国家级实验室教学示范中心");
        master.setResearchDirection("Java后端、分布式系统、云原生");
        master.setThesis("面向无线电池管理系统的监测数据异常检测与纠错方法研究");
        master.setHonors("北京理工大学研究生学业一等奖学金2次");
        master.setIsDefault(true);
        master.setSortOrder(0);
        educationRepository.save(master);

        EducationExperience bachelor = new EducationExperience();
        bachelor.setUserId(userId);
        bachelor.setSchool("河北工业大学");
        bachelor.setSchoolTags("211、双一流");
        bachelor.setDegree("本科");
        bachelor.setMajor("电子信息工程 / 人工智能");
        bachelor.setCollege("电子信息工程学院");
        bachelor.setStartDate("2018-09-01");
        bachelor.setEndDate("2022-06-28");
        bachelor.setGpa("3/4");
        bachelor.setAdvisor("邱波");
        bachelor.setLab("电子与通信工程国家级实验教学示范中心");
        bachelor.setResearchDirection("软硬件协同时序数据处理");
        bachelor.setThesis("LAMOST光谱参数测量模式识别方法对比研究");
        bachelor.setHonors("校学生会优秀部长");
        bachelor.setIsDefault(false);
        bachelor.setSortOrder(1);
        educationRepository.save(bachelor);
    }

    // ==================== 实习经历 ====================

    private record InternshipDef(String company, String department, String position, String start, String end,
                                 String techStack, String raw, String highlights, String shortName) {
    }

    private List<InternshipExperience> initInternships(Long userId) {
        List<InternshipDef> defs = List.of(
                new InternshipDef("中国工商银行北京市分行", "移动金融建设部", "金融科技", "2026-07-01", "2026-08-31",
                        "Java / Spring Boot / JPA / Redis / Vue3 / TypeScript / Vite / PaaS / Harbor / Apollo / ETCD / HAProxy",
                        "参与研发企业级 DevOps 一体化交付平台，围绕持续交付、精准出版、自动投验、生产发布、环境路由切换、任务审计及存量系统跨集群批量迁移等场景，负责后端领域建模、接口设计、基础设施客户端封装及发布任务可靠性建设，推动交付流程标准化、自动化与可追溯。",
                        "形成统一一体化交付入口，通过异步任务持久化、Redis 发布互斥、熔断、失败恢复和操作审计机制，减少多平台切换与人工操作成本，提升发布任务可靠性与可追溯性。",
                        "工行"),
                new InternshipDef("字节跳动", "国际支付", "AI 应用后端开发实习生", "2026-05-08", "2026-07-03",
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
            result.add(internshipRepository.save(entity));
        }
        return result;
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
            entity.setDescription(def.intro + def.responsibilities + def.result);
            entity.setShortName(def.shortName);
            entity.setIsDefault(order == 0);
            entity.setSortOrder(order++);
            result.add(projectRepository.save(entity));
        }
        return result;
    }

    // ==================== 奖项与技能 ====================

    private void initAwards(Long userId) {
        String[][] awards = {
                {"北京理工大学研究生学业一等奖学金", "奖学金", "2024"},
                {"北京理工大学研究生学业一等奖学金", "奖学金", "2025"},
                {"实用新型专利《一种电池及电池内部芯片保护装置》", "专利", "2025"},
                {"河北工业大学校学生会优秀部长", "校园荣誉", "2019"},
        };
        int order = 0;
        for (String[] award : awards) {
            AwardCertificate entity = new AwardCertificate();
            entity.setUserId(userId);
            entity.setAwardName(award[0]);
            entity.setAwardType(award[1]);
            entity.setAwardYear(award[2]);
            entity.setSortOrder(order++);
            awardRepository.save(entity);
        }
    }

    private void initSkills(Long userId) {
        String[][] skills = {
                {"开发语言", "Java、Go、C++、JavaScript"},
                {"后端开发", "Spring Boot、MyBatis-Plus、Kitex、Gin、RESTful API、gRPC、Proto"},
                {"数据库与中间件", "MySQL、Redis、Elasticsearch、Milvus、OSS"},
                {"分布式与稳定性", "RPC 调用、配置中心、异步消息、重试补偿、幂等控制、链路日志、异常处理、服务解耦"},
                {"AI 相关", "语音识别、语义理解、多模态模型、向量检索、RAG、Agent、多模型接入"},
                {"AI 工具", "Claude Opus 4.8、Codex、Cursor、GPT-5.6 sol"},
        };
        int order = 0;
        for (String[] skill : skills) {
            SkillProfile entity = new SkillProfile();
            entity.setUserId(userId);
            entity.setCategory(skill[0]);
            entity.setSkillName(skill[1]);
            entity.setSortOrder(order++);
            skillRepository.save(entity);
        }
    }

    // ==================== 岗位模板 ====================

    private void initTemplates(Long userId) {
        String[][] templates = {
                // name, audienceType, category, description, isDefault
                {"大厂互联网版", "big_tech", "大厂", "适用：腾讯、字节、阿里、美团、京东、小红书等。优先内容：京东 AI 工程化、字节支付后端、系统性能优化、AI 协作经历。语言风格：技术复杂度、工程效率、性能优化、业务规模、快速迭代。", "true"},
                {"国央企版", "state_owned", "国央企", "适用：航天院所、央企、国企、研究所、事业单位。优先内容：工行交付平台、京东 IoT/智能系统、科研经历、专利、奖学金。语言风格：稳定可靠、标准规范、系统工程、协同落地、安全可控。", "false"},
                {"银行金融科技版", "bank", "银行", "适用：银行、券商、基金、信托、金融科技公司。优先内容：工行金融科技、字节国际支付、账户治理、资产安全、发布审计。语言风格：风险控制、系统稳定、数据一致性、流程规范、审计留痕。", "false"},
                {"通用后端开发版", "general_backend", "通用", "适用：普通后端开发岗位。优先内容：Java、Go、Spring Boot、Redis、MySQL、gRPC、MQ、项目落地。语言风格：技术栈清晰、职责明确、结果可量化。", "false"},
        };
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
            entity.setSkillKeywords("Java / Go / Spring Boot / Redis / MySQL / gRPC / MQ / Kubernetes / RAG / Agent");
            templateRepository.save(entity);
        }
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

        // ---- 基础信息 ----
        fields.add(field(userId, "name", "姓名", "input", "基础信息", "胡宇欣",
                List.of("姓名", "真实姓名", "full name", "name"), null));
        fields.add(field(userId, "gender", "性别", "input", "基础信息", "女",
                List.of("性别", "gender"), null));
        fields.add(field(userId, "phone", "手机号", "input", "基础信息", "18813108802",
                List.of("手机", "手机号", "电话", "联系方式", "mobile", "phone", "tel"), null));
        fields.add(field(userId, "email", "邮箱", "input", "基础信息", "m15128278966@163.com",
                List.of("邮箱", "电子邮箱", "email", "mail"), null));
        fields.add(field(userId, "qq", "QQ", "input", "基础信息", "2318402884",
                List.of("QQ", "qq号"), null));
        fields.add(field(userId, "wechat", "微信", "input", "基础信息", "15128278966",
                List.of("微信", "微信号", "wechat"), null));
        fields.add(field(userId, "current_location", "当前所在地", "input", "基础信息", "中国大陆 / 北京 / 北京市",
                List.of("当前所在地", "现居住地", "所在地", "current location"), null));
        fields.add(field(userId, "expected_city", "期望城市", "input", "基础信息", "北京",
                List.of("期望城市", "意向城市", "工作城市", "expected city"), null));
        fields.add(field(userId, "expected_position", "期望岗位", "input", "基础信息", "后端开发 / AI应用工程师 / 金融科技",
                List.of("期望岗位", "投递岗位", "应聘岗位", "position", "job title"), null));
        fields.add(field(userId, "political_status", "政治面貌", "input", "基础信息", "",
                List.of("政治面貌", "政治身份"), null));
        // 敏感类字段按要求设置为可自动填写（sensitive=false），值可后续在后台补充
        fields.add(field(userId, "id_card", "身份证号", "input", "基础信息", "",
                List.of("身份证", "身份证号", "证件号", "id card"), null));
        fields.add(field(userId, "emergency_contact", "紧急联系人", "input", "基础信息", "",
                List.of("紧急联系人", "紧急联系人姓名"), null));
        fields.add(field(userId, "emergency_phone", "紧急联系人电话", "input", "基础信息", "",
                List.of("紧急联系人电话", "紧急联系电话"), null));
        fields.add(field(userId, "reference_phone", "证明人电话", "input", "基础信息", "",
                List.of("证明人电话", "证明人联系方式", "推荐人电话"), null));
        fields.add(field(userId, "bank_card", "银行卡号", "input", "基础信息", "",
                List.of("银行卡", "银行卡号", "银行账号"), null));
        fields.add(field(userId, "family_members", "家庭成员", "textarea", "基础信息", "",
                List.of("家庭成员", "家庭情况"), null));

        // ---- 应聘信息 ----
        fields.add(field(userId, "applicant_type", "应聘类型", "input", "应聘信息", "应届毕业生",
                List.of("应届生", "应届毕业生", "应聘类型", "毕业生类别"), null));
        fields.add(field(userId, "target_position", "目标岗位", "input", "应聘信息", "AI应用工程师",
                List.of("目标岗位", "意向岗位", "应聘岗位"), null));
        fields.add(field(userId, "target_city", "目标城市", "input", "应聘信息", "北京",
                List.of("目标城市", "意向工作城市"), null));
        fields.add(field(userId, "accept_other_city", "是否接受其他城市", "input", "应聘信息", "是",
                List.of("接受其他城市", "是否接受调剂", "接受工作地点调剂"), null));

        // ---- 教育经历 ----
        fields.add(field(userId, "graduate_school", "毕业院校", "input", "教育经历", "北京理工大学",
                List.of("毕业院校", "最高学历学校", "学校", "院校", "university", "college", "school"), null));
        fields.add(field(userId, "graduate_major", "专业", "input", "教育经历", "新一代电子信息技术",
                List.of("专业", "所学专业", "major"), null));
        fields.add(field(userId, "graduate_degree", "学历", "input", "教育经历", "硕士研究生",
                List.of("学历", "学位", "degree", "education"), null));
        fields.add(field(userId, "graduation_date", "毕业时间", "input", "教育经历", "2027-06-30",
                List.of("毕业时间", "毕业年份", "graduation", "graduate date"), null));
        fields.add(field(userId, "gpa", "GPA", "input", "教育经历", "3.5/4",
                List.of("GPA", "绩点"), null));
        fields.add(field(userId, "grade_rank", "成绩排名", "input", "教育经历", "前20%",
                List.of("成绩排名", "排名"), null));
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
                HOBBY, List.of("兴趣特长", "爱好特长", "个人特长"),
                ref(materials.get("HOBBY"))));
        fields.add(field(userId, "additional_info", "补充信息", "textarea", "开放题",
                "", List.of("补充信息", "其他信息", "其他相关信息"), null));
        fields.add(field(userId, "why_company", "为什么选择本公司", "textarea", "开放题",
                WHY_COMPANY, List.of("为什么选择本公司", "选择我们的原因", "为什么投递本公司"),
                ref(materials.get("WHY_COMPANY"))));
        fields.add(field(userId, "why_position", "为什么选择该岗位", "textarea", "开放题",
                WHY_POSITION, List.of("为什么选择该岗位", "岗位理解", "应聘原因"),
                ref(materials.get("WHY_POSITION"))));

        for (UserCustomField f : fields) {
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

    private static final List<String> AUDIENCES = List.of("general", "big_tech", "state_owned", "bank");
    private static final List<String> LENGTHS = List.of("within_200", "within_300", "within_500", "within_1000");

    private void initVariants(Long userId,
                              List<InternshipExperience> internships,
                              List<ProjectExperience> projects,
                              Map<String, AnswerMaterial> materials) {
        // 实习经历 3 × 16 版本
        for (InternshipExperience internship : internships) {
            for (String audience : AUDIENCES) {
                String full = composeInternship(audience, internship);
                for (String length : LENGTHS) {
                    saveVariant(userId, "internship", internship.getId(), audience, length, truncate(full, limitOf(length)));
                }
            }
        }
        // 项目经历 6 × 16 版本
        for (ProjectExperience project : projects) {
            for (String audience : AUDIENCES) {
                String full = composeProject(audience, project);
                for (String length : LENGTHS) {
                    saveVariant(userId, "project", project.getId(), audience, length, truncate(full, limitOf(length)));
                }
            }
        }
        // 开放题素材 4 受众 × 4 长度版本
        for (AnswerMaterial material : materials.values()) {
            for (String audience : AUDIENCES) {
                String full = audiencePrefix(audience) + material.getContent();
                for (String length : LENGTHS) {
                    saveVariant(userId, "material", material.getId(), audience, length, truncate(full, limitOf(length)));
                }
            }
        }
    }

    private void saveVariant(Long userId, String sourceType, Long sourceId, String audience, String length, String content) {
        ContentVariant variant = new ContentVariant();
        variant.setUserId(userId);
        variant.setSourceType(sourceType);
        variant.setSourceId(sourceId);
        variant.setAudienceType(audience);
        variant.setLengthType(length);
        variant.setContent(content);
        variant.setEnabled(true);
        contentVariantRepository.save(variant);
    }

    /** 实习内容按受众组合：大厂突出技术栈与复杂度，国央企/银行先讲成果与可靠性 */
    private String composeInternship(String audience, InternshipExperience internship) {
        String intro = nz(internship.getDescription());
        String highlight = nz(internship.getHighlights());
        String tech = hasText(internship.getTechStack()) ? "技术栈：" + internship.getTechStack() + "。" : "";
        return switch (audience) {
            case "state_owned" -> PREFIX_STATE_OWNED + intro + highlight + tech;
            case "bank" -> PREFIX_BANK + intro + highlight + tech;
            case "big_tech" -> intro + tech + highlight;
            default -> intro + highlight;
        };
    }

    /** 项目内容按受众组合 */
    private String composeProject(String audience, ProjectExperience project) {
        String intro = nz(project.getProjectIntro());
        String responsibilities = nz(project.getResponsibilities());
        String result = nz(project.getResult());
        String tech = hasText(project.getTechStack()) ? "技术栈：" + project.getTechStack() + "。" : "";
        return switch (audience) {
            case "state_owned" -> PREFIX_STATE_OWNED + intro + result + "主要职责：" + responsibilities;
            case "bank" -> PREFIX_BANK + intro + result + "主要职责：" + responsibilities;
            case "big_tech" -> intro + tech + "主要职责：" + responsibilities + result;
            default -> intro + "主要职责：" + responsibilities + result;
        };
    }

    private String audiencePrefix(String audience) {
        return switch (audience) {
            case "state_owned" -> PREFIX_STATE_OWNED;
            case "bank" -> PREFIX_BANK;
            default -> "";
        };
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
}
