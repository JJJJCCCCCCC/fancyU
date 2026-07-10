# SYSUDate 系统文档

## 📋 系统概述

基于阿里云函数计算的校园交友匹配平台，包含函数计算(FC)、关系数据库(RDS MySQL)和对象存储(OSS)。支持账号密码登录 + 序列号绑定双模式。

## 🏗️ 系统架构

```
用户请求
    ↓
OSS 静态页面 (sysu-date bucket, cn-hongkong)
    ↓
函数计算 (FC) - sysudate-data (cn-hangzhou)
    ↓
数据库 (RDS MySQL) - sysudate (cn-hangzhou)
```

## 📦 组件详情

### 1. 函数计算 (Function Compute)

**函数名称**: sysudate-data
- **地域**: cn-hangzhou (华东1杭州)
- **运行时**: custom.debian10 (Node.js)
- **内存**: 512 MB | **超时**: 60秒 | **实例并发**: 20
- **公网URL**: https://sysudate-data-rumzgdjkdp.cn-hangzhou.fcapp.run

**环境变量**:
```
MYSQL_HOST=rm-bp13dsnzl0d761021.mysql.rds.aliyuncs.com
MYSQL_USER=<your-mysql-user>
MYSQL_PASSWORD=<your-mysql-password>
SMTP_PASSWORD=<your-smtp-password>
JWT_SECRET=<generate-a-long-random-secret>
ADMIN_USERNAME=<your-admin-username>
ADMIN_PASSWORD=<your-admin-password>
```

真实部署值只应配置在阿里云函数计算环境变量或本地未提交的 `.env` 中；仓库只保留 `.env.example` 模板，避免数据库、邮件和 JWT 凭据泄露。

**VPC配置**:
- VPC: vpc-bp1csviteo2w98ogfjhp0
- VSwitch: vsw-bp1sfx6gzsdidc5qrfbi8
- 安全组: sg-bp17pgifvjoug3rp08pw

### 2. 数据库 (RDS MySQL)

**实例ID**: rm-bp13dsnzl0d761021
- **引擎**: MySQL 8.0 | **地域**: cn-hangzhou
- **数据库名称**: sysudate | **用户名**: sysudate
- **到期时间**: 2026-05-08

### 3. 对象存储 (OSS)

| Bucket | 地域 | 用途 |
|--------|------|------|
| sysu-date | cn-hongkong | 前端静态页面 |
| sysudate-temp-cn-hangzhou | cn-hangzhou | FC 代码部署包 |

## 🔌 API 接口文档

### 序列号认证 API

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/` | action=generateSerial&email | 生成序列号 |
| GET | `/` | action=query&serial | 查询用户资料 |
| GET | `/` | action=getSurveyData&serial&email&code | 获取问卷数据 |
| POST | `/` | action=sendCode&email&source | 发送验证码 |
| POST | `/` | action=checkCode&email&code | 验证验证码（不删除） |
| POST | `/` | action=verifyMatch&serial&email | 验证序列号匹配 |
| POST | `/` | action=submitSurvey&data | 提交问卷 |
| POST | `/` | action=modifySurvey&serial&email&data | 修改问卷 |

### 账号系统 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/account/register` | 注册（需邮箱验证码） |
| POST | `/account/bind` | 绑定序列号到账号 |
| POST | `/account/login` | 登录（邮箱或用户名） |
| GET | `/account/me` | 获取当前用户信息（需Token） |
| POST | `/account/change-password` | 修改密码（需Token） |
| POST | `/account/reset-password` | 重置密码（需验证码） |
| POST | `/account/change-username` | 修改用户名（需Token） |
| POST | `/account/change-contact-email` | 修改联系邮箱（需Token） |
| POST | `/account/delete` | 注销账号（需Token） |
| GET | `/account/check-username` | 检查用户名是否可用 |
| POST | `/account/toggle-matching` | 切换恋爱匹配参与状态（需Token） |
| POST | `/account/toggle-friend-matching` | 切换交友匹配参与状态（需Token） |

### 管理员 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/login` | 管理员登录（限流保护） |
| GET | `/admin/users` | 用户列表查询（分页/筛选/搜索） |
| GET | `/admin/user/:serial` | 单个用户详情 |
| GET | `/admin/stats` | 数据统计（Chart.js） |
| DELETE | `/admin/user/:serial` | 删除用户 |
| GET | `/admin/chat/list` | AI助手对话列表 |
| POST | `/admin/chat/save` | 保存AI助手对话 |
| DELETE | `/admin/chat/:id` | 删除AI助手对话 |
| POST | `/admin/check-password` | 验证管理员密码 |

