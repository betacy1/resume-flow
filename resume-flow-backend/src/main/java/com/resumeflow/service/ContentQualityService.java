package com.resumeflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容质量检查：保存内容前的基础校验。
 * 空内容由调用方阻断；超字数、英文技术词大小写错误仅返回提醒，不阻止保存。
 */
public final class ContentQualityService {

    private ContentQualityService() {
    }

    /** 常见英文技术词的标准写法（检查大小写是否明显错误） */
    private static final Map<String, String> TECH_TERMS = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("javascript", "JavaScript"),
            Map.entry("typescript", "TypeScript"), Map.entry("spring boot", "Spring Boot"),
            Map.entry("springboot", "Spring Boot"), Map.entry("spring cloud", "Spring Cloud"),
            Map.entry("spring", "Spring"), Map.entry("mybatis", "MyBatis"),
            Map.entry("mysql", "MySQL"), Map.entry("redis", "Redis"),
            Map.entry("kafka", "Kafka"), Map.entry("rabbitmq", "RabbitMQ"),
            Map.entry("rocketmq", "RocketMQ"), Map.entry("docker", "Docker"),
            Map.entry("kubernetes", "Kubernetes"), Map.entry("nginx", "Nginx"),
            Map.entry("linux", "Linux"), Map.entry("maven", "Maven"),
            Map.entry("gradle", "Gradle"), Map.entry("vue", "Vue"),
            Map.entry("react", "React"), Map.entry("angular", "Angular"),
            Map.entry("python", "Python"), Map.entry("golang", "Go"),
            Map.entry("elasticsearch", "Elasticsearch"), Map.entry("mongodb", "MongoDB"),
            Map.entry("postgresql", "PostgreSQL"), Map.entry("oracle", "Oracle"),
            Map.entry("grpc", "gRPC"), Map.entry("dubbo", "Dubbo"),
            Map.entry("zookeeper", "ZooKeeper"), Map.entry("github", "GitHub"),
            Map.entry("gitlab", "GitLab"), Map.entry("wechat", "WeChat"));

    /** 字数档位上限 */
    private static final Map<String, Integer> LENGTH_LIMIT = Map.of(
            "within_100", 100, "within_200", 200, "within_300", 300,
            "within_500", 500, "within_1000", 1000);

    /**
     * 检查内容并返回提醒列表（中文、英文、数字、空格、标点均计入字数）
     */
    public static List<String> check(String content, String lengthType) {
        List<String> warnings = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return warnings;
        }
        Integer limit = lengthType == null ? null : LENGTH_LIMIT.get(lengthType.trim());
        if (limit != null && content.length() > limit) {
            warnings.add(String.format("内容共 %d 字，超过 %s 档位上限 %d 字", content.length(), lengthType, limit));
        }
        for (Map.Entry<String, String> term : TECH_TERMS.entrySet()) {
            String canonical = term.getValue();
            Pattern p = Pattern.compile("(?<![A-Za-z])" + Pattern.quote(term.getKey()) + "(?![A-Za-z])",
                    Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(content);
            if (m.find() && !m.group().equals(canonical)) {
                warnings.add(String.format("英文技术词大小写可能有误：发现“%s”，建议写作“%s”", m.group(), canonical));
                if (warnings.size() >= 3) {
                    break;
                }
            }
        }
        return warnings;
    }
}
