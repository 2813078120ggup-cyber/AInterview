# 文档索引

> 最后整理：2026-08-20。本文档用于说明当前交付基线，并区分有效设计、运行文档与历史记录。

## 当前交付基线

- 架构：Spring Boot 4.0.7 模块化单体业务核心、Spring Cloud 2025.1.2 Eureka/Gateway、独立 `algorithm-judge-worker` 和 React 19 前端。
- 候选人端：岗位、申请、私有简历及 AI 解析、AI/线下面试、报告、能力发展、算法练习、学习资料、反馈工单和账户设置。
- 企业端：岗位与申请流程、人才库、AI/线下面试、报告复核与发布、企业团队角色权限、招聘分析和企业设置。
- 管理端：企业、用户、平台员工、候选人档案、角色权限、跨企业招聘运营、AI Provider/Prompt/任务追踪、服务工单、操作审计和运行健康。
- 账户与安全：密码/验证码登录、短期 Access Token 与 Refresh Token 轮换、设备会话管理、联系方式验证、头像、通知偏好、安全活动和角色变更会话撤销；公开认证页统一支持个人注册、企业 HR 注册、找回密码和四位图形验证码，前端以 `/v1/auth/me` 建立权威会话并按候选人、企业和管理员受众隔离路由。
- 运维与数据治理：超级管理员运维域提供只读数据字典，从当前 MySQL catalog 的 `information_schema` 展示表、视图、字段、索引、约束和外键关系，敏感默认值脱敏且不读取业务行数据；数据字典 API 仅允许 `ADMIN` 访问。
- AI 与虚拟人：异步 AI 任务租约与恢复、版本化 Prompt、生成审计、Provider 测试状态持久化；OpenTalking 是唯一保留的虚拟人运行链路。
- 数据库：唯一迁移源为 `backend/src/main/resources/db/migration/`，当前共有 40 个版本化脚本，版本为 V1、V9–V47，最新为 `V47__support_public_company_registration.sql`。新增迁移前必须重新检查最高版本；若目录未变化，则从 V48 开始，禁止修改 V1–V47。
- 交付：Docker Compose 默认包含 MySQL、Redis、注册中心、判题 Worker、业务后端、Gateway 和前端；监控 profile 提供 Prometheus、Alertmanager 与 Grafana。

具体功能、测试、浏览器和容器验证状态以 [项目更新日志](CHANGELOG.md) 为准；生产版本必须以目标服务器的镜像摘要、容器状态和 `flyway_schema_history` 为准。

## 核心文档入口

- [项目更新日志](CHANGELOG.md)：所有已完成功能、配置、数据库、运维、验证结果和待执行生产事项的唯一持续记录。
- [项目结构说明](project-structure.md)：模块、包、前端路由、Flyway、Compose、运行边界和提交边界。
- [当前迭代计划](iteration-plan.md)：阶段能力与后续事项；涉及版本时应与当前代码及更新日志交叉核对。
- [迭代文档](iterations/README.md)：按日期保存阶段性交付、风险和后续计划。
- [需求分析](01-requirements-analysis.md)、[系统设计](02-system-design.md)、[数据库设计](03-database-design.md)、[API 设计](04-api-design.md)：产品与技术设计入口；版本事实以当前代码为准。
- [数据库迁移指南](database/README.md)：Flyway 基线、迁移源和已发布脚本不可变规则。
- [仓库迁移与接管指南](REPOSITORY_MIGRATION.md)：源码迁移范围、排除项、验证方式和新仓库接管步骤。
- [项目技术知识库](knowledge-base/README.md)：当前技术、配置边界和排障入口。

## 部署与运维入口

- [部署文档目录](deployment/)：部署、更新、回滚、服务器配置和生产基线。
- [P0 安全、恢复与监控实施手册](deployment/P0_OPERATIONS.md)：KMS、ClamAV、备份恢复、指标与告警。
- [云服务器端口与文件存放说明](deployment/SERVER_PORTS_AND_STORAGE.md)：公网/内部端口、服务器路径、Docker 卷和更新保护项。
- [生产更新指南](deployment/UPDATE_GUIDE.md)：生产更新与验证流程。
- [OpenTalking 知识库](knowledge-base/04-AI与数字人.md)：当前唯一保留的虚拟人链路与配置边界。

## 维护约定

- 日常功能、配置、UI、数据库和运维变更统一登记在 [项目更新日志](CHANGELOG.md)，不创建分散的更新报告。
- 项目结构、阶段总结、设计总览和本文档索引仅在用户明确要求时集中同步。
- 优先修改已有文档；除非用户明确要求，不新建结构、总结或一次性更新文档。
- 数据库结构变更只在 `backend/src/main/resources/db/migration/` 新增更高版本的 `V<版本>__<描述>.sql`，不得修改已发布迁移，也不得在 `docs/database/` 保存可执行副本。
- `.env`、密钥、用户媒体、构建产物、依赖目录、测试报告、调试截图、本地代理/设计工具目录和发布暂存副本不得提交。
- 普通服务更新不得执行 `docker compose down -v`；涉及单个服务时只重建并以 `--no-deps` 替换受影响服务，保留数据库、Redis、媒体和监控卷。
