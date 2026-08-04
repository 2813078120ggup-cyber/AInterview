# P0 安全、恢复与监控实施手册

本手册对应 [项目更新日志](../CHANGELOG.md) 中的 P0 待执行项。仓库已提供应用端指标、备份脚本和配置树支持；KMS、安全组、ClamAV、OSS、监控平台的实际变更必须由具备云账号/服务器权限的人员执行并在更新日志中登记。

## 1. KMS 与运行时 Secret

1. 在阿里云 KMS Secrets Manager 创建生产 Secret，并为 ECS 绑定仅能读取这些 Secret 的 RAM 实例角色。
2. 不再把真实密码保存在发布包、Git 或长期 `.env` 中；服务器启动流程将 Secret 写入权限为 `0600` 的 `/run/secrets/`。
3. 文件名使用 Spring 属性名，例如：

```text
/run/secrets/spring.datasource.password
/run/secrets/spring.data.redis.password
/run/secrets/spring.mail.password
/run/secrets/app.security.jwt-secret
/run/secrets/app.deepseek.api-key
/run/secrets/app.verification-code.sms-app-code
```

4. 设置 `APP_SECRETS_DIRECTORY=/run/secrets/`。应用已通过 `optional:configtree:` 自动读取该目录；目录不存在时适合本地开发，不代表生产可以跳过 KMS。
5. 轮换 Secret 后重启对应服务，并撤销旧值。不要在 Docker 日志、`docker inspect` 输出或工单中粘贴 Secret。

## 2. ClamAV 与上传验证

1. 在 Docker 私有网络或仅本机网络部署 ClamAV/clamd，禁止公开其服务端口。
2. 设置：`CLAMAV_REQUIRED=true`、`CLAMAV_HOST=<私网主机>`、`CLAMAV_PORT=3310`。
3. 应用会先校验大小、MIME、文件签名，再通过 clamd `INSTREAM` 协议同步扫描；扫描服务不可用时会拒绝上传，避免“未扫描即入库”。
4. 使用 EICAR 测试文件验证会被拒绝；再验证正常 PDF、DOCX、WebM 上传成功。

当前支持的通用媒体类型：PDF、JPEG、PNG、GIF、WAV、WebM、MP4、MP3。自由简历只支持 PDF、DOCX、TXT、Markdown。

## 3. 备份与恢复

服务器上执行：

```bash
cd /opt/ai-interview-platform
chmod 700 deployment/scripts/backup-production.sh deployment/scripts/verify-production-backup.sh
MEDIA_VOLUME=ai-interview-platform_media_data \
  deployment/scripts/backup-production.sh
deployment/scripts/verify-production-backup.sh backups/automatic-<timestamp>
```

- 备份包含 MySQL 逻辑导出、`media_data` 压缩包、校验和与容器清单。
- 每日执行后上传到加密 OSS；脚本只生成本机副本，未上传前不算完成。
- 每季度在隔离 Docker/ECS 恢复 MySQL 和媒体包，启动后确认 Flyway `validate`、登录、报告读取和媒体抽样均正常。
- 正常更新绝不执行 `docker compose down -v`；Flyway V17+ 上线前先运行备份脚本。

## 4. 指标与告警

| 地址/指标 | 访问边界 | 用途 |
|---|---|---|
| `http://backend:8081/actuator/health` | Docker 私有网络 | 存活、数据库和 Redis 健康。 |
| `http://backend:8081/actuator/prometheus` | Docker 私有网络；不要求应用 JWT | Prometheus 拉取 JVM、HTTP、数据库及项目自定义指标。该端口不得发布到宿主机、Nginx 或公网。 |
| `ai_interview_ai_tasks{status="pending"}` | Prometheus | 待处理 AI 任务积压。 |
| `ai_interview_ai_tasks_oldest_pending_seconds` | Prometheus | 最老任务等待时间。 |
| `ai_interview_ai_generations{status="failed"}` | Prometheus | AI 生成失败积压。 |

先配置以下告警：应用/数据库不可用、Flyway 启动失败、最老 AI 任务超 SLA、AI 失败率异常、磁盘高水位、HTTPS 证书剩余不足 14 天、OpenTalking `/health` 或 WebRTC 合成探针失败。
