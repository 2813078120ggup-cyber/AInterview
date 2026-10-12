# AInterview 多模态智能模拟面试平台

当前项目目录：`D:\Ainterview`

AInterview 是一套基于 Spring Boot、React、DeepSeek 和 OpenTalking 的模拟面试平台，覆盖候选人练习、管理端运营、AI 追问与评分、语音/文本多模态感知分析、可追溯评测报告、音视频录制回放、算法练习、反馈工单和站内通知。

## 当前功能

- 注册、登录、JWT 鉴权、角色权限与认证接口限流。
- 题库、题目分类、岗位、候选人和面试场次管理。
- 文字、语音、视频模拟面试，支持进度恢复、答案可靠保存和智能追问。
- 简历驱动的自由面试、AI 评分、报告生成、能力趋势与面试心得。
- **语音分析感知模块**：独立 Python 服务（librosa）对录音作答分析语速、流利度、情绪稳定性、清晰度四维子分，按录音段聚合接入评分引擎，结果可追溯；webm 经 ffmpeg 转码兜底，语音旁路失败不阻断主链路。
- **文本理解与评价感知模块**：DeepSeek 语义评分按分档规则输出专业/表达/逻辑/应变四维分与评语，`text_evidence` 记录输入摘要、命中分档、原始/校准分与评分规则版本，报告逐题可追溯。
- **可追溯评测报告**：两个感知结果进入四维加权评分规则，报告页逐题展示语音表现与回答评价，未测维度显示"未评估"。
- 按题保存音视频分段和事件时间轴，管理端鉴权回放。
- OpenTalking WebRTC 数字人、TTS/STT；不可用时可降级到浏览器语音能力。
- Java 17 算法练习与 Docker 隔离判题。
- 候选人问题反馈工单、截图附件、双向留言、管理员转派与状态流转。
- 持久化站内通知、未读数量、已读状态和工单阅读位置。
- 学习资料中心：管理员上传和发布 PDF、按用户授权查看/批注，候选人使用 PDF 阅读器进行高亮与个人笔记。
- Prometheus 指标、上传安全校验、生产备份与部署维护脚本。

## 技术栈

| 层级 | 当前实现 |
|---|---|
| 后端 | Java 17、Spring Boot 4.0、Spring Security、MyBatis-Plus、Flyway、MySQL 8、Redis |
| AI 与数字人 | DeepSeek Provider、版本化提示词、异步 AI 任务、OpenTalking、WebRTC |
| 语音分析 | 独立 FastAPI 服务（Python 3.11、librosa、ffmpeg），语速/流利度/情绪稳定性/清晰度四维分析 |
| 前端 | React 19、TypeScript、Vite、Tailwind CSS 4、Radix UI、Framer Motion、Monaco Editor |
| 文件与报告 | PDFBox、Apache POI、PDF.js、浏览器 MediaRecorder |
| 交付 | Docker Compose、Nginx、GitHub Actions、Prometheus |

当前数据库结构由 Flyway V1、V9–V49 管理。V48 为语音分析维度（`voice_score`/`voice_evidence`），V49 为文本理解与评价可追溯证据（`text_evidence`）。

## 目录结构

```text
D:\Ainterview\
├─ backend/             Spring Boot 后端、Flyway 迁移和测试
├─ frontend-react/      当前 React 前端
├─ voice-analysis-service/  语音分析感知模块（FastAPI + librosa 四维分析）
├─ runner-images/       Java 17 判题沙箱镜像
├─ deployment/          Nginx、OpenTalking、备份和生产运维工具
├─ docs/                设计、部署、知识库、迭代与更新日志
├─ scripts/             本地开发辅助脚本
├─ .github/workflows/   CI 质量门禁
├─ .env.example         环境变量模板，不含真实密钥
└─ docker-compose.yml   本地与基础部署编排
```

迁移清单、排除项和更换 Git 仓库步骤见 [仓库迁移与接管指南](docs/REPOSITORY_MIGRATION.md)。

## 环境要求

- JDK 17
- Maven 3.9+
- Node.js 22.13+ 与 npm（`pdfjs-dist` 6.x 构建要求）
- Python 3.11 与 ffmpeg（语音分析服务 `voice-analysis-service` 需要）
- Docker Engine 26+、Docker Compose v2+
- 独立部署的 OpenTalking 服务（使用数字人功能时需要）

## Docker 启动

```powershell
cd D:\Ainterview
Copy-Item .env.example .env
```

编辑 `.env`，至少替换 MySQL、Redis、JWT 和 DeepSeek 的占位值。真实 `.env` 不得提交仓库。

启用语音分析感知模块时，将 `.env` 中 `VOICE_ANALYSIS_ENABLED` 设为 `true`（默认关闭；该服务随 `docker compose` 一并构建，作为旁路增强，失败不阻断文本评分主链路）。

```powershell
docker compose --profile judge build java-runner
docker compose up -d --build
docker compose ps
```

默认通过 `http://localhost/` 访问。OpenTalking 独立运行时，浏览器访问同源 `/opentalking`，Nginx 根据 `OPENTALKING_UPSTREAM` 转发。

## 本地开发

先从项目根目录启动 MySQL 和 Redis，再分别启动后端和前端：

```powershell
cd D:\Ainterview
docker compose up -d mysql redis

cd backend
mvn spring-boot:run

cd ..\frontend-react
npm ci
npm run dev
```

前端默认端口为 `5174`，后端默认端口为 `8080`。Vite 将 `/api` 代理到后端，并将 `/opentalking` 代理到本机 OpenTalking 开发服务。

## 提交前验证

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

数据库结构只允许通过 `backend/src/main/resources/db/migration/` 新增 Flyway 迁移。所有后续更新继续登记在 [项目更新日志](docs/CHANGELOG.md)。

## 文档入口

- [文档索引](docs/README.md)
- [项目结构](docs/project-structure.md)
- [数据库迁移指南](docs/database/README.md)
- [服务器端口与存储](docs/deployment/SERVER_PORTS_AND_STORAGE.md)
- [生产更新指南](docs/deployment/UPDATE_GUIDE.md)
- [OpenTalking 知识库](docs/knowledge-base/04-AI与数字人.md)

## License

本项目用于学习、课程设计及技术研究。
