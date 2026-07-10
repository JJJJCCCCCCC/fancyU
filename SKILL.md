# SYSUDate 系统维护技能手册

## 🎯 系统概述

SYSUDate 是基于阿里云 Serverless 架构的校园交友匹配平台，使用函数计算处理请求，MySQL 存储数据，OSS 存储前端页面。支持账号密码登录 + 序列号绑定双模式。

---

##  常见操作指南

### 1. 函数代码部署

```bash
cd /home/sse/sysudate/fc-code
zip -r /tmp/fc-code.zip index.js package.json node_modules/
aliyun oss cp /tmp/fc-code.zip oss://sysudate-temp-cn-hangzhou/fc-code.zip --region cn-hangzhou --force
aliyun fc update-function --function-name sysudate-data --region cn-hangzhou \
  --code '{"ossBucketName": "sysudate-temp-cn-hangzhou", "ossObjectName": "fc-code.zip"}'
```

### 2. 前端页面部署

```bash
# 全部上传
for f in survey friend_survey modify query dashboard login register bind index profile admin forgot-password; do
  aliyun oss cp frontend/${f}.html oss://sysu-date/${f}.html --region cn-hongkong --force
done
```

### 3. 同步 OSS 到本地

```bash
aliyun oss cp oss://sysu-date/ /home/sse/sysudate/oss/ --region cn-hongkong --force -r
```

### 4. 环境变量更新

```bash
aliyun fc update-function --function-name sysudate-data --region cn-hangzhou \
  --environment-variables \
    LD_LIBRARY_PATH=/code:/code/lib:/usr/lib:/opt/lib:/usr/local/lib \
    MYSQL_HOST=rm-bp13dsnzl0d761021.mysql.rds.aliyuncs.com \
    MYSQL_PASSWORD='<your-mysql-password>' \
    MYSQL_USER='<your-mysql-user>' \
    NODE_PATH=/opt/nodejs/node_modules \
    PATH=/var/fc/lang/nodejs18/bin:/usr/local/bin/apache-maven/bin:/usr/local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/ruby/bin:/opt/bin:/code:/code/bin \
    SMTP_PASSWORD='<your-smtp-password>' \
    JWT_SECRET='<generate-a-long-random-secret>' \
    ADMIN_USERNAME='<your-admin-username>' \
    ADMIN_PASSWORD='<your-admin-password>'
```

### 5. 数据库操作

```bash
# 查看表结构
mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" -p \
  -e "USE sysudate; SHOW TABLES;"

# 查看账号数
mysql -h "$MYSQL_HOST" -u "$MYSQL_USER" -p \
  -e "USE sysudate; SELECT COUNT(*) FROM accounts;"
```

### 6. LLM API 代理配置

通过 autossh 反向代理连接 LLM 推理服务：

```bash
# 启动 SSH 隧道（将本机 18083 端口映射到远程 8888 端口）
sshpass -e autossh -M 0 -f -N \
  -o "ServerAliveInterval 15" -o "ServerAliveCountMax 3" \
  -o "ExitOnForwardFailure yes" -o "StrictHostKeyChecking no" \
  -R *:8888:localhost:18083 sse@ssemarket.cn -p 22

# 测试 LLM API
curl http://ssemarket.cn:8888/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model": "/home/sse/model/qwen3.5-2b", "messages": [{"role": "user", "content": "你好"}], "max_tokens": 50}'
```

---

## � 故障排查

