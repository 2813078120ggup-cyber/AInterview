# AI 多模态智能模拟面试评测平台：接口设计（第四阶段）

## 1. 全局约定

- Base URL：`/api/v1`；JSON 使用 `application/json; charset=utf-8`。
- 认证：除认证、健康检查和受控上传回调外，全部使用 `Authorization: Bearer <accessToken>`。
- OpenAPI：`/api/swagger-ui/index.html`；生产环境应通过网络边界或管理员策略限制访问。
- 资源 ID 为无符号长整型，日期时间为 ISO-8601（例如 `2026-07-23T14:30:00+08:00`）。

### 统一返回

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "01J...",
  "timestamp": "2026-07-23T14:30:00+08:00"
}
```

### 分页返回

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "pageNo": 1,
    "pageSize": 20
  },
  "requestId": "01J...",
  "timestamp": "2026-07-23T14:30:00+08:00"
}
```

分页参数统一为 `pageNo`（默认 1）和 `pageSize`（默认 20，最大 100）。

### 错误码

| HTTP | code | 含义 |
| --- | --- | --- |
| 400 | 40001 | 参数校验失败 |
| 401 | 40101 | 未认证、令牌无效或已过期 |
| 403 | 40301 | 无权限或不属于该业务资源 |
| 404 | 40401 | 资源不存在 |
| 409 | 40901 | 重复操作或状态冲突 |
| 422 | 42201 | 业务规则不满足 |
| 429 | 42901 | 触发限流 |
| 500 | 50000 | 未预期系统异常 |
| 502 | 50201 | 外部 AI / 存储服务异常 |

## 2. 接口分组

### 2.1 认证与个人中心（公开接口除外）

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| POST | `/auth/register` | 公开 | 注册候选人账号 |
| POST | `/auth/login` | 公开 | 用户名密码登录，返回访问/刷新令牌与用户信息 |
| POST | `/auth/refresh` | 刷新令牌 | 轮换刷新令牌并获取新访问令牌 |
| POST | `/auth/logout` | 已登录 | 吊销当前刷新令牌 |
| GET | `/auth/me` | 已登录 | 当前用户与角色/权限摘要 |
| PUT | `/auth/me/password` | 已登录 | 修改密码并使旧刷新令牌失效 |

### 2.2 用户、角色与岗位管理

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET/POST | `/users` | ADMIN | 分页查询 / 新建用户 |
| GET/PUT | `/users/{id}` | ADMIN 或本人受限字段 | 用户详情 / 更新 |
| PATCH | `/users/{id}/status` | ADMIN | 启用或停用 |
| PUT | `/users/{id}/roles` | ADMIN | 覆盖式分配角色 |
| GET/POST | `/roles` | ADMIN | 角色列表 / 新建角色 |
| GET/PUT | `/roles/{id}` | ADMIN | 角色详情 / 更新 |
| PUT | `/roles/{id}/permissions` | ADMIN | 分配细粒度权限 |
| GET | `/permissions` | ADMIN | 权限字典 |
| GET/POST | `/positions` | ADMIN, HR | 岗位分页 / 新建岗位 |
| GET/PUT/DELETE | `/positions/{id}` | ADMIN, HR | 岗位详情 / 更新 / 软删除 |

### 2.3 题库与题目

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET/POST | `/question-banks/categories/tree`、`/question-banks/categories` | ADMIN, HR, INTERVIEWER | 分类树 / 新建分类 |
| GET/PUT/DELETE | `/question-banks/categories/{id}` | ADMIN, HR, INTERVIEWER | 分类详情 / 更新 / 软删除分类 |
| GET/POST | `/question-banks` | ADMIN, HR, INTERVIEWER | 题库分页 / 新建题库 |
| GET/PUT/DELETE | `/question-banks/{id}` | ADMIN, HR, INTERVIEWER | 详情 / 更新 / 软删除 |
| GET/POST | `/question-banks/{id}/questions` | ADMIN, HR, INTERVIEWER | 题目分页 / 新增题目 |
| GET/PUT/DELETE | `/question-banks/{bankId}/questions/{id}` | ADMIN, HR, INTERVIEWER | 题目详情 / 更新 / 软删除 |
| POST | `/question-banks/{id}/ai-generation` | ADMIN, HR, INTERVIEWER | 创建 AI 出题异步任务 |

