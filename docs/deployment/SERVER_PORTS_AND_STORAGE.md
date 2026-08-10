# 云服务器端口配置与文件存放说明

> 更新日期：2026-07-30  
> 生产基线：`SERVER-BASELINE-20260729-V16-HTTPS1`  
> 适用范围：阿里云 ECS 上的 AI Interview Platform 与 OpenTalking 服务。服务器的生效配置优先于仓库默认配置。

## 1. 仓库默认配置与服务器配置

| 配置层 | 位置 | 说明 | 更新规则 |
|---|---|---|---|
| 仓库基础 Compose | `docker-compose.yml` | 本地/基础部署，默认发布 `80:80`。 | 不能直接覆盖服务器 Compose。 |
| 服务器 Compose | `/opt/ai-interview-platform/docker-compose.yml` | 生产容器、HTTPS 端口和挂载。 | 先 `diff`，保留 443 与 SSL/Nginx 挂载。 |
| 仓库 Nginx 模板 | `deployment/nginx/default.conf.template` | 含 HTTPS 的恢复参考。 | 不直接代表线上。 |
| 服务器 Nginx 模板 | `/opt/ai-interview-platform/nginx/default.conf.template` | 生产实际读取。 | 修改前备份，验证后再重建前端容器。 |

## 2. 端口配置

### 2.1 公网与云安全组

| 协议/端口 | 访问范围 | 用途 | 处理要求 |
|---|---|---|---|
| TCP 80 | 公网 | HTTP 网站入口，Nginx 前端容器。 | 由 `APP_PORT:80` 映射；可用于 HTTP 访问或跳转。 |
| TCP 443 | 公网 | HTTPS 网站入口。 | 生产 Compose 必须保留 `443:443`；Nginx 监听 `443 ssl` 并读取 `ssl/`。 |
| UDP 3478 | 公网（实时媒体需要时） | TURN/STUN 协商。 | 以 OpenTalking/coturn 配置为准。 |
| UDP 30000-60999 | 公网（实时媒体需要时） | TURN 中继媒体端口段。 | 当前服务器记录的中继范围。 |
| TCP 22 | 仅管理员来源 IP | SSH 运维。 | 不属于应用 Compose；云安全组应限制到固定运维出口 IP。 |

`/etc/coturn/turnserver.conf` 是 TURN 的实际配置来源。本文只根据现有服务器文档记录 UDP 端口；若 coturn 启用 TCP 或 TLS，先核对该文件后再调整安全组。

### 2.2 内部/本机端口（不得直接开放到公网）

| 协议/端口 | 监听位置 | 用途 | 外网开放 |
|---|---|---|---|
| TCP 8080 | `backend` Docker 容器 | Spring Boot API，context path 为 `/api`。 | 否，Nginx 通过 Docker 私有网络访问。 |
| TCP 8081 | `backend` Docker 容器 | Spring Boot Actuator：`/actuator/health`、`/actuator/prometheus`。 | 否；仅允许同一 Docker 网络中的监控采集端访问。 |
| TCP 3306 | `mysql` Docker 容器 | MySQL 业务数据库。 | 否，Compose 未发布宿主机端口。 |
| TCP 6379 | `redis` Docker 容器 | Redis 缓存/任务支持。 | 否，Compose 未发布宿主机端口。 |
| TCP 8210 | `127.0.0.1` | OpenTalking API 和 API 文档。 | 否，Nginx 的 `/opentalking/` 代理访问。 |
| TCP 5280 | `127.0.0.1` | OpenTalking Web UI。 | 否，仅服务器本机运维。 |
| TCP 5174 | 本地开发机 | Vite 开发服务器。 | 否，不是生产端口。 |
| TCP 8000 | 本地开发机（可选） | 本地 OpenTalking 测试代理目标。 | 否，仅开发调试。 |