### 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 验证码错误或已过期 | checkCode 删除了验证码，注册时再验证已不存在 | v2.1.0 已修复：checkCode 只验证不删除 |
| 请求过于频繁 | 频率限制过低 | 已调整为 5次/分钟 |
| 按钮重复提交 | 前端无防抖 | v2.1.0 已添加按钮禁用+状态文字 |
| 注册时间不显示 | /account/me 未返回 created_at | v2.1.0 已修复 |
| 老页面无需登录 | survey/modify/query 未接入JWT | v2.1.0 已添加JWT认证拦截 |
| 登录暴力破解 | 无登录限流 | v2.4.0 已添加：1分钟3次限流 |
| LLM幻觉严重 | 直接查询无上下文 | v2.4.0 已添加RAG增强 |
| 匹配开关单一 | 只有一个总开关 | v2.3.0 已拆分为恋爱/交友独立开关 |
| 注册/登录网络错误 | 前端调用不存在端点 | v2.4.1 已修复：send-code/check-code改为POST / |
| 注销后残留数据 | 未清理account_preferences | v2.4.1 已修复：删除时级联清理 |
| 数据库连接超时 | 连接池无超时配置 | v2.4.2 已修复：添加connectTimeout/acquireTimeout/timeout |
| 无健康检查 | 无法监控服务状态 | v2.4.2 已添加：/health端点 |
| CORS配置不完整 | 缺少Credentials头 | v2.4.2 已修复：添加Access-Control-Allow-Credentials |
| 日志泄露敏感信息 | 日志输出密码和邮箱 | v2.4.2 已修复：移除敏感信息日志 |
| 无匹配历史 | 用户无法查看历史匹配 | v2.5.0 已添加：match_history表，/account/matches接口 |
| 联系方式直接暴露 | 无双方同意机制 | v2.5.0 已添加：user_agreed/match_agreed双向确认 |
| 隐私协议虚假承诺 | AES-256和物理分离不实 | v2.5.1 已修复：删除不实描述 |
| 注销后match_history残留 | 未清理匹配历史 | v2.5.1 已修复：级联删除 |
| 无投诉举报机制 | 用户无法举报不良行为 | v2.5.1 已添加：complaints表+举报API |
| 无敏感词过滤 | 用户可提交敏感内容 | v2.5.1 已添加：问卷敏感词检测 |
| AI助手对话丢失 | 对话存在localStorage，刷新丢失 | v2.6.0 已修复：迁移到数据库持久化 |
| LLM查询准确率低 | 意图识别不够精准，数据重复 | v2.6.0 已优化：15种查询类型，精准参数提取 |
| LLM回答混乱 | 缺少结果分析和格式指导 | v2.6.0 已优化：增强system prompt+结果摘要 |

### 查看日志

```bash
aliyun fc GetFunction logs --region cn-hangzhou --functionName sysudate-data
```

---

## 🔐 安全配置

### 账号系统安全

- JWT_SECRET: 必须通过环境变量配置，使用长随机字符串，禁止提交真实值
- 密码: bcrypt 加密存储
- Token 有效期: 30 天
- CORS: 限制为 `sysudate.cn`
- SQL 注入: 参数化查询
- XSS: 输入转义处理
- 数据库、SMTP、管理员密码等敏感值: 统一放入部署环境变量或本地未提交的 `.env`
- 频率限制: 所有 API 均有保护
- 登录限流: 1分钟3次，IP级限制
- 迁移端点: 已禁用
- 管理员密码: 环境变量配置，bcrypt加密
- XSS防护: 输入转义+Content-Type限制
- SQL注入: 参数化查询

### 邮箱设计

| 类型 | 说明 | 可见性 |
|------|------|--------|
| verify_email | 账号绑定，必须 *.sysu.edu.cn | 他人不可见 |
| contact_email | 联系邮箱，用户自定义 | 用于匹配通知 |

---

## 📦 组件信息

| 组件 | 名称 | 地域 | 备注 |
|------|------|------|------|
| FC | sysudate-data | cn-hangzhou | 公网: fcapp.run |
| RDS | rm-bp13dsnzl0d761021 | cn-hangzhou | 到期 2026-05-08 |
| OSS(前端) | sysu-date | cn-hongkong | 静态页面 |
| OSS(部署) | sysudate-temp-cn-hangzhou | cn-hangzhou | FC代码包 |

---

## 🔄 变更记录

| 日期 | 版本 | 变更内容 |
|------|------|----------|
| 2026-04-09 | v1.0.0 | 初始版本，序列号认证 |
| 2026-04-29 | v2.0.0 | 新增账号系统，JWT认证，前端登录页面 |
| 2026-04-30 | v2.1.0 | 前端JWT拦截，双邮箱设计，按钮防抖，安全加固，验证码bug修复 |
| 2026-05-01 | v2.2.0 | 管理后台(admin.html)，Chart.js中文修复，CORS更新，环境变量安全化 |
| 2026-05-02 | v2.3.0 | 个人中心(profile.html)，恋爱/交友匹配独立开关，account_preferences表 |
| 2026-05-08 | v2.4.0 | AI智能查询(LLM RAG)，登录限流(1分钟3次)，安全加固 |
| 2026-05-09 | v2.4.1 | 修复前端404端点(send-code/check-code)，修复注销残留数据，统一IP获取方式 |
| 2026-05-12 | v2.4.2 | 数据库连接池超时配置，健康检查端点，CORS增强，日志脱敏，请求日志中间件 |
| 2026-05-16 | v2.5.1 | 投诉举报机制，敏感词过滤，撤回同意功能，隐私协议修复 |
| 2026-05-17 | v2.6.0 | AI助手对话持久化（数据库存储），15种查询类型意图识别，LLM提示词优化 |

---

最后更新: 2026-05-17
