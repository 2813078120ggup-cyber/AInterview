export const mockJob = {
  title: 'Java 后端开发工程师',
  salary: '15K - 25K',
  location: '北京',
  experience: '1-3 年',
  skills: ['Java', 'Spring Boot', 'MySQL', 'Redis', 'Docker', 'Spring Cloud'],
}

export const mockCandidate = {
  name: '张三',
  school: '太原理工大学',
  major: '软件工程',
  skills: ['Java', 'Spring Boot', 'Spring Security', 'MySQL', 'Redis', 'Docker'],
}

export const mockMatch = {
  score: 86,
  matchedSkills: ['Java', 'Spring Boot', 'Redis', 'MySQL', 'Docker'],
  missingSkills: ['消息队列', 'Spring Cloud 实战'],
  risk: '高并发场景经验需进一步核实',
}

export const mockResumeAnalysis = {
  strengths: ['具备完整 Spring Boot 项目经验', '简历记录 Redis 实际应用', '具备 Docker 部署实践'],
  risks: ['消息队列（MQ）相关经验需进一步核实', '分布式系统实践深度需进一步核实'],
  topics: ['哈希表（HashMap）', 'Redis 缓存一致性', 'MySQL 索引', '网关（Gateway）', 'Docker'],
}

export const mockInterview = {
  round: '第 4 / 10 轮',
  question: '你在项目中使用了 Redis，如果缓存和数据库出现不一致，你会怎么处理？',
  answer: '我会采用旁路缓存（Cache Aside）模式，更新数据库后删除缓存，并通过重试和过期时间降低短暂不一致的影响。',
  followUp: '如果删除缓存失败，你会如何保证最终一致性？',
}

export const mockReport = {
  overall: 85,
  match: 86,
  dimensions: [
    ['Java 基础', 90],
    ['Spring', 88],
    ['MySQL', 82],
    ['Redis', 86],
    ['项目经验', 91],
    ['系统设计', 74],
    ['沟通表达', 83],
  ] as Array<[string, number]>,
  strengths: '能够结合项目场景说明缓存策略与部署流程。',
  weaknesses: '消息队列与分布式系统设计经验仍需通过项目细节进一步核实。',
  conclusion: '建议进入企业复核，重点追问高并发和故障恢复经验。',
}

export const mockApplications = [
  { name: '张三', role: 'Java 后端开发', match: 86, interview: 85, status: '企业评估中', tone: 'warning' as const },
  { name: '王五', role: 'Java 后端开发', match: 91, interview: 89, status: '企业评估中', tone: 'success' as const },
  { name: '赵六', role: '后端研发', match: 72, interview: 68, status: '未通过', tone: 'danger' as const },
]

export const mockInvitation = {
  date: '2026-08-15 14:00',
  location: '北京市海淀区中关村软件园',
  contact: '张经理',
  note: '请提前 15 分钟到达。',
}

export const recruitmentWorkflow = [
  ['浏览', '岗位浏览'],
  ['投递', '提交申请'],
  ['匹配', '匹配评估'],
  ['面试', '智能面试'],
  ['复核', '企业复核'],
  ['安排', '线下面试'],
  ['结果', '录用结果'],
] as Array<[string, string]>