```text
浏览器
  ├─ https://ainterview.xyz/            → TCP 443 → frontend Nginx
  ├─ https://ainterview.xyz/api/...     → Nginx → backend:8080/api/...
  └─ https://ainterview.xyz/opentalking → Nginx → OpenTalking 127.0.0.1:8210

Prometheus → backend:8081/actuator/prometheus（Docker 私有网络）

MySQL:3306 和 Redis:6379 仅在 Docker private 网络中使用。
```

## 3. 服务器文件与目录

### 3.1 AI Interview Platform

| 分类 | 服务器路径 | 更新规则 |
|---|---|---|
| 部署根目录 | `/opt/ai-interview-platform` | 平台服务根目录。 |
| Compose 生效文件 | `/opt/ai-interview-platform/docker-compose.yml` | 合并更新，禁止直接覆盖。 |
| 环境变量与秘密 | `/opt/ai-interview-platform/.env` | 敏感；不提交、不上传、不替换。 |
| TLS 证书和私钥 | `/opt/ai-interview-platform/ssl/` | 敏感；挂载到 `/etc/nginx/ssl:ro`，更新前备份。 |
| 生产 Nginx 模板 | `/opt/ai-interview-platform/nginx/default.conf.template` | 保留证书路径、80/443 监听与代理规则。 |
| 发布包暂存 | `/home/admin/ai-upload/` | 上传后校验；完成后移入部署根目录或清理。 |
| 更新备份 | `/opt/ai-interview-platform/backups/` | 保存 Compose、`.env`、SSL、Nginx 和 MySQL 备份。 |
| 空库初始化 SQL | `/opt/ai-interview-platform/docs/database/docker-init/01-ai-interview-init.sql` | 只对首次创建的空 MySQL 卷生效。 |

### 3.2 Docker 数据卷

| 命名卷 | 容器挂载点 | 保存内容 | 维护规则 |
|---|---|---|---|
| `mysql_data` | `/var/lib/mysql` | 业务表、Flyway 历史、提示词、审计。 | 更新前导出 MySQL；不得普通更新时删除。 |
| `redis_data` | `/data` | Redis AOF 与缓存/运行状态。 | 不对公网开放；按需备份。 |
| `media_data` | `/app/data/media` | 上传的简历、普通媒体，以及语音/视频面试的按题 WebM 录制分段。 | 与 MySQL 一起纳入备份；录制功能上线后持续监控容量。 |

V19 录制媒体不写入 MySQL，也不常驻 Java 堆：浏览器按题生成 WebM，后端流式落盘并计算摘要，MySQL 只保存媒体 ID、题目关联和毫秒时间轴。默认每个上传分段上限为 100MB，由 `.env` 中的 `MEDIA_MAX_UPLOAD_BYTES`、`MEDIA_MAX_UPLOAD_SIZE` 和 `MEDIA_MAX_REQUEST_SIZE` 共同控制，三者调整时必须保持一致。

生产环境应为录制数据制定保留周期。使用量增长后，优先迁移至私有 OSS，并配置服务端加密、生命周期清理和受控下载；不要长期无限扩张 ECS 系统盘上的 Docker 卷。

Docker 卷物理路径由 Docker 管理，不要硬编码。通过下面命令查看实际挂载点：

```bash
cd /opt/ai-interview-platform
docker volume ls
docker volume inspect ai-interview-platform_mysql_data
docker volume inspect ai-interview-platform_redis_data
docker volume inspect ai-interview-platform_media_data
```

卷名前缀取决于 Compose 项目名；若名称不同，先执行 `docker volume ls` 再替换命令中的名称。

### 3.3 OpenTalking 与 TURN

