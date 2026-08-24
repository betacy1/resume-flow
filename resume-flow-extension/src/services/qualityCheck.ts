/**
 * 内容质量检查（插件侧）：保存内容前的基础校验。
 * 空内容由调用方阻断；超字数、英文技术词大小写错误仅提醒，不阻止保存。
 * 字符统计：中文、英文、数字、空格、标点均计入（即字符串长度）。
 */

/** 常见英文技术词的标准写法 */
const TECH_TERMS: Array<[string, string]> = [
  ['javascript', 'JavaScript'], ['typescript', 'TypeScript'],
  ['spring boot', 'Spring Boot'], ['springboot', 'Spring Boot'],
  ['spring cloud', 'Spring Cloud'], ['mybatis', 'MyBatis'],
  ['mysql', 'MySQL'], ['redis', 'Redis'], ['kafka', 'Kafka'],
  ['rabbitmq', 'RabbitMQ'], ['rocketmq', 'RocketMQ'],
  ['docker', 'Docker'], ['kubernetes', 'Kubernetes'], ['nginx', 'Nginx'],
  ['linux', 'Linux'], ['maven', 'Maven'], ['gradle', 'Gradle'],
  ['python', 'Python'], ['elasticsearch', 'Elasticsearch'],
  ['mongodb', 'MongoDB'], ['postgresql', 'PostgreSQL'], ['grpc', 'gRPC'],
  ['zookeeper', 'ZooKeeper'], ['github', 'GitHub'], ['gitlab', 'GitLab'],
  ['java', 'Java'], ['spring', 'Spring'], ['vue', 'Vue'], ['react', 'React'],
];

const LENGTH_LIMIT: Record<string, number> = {
  within_100: 100, within_200: 200, within_300: 300, within_500: 500, within_1000: 1000,
};

export function charCount(text: string): number {
  return Array.from(text || '').length;
}

export function checkContent(content: string, lengthType?: string): string[] {
  const warnings: string[] = [];
  if (!content || !content.trim()) return warnings;

  const limit = lengthType ? LENGTH_LIMIT[lengthType] : undefined;
  if (limit && content.length > limit) {
    warnings.push(`内容共 ${content.length} 字，超过 ${lengthType} 档位上限 ${limit} 字`);
  }
  for (const [lower, canonical] of TECH_TERMS) {
    const re = new RegExp(`(?<![A-Za-z])${lower.replace(/ /g, '\\s*')}(?![A-Za-z])`, 'i');
    const m = content.match(re);
    if (m && m[0] !== canonical) {
      warnings.push(`英文技术词大小写可能有误：发现“${m[0]}”，建议写作“${canonical}”`);
      if (warnings.length >= 3) break;
    }
  }
  return warnings;
}
