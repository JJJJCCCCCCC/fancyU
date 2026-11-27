# Legacy Node Reference

此目录保存重构前的 Node.js 行为基线，仅用于核对旧前端请求和历史数据库字段。

- 不在 `compose.yaml`、Dockerfile 或 CI 运行路径中。
- 不应重新部署到服务器，也不应继续执行其中的运行时 DDL。
- Java API 与 Flyway 已成为业务逻辑和数据库结构的唯一维护入口。
- 确认生产切换及回滚窗口结束后，可以从发布包中永久排除此目录。
