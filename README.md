<div align="center">

# 🎯 AInterview

### 多模态智能模拟面试平台

**文字 / 语音 / 视频三模态作答 · AI 智能追问 · 语音与文本双感知评测 · 数字人面试官 · 全链路可追溯报告**

<br />

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://www.python.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

[![DeepSeek](https://img.shields.io/badge/AI-DeepSeek-4D6BFE?style=flat-square)](https://www.deepseek.com/)
[![librosa](https://img.shields.io/badge/语音分析-librosa-9C27B0?style=flat-square)](https://librosa.org/)
![OpenTalking](https://img.shields.io/badge/数字人-OpenTalking-FF6F00?style=flat-square)
[![Flyway](https://img.shields.io/badge/迁移-Flyway_V49-CC0200?style=flat-square)](https://flywaydb.org/)
![License](https://img.shields.io/badge/用途-学习与课程设计-lightgrey?style=flat-square)

[✨ 功能特性](#-功能特性) · [🏗️ 系统架构](#️-系统架构) · [🚀 快速开始](#-快速开始) · [📂 目录结构](#-目录结构) · [📖 文档](#-文档入口)

</div>

---

## 📖 项目简介

**AInterview** 是一套生产级模拟面试平台，基于 **Spring Boot + React + DeepSeek + OpenTalking** 构建，覆盖候选人练习、企业管理端运营、AI 追问与评分、语音/文本多模态感知分析、可追溯评测报告、音视频录制回放、算法练习、反馈工单与站内通知的完整闭环。

> 💡 核心亮点：**两个独立感知模块**（语音分析 + 文本理解）接入统一融合评分引擎，每一分都可追溯到原始输入与评分规则。

---

## ✨ 功能特性

### 🧠 核心感知与评测

| 模块 | 说明 |
|:---:|---|
| 🎙️ **语音分析感知** | 独立 Python 服务（librosa）分析录音作答的**语速 / 流利度 / 情绪稳定性 / 清晰度**四维子分，按录音段聚合接入评分引擎；webm 经 ffmpeg 转码兜底，旁路失败不阻断主链路 |
| 📝 **文本理解感知** | DeepSeek 语义评分按分档规则输出**专业 / 表达 / 逻辑 / 应变**四维分与评语，`text_evidence` 记录输入摘要、命中分档、原始/校准分与规则版本 |
| 🔍 **可追溯评测报告** | 两个感知结果进入四维加权评分规则，报告页逐题展示语音表现与回答评价，未测维度显示「未评估」 |
| 💬 **AI 智能追问** | 文字 / 语音 / 视频三模态作答，服务端快照 + 幂等任务键，最多 3 次追问，切题自动丢弃迟到追问 |

### 💼 面试与业务闭环

| 功能 | 说明 |
|:---:|---|
| 🎭 **数字人面试官** | OpenTalking WebRTC 数字人 + TTS/STT，不可用时降级浏览器语音能力 |
| 📄 **简历驱动面试** | 简历解析技能画像 → 智能组卷 → 自由面试 → AI 评分 → 能力趋势 |
| 🏢 **企业多租户** | 岗位管理、候选人申请、人才库、AI 岗位匹配、录用审批，数据按 `companyId` 隔离 |
| 🎬 **录制回放** | 按题保存音视频分段和事件时间轴，管理端鉴权回放 |

### 🛠️ 训练与运营

| 功能 | 说明 |
|:---:|---|
| ⌨️ **算法练习** | Java 17 算法题 + Docker 隔离判题 + 可视化 + 错题本 |
| 📚 **学习资料中心** | PDF 发布与授权阅读，高亮与个人批注 |
| 🎫 **反馈工单** | 截图附件、双向留言、管理员转派与状态流转 |
| 🔔 **站内通知** | 持久化通知、未读数、已读状态，邮件降级不误报 |
| 📊 **可观测性** | Prometheus 指标、操作审计、上传安全校验、生产备份脚本 |

---

## 🏗️ 系统架构

```text
                          ┌─────────────────────────────────────┐
                          │        Nginx（同源反向代理）         │
                          └───────────────┬─────────────────────┘
                                          │
        ┌─────────────────┬───────────────┼───────────────┬────────────────┐
        ▼                 ▼               ▼               ▼                ▼
 ┌─────────────┐  ┌──────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────────┐
 │ React 19    │  │ Spring Boot  │ │ 语音分析服务 │ │ OpenTalking │ │ 判题沙箱      │
 │ 前端 SPA    │─▶│ 后端主服务   │─▶│ FastAPI     │ │ WebRTC      │ │ Java 17      │
 │ Vite        │  │ Security/JWT │ │ librosa四维 │ │ 数字人      │ │ Docker 隔离  │
 └─────────────┘  └──────┬───────┘ └─────────────┘ └─────────────┘ └──────────────┘
                         │
              ┌──────────┼──────────┬─────────────┐
              ▼          ▼          ▼             ▼
        ┌──────────┐ ┌────────┐ ┌────────┐ ┌─────────────┐
        │ MySQL 8  │ │ Redis  │ │DeepSeek│ │ Prometheus  │
        │ Flyway   │ │ 缓存   │ │ LLM    │ │ 指标监控    │
        │ V1~V49   │ │        │ │        │ │             │
        └──────────┘ └────────┘ └────────┘ └─────────────┘
```

<details>
<summary><b>📊 技术栈详情（点击展开）</b></summary>

<br />

| 层级 | 实现 |
|---|---|
| **后端** | Java 17、Spring Boot 4.0、Spring Security、MyBatis-Plus、Flyway、MySQL 8、Redis |
| **AI 与数字人** | DeepSeek Provider、版本化提示词、异步 AI 任务、OpenTalking、WebRTC |
| **语音分析** | 独立 FastAPI 服务（Python 3.11、librosa、ffmpeg），语速/流利度/情绪稳定性/清晰度四维分析 |
| **前端** | React 19、TypeScript、Vite、Tailwind CSS 4、Radix UI、Framer Motion、Monaco Editor |
| **文件与报告** | PDFBox、Apache POI、PDF.js、浏览器 MediaRecorder |
| **交付** | Docker Compose、Nginx、GitHub Actions、Prometheus |

</details>

> 🗄️ 数据库结构由 Flyway **V1、V9–V49** 管理：**V48** 新增语音分析维度（`voice_score` / `voice_evidence`），**V49** 新增文本理解可追溯证据（`text_evidence`）。

---

## 🚀 快速开始

### 环境要求

| 依赖 | 版本 | 用途 |
|---|:---:|---|
| JDK | 17 | 后端主服务 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 22.13+ | 前端构建（`pdfjs-dist` 6.x 要求） |
| Python | 3.11 | 语音分析服务（需配合 ffmpeg） |
| Docker | Engine 26+ / Compose v2+ | 一键编排 |
| OpenTalking | 独立部署 | 数字人功能（可选） |

### 🐳 Docker 一键启动

```powershell
cd D:\Ainterview
Copy-Item .env.example .env
```

编辑 `.env`，至少替换 **MySQL、Redis、JWT、DeepSeek** 的占位值。

> ⚠️ 真实 `.env` 不得提交仓库。
>
> 🎙️ 启用语音分析感知模块：将 `.env` 中 `VOICE_ANALYSIS_ENABLED` 设为 `true`（默认关闭；随 compose 一并构建，旁路增强、失败不阻断文本评分主链路）。

```powershell
docker compose --profile judge build java-runner
docker compose up -d --build
docker compose ps
```

默认访问 **http://localhost/**。OpenTalking 独立运行时，浏览器访问同源 `/opentalking`，Nginx 根据 `OPENTALKING_UPSTREAM` 转发。

### 💻 本地开发

```powershell
cd D:\Ainterview
docker compose up -d mysql redis

cd backend
mvn spring-boot:run

cd ..\frontend-react
npm ci
npm run dev
```

- 前端：`http://localhost:5174` ｜ 后端：`http://localhost:8080`
- Vite 将 `/api` 代理到后端，`/opentalking` 代理到本机 OpenTalking 开发服务

### ✅ 提交前验证

```powershell
cd D:\Ainterview\backend
mvn test

cd ..\frontend-react
npm ci
npm run build
npm run lint

cd ..
docker compose config --quiet
```

> 📌 数据库结构只允许通过 `backend/src/main/resources/db/migration/` 新增 Flyway 迁移。所有更新登记在 [项目更新日志](docs/CHANGELOG.md)。

---

## 📂 目录结构

```text
D:\Ainterview\
├─ 🖥️  backend/                Spring Boot 后端、Flyway 迁移和测试
├─ 🎨  frontend-react/         React 前端
├─ 🎙️  voice-analysis-service/ 语音分析感知模块（FastAPI + librosa 四维分析）
├─ ⌨️  runner-images/           Java 17 判题沙箱镜像
├─ 🚢  deployment/              Nginx、OpenTalking、备份和生产运维工具
├─ 📖  docs/                    设计、部署、知识库、迭代与更新日志
├─ 🔧  scripts/                 本地开发辅助脚本
├─ ⚙️  .github/workflows/       CI 质量门禁
├─ 🔐  .env.example             环境变量模板，不含真实密钥
└─ 🐳  docker-compose.yml       本地与基础部署编排
```

迁移清单、排除项和更换 Git 仓库步骤见 [仓库迁移与接管指南](docs/REPOSITORY_MIGRATION.md)。

---

## 📖 文档入口

| 文档 | 说明 |
|---|---|
| 📚 [文档索引](docs/README.md) | 全部文档导航 |
| 🏗️ [项目结构](docs/project-structure.md) | 模块划分与代码组织 |
| 🗄️ [数据库迁移指南](docs/database/README.md) | Flyway 迁移规范 |
| 🌐 [服务器端口与存储](docs/deployment/SERVER_PORTS_AND_STORAGE.md) | 部署拓扑 |
| 🚀 [生产更新指南](docs/deployment/UPDATE_GUIDE.md) | 上线与回滚流程 |
| 🎭 [OpenTalking 知识库](docs/knowledge-base/04-AI与数字人.md) | 数字人接入细节 |

---

<div align="center">

## 📄 License

本项目用于学习、课程设计及技术研究。

**如果这个项目对你有帮助，欢迎 Star ⭐**

</div>
