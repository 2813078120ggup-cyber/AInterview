# AI 多模态智能模拟面试评测平台：数据库设计（第三阶段）

## 1. 设计约定

- 数据库：MySQL 8.0+，InnoDB，`utf8mb4_0900_ai_ci`。
- 主键统一使用 `BIGINT UNSIGNED`；所有时间使用 `DATETIME`，由应用以 `Asia/Shanghai` 业务时区写入。
- 可软删除的业务表使用 `deleted_at DATETIME NULL`：`NULL` 表示有效记录，禁止以 `0/1` 表示时间型逻辑删除。
- 密码只保存 BCrypt 哈希；令牌只保存不可逆哈希；媒体对象只保存对象存储定位信息。
- 外键保护核心引用完整性；高并发读写表使用与查询路径匹配的复合索引。

## 2. 实体关系图

```mermaid
erDiagram
  USER ||--o{ USER_ROLE : has
  ROLE ||--o{ USER_ROLE : grants
  ROLE ||--o{ ROLE_PERMISSION : contains
  PERMISSION ||--o{ ROLE_PERMISSION : assigned
  QUESTION_CATEGORY ||--o{ QUESTION_BANK : groups
  JOB_POSITION ||--o{ QUESTION_BANK : scopes
  QUESTION_BANK ||--o{ QUESTION : contains
  JOB_POSITION ||--o{ INTERVIEW : targets
  USER ||--o{ INTERVIEW : candidate
  USER ||--o{ INTERVIEW : interviewer
  INTERVIEW ||--o{ INTERVIEW_QUESTION : selects
  QUESTION ||--o{ INTERVIEW_QUESTION : snapshot_source
  INTERVIEW_QUESTION ||--o| INTERVIEW_ANSWER : receives
  MEDIA_FILE ||--o{ INTERVIEW_ANSWER : attaches
  INTERVIEW ||--o| INTERVIEW_RECORDING : records
  INTERVIEW_RECORDING ||--o{ INTERVIEW_RECORDING_SEGMENT : splits
  INTERVIEW_RECORDING ||--o{ INTERVIEW_TIMELINE_EVENT : aligns
  INTERVIEW_QUESTION ||--o{ INTERVIEW_RECORDING_SEGMENT : plays
  INTERVIEW_QUESTION ||--o{ INTERVIEW_TIMELINE_EVENT : marks
  MEDIA_FILE ||--o{ INTERVIEW_RECORDING_SEGMENT : stores
  INTERVIEW ||--o{ AI_SESSION : owns
  AI_SESSION ||--o{ AI_MESSAGE : contains
  INTERVIEW ||--o{ AI_TASK : schedules
  INTERVIEW_QUESTION ||--o{ EVALUATION : evaluated_by
  INTERVIEW ||--o| REPORT : produces
  INTERVIEW ||--o| INTERVIEW_REFLECTION : reflects
  USER ||--o{ INTERVIEW_REFLECTION : writes
  USER ||--o{ REFRESH_TOKEN : authenticates
  USER ||--o{ OPERATION_LOG : operates
```

## 3. 表职责

| 分组 | 表 | 职责 |
| --- | --- | --- |
| 身份与授权 | `user`、`role`、`permission`、`user_role`、`role_permission`、`refresh_token` | 账号、RBAC、刷新令牌 |
| 招聘资产 | `job_position`、`question_category`、`question_bank`、`question` | 岗位能力模型、分类、题库与题目 |
| 面试过程 | `interview`、`interview_question`、`interview_answer`、`media_file`、`interview_recording`、`interview_recording_segment`、`interview_timeline_event` | 排期、题目快照、作答、媒体对象、面试方式、录制分段与时间轴 |
| AI 能力 | `ai_session`、`ai_message`、`ai_task` | 多轮对话、追问、转写、评分、任务重试 |
| 评价与报告 | `evaluation`、`report`、`interview_reflection` | AI/人工评分、报告生成与发布、候选人面试复盘 |
| 审计 | `operation_log` | 管理和敏感操作审计 |

## 4. 数据一致性与索引策略

- `user.username`、`email`、`phone` 唯一；停用用户不可登录，但历史引用保持有效。
- `user_role`、`role_permission` 使用联合主键消除重复授权。
- `interview_question` 同一面试中题目与序号均唯一；`interview_answer` 对每道面试题唯一，实现幂等保存。
- `interview.active_question_index` 保存服务端可恢复的零基当前题号，`progress_updated_at` 记录最近切题时间；倒计时不持久化每秒值，而是由 `started_at + duration` 计算，避免客户端刷新或时钟漂移造成重置。
- `interview_recording` 对 `interview_id` 唯一，保证一场面试只能选定一种方式；语音/视频录制模式下，进度接口只允许当前题答案已保存后顺序前进。
- `interview_recording_segment` 只保存 `media_file` 引用和题目内可回放的时间范围；媒体文件本体不进 MySQL。`recording_id + segment_no` 唯一，时间范围必须满足结束偏移不小于开始偏移。
- `interview_timeline_event` 以相对录制开始的毫秒偏移记录题目、回答、追问和过渡事件，并按 `recording_id + offset_ms + id` 排序。
- `evaluation` 用 `interview_question_id + source + evaluator_id` 约束人工/AI 评价来源；AI 评价的 `evaluator_id` 为 `NULL`。
- 高频路径建立索引：用户状态、题库检索、候选人/面试官排期、面试状态、任务领取、报告发布和审计查询。
- 报告采用一场面试一份主报告约束；报告版本通过 `generation_version` 和任务审计追溯，不复制面试记录。
- `interview_reflection` 对 `interview_id` 唯一；服务端仅允许本场候选人在面试结束后写入。自评分与信心程度使用数据库范围约束，AI 分数从已发布报告动态关联。

## 5. 初始化与迁移策略

完整空库基线由 [V1__baseline_schema.sql](../backend/src/main/resources/db/migration/V1__baseline_schema.sql) 提供，并随后端 JAR 由 Flyway 执行。历史的分离建表、种子数据和替代全量结构脚本已归档，不再作为初始化入口。

现网升级使用 Flyway 版本化迁移：既有非空库以 V8 建基线后按顺序执行 V9 及后续脚本；空库执行 V1 基线后继续执行后续版本。当前最新结构迁移为 V24 算法题目模板对齐，V20–V24 依次覆盖面试心得和算法练习中心。迁移按“新增表 → 新增列/索引 → 数据回填 → 应用切换”的顺序前进，不使用破坏性重建，也不修改已发布脚本。详见 [数据库脚本与 Flyway 迁移指南](database/README.md)。

## 6. 测试数据策略

测试数据脚本将以事务和可重复的固定命名规则生成：5 名管理员、20 名面试官、100 名候选人、1,100 道分领域题目与 100 场关联面试。密码由 Spring Security BCrypt 生成且所有外键按依赖顺序写入。
