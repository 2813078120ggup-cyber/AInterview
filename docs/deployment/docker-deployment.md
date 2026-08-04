# Docker 部署指南

## 前置条件

- Docker Engine 26+ 与 Docker Compose v2+
- 已开放服务器入站 TCP 80；HTTPS 上线时还需开放 TCP 443
- 已准备可用的 DeepSeek API Key（不启用 AI 时可将 `DEEPSEEK_ENABLED` 设置为 `false`）

## 首次部署

在项目根目录执行：

```bash
cp .env.example .env
chmod 600 .env
```

编辑 `.env`，为 `MYSQL_ROOT_PASSWORD`、`MYSQL_APP_PASSWORD`、`REDIS_PASSWORD` 和 `JWT_SECRET` 设置不同的高强度随机值；`JWT_SECRET` 至少 32 个字符。然后填写 `DEEPSEEK_API_KEY`。

启动全部服务：

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

前端通过 `http://<服务器公网 IP>/` 访问，后端 API 由 Nginx 反向代理至 `/api`。MySQL 与 Redis 只在 Docker 私有网络中可见，不暴露宿主机端口。

## 初始化与数据持久化

首次创建 MySQL 数据卷时，MySQL 会执行 `docs/database/docker-init` 内的初始化 SQL。后续重启不会重复执行。数据库、Redis AOF 与上传媒体分别保存于 Docker 命名卷 `mysql_data`、`redis_data`、`media_data`。

不要在生产环境随意执行 `docker compose down -v`，该命令会删除上述数据卷。

## 更新

将新的项目压缩包解压覆盖代码目录后执行：

```bash
docker compose up -d --build
docker image prune -f
```

查看服务状态和日志：

```bash
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=200 frontend
```

## 生产建议

- 使用域名和 HTTPS 反向代理/证书，而非长期使用裸 IP。
- 定期备份 `mysql_data` 与 `media_data` 卷。
- `.env` 仅保留在服务器，绝不提交至 Git 仓库或上传公开位置。
- DeepSeek Key 泄露后立即在供应商控制台轮换，并同步更新服务器 `.env` 后重启后端。
