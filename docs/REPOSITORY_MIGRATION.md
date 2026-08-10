# 仓库迁移与接管指南

> 当前代码目录：`D:\Ainterview`  
> 整理日期：2026-08-05

## 1. 本次迁移结果

新目录保存可独立构建、测试和部署的当前项目源码，不继承旧目录的 Git 元数据。业务代码、测试、Flyway 迁移、React 前端、OpenTalking 接入、判题镜像、Docker Compose、CI 与运维脚本均已保留。

当前目录已初始化为独立 Git 仓库并绑定 `https://github.com/2813078120ggup-cyber/AInterview.git`；`main` 已包含反馈工单、站内通知和管理端界面统一提交 `6d6301e`。后续迁移仓库时仍按本指南保护 `.env`、Docker 卷和服务器配置。

以下内容没有迁移：

| 未迁移内容 | 原因 |
|---|---|
| `.git` | 避免继承旧仓库、分支和远端；便于绑定新仓库 |
| `.env`（源码迁移阶段） | 包含环境私密配置，不进入源码副本或新 Git 仓库；当前机器已在迁移后单独重建本地文件 |
| `.idea/` | 本地 IDE 状态 |
| `frontend/` | 已停用的历史 Vue 前端 |
| `docs/archive/`、`docs/database/archive/` | 已归档内容，不参与运行 |
| `docs/change-reports/` 和旧版发布记录 | 历史追溯材料，当前统一由 `docs/CHANGELOG.md` 维护 |
| `data/media/`、`backend/data/`、`uploads/` | 本地或用户运行数据，不属于源码仓库 |
| `node_modules/`、`dist/`、`target/`、`*.log`、`*.tsbuildinfo` | 可重新生成的依赖、构建和运行产物 |

旧目录 `D:\AAAAAAAtyut\ai-interview-platform-test` 未被删除，可在确认新仓库、生产备份和部署均正常后自行决定是否保留。

## 2. 首次接管

```powershell
cd D:\Ainterview
Copy-Item .env.example .env
```

编辑 `.env` 并替换所有 `replace-with-*`、示例邮箱和其他占位值。不要从公开仓库、聊天记录或发布包复制真实密钥。

当前机器已于 2026-08-04 从 `D:\AAAAAAAtyut\ai-interview-platform-test\.env` 原样复制为 `D:\Ainterview\.env`，并用 SHA-256 确认内容一致。新增模板变量由 Compose 默认值接管，没有修改源配置值；`.env` 仍被 `.gitignore` 排除。迁移到其他机器或仓库时仍应按环境重新创建，不要提交该文件。

本地运行数据不会随仓库迁移。若需要继续使用旧环境数据，应通过数据库备份/恢复和受控媒体备份单独迁移，不能把数据库文件、Docker 卷或用户媒体直接提交 Git。

## 3. 验证项目

```powershell
cd D:\Ainterview\backend
mvn test

cd ..\frontend-react
npm ci
npm run build
npm run lint

cd ..
$env:MYSQL_ROOT_PASSWORD = 'compose-check-only'
$env:MYSQL_APP_PASSWORD = 'compose-check-only'
$env:REDIS_PASSWORD = 'compose-check-only'
$env:JWT_SECRET = 'compose-check-only-jwt-secret-at-least-32-characters'
docker compose config --quiet
```

使用算法判题前还需构建沙箱镜像：

```powershell
docker compose --profile judge build java-runner
```

## 4. 创建新 Git 仓库

确认目标远端是空仓库或已明确合并策略后执行：

```powershell
cd D:\Ainterview
git init -b main
git add .
git status --short
git commit -m "chore: migrate AInterview project"
git remote add origin <新仓库地址>
git push -u origin main
```

如果远端不是空仓库，不要直接强制推送。先拉取远端历史，在临时分支中比较 README、许可证、CI 和目录结构，再决定 merge、rebase 或保留独立历史。

## 5. 推送前安全检查

```powershell
git status --short
git ls-files | Select-String -Pattern '(^|/)(\.env|data/media|node_modules|dist|target|docs/archive)(/|$)'
rg -n "replace-with-|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY" .
```

第一条检查变更范围；第二条应无输出；第三条只允许在 `.env.example` 和文档中出现明确的示例占位值，不得出现真实私钥。

## 6. 后续维护约定

- 所有功能、配置、部署和文档变更都更新 `docs/CHANGELOG.md` 的 `Unreleased`。
- 新增 Flyway 迁移时同步更新 `docs/database/README.md` 和相关数据文档。
- 端口、环境变量、卷或 OpenTalking 代理变化时同步更新 `.env.example` 与部署文档。
- 不在仓库中恢复本次明确排除的归档、历史前端、用户媒体或构建产物。

## 7. 本次迁移验证结果

| 检查 | 结果 |
|---|---|
| 后端测试（本次 Docker/OpenTalking 修复后） | `mvn test` 通过：55 项测试，0 失败、0 错误、0 跳过；新增 3 项 OpenTalking 上游 URL 解析测试 |
| 数据库迁移 | 当前共有 19 个 Flyway 版本文件（V1、V9–V26）；本地数据库已迁移至 V26，最新记录 `success=1` |
| 前端构建 | `npm run build` 通过；已生成 PDF.js worker，Nginx `.mjs` 响应类型验证为 `application/javascript` |
| 前端静态检查 | `npm run lint` 通过：0 错误；保留 24 条 React Hook 依赖警告 |
| Docker/Compose | Docker Desktop 29.6.2、Compose 5.3.1；配置解析、Java 判题镜像、后端和前端镜像构建通过 |
| 容器运行态 | MySQL、Redis、后端、前端均运行；MySQL/Redis healthy，首页与 OpenAPI 返回 200，Actuator 为 `UP`，Redis 为 `PONG` |
| OpenTalking API | WSL mock 8210/5280 已启动；直连和 `/opentalking` 代理的 health、runtime-config、avatars、ICE、会话创建/删除均通过 |
| OpenTalking 浏览器链路 | Provider 测试 HTTP 200；模拟面试成功建立 WebRTC，会话媒体 `readyState=4` 且播放时间推进；对话文字与当前题目一致，朗读结束恢复“已就绪” |
| 本地环境文件 | `.env` 与源项目文件 SHA-256 一致，必填项非空、非占位，且由 `.gitignore` 排除 |
| 迁移完整性 | 初始暂存副本与 `D:\Ainterview` 的 376 个文件曾逐项 SHA-256 一致；后续功能更新均由当前 Git 仓库跟踪 |
| 清理边界 | 私密文件、归档、历史前端、媒体、依赖、日志和构建产物检查通过 |
| 文档 | 当前仓库内 Markdown 相对链接检查通过 |

当前 Docker Desktop 安装在 `%LOCALAPPDATA%\Programs\DockerDesktop`。如果旧终端尚未刷新 PATH，可暂时使用 `%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin\docker.exe`，或关闭并重新打开终端。

本次已覆盖 OpenTalking mock 的 API、同源代理、Provider、WebRTC 与 TTS 播放链路；未下载 QuickTalk 模型权重，也未申请浏览器摄像头/麦克风权限，因此 QuickTalk GPU 渲染、真实语音 STT 和音视频录制仍需在对应硬件与权限环境单独验收。当前 Compose 与 WSL OpenTalking 服务保持运行。