### LLM API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/llm/chat` | AI智能查询（意图识别 + RAG增强 + 对话上下文） |

### 投诉举报 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/complaint` | 提交投诉举报（需Token） |
| GET | `/admin/complaints` | 管理员查看投诉列表 |
| POST | `/admin/complaint/:id/status` | 管理员处理投诉 |

### 匹配相关 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/account/matches` | 查看匹配历史（需Token） |
| POST | `/account/exchange/:matchId` | 同意交换联系方式（需Token） |
| POST | `/account/agree-exchange/:matchId` | 确认收到联系方式（需Token） |

### 请求示例

**注册**:
```json
POST /account/register
{ "email": "xxx@mail.sysu.edu.cn", "code": "123456", "password": "xxx", "username": "可选" }
```

**登录**:
```json
POST /account/login
{ "login": "xxx@mail.sysu.edu.cn", "password": "xxx" }
```

**使用Token**:
```
GET /account/me
Authorization: Bearer <token>
```

## 📄 前端页面

| 文件 | 说明 | 认证 |
|------|------|------|
| `index.html` | 首页 | 无 |
| `login.html` | 账号登录 | 无 |
| `register.html` | 账号注册 | 无 |
| `bind.html` | 绑定序列号 | 无 |
| `forgot-password.html` | 忘记密码 | 无 |
| `profile.html` | 个人中心（含匹配开关） | JWT |
| `dashboard.html` | 用户中心（旧版） | JWT |
| `survey.html` | 恋爱问卷填写 | JWT |
| `friend_survey.html` | 交友问卷填写 | JWT |
| `modify.html` | 修改资料 | JWT |
| `query.html` | 查询匹配 | JWT |
| `admin.html` | 管理后台（含AI助手） | Admin JWT |

### 邮箱设计

| 类型 | 说明 | 来源 | 可见性 |
|------|------|------|--------|
| verify_email | 账号绑定邮箱，必须 *.sysu.edu.cn | 注册时验证 | 他人不可见 |
| contact_email | 联系邮箱，接收匹配结果 | 问卷中填写 | 用户自定义 |

## 🗄️ 数据库结构

### accounts 表（账号）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT AUTO_INCREMENT | 主键 |
| email | VARCHAR(255) UNIQUE | 验证邮箱 |
| username | VARCHAR(50) UNIQUE | 用户名 |
| password_hash | VARCHAR(255) | bcrypt加密密码 |
| email_verified | BOOLEAN | 邮箱是否验证 |
| created_at | TIMESTAMP | 注册时间 |
| last_login_at | TIMESTAMP | 最后登录时间 |

### users 表（问卷数据）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT AUTO_INCREMENT | 主键 |
| account_id | INT NULL | 关联账号ID |
| serial_number | VARCHAR(50) UNIQUE | 序列号 |
| email | VARCHAR(255) | 验证邮箱 |
| contact_email | VARCHAR(255) | 联系邮箱 |
| ... | ... | 问卷字段（恋爱+交友双问卷） |

### account_preferences 表（匹配偏好）
| 字段 | 类型 | 说明 |
|------|------|------|
| account_id | INT PRIMARY KEY | 关联账号ID |
| join_matching | TINYINT(1) DEFAULT 1 | 恋爱匹配开关 |
| join_friend_matching | TINYINT(1) DEFAULT 1 | 交友匹配开关 |
| updated_at | TIMESTAMP | 更新时间 |

### verification_codes 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT AUTO_INCREMENT | 主键 |
| email | VARCHAR(255) | 邮箱 |
| code | VARCHAR(10) | 验证码 |
| expire_at | TIMESTAMP | 过期时间 |
| source | VARCHAR(20) | 来源 |
| send_at | TIMESTAMP DEFAULT NOW() | 发送时间 |

### match_history 表（匹配历史）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT AUTO_INCREMENT | 主键 |
| account_id | INT | 发起匹配的账号ID |
| match_account_id | INT | 匹配到的账号ID |
| match_type | VARCHAR(20) | 匹配类型（love/friend） |
| matched_at | TIMESTAMP | 匹配时间 |
| user_agreed | TINYINT(1) DEFAULT 0 | 用户是否同意交换联系方式 |
| match_agreed | TINYINT(1) DEFAULT 0 | 对方是否同意交换联系方式 |
| contact_exchanged | TINYINT(1) DEFAULT 0 | 联系方式是否已交换 |

