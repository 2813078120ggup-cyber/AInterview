# 项目结构说明

> 更新日期：2026-08-04  
> 当前根目录：`D:\Ainterview`

## 1. 根目录

```text
Ainterview/
├─ .github/workflows/       GitHub Actions 质量门禁
├─ backend/                 Spring Boot 后端
├─ frontend-react/          当前 React 前端
├─ runner-images/           Java 17 算法判题镜像
├─ deployment/              部署、OpenTalking、Nginx、备份和恢复工具
├─ docs/                    当前设计、数据库、部署、知识库和迭代文档
├─ scripts/                 本地辅助脚本
├─ .env.example             可提交的环境变量模板
├─ docker-compose.yml       Docker Compose 编排
└─ README.md                项目入口
```

本目录是可直接绑定新 Git 仓库的干净源码副本，不包含旧 `.git`、私密 `.env`、历史 Vue 前端、归档、用户媒体、依赖和构建产物。完整说明见 [仓库迁移与接管指南](REPOSITORY_MIGRATION.md)。

## 2. 后端 `backend/`

```text
backend/
├─ src/main/java/com/tyut/aiinterview/
│  ├─ ai/                   DeepSeek、异步 AI 任务、追问策略和质量保护
│  ├─ algorithm/            算法题、提交、判题任务和统计
│  ├─ auth/                 注册、登录与验证码
│  ├─ config/               应用配置与限流/上传安全配置
│  ├─ domain/               MyBatis-Plus 实体
│  ├─ evaluation/           面试评分
│  ├─ freeinterview/        简历驱动自由面试
│  ├─ interview/            常规模拟面试与进度恢复
│  ├─ mapper/               MyBatis-Plus Mapper
│  ├─ media/                媒体上传与鉴权访问
│  ├─ observability/        Actuator/Prometheus 业务指标
│  ├─ prompt/               提示词版本与渲染
│  ├─ question/             题库、分类和题目管理
│  ├─ recording/            录制会话、按题分段和时间轴
│  ├─ reflection/           候选人面试心得
│  ├─ report/               面试报告
│  ├─ security/             JWT、Spring Security 和当前用户
│  ├─ settings/             AI Provider 与密钥加密
│  └─ virtualhuman/         OpenTalking 配置边界
├─ src/main/resources/
│  ├─ db/migration/         唯一有效的 Flyway 迁移目录（当前至 V24）
│  ├─ prompts/defaults/     默认提示词资源
│  └─ application.yml       应用默认配置
├─ src/test/                后端测试
├─ pom.xml                  Maven 依赖与 Java 17 配置
├─ Dockerfile               生产镜像构建
└─ Dockerfile.runtime       运行时镜像参考
```

后端生成的 `target/`、日志和本地媒体不属于源码。已发布 Flyway 文件不能修改；结构变化只能增加更高版本迁移。

## 3. 前端 `frontend-react/`

```text
frontend-react/
├─ src/
│  ├─ components/           通用组件、算法组件和页面壳
│  ├─ lib/                  API、OpenTalking、语音和状态工具
│  ├─ pages/                候选人、管理端、面试与算法页面
│  ├─ app.tsx               路由与页面装配
│  └─ styles.css            全局样式与设计令牌
├─ public/                  静态资源
├─ nginx/                   容器 Nginx 模板与 OpenTalking IPv4 上游解析脚本
├─ package.json             npm 依赖与质量脚本
├─ package-lock.json        锁定依赖版本
├─ vite.config.ts           `/api`、`/opentalking` 本地代理
└─ Dockerfile               Node 构建 + Nginx 运行镜像
```

`node_modules/`、`dist/` 和 `*.tsbuildinfo` 均可重新生成，不进入仓库。浏览器只访问同源 `/api` 和 `/opentalking`，不得接收数据库或第三方服务密钥。

## 4. 数据库与文档

```text
docs/
├─ README.md                文档索引
├─ CHANGELOG.md             唯一持续更新日志
├─ REPOSITORY_MIGRATION.md  仓库迁移与接管指南
├─ iteration-plan.md        当前能力与下一迭代
├─ iterations/              迭代汇总
├─ database/
│  ├─ README.md             Flyway 规则
│  ├─ data_dictionary.md    数据字典
│  ├─ docker-init/          空 MySQL 卷首次初始化
│  └─ local-test-*.sql      手工测试数据
├─ deployment/              服务器、更新与运维文档
└─ knowledge-base/          当前技术知识库
```

`docs/archive/`、`docs/database/archive/`、旧变更报告和一次性发布记录未迁入当前仓库。Docker 仅挂载 `docs/database/docker-init/`；应用只扫描后端 classpath 中的 Flyway 迁移。

## 5. 部署与判题

| 路径 | 用途 |
|---|---|
| `deployment/nginx/` | HTTPS/Nginx 恢复参考 |
| `deployment/opentalking/` | OpenTalking 配置样例、补丁和同步工具 |
| `deployment/opentalking-mock/` | 本地 OpenTalking 模拟辅助内容 |
| `deployment/scripts/` | 生产备份和完整性验证 |
| `runner-images/java17/` | Java 17 判题沙箱镜像上下文 |

OpenTalking 运行目录和 `.env` 位于独立环境，不随本仓库源码迁移。算法判题依赖 Docker 套接字和 `ai-java17-runner:2.0` 镜像。

## 6. 运行时边界

| 路径/内容 | 是否提交 | 说明 |
|---|---|---|
| `.env`、密钥、证书私钥 | 否 | 每个环境独立创建和保护 |
| `backend/target/` | 否 | Maven 构建产物 |
| `frontend-react/node_modules/`、`dist/` | 否 | npm 依赖和前端构建产物 |
| `data/media/`、`uploads/` | 否 | 用户媒体和本地运行数据 |
| `*.log` | 否 | 运行日志 |
| MySQL/Redis/媒体 Docker 卷 | 否 | 通过备份流程单独迁移 |

## 7. 文档同步规则

| 变更类型 | 至少同步更新 |
|---|---|
| 功能或配置 | `docs/CHANGELOG.md`、相关主题文档 |
| 数据库迁移 | `docs/database/README.md`、数据字典/设计文档 |
| 环境变量、端口、卷或代理 | `.env.example`、部署文档、更新日志 |
| OpenTalking 运行方式 | OpenTalking 知识库、部署文档、更新日志 |
| 仓库或目录迁移 | `README.md`、本文件、`REPOSITORY_MIGRATION.md` |