### 2.4 面试与作答

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/interviews/page` | 已登录 | 按状态、参与人、岗位和时间分页查询；非管理者仅本人数据 |
| GET/POST | `/interviews` | 参与者查询 / ADMIN、HR 创建 | 面试分页 / 创建排期 |
| GET | `/interviews/{id}` | 参与者或管理者 | 面试详情 |
| PUT | `/interviews/{id}/schedule` | ADMIN, HR | 改期 |
| POST | `/interviews/{id}/cancel` | ADMIN, HR | 取消待开始面试 |
| POST | `/interviews/{id}/start` | 指定 INTERVIEWER | 开始面试 |
| POST | `/interviews/{id}/end` | 指定 INTERVIEWER | 结束面试并触发评价任务 |
| GET | `/interviews/{id}/questions` | 参与者或管理者 | 题目快照及顺序 |
| GET | `/interviews/{id}/answers` | 参与者或管理者 | 已保存作答 |
| PUT | `/interviews/{id}/questions/{interviewQuestionId}/answer` | 指定 CANDIDATE | 幂等保存文字/结构化答案 |
| GET | `/interviews/history` | CANDIDATE | 本人历史面试分页 |
| GET/PUT | `/interviews/{id}/progress` | 参与者读取 / 指定 CANDIDATE 更新 | 恢复服务端当前题号、倒计时与追问任务；录制模式强制顺序前进 |

### 2.4.1 面试方式、录制与时间轴（V19）

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/interviews/{id}/recording` | 面试参与者或 ADMIN | 查询面试方式、录制分段和时间轴 |
| POST | `/interviews/{id}/recording/select` | 指定 CANDIDATE | 一次性选择 `TEXT`、`AUDIO` 或 `VIDEO` |
| POST | `/interviews/{id}/recording/events` | 指定 CANDIDATE | 保存题目、回答、追问、过渡和录制状态的毫秒时间轴事件 |
| POST multipart | `/interviews/{id}/recording/segments` | 指定 CANDIDATE | 上传按题 WebM 分段及开始/结束偏移 |
| POST | `/interviews/{id}/recording/complete` | 指定 CANDIDATE | 标记录制会话完成；正常结束面试时服务端也会自动完成 |
| GET | `/interviews/{id}/recording/segments/{segmentId}/content` | 面试参与者或 ADMIN | 鉴权读取属于本场面试的音视频分段 |

### 2.4.2 候选人面试心得（V20）

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/interviews/{id}/reflection` | 本场 CANDIDATE | 获取本人对该场面试的心得；未记录时返回空数据 |
| PUT | `/interviews/{id}/reflection` | 本场 CANDIDATE | 在面试结束后新增或更新心得，一场面试仅保留一份 |
| GET | `/reflections/my/summary` | CANDIDATE | 获取心得数量、平均自评分、平均信心程度、已发布 AI 平均分和成长趋势 |

写入接口仅接受已结束、报告生成中、报告已生成、已通过或未通过的面试。服务端同时校验候选人归属，禁止候选人读取或修改他人的心得。

### 2.5 媒体与 AI 交互

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| POST multipart | `/media` | 已登录 | 校验大小、MIME、文件签名、SHA-256 和可选 ClamAV 后保存媒体元数据与私有文件 |
| GET | `/media/{id}`、`/media/{id}/content` | 所有者 | 查询媒体元数据 / 获取媒体内容 |
| POST | `/interviews/{id}/follow-ups` | 面试参与者 | 使用当前启用的 DeepSeek Provider 创建 AI 追问任务 |
| GET | `/ai-tasks/{id}` | 创建者或管理者 | 查询持久化 AI 任务状态与结果 |
| POST | `/interviews/{id}/ai-opening` | 面试参与者 | 创建面试开场问题/提示任务 |
| GET | `/interviews/{id}/evaluation-task` | 面试参与者或 ADMIN | 查询报告评分任务状态 |
| POST | `/interviews/{id}/evaluation-task/retry` | 有权用户 | 重试失败的评分任务 |
| POST | `/interviews/{id}/evaluation-task/regenerate` | ADMIN | 重新生成已完成面试报告 |

### 2.6 评价、报告与统计

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/interviews/{id}/evaluations` | 面试官、管理者 | 获取题目评价 |
| PUT | `/interviews/{id}/evaluations/{interviewQuestionId}` | 指定 INTERVIEWER | 保存人工评分/复核 |
| POST | `/interviews/{id}/evaluations/ai` | 面试官、管理者 | 手动触发 AI 评分任务 |
| GET | `/interviews/{id}/report` | 参与者或管理者；候选人仅发布 | 获取报告 |
| POST | `/interviews/{id}/report/generate` | 面试官、管理者 | 创建报告生成任务 |
| POST | `/interviews/{id}/report/publish` | 面试官、管理者 | 发布报告 |
| POST | `/interviews/{id}/report/pdf` | 面试官、管理者 | 创建 PDF 导出任务 |
| GET | `/analytics/overview` | ADMIN, HR | 面试、完成率、平均分总览 |
| GET | `/analytics/positions` | ADMIN, HR | 岗位维度统计 |
| GET | `/analytics/questions` | ADMIN, HR | 题目使用与得分统计 |

