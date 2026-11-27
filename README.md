# FancyU（中意你）

![API 包版本 1.0.0](https://img.shields.io/badge/API-1.0.0-blue) ![Node.js](https://img.shields.io/badge/Node.js-22%2B-339933?logo=nodedotjs) ![许可证未声明](https://img.shields.io/badge/License-未声明-lightgrey)

FancyU 是一个面向高校学生的校园交友与恋爱匹配平台。学生通过校园邮箱注册，填写交友或恋爱问卷，查看匹配记录，并在双方同意后交换联系方式。

本仓库面向希望学习或复现校园匹配应用的开发者。项目曾部署在阿里云函数计算、RDS MySQL 和 OSS，现作为源码和历史运行材料保留。

![FancyU 移动端首页](docs/screenshots/product-home-detail.jpg)

## 目录

- [功能与技术栈](#features)
- [快速开始](#quickstart)
- [配置和 API](#usage)
- [项目结构](#structure)
- [开发与测试](#development)
- [贡献方式](#contributing)
- [许可证](#license)
- [致谢](#acknowledgements)

<a id="features"></a>

## 功能与技术栈

| 功能 | 实现 |
| --- | --- |
| 账号与资料 | 校园邮箱验证码注册、登录、密码重置、资料管理和旧序列号绑定 |
| 问卷与匹配 | 恋爱和交友问卷、参与开关、匹配记录、双方同意后交换联系方式 |
| 投诉与管理 | 投诉举报、管理员处理、用户检索与运营统计 |
| 管理查询 | 管理员可通过配置的 LLM 接口辅助查询 |

前端以 HTML、CSS 和原生 JavaScript 实现。后端使用 Node.js、Express、MySQL 和 mysql2，通过 JWT 管理登录态、用 bcryptjs 哈希密码，并通过 Nodemailer 连接 SMTP。历史部署由 OSS 提供静态页面、函数计算运行 API、RDS 提供 MySQL。

<a id="quickstart"></a>

## 快速开始

下面的命令用 Node.js 22 或较新的版本执行，并依赖 npm 和 Git。API 包版本取自 [`fc-code/package.json`](fc-code/package.json)。项目当前未配置自动构建工作流，因此只展示包版本徽章。

1. 在仓库页面通过 **Code** 获取克隆地址，在本地克隆后进入根目录。
2. 复制变量模板。如果根目录已经有 `.env`，保留该文件并按下一节检查配置。

   ```bash
   cp .env.example .env
   ```

   PowerShell 使用 `Copy-Item .env.example .env`。

3. 填好 `MYSQL_HOST`、`MYSQL_USER`、`MYSQL_PASSWORD` 和 `JWT_SECRET`，然后启动 API。仅检查服务是否启动时，可以保留模板的本地 MySQL 地址并使用测试库配置。健康检查只验证进程，业务接口访问数据库时才创建连接池。

   ```bash
   cd fc-code
   npm ci
   node --env-file=../.env index.js
   ```

4. 在另一个终端检查服务。

   ```bash
   curl -fsS http://127.0.0.1:9000/health
   ```

   PowerShell 可用 `Invoke-RestMethod http://127.0.0.1:9000/health`。返回 JSON 中的 `status` 为 `ok` 时，说明 API 进程已启动。数据库和邮件需通过对应业务流程另行验证。

<a id="usage"></a>

## 配置和 API

### 环境变量

配置模板位于 [`.env.example`](.env.example)。以上启动命令通过 Node.js 的 `--env-file` 加载根目录下的 `.env`。

| 配置项 | 用途 |
| --- | --- |
| `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_DATABASE` | MySQL 连接参数。模板使用本地地址、`3306` 和测试库 `sysudate_test`。 |
| `JWT_SECRET` | 签发账号和管理员令牌的密钥。启动时必填，使用为当前环境生成的随机值。 |
| `ACCOUNT_NAME`、`FROM_ALIAS`、`SMTP_HOST`、`SMTP_PORT`、`SMTP_PASSWORD` | 邮件发送配置。注册验证码和密码重置需要可用的 SMTP。 |
| `ADMIN_USERNAME`、`ADMIN_PASSWORD` | 管理员登录配置。只在需要验证后台功能时使用测试账号。 |
| `LLM_API_URL` | 管理员 LLM 辅助查询地址。联调该功能时填写自己的测试服务地址。 |

### 数据库与前端联调

业务联调需要 MySQL 8.0 测试库。仓库内的 SQL 面向已有旧版结构，基础迁移会修改预先存在的 `users` 表。它们无法单独从空数据库初始化整套应用。准备与旧版兼容的测试结构后，按 `migration.sql`、`migration_v3.sql`、`migration_v3_fix.sql`、`migration_v4.sql` 的顺序**审阅并应用当前数据库仍缺少的迁移**。保留历史字段与结构要求，用合成数据验证功能。

前端页面为静态 HTML。联调前，将要访问的页面里的 API 地址改为 `http://127.0.0.1:9000`，然后从仓库根目录启动静态服务。以下示例需要 Python 3。

```bash
python -m http.server 8080 --bind 127.0.0.1 --directory frontend
```

打开 [本地首页](http://127.0.0.1:8080/)，用测试数据走一遍登录、问卷、匹配记录和联系方式同意流程。验证邮箱时使用测试邮箱服务和系统允许的邮箱域。

### API 示例

| 接口 | 用途 | 授权 |
| --- | --- | --- |
| `GET /health` | 读取进程状态、时间戳和运行时长 | 无 |
| `POST /account/register` | 使用邮箱验证码注册 | 无 |
| `POST /account/login` | 通过邮箱或用户名登录 | 无 |
| `GET /account/me` | 获取当前账号资料 | 账号 JWT |
| `POST /?action=submitSurvey` | 提交恋爱问卷 | 支持已登录账号及旧版序列号流程 |
| `GET /account/matches` | 查询当前账号的匹配记录 | 账号 JWT |
| `POST /account/exchange-contact` | 对指定匹配提交交换联系方式的同意 | 账号 JWT |
| `GET /admin/stats` | 读取后台运营统计 | 管理员 JWT |

数据库和测试账号准备好后，可从终端登录并查询资料。示例使用 Bash 和 curl。

```bash
curl -sS http://127.0.0.1:9000/account/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"your-test-username","password":"your-test-password"}'

TOKEN='替换为登录响应顶层 token 字段的值'
curl -sS http://127.0.0.1:9000/account/me \
  -H "Authorization: Bearer ${TOKEN}"
```

调用业务接口时同时检查 HTTP 状态和 JSON 中的 `code`。部分业务错误通过响应体返回。

<a id="structure"></a>

## 项目结构

| 路径 | 职责 |
| --- | --- |
| [frontend/](frontend/) | 首页、注册登录、问卷、匹配结果、个人资料和管理页面 |
| [fc-code/index.js](fc-code/index.js) | Express 路由、登录态、问卷和匹配逻辑、邮件与管理接口 |
| [fc-code/](fc-code/) | 依赖清单、MySQL 迁移和阿里云函数计算启动脚本 |
| [docs/EVIDENCE.md](docs/EVIDENCE.md) | 产品页面、部署和运行截图的索引 |
| [upload_frontend.sh](upload_frontend.sh) | 历史 OSS 上传脚本，默认指向原部署存储桶 |
| [.env.example](.env.example) | 本地配置模板 |

<a id="development"></a>

## 开发与测试

前端使用静态文件，无需打包。后端在 `fc-code/` 目录安装锁定依赖，通过 Node.js 直接运行。当前包没有定义 `npm run build` 或自动测试脚本。修改后可先检查语法，再执行上面的接口和浏览器联调。

```bash
cd fc-code
npm ci
node --check index.js
```

手动验证时，至少检查健康接口、测试账号登录、问卷提交和已登录用户可访问的匹配记录。修改匹配或联系方式逻辑时，分别检查单方和双方同意的状态。修改管理接口时，用测试管理员验证权限边界。历史截图和说明见 [展示证据索引](docs/EVIDENCE.md)。

<a id="contributing"></a>

## 贡献方式（Contributing）

欢迎提交 Issue 或 Pull Request。请附上运行环境、复现步骤、预期结果和验证结果。涉及页面的改动可附脱敏截图，涉及数据库结构的改动请说明适用的旧版本和迁移顺序。提交前请移除真实测试账号、邮箱、凭据和数据库导出。

<a id="license"></a>

## 许可证（License）

仓库当前未声明项目许可证，也未包含 `LICENSE` 文件。复制、修改或分发源码前，请向权利人确认授权。各第三方组件适用各自的许可证。

<a id="acknowledgements"></a>

## 致谢（Acknowledgements）

感谢 Node.js、Express、mysql2、Nodemailer、bcryptjs 和 jsonwebtoken 为项目提供运行时、HTTP、数据库、邮件及认证能力。
