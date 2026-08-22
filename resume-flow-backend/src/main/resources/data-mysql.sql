-- ResumeFlow 示例数据初始化（MySQL 生产环境）
-- Spring Boot 通过 spring.sql.init.platform=mysql 加载本文件（data-{platform}.sql）
-- REPLACE INTO 按主键 upsert，重复启动不会产生重复数据
-- 默认账号: demo / 123456

REPLACE INTO sys_user (id, username, password, email, phone, create_time, update_time, deleted)
VALUES (1, 'demo', '$2a$10$4wlhdIFimkOVkW9YO9ee6OGr8pI94O3YpTx8AA51jA1PsS3EqNQCq', 'demo@resumeflow.com', '13800138000', NOW(), NOW(), FALSE);

REPLACE INTO application_template (id, user_id, name, category, self_evaluation, internship_description, project_description,
                                   career_plan, ai_collaboration, skill_keywords, is_default, create_time, update_time, deleted)
VALUES
    (1, 1, '后端开发版', '后端开发版', '具备扎实 Java 后端工程能力，注重稳定性与可维护性。',
     '有京东、字节与工行实习经历，覆盖高并发、数据服务和业务系统交付。',
     '主导过简历自动填充与AI协作项目，熟悉从需求到上线闭环。',
     '希望深耕后端架构与AI工程结合方向。', '与 AI 协作完成页面字段解析和匹配策略优化，显著提升填写成功率。',
     'Java, Spring Boot, MySQL, Redis, MQ, AI Workflow', TRUE, NOW(), NOW(), FALSE),
    (2, 1, 'AI应用版', 'AI应用版', '具备 AI 工具链实践经验，善于将 AI 能力工程化落地。', NULL, NULL,
     '职业目标是成为懂业务的 AI 应用工程师。', '负责将大模型能力接入业务流程，设计可配置提示词与评估机制。',
     'Prompt, RAG, Agent, Backend', FALSE, NOW(), NOW(), FALSE);

REPLACE INTO user_custom_field (id, user_id, template_id, field_key, field_name, field_type, field_category, field_value,
                                match_keywords, sensitive, enabled, sort_order, create_time, update_time, deleted)