### 2.7 运维与文档

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/actuator/health` | 公开或内网 | 容器健康检查 |
| GET | `/actuator/prometheus` | 内网 | 指标抓取 |
| GET | `/swagger-ui/index.html` | 开发公开、生产 ADMIN | OpenAPI 文档 |

### 2.8 反馈工单（V25）

候选人接口均校验 `CANDIDATE` 角色和工单创建者归属；管理员接口统一要求 `ADMIN`。双方共用详情、时间线、留言、附件读取和阅读位置接口，但后端会按当前用户重新鉴权。

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| POST | `/tickets` | CANDIDATE | 创建反馈工单草稿 |
| GET | `/tickets/my` | CANDIDATE | 分页查看本人草稿和已提交工单 |
| GET | `/tickets/{id}` | 创建者或 ADMIN | 查看工单详情、附件和未读数量 |
| PUT/DELETE | `/tickets/{id}` | 创建者 | 修改或删除本人草稿 |
| POST | `/tickets/{id}/submit` | 创建者 | 将草稿提交为 `PENDING` |
| GET | `/tickets/{id}/activities` | 创建者或 ADMIN | 按活动 ID 增量获取工单时间线 |
| POST | `/tickets/{id}/messages` | 创建者或 ADMIN | 使用 `clientRequestId` 幂等发送留言；`CLOSED` 时拒绝 |
| POST multipart | `/tickets/{id}/attachments` | 创建者或 ADMIN | 上传工单截图；`CLOSED` 时拒绝 |
| GET | `/tickets/{ticketId}/attachments/{attachmentId}/content` | 创建者或 ADMIN | 按工单参与者权限读取附件 |
| PUT | `/tickets/{id}/read` | 创建者或 ADMIN | 更新当前用户最后已读活动位置 |
| GET | `/admin/tickets` | ADMIN | 按关键词、状态、类型和处理人分页查询全部工单 |
| GET | `/admin/tickets/assignees` | ADMIN | 查询可转派的启用管理员 |
| PUT | `/admin/tickets/{id}/assignee` | ADMIN | 使用版本号转派或取消分配；关闭后禁止 |
| PUT | `/admin/tickets/{id}/status` | ADMIN | 使用版本号执行合法状态流转 |

状态流转为 `DRAFT → PENDING → PROCESSING → RESOLVED/CLOSED`，允许 `RESOLVED → PROCESSING`；`CLOSED` 是禁止留言、上传和继续转派的终态。

### 2.9 持久化站内通知（V25）

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/notifications` | 已登录 | 分页获取当前用户站内通知 |
| GET | `/notifications/unread-count` | 已登录 | 获取当前用户未读总数 |
| PUT | `/notifications/{id}/read` | 通知接收人 | 标记单条通知已读 |
| PUT | `/notifications/read-all` | 已登录 | 标记当前用户全部通知已读 |
| POST | `/notifications/site` | ADMIN | 创建受去重键保护的站内通知 |
| POST | `/notifications/mail-sync` | ADMIN | 执行邮件同步；邮件失败不回滚已落库站内通知 |

### 2.10 学习资料与 PDF 批注（V26）

