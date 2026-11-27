# SYSUDate 求职项目说明

## 一句话介绍

SYSUDate 是一个曾真实上线并服务高校用户的匹配系统。项目停用后，在不增加产品
功能的前提下，将旧的 Node.js 单文件后端重构为 Java 21 模块化单体与独立
FastAPI AI 编排服务，重点解决数据兼容、安全、事务、异步交付、可观测性和测试
问题。

## 版本时间线

| 阶段 | 事实 | 证据 |
|---|---|---|
| 历史上线版本 | 阿里云函数计算、RDS、OSS；完成注册、问卷、序列号、匹配和查询流程 | `docs/EVIDENCE.md` 中的脱敏截图，本地另存完整 Demo 和原始证据 |
| 工程化重构版本 | Spring Boot 核心业务、FastAPI AI、Redis、RabbitMQ、Prometheus、Grafana、Compose 和 CI | 当前源码、自动化测试、`docs/verification/` |

历史云端截图证明“项目曾上线运行”，当前代码证明“能够完成后端和 AI 应用工程化
重构”。不声称重构版本仍在线运行，也不需要为了展示而恢复停用服务器。

## 技术难点与解决方案

### 数据兼容和事务

- 使用 Flyway 兼容空库初始化与旧 MySQL 数据库升级。
- 将问卷、不可变序列号、幂等记录和 outbox 事件放在同一事务中。
- 使用数据库唯一约束和 `Idempotency-Key` 防止重复提交产生多个序列号。

### 身份与隐私

- 使用 BCrypt、短期 JWT、`tokenVersion` 和 Redis HMAC 验证码。
- 用户 JWT 与内部 SERVICE JWT 使用不同密钥；SERVICE token 带固定 audience。
- 联系方式只在双方授权后返回，公开资料按 `display_self` 做 fail-closed 过滤。

### AI 应用工程

- FastAPI 不持有数据库凭据，只把自然语言转换为 Pydantic 类型化意图。
- Java 端执行参数化白名单查询，限制字段和返回行数。
- 用户列表、个人详情和低基数敏感分组不会发送给第三方 Provider。
- Provider 超时或故障时使用确定性格式化降级，并按原因记录 Prometheus 指标。
- 固定评测集验证意图参数、提示注入拦截和敏感字段泄漏。

### 异步与可观测性

- Transactional outbox 将业务事务与 RabbitMQ 发布解耦。
- 邮件消费者通过 delivery ID 幂等处理，避免重复发送。
- Java 与 FastAPI 输出结构化日志、request ID 和 Prometheus 指标。
- Grafana 看板与告警规则随 Compose 一起配置。

## 可验证结果

- Java：45 个非 Testcontainers 测试通过；Linux CI 运行完整 Testcontainers 套件。
- AI：22 个测试通过；11/11 固定评测通过；4/4 不安全请求被拦截。该离线评测覆盖意图、参数和拦截。
- 模型出站：24 个合成场景通过，敏感标记外发从 9/24 降至 0/24，合规模型请求保留聚合数据。 [出站验证](../verification/outbound-privacy.md)
- 前端：14 个页面的链接、API 契约和运行时配置检查通过。
- 整栈：序列号契约、未登录查询拒绝、管理员登录、AI 查询、提示注入拒绝和 CORS
  均由 `scripts/stack_smoke.py` 验证。
- 浏览器：浅色主题、页面跳转、序列号查询提交和公开资料渲染由 Playwright 验证。
- 交付：Actionlint、Gitleaks、Compose 健康检查、Prometheus 和 Grafana 均纳入验证。

## 简历表述

可按实际篇幅选择以下 3 至 4 条：

- 将曾服务 1500+ 注册用户的高校匹配系统从 Node.js 单文件后端重构为 Java 21 +
  Spring Boot 3.5 模块化单体，使用 Flyway 保持旧 MySQL 数据兼容，并以事务和
  `Idempotency-Key` 保证问卷与序列号分配一致性。
- 基于 Redis 实现 HMAC 验证码、原子消费和分布式限流，使用 BCrypt、JWT
  `tokenVersion`、独立 SERVICE JWT 和授权策略保护账号与联系方式。
- 设计 FastAPI AI 编排服务，通过类型化意图和 Java 白名单查询隔离数据库，
  加入提示注入拦截、敏感数据外发策略、确定性降级、Prometheus 指标及 11 项固定
  评测。
- 使用 transactional outbox、RabbitMQ 和幂等 delivery 记录实现可靠通知；
  配套 Docker Compose、GitHub Actions、Testcontainers、Playwright、Grafana 和
  Gitleaks，形成可复现交付链路。

“1500+”来自历史真实注册数据，只用于概括产品规模；不得公开数据库导出、邮箱、
联系方式、验证码或生产凭据。

## 五分钟面试顺序

1. 用历史证据说明项目确实上线和被使用，但线上服务已停用。
2. 展示 `SurveyService` 和 Flyway 迁移，说明序列号一致性和旧数据兼容。
3. 展示 JWT、Redis 验证码、联系方式授权和 outbox。
4. 展示 FastAPI 类型化意图、Java 查询白名单和危险提示词 400 响应。
5. 展示 CI、测试报告、Grafana 和 `scripts/stack_smoke.py`。

详细演示台词见 `docs/demo/five-minute-demo.md`。

## 面试边界

- 不把“模块化单体”包装成微服务；当前规模下它减少了分布式事务复杂度。
- 不声称重构版本仍在生产运行；它在本地 Compose 和 CI 中验证。
- 不声称 AI 直接访问数据库；AI 只能调用 Java 的类型化查询工具。
- 不公开真实用户数据；仓库只保留脱敏截图和汇总口径。