VALUES
    (1, 1, NULL, 'name', '姓名', 'input', '基础信息', '胡雨欣', '["姓名","name","真实姓名"]', FALSE, TRUE, 1, NOW(), NOW(), FALSE),
    (2, 1, NULL, 'phone', '手机号', 'input', '基础信息', '13800138000', '["手机号","手机","电话","联系电话","mobile","phone"]', FALSE, TRUE, 2, NOW(), NOW(), FALSE),
    (3, 1, NULL, 'email', '邮箱', 'input', '基础信息', 'demo@resumeflow.com', '["邮箱","电子邮箱","email","mail"]', FALSE, TRUE, 3, NOW(), NOW(), FALSE),
    (4, 1, NULL, 'school', '学校', 'input', '教育经历', 'XX大学', '["学校","院校","毕业院校","university"]', FALSE, TRUE, 4, NOW(), NOW(), FALSE),
    (5, 1, NULL, 'degree', '学历', 'input', '教育经历', '本科', '["学历","学位","最高学历","degree"]', FALSE, TRUE, 5, NOW(), NOW(), FALSE),
    (6, 1, NULL, 'major', '专业', 'input', '教育经历', '计算机科学与技术', '["专业","major"]', FALSE, TRUE, 6, NOW(), NOW(), FALSE),
    (7, 1, NULL, 'graduationDate', '毕业时间', 'input', '教育经历', '2026-06', '["毕业时间","毕业年份","预计毕业"]', FALSE, TRUE, 7, NOW(), NOW(), FALSE),
    (8, 1, NULL, 'expectedCity', '期望城市', 'input', '基础信息', '上海', '["期望城市","意向城市","工作地点"]', FALSE, TRUE, 8, NOW(), NOW(), FALSE),
    (9, 1, NULL, 'expectedPosition', '期望岗位', 'input', '基础信息', '后端开发工程师', '["期望岗位","应聘岗位","意向岗位"]', FALSE, TRUE, 9, NOW(), NOW(), FALSE),
    (10, 1, NULL, 'selfEvaluation', '自我评价', 'textarea', '开放题', '我具备良好的工程实践能力和跨团队协作能力，能快速理解业务并交付高质量代码。', '["自我评价","个人评价","综合评价","个人优势"]', FALSE, TRUE, 10, NOW(), NOW(), FALSE),
    (11, 1, NULL, 'jdInternship', '京东实习经历', 'textarea', '实习经历', '在京东负责履约系统后端接口开发与性能优化，接口响应时间降低约30%。', '["京东实习","京东经历","实习经历","工作经历"]', FALSE, TRUE, 11, NOW(), NOW(), FALSE),
    (12, 1, NULL, 'byteDanceInternship', '字节实习经历', 'textarea', '实习经历', '在字节参与中台服务治理，完成日志链路优化和告警规则建设。', '["字节实习","字节经历","实习经历","工作经历"]', FALSE, TRUE, 12, NOW(), NOW(), FALSE),
    (13, 1, NULL, 'icbcInternship', '工行实习经历', 'textarea', '实习经历', '在工行金融科技团队参与清算系统需求开发，重构批处理任务提升稳定性。', '["工行实习","工商银行实习","实习经历","金融科技"]', FALSE, TRUE, 13, NOW(), NOW(), FALSE),
    (14, 1, 2, 'aiCollaboration', 'AI协作经历', 'textarea', '开放题', '在 AI 应用项目中，我将大模型用于代码生成、测试用例补全和文档结构化产出。', '["AI协作","人工智能工具","AI工具","AI辅助开发","与AI协作完成的项目"]', FALSE, TRUE, 14, NOW(), NOW(), FALSE),
    (15, 1, NULL, 'careerPlan', '职业规划', 'textarea', '开放题', '短期内成为业务可依赖的后端工程师，中长期向AI工程化与架构方向发展。', '["职业规划","未来规划","职业发展"]', FALSE, TRUE, 15, NOW(), NOW(), FALSE),
    (16, 1, NULL, 'hobby', '兴趣特长', 'textarea', '其他', '热爱技术写作与开源协作，擅长系统化拆解复杂问题。', '["兴趣特长","兴趣爱好","个人特长"]', FALSE, TRUE, 16, NOW(), NOW(), FALSE),
    (17, 1, NULL, 'supplement', '补充信息', 'textarea', '补充信息', '可接受出差，具备较强抗压能力。', '["补充信息","其他信息","备注"]', FALSE, TRUE, 17, NOW(), NOW(), FALSE);

REPLACE INTO answer_material (id, user_id, template_id, title, material_type, content, short_name, word_limit_type, enabled,
                              sort_order, create_time, update_time, deleted)
VALUES
    (1, 1, NULL, '自我评价-200字', 'SELF_EVALUATION', '我具备扎实的后端开发基础，熟悉 Spring Boot、数据库和接口设计，能够快速理解业务并交付稳定代码。', '自我评价200', '200字', TRUE, 1, NOW(), NOW(), FALSE),
    (2, 1, NULL, '职业规划-500字', 'CAREER_PLAN', '未来三年我希望在后端架构和 AI 工程化交叉方向持续深耕，先成为能够独立负责复杂模块交付的工程师，再向技术负责人方向发展。', '职业规划500', '500字', TRUE, 2, NOW(), NOW(), FALSE),
    (3, 1, 2, 'AI协作经历-500字', 'AI_COLLABORATION', '我在项目中将 AI 工具用于需求拆解、代码初稿生成、测试样例补全和文档提炼，通过人工复核确保质量，整体研发效率有明显提升。', 'AI协作500', '500字', TRUE, 3, NOW(), NOW(), FALSE),
    (4, 1, NULL, '为什么选择本公司', 'WHY_COMPANY', '贵司在行业中的技术深度与业务规模都非常有吸引力，我希望在高标准工程环境中持续成长。', '选公司', '200字', TRUE, 4, NOW(), NOW(), FALSE),
    (5, 1, NULL, '补充信息模板', 'SUPPLEMENT', '如有需要，可提供更详细项目文档与代码示例。', '补充', '200字', TRUE, 5, NOW(), NOW(), FALSE);