### complaints 表（投诉举报）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT AUTO_INCREMENT | 主键 |
| reporter_account_id | INT | 举报人账号ID |
| target_serial | VARCHAR(50) | 被举报人序列号 |
| reason | VARCHAR(50) | 举报原因分类 |
| description | TEXT | 详细描述 |
| status | VARCHAR(20) DEFAULT 'pending' | 处理状态（pending/dismissed/resolved） |
| admin_note | TEXT | 管理员备注 |
| created_at | TIMESTAMP | 举报时间 |
| updated_at | TIMESTAMP | 处理时间 |

### chat_sessions 表（AI助手对话）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(36) | UUID主键 |
| admin_username | VARCHAR(50) | 管理员用户名 |
| title | VARCHAR(255) | 对话标题 |
| messages | JSON | 对话消息列表 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

## 🛠️ 维护指南

### 部署函数代码
```bash
cd /home/sse/sysudate/fc-code
zip -r /tmp/fc-code.zip index.js package.json node_modules/
aliyun oss cp /tmp/fc-code.zip oss://sysudate-temp-cn-hangzhou/fc-code.zip --region cn-hangzhou --force
aliyun fc update-function --function-name sysudate-data --region cn-hangzhou \
  --code '{"ossBucketName": "sysudate-temp-cn-hangzhou", "ossObjectName": "fc-code.zip"}'
```

### 前端页面部署

```bash
# 批量上传所有前端页面
for f in survey friend_survey modify query dashboard login register bind index profile admin forgot-password; do
  aliyun oss cp frontend/${f}.html oss://sysu-date/${f}.html --region cn-hongkong --force
done
```

### 同步 OSS 到本地
```bash
aliyun oss cp oss://sysu-date/ /home/sse/sysudate/oss/ --region cn-hongkong --force -r
```

### 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0.0 | 2026-04-09 | 初始版本，序列号认证 |
| v2.0.0 | 2026-04-29 | 新增账号系统，JWT认证 |
| v2.1.0 | 2026-04-30 | 前端接入JWT认证，双邮箱设计，按钮防抖，安全加固 |
| v2.2.0 | 2026-05-01 | 管理后台(admin.html)，Chart.js中文修复，CORS更新，环境变量安全化 |
| v2.3.0 | 2026-05-02 | 个人中心(profile.html)，恋爱/交友匹配独立开关，account_preferences表 |
| v2.4.0 | 2026-05-08 | AI智能查询(LLM RAG)，登录限流(1分钟3次)，安全加固 |
| v2.4.1 | 2026-05-09 | 修复前端404端点(send-code/check-code)，修复注销残留数据，统一IP获取方式 |
| v2.4.2 | 2026-05-12 | 数据库连接池超时配置，健康检查端点，CORS增强，日志脱敏，请求日志中间件 |
| v2.5.0 | 2026-05-12 | 匹配历史功能，联系方式交换（需双方同意），问卷状态查询 |
| v2.5.1 | 2026-05-16 | 投诉举报机制，敏感词过滤，撤回同意功能，隐私协议修复 |
| v2.6.0 | 2026-05-17 | AI助手对话持久化（数据库存储），意图识别大幅增强，LLM提示词优化 |

### 依赖包

| 包名 | 用途 |
|------|------|
| express | Web框架 |
| mysql2 | MySQL数据库连接 |
| nodemailer | 邮件发送 |
| jsonwebtoken | JWT Token生成与验证 |
| bcryptjs | 密码加密 |

## ⚠️ 安全提醒

1. JWT_SECRET、ADMIN_PASSWORD、MYSQL_PASSWORD、SMTP_PASSWORD 必须通过环境变量配置，不在代码或文档中明码保存
2. 密码使用 bcrypt 加密存储
3. Token 有效期 30 天
4. 所有 API 均有频率限制保护
5. CORS 限制为 `sysudate.cn` 域名
6. SQL 注入防护：使用参数化查询
7. XSS 防护：输入内容转义处理
8. 数据库迁移端点已禁用

---

最后更新: 2026-05-17