资料列表只返回已发布且当前用户拥有查看权限的记录；PDF 内容、下载和批注接口都会在后端重新校验资料状态、用户/角色权限和批注归属。管理员接口统一要求 `ADMIN`，资料上传使用 `multipart/form-data` 的 `metadata` JSON 部分和 `file` PDF 部分。

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| GET | `/learning-resources` | 已登录且有权限 | 获取当前用户可查看的资料列表 |
| GET | `/learning-resources/page` | 已登录且有权限 | 分页查询当前用户可查看的资料 |
| GET | `/learning-resources/{publicId}` | 已登录且有权限 | 获取资料详情和批注能力 |
| GET | `/learning-resources/{publicId}/content` | 已登录且有权限 | 在线读取 PDF 内容；仅校验查看权限 |
| GET | `/learning-resources/{publicId}/download` | 已登录且有权限 | 下载 PDF；除查看权限外还需 `allowDownload` |
| GET | `/learning-resources/{publicId}/annotations` | 已登录且有权限 | 获取当前用户可见的批注 |
| POST | `/learning-resources/{publicId}/annotations` | 已登录且可批注 | 新建高亮、便签或其他允许的批注 |
| PUT | `/learning-resources/annotations/{annotationPublicId}` | 批注所有者且可批注 | 使用版本号更新个人笔记/批注 |
| DELETE | `/learning-resources/annotations/{annotationPublicId}` | 批注所有者且可批注 | 逻辑删除个人批注 |
| GET | `/admin/learning-resources` | ADMIN | 分页查询全部资料 |
| GET | `/admin/learning-resources/{publicId}` | ADMIN | 查看管理端资料详情 |
| POST multipart | `/admin/learning-resources` | ADMIN | 上传并创建 PDF 资料及首个版本 |
| PUT | `/admin/learning-resources/{publicId}` | ADMIN | 修改标题、说明、发布状态和下载开关 |
| DELETE | `/admin/learning-resources/{publicId}` | ADMIN | 逻辑删除资料并撤销关联媒体访问 |
| GET | `/admin/learning-resources/{publicId}/permissions` | ADMIN | 查询用户/角色授权 |
| PUT | `/admin/learning-resources/{publicId}/permissions` | ADMIN | 整体替换用户/角色查看和批注权限 |

## 3. 关键 DTO 契约

### 创建面试 `POST /interviews`

```json
{
  "title": "Java 后端开发模拟面试",
  "positionId": 1,
  "candidateId": 101,
  "interviewerId": 21,
  "scheduledAt": "2026-08-01T14:00:00+08:00",
  "duration": 60,
  "type": "tech",
  "questionIds": [1, 2, 3, 4, 5],
  "meetingUrl": "https://example.com/room/xxx",
  "remark": "重点考察并发与数据库"
}
```

### 幂等保存作答 `PUT /interviews/{id}/questions/{qid}/answer`

```json
{
  "answerContent": "候选人的文字或代码回答",
  "answerData": { "selectedOptions": ["A", "C"] },
  "mediaId": 9001,
  "durationSeconds": 135
}
```

### 人工评分 `PUT /interviews/{id}/evaluations/{qid}`

```json
{
  "professionalScore": 82,
  "expressionScore": 80,
  "logicScore": 85,
  "adaptabilityScore": 78,
  "overallScore": 81.5,
  "comment": "能够说明核心原理，复杂边界分析仍可加强。"
}
```

## 4. OpenAPI 实施规则

- 每个 Controller 使用 `@Tag`，每个公开方法使用 `@Operation` 与明确的响应码说明。
- DTO 使用 Hibernate Validator 和 `@Schema`；分页/枚举提供示例值。
- 不将密码哈希、刷新令牌、`correctAnswer`、DeepSeek 原始请求/响应和第三方服务密钥暴露于候选人接口。
- 接口变更仅新增字段或发布新版本，不破坏既有客户端。

## 5. 当前实现基线

当前后端已实现统一响应与异常、认证/RBAC、用户/角色/岗位、题库、面试、录制、媒体、AI 任务、评价报告、算法练习、反馈工单、站内通知、学习资料/PDF 批注和 OpenAPI。业务持久化使用 MySQL/Flyway，Redis承担限流和任务协调，媒体保存在受保护的本地存储卷。日常接口变更只持续登记 `docs/CHANGELOG.md`；在用户明确要求集中更新结构总结时再同步本文件。数据库变更从 Flyway V27 开始。
