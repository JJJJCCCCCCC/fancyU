# Server And Upload Manifest

## 必须保留在服务器

这些内容是服务器状态，不应被本地文件覆盖或上传到 Git：

- 生产 `.env.compose` 或平台 Secret 配置。
- Docker volumes：`mysql-data`、`redis-data`、`rabbitmq-data`、
  `prometheus-data`、`grafana-data`。
- MySQL 全量备份、binlog、对象存储文件和用户上传资源。
- 域名、TLS 证书、云防火墙、安全组和宿主机反向代理配置。
- 生产 SMTP、数据库、JWT、RabbitMQ 和 Grafana 凭据。

升级前必须先备份 MySQL。不要执行 `docker compose down -v`，不要上传本地空数据库
覆盖生产卷。

## 需要重新上传或从 Git 拉取

### Compose Web 模式

本次重构后的服务器发布包应包含：

- `apps/api/`
- `apps/ai-service/`
- `frontend/`
- `infra/`
- `compose.yaml`
- `.env.compose.example`
- `scripts/`
- `docs/contracts/`
- `docs/deployment/`
- `README.md`

如果服务器使用 CI/CD，应发布本分支对应提交构建出的 API、AI、Web 三个镜像，
而不是手动复制 `target/`、`__pycache__/` 或测试缓存。

生产 `.env.compose` 中的 `DATABASE_URL`、`DATABASE_USERNAME`、
`DATABASE_PASSWORD` 必须指向现有生产数据库（例如原 RDS），不能照抄示例中的
`mysql:3306` 本地地址。Compose 内置 MySQL 位于 `local-db` profile，只用于本地
开发或新环境；生产启动命令不要添加 `--profile local-db`。

### OSS 静态前端模式

如果继续使用原 OSS 托管前端，不要直接上传 HTML。先确保 Java API 有公网 HTTPS
地址，并在生产 `.env.compose` 中把 `CORS_ALLOWED_ORIGINS` 设置为精确的 OSS
站点 Origin（多个值用英文逗号分隔，不要使用 `*`），然后执行：

```sh
API_BASE_URL=https://api.example.com ./upload_frontend.sh
```

脚本会上传全部 HTML，并生成、上传指向该 API 的 `config.js`。遗漏 `config.js`
或把它配置为 OSS 同源 `/api` 都会导致业务请求 404。

## 不需要上传到运行服务器

- `fc-code/`：旧 Node 行为基线。
- `.github/`：只供 GitHub Actions 使用。
- `.gitleaks.toml`：源码发布和 CI 扫描配置，运行服务器不读取。
- `docs/screenshots/`、`docs/superpowers/`、`docs/demo/`、`docs/verification/`：
  作品集和研发证据。
- `apps/**/src/test/`、Python `tests/` 与 `eval/`：镜像构建上下文可保留，但运行时
  镜像不会复制。
- `.git/`、`.worktrees/`、`target/`、`__pycache__/`、`.pytest_cache/`。

## 推荐升级步骤

1. 备份生产 MySQL 并记录当前镜像标签。
2. 上传或拉取新代码，只创建新的 `.env.compose` 缺失项，不覆盖现有生产值。
3. 核对 `DATABASE_URL` 指向原生产库，并记录升级前账户/问卷行数作为校验基线。
4. 先执行 `docker compose config --quiet`。
5. 构建带不可变版本标签的 API、AI 和 Web 镜像。
6. 执行 `docker compose up -d --wait`；Flyway 在 `DATABASE_URL` 指向的旧库上迁移。
7. 对比升级前后的账户/问卷行数，再验证 Web、API、AI、Prometheus 和 Grafana。
8. 验证登录、序列号查询、问卷提交、匹配同意和管理员 AI 查询。
9. 保留旧镜像和数据库备份，完成观察窗口后再清理。

回滚应用时切回旧镜像标签。数据库迁移是向前兼容的新增表/列，不要未经验证手工
删除新列或 Flyway history。
