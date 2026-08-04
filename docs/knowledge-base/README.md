# 项目技术知识库

> 更新日期：2026-08-04  
> 范围：仅记录本仓库中已经使用、正在运行或仍保留用于维护的技术；不把计划中的技术当作现有能力。

## 技术全景

| 领域 | 当前技术 | 入口文档 |
|---|---|---|
| 后端与接口 | Java 17、Maven、Spring Boot 3.3、Spring MVC、Validation、Jackson、Springdoc | [后端与工程化](01-后端与工程化.md) |
| 数据与安全 | MySQL 8、Flyway、MyBatis-Plus、Redis、Spring Security、JWT/JJWT、BCrypt | [数据与安全](02-数据与安全.md) |
| Web 前端 | React 19、TypeScript、Vite、Tailwind CSS 4、React Router、Radix UI、Framer Motion、Lucide、mpegts.js | [当前前端](03-当前前端.md) |
| AI 与数字人 | DeepSeek Chat Completions、提示词版本、异步 AI 任务、OpenTalking、WebRTC | [AI 与数字人](04-AI与数字人.md) |
| 通信与文件 | Java HttpClient、阿里云市场短信、QQ SMTP、PDFBox、Apache POI、浏览器媒体/语音能力 | [外部服务与文件](05-外部服务与文件.md) |
| 交付与网络 | Docker、Docker Compose、Nginx、HTTPS/TLS、健康检查、反向代理、STUN/TURN | [部署与网络](06-部署与网络.md) |

## 阅读顺序

1. 新成员先阅读“后端与工程化”“数据与安全”“当前前端”，即可建立主链路认知。
2. 涉及数据库结构变更时，必须同时阅读 [数据库脚本与 Flyway 迁移指南](../database/README.md)。
3. 涉及数字人、AI 模型或外部凭据时，继续阅读“AI 与数字人”“外部服务与文件”，并遵守其中的密钥边界。
4. 上线、排错、服务迁移时，以“部署与网络”和 `docs/deployment/` 下的运行手册为准。

## 当前架构速览

```text
React 单页应用
  ├─ /api          → Nginx → Spring Boot REST API
  └─ /opentalking  → Nginx → OpenTalking 数字人服务

Spring Boot
  ├─ MyBatis-Plus → MySQL 8（Flyway 管理结构版本）
  ├─ Spring Data Redis → Redis
  ├─ Java HttpClient → DeepSeek / 短信 / Provider 连通性检查
  ├─ JavaMail → QQ SMTP
  └─ 建立 OpenTalking WebRTC 会话
```

## 维护规则

- 本目录是项目知识入口；技术版本或架构调整时，同步更新对应主题和本索引。
- 文档中的环境变量只写变量名和用途，严禁写入真实密码、API Key、API Secret、JWT 密钥或 TLS 私钥。
- 当前唯一前端是 `frontend-react/`；新功能、修复和部署都以该目录为准。
