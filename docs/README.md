# 文档索引

> 最后整理：2026-08-04。本文档用于区分当前有效的交付、运行文档与历史变更记录。

## 当前入口

- [当前迭代计划](iteration-plan.md)：已交付能力、当前发布基线和下一迭代事项。
- [迭代文档](iterations/README.md)：按日期汇总每次迭代的交付内容、验证结果、风险与后续计划。
- [项目更新日志](CHANGELOG.md)：以后所有已完成更新与待执行生产操作的唯一持续登记位置。
- [仓库迁移与接管指南](REPOSITORY_MIGRATION.md)：迁移范围、排除项、验证方式和新仓库初始化步骤。
- [项目技术知识库](knowledge-base/README.md)：项目已使用技术的中文说明、配置边界与排障入口。
- [需求分析](01-requirements-analysis.md)、[系统设计](02-system-design.md)、[数据库设计](03-database-design.md)、[API 设计](04-api-design.md)。
- [数据库迁移指南](database/README.md)：Flyway 的唯一迁移源、基线策略和脚本归档说明。
- [项目结构说明](project-structure.md)：代码、配置、文档和运行时目录的职责与更新入口。
- [云服务器端口与文件存放说明](deployment/SERVER_PORTS_AND_STORAGE.md)：公网/内部端口、服务器路径、Docker 卷与更新保护项。
- [部署文档](deployment/)：部署、更新、服务器配置、发布说明和当前生产基线。
- [OpenTalking 知识库](knowledge-base/04-AI与数字人.md)：当前唯一保留的虚拟人运行链路。

## 维护约定

- 每次功能、配置、运维或数据库变更，先更新统一的 [更新日志](CHANGELOG.md)；不要另建分散的更新记录。

- 新的产品能力完成后，先更新 [当前迭代计划](iteration-plan.md)，再按需补充专题说明。
- 数据库结构变更只新增 `backend/src/main/resources/db/migration/V<版本>__<描述>.sql`；不要在 `docs/database` 中新增可执行迁移副本。
- 归档脚本不属于当前仓库；应用、Flyway 和 Docker 初始化流程只读取本文档标明的有效路径。