| 分类 | 服务器路径 | 更新规则 |
|---|---|---|
| OpenTalking 运行根 | `/opt/digital_human/opentalking` | 禁止用发布包整体覆盖。 |
| Python 虚拟环境 | `/opt/digital_human/opentalking/.venv` | 随 OpenTalking 环境维护。 |
| OpenTalking 配置 | `/opt/digital_human/opentalking/.env` | 模型、TTS/STT、WebRTC、TURN 等服务器私密配置。 |
| OpenTalking 日志 | `/opt/digital_human/opentalking/logs/unified.log` | 更新后检查启动和媒体错误。 |
| 只读播报补丁目标 | `opentalking/pipeline/speak/synthesis_runner.py` | 先检查是否已应用，再备份后打补丁。 |
| TURN 配置 | `/etc/coturn/turnserver.conf` | 保留凭据、端口范围和公网网络策略。 |

## 4. 更新保护清单

| 内容 | 随发布包更新 | 必须保留服务器副本 |
|---|---:|---:|
| 后端/前端镜像与业务代码 | 是 | 否 |
| `docs/`、补丁、无密钥配置样例 | 是 | 否 |
| `.env`、`ssl/`、生产 Nginx 模板 | 否 | 是 |
| `mysql_data`、`redis_data`、`media_data` | 否 | 是 |
| OpenTalking `.env`、TURN 配置、模型运行目录 | 否 | 是 |
| 已发布/待发布 Flyway 迁移 V1、V9–V26 | 否 | 不手工重跑 |

代码库当前最新迁移为 V26；生产数据库版本必须在服务器查询 `flyway_schema_history`，不能根据旧文档推断。后续数据库结构变更从 V27 起新增 Flyway 脚本。应用镜像回退不等于数据库回退，数据库回退依赖更新前的 MySQL 备份；发布学习资料 PDF、录制与工单附件能力前还需估算并验证 `media_data` 可用空间和保留周期。

## 5. 更新前后检查

### 更新前

```bash
sudo -i
cd /opt/ai-interview-platform

stamp=$(date +%Y%m%d-%H%M%S)
backup="backups/pre-update-$stamp"
mkdir -p "$backup"
cp -a docker-compose.yml "$backup/"
[ -f .env ] && cp -a .env "$backup/"
[ -d ssl ] && cp -a ssl "$backup/"
[ -d nginx ] && cp -a nginx "$backup/"

docker compose exec -T mysql sh -c \
'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
> "$backup/mysql-before-update.sql"
```

1. 对比发布包 Compose 与服务器 Compose，保留 `443:443`、SSL 与 Nginx 挂载。
2. 记录 `docker compose ps`、Flyway 版本和 OpenTalking `runtime-config`。
3. 禁止执行 `docker compose down -v`，禁止手工执行已发布 Flyway SQL。

### 更新后

```bash
cd /opt/ai-interview-platform
docker compose config --quiet
docker compose run --rm --no-deps frontend nginx -t
docker compose up -d --force-recreate --no-deps backend
docker compose up -d --force-recreate --no-deps frontend
docker compose ps
docker compose logs --tail=100 backend
curl -s http://127.0.0.1/opentalking/health
```

还应验证 HTTPS 证书、`/api`、`/opentalking`、Flyway `flyway_schema_history`、登录、面试、报告和数字人媒体连接。V19 额外验证文字模式不录制、进入页面不自动浏览器朗读、语音/视频设备授权、720p/15fps 目标、录制中不能主动切题、结束前分段上传，以及管理端按题回放和下载鉴权。

## 6. 后续维护约定

发生以下变更时同步更新本文档：

- 新增或调整公网端口、内部端口、安全组或 TURN 端口段。
- 改动挂载卷、媒体目录、Nginx 代理、HTTPS 证书路径或域名。
- 改动 OpenTalking API/Web/TURN 端口、服务根目录或 `.env` 配置边界。
- 新增 Flyway 迁移、发布基线或备份策略。

每次生产变更后，统一更新 [项目更新日志](../CHANGELOG.md)：发布日期、基线 ID、端口/路径变更、镜像版本、Flyway 版本、备份位置和验证结果。`docs/deployment/` 只保留长期适用的操作手册。
