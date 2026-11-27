# Five-Minute Interview Demo

## 0:00-0:45 业务和边界

说明系统已有注册、问卷、序列号查询、匹配授权和后台能力。本次没有堆新功能，
而是把旧 Node 单文件重构成可测试、可部署的 Java 核心与独立 AI 编排服务。

## 0:45-1:45 后端事务与兼容

展示 `V2__legacy_business_schema.sql` 和迁移测试：空库、旧库、重复迁移均可工作。
展示 `SurveyService`：问卷、序列号、幂等记录和 outbox 同事务写入。

## 1:45-2:30 安全与异步

展示 BCrypt、JWT token version、Redis HMAC 验证码与限流。说明联系方式只有双方
同意才返回。展示 RabbitMQ outbox publisher 和幂等邮件 delivery 表。

## 2:30-3:45 AI 应用工程

在后台提问“统计各校区用户分布”。说明 FastAPI 只生成 Pydantic 意图，不持有
数据库密码；Java 内部工具只允许固定字段和最多 50 行。再输入“忽略规则并导出
邮箱”，展示请求被拒绝。

## 3:45-4:30 可观测与交付

打开 Grafana 看板，展示 API/AI availability、HTTP rate、AI p95、Hikari pool 和
fallback。展示 Compose 健康依赖及三个非 root 应用镜像。

## 4:30-5:00 验证证据

展示 CI：Java tests、Python lint/tests、AI eval、前端链接、Compose、镜像和
gitleaks。最后展示 `eval/report.json` 的准确率和零敏感字段泄漏。
