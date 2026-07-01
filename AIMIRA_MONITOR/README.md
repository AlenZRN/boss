# AIMIRA Monitor — 阿里云资源监控系统

轻量级单账号阿里云监控与费用管理系统，自动采集账户余额、账单、云资源信息，提供 Dashboard API 和企业微信告警通知。

---

## 目录

- [快速开始](#快速开始)
- [系统账号密码](#系统账号密码)
- [接口地址](#接口地址)
- [阿里云 AccessKey 最小权限](#阿里云-accesskey-最小权限)
- [配置说明](#配置说明)
- [告警规则](#告警规则)
- [部署指南](#部署指南)
- [风险点](#风险点)
- [常见问题](#常见问题)

---

## 快速开始

### 环境要求

| 依赖 | 版本要求 |
|------|----------|
| Docker | 20.10+ |
| Docker Compose | v2+ |
| 阿里云 AccessKey | 需预先创建（权限见[下方](#阿里云-accesskey-最小权限)） |
| 企业微信机器人 Webhook | 可选，不填则关闭告警通知 |

### 一键部署 (Windows)

```powershell
# 右键 deploy.ps1 → "使用 PowerShell 运行"
# 或终端执行：
.\deploy.ps1              # 一键部署（交互式）
.\deploy.ps1 status       # 查看状态
.\deploy.ps1 restart      # 重启服务
.\deploy.ps1 logs         # 查看日志
```

### 手动部署 (Linux / macOS)

```bash
# 1. 复制环境变量模板
cp .env.example .env

# 2. 编辑 .env，填入阿里云 AK 等信息
vim .env

# 3. 构建并启动
docker compose up -d --build

# 4. 验证
curl http://localhost:8080/actuator/health
```

### 本地开发

```bash
# 设置阿里云凭证
export ALIYUN_ACCESS_KEY="your-access-key"
export ALIYUN_SECRET="your-secret"

# 启动（默认 dev profile，使用 H2 内存数据库）
mvn spring-boot:run
```

启动后访问：
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Dashboard API: `http://localhost:8080/api/dashboard/overview`
- H2 控制台: `http://localhost:8080/h2-console`
- Health Check: `http://localhost:8080/actuator/health`

---

## 系统账号密码

### 开发环境 (dev profile — H2)

| 项目 | 值 |
|------|-----|
| 数据库类型 | H2 (内存数据库, PostgreSQL 兼容模式) |
| JDBC URL | `jdbc:h2:mem:aimira_monitor` |
| 用户名 | `sa` |
| 密码 | 无 (留空) |
| H2 控制台 | `http://localhost:8080/h2-console` |

### 生产环境 (prod profile — PostgreSQL)

| 项目 | 值 |
|------|-----|
| 数据库类型 | PostgreSQL 16 |
| 主机 | `localhost`（或 docker-compose 中的 `postgres`） |
| 端口 | `5432` |
| 数据库名 | `aimira_monitor` |
| 用户名 | `aimira` |
| 密码 | `aimira123`（可通过 `DB_PASSWORD` 环境变量修改） |

### 其他系统账号

| 系统 | 说明 |
|------|------|
| Dashboard API | 无认证（MVP 不包含登录功能，部署于内网） |
| Spring Actuator | 无认证，暴露 `/actuator/health` |

> ⚠️ **安全提醒：** 生产环境请务必修改默认数据库密码，并考虑在内网防火墙后部署，不将 8080 端口暴露到公网。

---

## 接口地址

所有接口以 `/api/dashboard` 为前缀，返回统一格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

### Dashboard

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/dashboard/overview` | 首页概览（卡片 + 30天趋势 + 到期资源第1页） |
| `GET` | `/api/dashboard/expiring-resources` | 到期资源分页列表 |
| `GET` | `/api/dashboard/trend/balance` | 余额趋势（最近N天） |
| `GET` | `/api/dashboard/trend/billing` | 费用趋势（最近N天） |

**请求示例：**

```bash
# 概览数据
curl http://localhost:8080/api/dashboard/overview

# 到期资源（搜索 + 分页）
curl "http://localhost:8080/api/dashboard/expiring-resources?keyword=web&page=0&size=10"

# 余额趋势
curl "http://localhost:8080/api/dashboard/trend/balance?days=30"

# 费用趋势
curl "http://localhost:8080/api/dashboard/trend/billing?days=30"
```

**`/overview` 响应结构：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "overview": {
      "balance": 12345.67,
      "dailyCost": 12.34,
      "monthlyCost": 345.67,
      "expiringCount": 3,
      "currency": "CNY"
    },
    "trend": {
      "balanceTrend": [
        { "date": "2026-06-01", "value": 12000.00 },
        { "date": "2026-06-02", "value": 11800.00 }
      ],
      "billingTrend": [
        { "date": "2026-06-01", "value": 10.50 },
        { "date": "2026-06-02", "value": 12.30 }
      ]
    },
    "expiringResources": {
      "items": [
        {
          "resourceId": "i-bp1xxx",
          "resourceName": "web-server-01",
          "resourceType": "ECS",
          "region": "cn-hangzhou",
          "expireTime": "2026-07-15T00:00:00",
          "remainingDays": 14
        }
      ],
      "page": 0,
      "size": 10,
      "total": 3
    }
  }
}
```

### 告警规则管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/dashboard/alarm-rules` | 查询所有告警规则 |
| `POST` | `/api/dashboard/alarm-rules` | 创建告警规则 |
| `PUT` | `/api/dashboard/alarm-rules/{id}` | 更新告警规则 |
| `DELETE` | `/api/dashboard/alarm-rules/{id}` | 删除告警规则 |

**创建规则示例：**

```bash
curl -X POST http://localhost:8080/api/dashboard/alarm-rules \
  -H "Content-Type: application/json" \
  -d '{
    "ruleName": "余额低于1000告警",
    "alarmType": "BALANCE",
    "threshold": 1000.00,
    "operator": "LESS_THAN",
    "enabled": true
  }'
```

**告警类型：** `BALANCE`（余额告警）、`EXPIRY`（到期告警）

**操作符：** `LESS_THAN`、`LESS_THAN_OR_EQUAL`、`GREATER_THAN`、`GREATER_THAN_OR_EQUAL`

### 手动触发

便于更新代码后立即验证功能，无需等待整点定时任务：

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/dashboard/trigger/collect` | 立即执行数据采集（余额+账单+资源） |
| `POST` | `/api/dashboard/trigger/alarm` | 立即执行告警检测 |

```bash
# 手动触发采集
curl -X POST http://localhost:8080/api/dashboard/trigger/collect

# 手动触发告警
curl -X POST http://localhost:8080/api/dashboard/trigger/alarm
```

### Swagger UI

启动后浏览器访问 `http://localhost:8080/swagger-ui.html`，可直接查看和调试所有接口。

### 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/actuator/health` | 应用健康状态 |

---

## 阿里云 AccessKey 最小权限

### 推荐方案：使用 RAM 自定义权限策略

建议创建专用 RAM 子账号，仅授予本项目所需的最小权限，**不要使用主账号 AK**。

#### 自定义策略 JSON

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "bssapi:QueryAccountBalance",
        "bssapi:QueryBill"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecs:DescribeInstances",
        "rds:DescribeDBInstances",
        "swas:ListInstances"
      ],
      "Resource": "*"
    }
  ]
}
```

### 权限对照表

| API 接口 | 对应功能 | 所需 Action | 所属服务 |
|----------|----------|-------------|----------|
| `QueryAccountBalance` | 采集账户余额 | `bssapi:QueryAccountBalance` | 费用中心 (BSS) |
| `QueryBill` | 采集账单费用 | `bssapi:QueryBill` | 费用中心 (BSS) |
| `DescribeInstances` | 采集 ECS 实例及到期时间 | `ecs:DescribeInstances` | 弹性计算 (ECS) |
| `ListInstances` | 采集 SWAS 轻量应用服务器 | `swas:ListInstances` | 轻量应用服务器 (SWAS) |
| `DescribeDBInstances` | 采集 RDS 实例及到期时间 | `rds:DescribeDBInstances` | 云数据库 (RDS) |

### 最小权限汇总

| 方案 | 说明 | 推荐度 |
|------|------|--------|
| **自定义策略**（推荐） | 仅上述 5 个 Action，权限最小化 | ⭐⭐⭐ |
| `AliyunBSSReadOnlyAccess` + `AliyunECSReadOnlyAccess` + `AliyunRDSReadOnlyAccess` | 系统预置只读策略，但权限范围比实际需要的宽 | ⭐⭐ |
| 主账号 AK | 拥有全部权限，风险极大 | ❌ 禁止 |

### RAM 子账号创建步骤

1. 登录 [RAM 控制台](https://ram.console.aliyun.com/users)
2. 创建用户 → 选择"OpenAPI 调用访问" → 保存 AccessKey
3. 创建自定义权限策略（粘贴上方 JSON）
4. 将策略授权给该用户
5. 将此子账号的 AccessKey 填入 `.env`

---

## 配置说明

所有配置通过环境变量注入，`.env.example` 提供模板：

```bash
# .env 文件（复制自 .env.example 并修改）

# === 阿里云 AccessKey（必填）===
ALIYUN_ACCESS_KEY=your_access_key_here
ALIYUN_SECRET=your_secret_here
ALIYUN_REGION=cn-hangzhou
ALIYUN_REGIONS=cn-guangzhou,cn-shenzhen,cn-hangzhou,cn-hongkong

# === 数据库密码 ===
DB_PASSWORD=aimira123

# === 企业微信机器人 Webhook（可选）===
WECOM_WEBHOOK_URL=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=your_key_here
WECOM_ENABLED=true

# === 定时任务（Cron 表达式，6位：秒 分 时 日 月 周）===
SCHEDULER_COLLECT_CRON=0 0 * * * *     # 数据采集：每小时整点
SCHEDULER_ALARM_CRON=0 0 * * * *       # 告警检测：每小时整点
ALARM_COOLDOWN_SECONDS=86400           # 告警冷却：24小时（同一规则+资源不重复通知）
```

| 环境变量 | 必填 | 默认值 | 说明 |
|----------|------|--------|------|
| `ALIYUN_ACCESS_KEY` | ✅ 是 | — | 阿里云 RAM 子账号 AccessKey |
| `ALIYUN_SECRET` | ✅ 是 | — | 阿里云 RAM 子账号 Secret |
| `ALIYUN_REGION` | 否 | `cn-hangzhou` | 阿里云默认 Region |
| `ALIYUN_REGIONS` | 否 | `cn-guangzhou,cn-shenzhen,cn-hangzhou,cn-hongkong` | 多区域采集列表（逗号分隔），为空时回退到 `ALIYUN_REGION` |
| `DB_HOST` | 否 | `localhost` | PostgreSQL 主机 |
| `DB_PORT` | 否 | `5432` | PostgreSQL 端口 |
| `DB_NAME` | 否 | `aimira_monitor` | 数据库名 |
| `DB_USERNAME` | 否 | `aimira` | 数据库用户 |
| `DB_PASSWORD` | 否 | `aimira123` | 数据库密码 |
| `WECOM_WEBHOOK_URL` | 否 | — | 企业微信机器人 Webhook（空则不发送通知） |
| `WECOM_ENABLED` | 否 | `true` | 是否启用企微通知 |
| `SCHEDULER_COLLECT_CRON` | 否 | `0 0 * * * *` | 采集 cron 表达式 |
| `SCHEDULER_ALARM_CRON` | 否 | `0 0 * * * *` | 告警检测 cron 表达式 |
| `ALARM_COOLDOWN_SECONDS` | 否 | `86400` | 告警冷却秒数 |
| `SPRING_PROFILES_ACTIVE` | 否 | `prod` | Spring Profile（docker-compose 默认 prod） |
| `APP_PORT` | 否 | `8080` | 应用端口 |

---

## 告警规则

### 默认规则（数据库初始化时自动创建）

| 规则名称 | 类型 | 条件 | 阈值 |
|----------|------|------|------|
| 余额不足告警 | BALANCE | 余额 < | 500.00 元 |
| 到期30天告警 | EXPIRY | 剩余天数 ≤ | 30 天 |
| 到期15天告警 | EXPIRY | 剩余天数 ≤ | 15 天 |
| 到期7天告警 | EXPIRY | 剩余天数 ≤ | 7 天 |

### 冷却机制

同一规则 + 同一资源（或余额），默认 **24 小时内只发送一次**，防止重复刷屏。

### 通知格式

企业微信机器人收到 Markdown 格式消息，示例：

```markdown
## ⚠️ 余额告警
> 规则: **余额不足告警**
> 当前余额: **423.50 CNY**
> 告警阈值: **500.00 CNY**
> 可用金额: **423.50 CNY**
> 检测时间: 2026-07-01 14:00:00
```

---

## 部署指南

### 目录结构

```
AIMIRA_MONITOR/
├── deploy.ps1              # Windows 一键部署脚本
├── docker-compose.yml      # Docker 编排文件
├── Dockerfile               # 多阶段构建（maven build → jre runtime）
├── .env.example             # 环境变量模板
├── .env                     # 实际环境变量（需自行创建，已在 .gitignore）
└── src/                     # 源码
```

### Docker 服务组成

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| `postgres` | `postgres:16-alpine` | `5432` | PostgreSQL 数据库（数据持久化到 `pgdata` 卷） |
| `app` | 本地构建 | `8080` | Spring Boot 应用 |

### 常用运维命令

```bash
# 查看运行状态
docker compose ps

# 查看应用日志
docker compose logs -f app

# 重启服务
docker compose restart

# 停止服务
docker compose stop

# 停止并删除容器（保留数据卷）
docker compose down

# 停止并删除容器+数据卷（彻底清理）
docker compose down -v

# 重新构建镜像
docker compose build --no-cache
```

---

## 风险点

### 🔴 高风险

| 序号 | 风险 | 影响 | 应对措施 |
|------|------|------|----------|
| 1 | **AK/SK 泄漏** | 阿里云账户被恶意控制、产生巨额费用 | `.env` 已加入 `.gitignore`；使用 RAM 子账号最小权限；定期轮换 AK |
| 2 | **API 无认证** | 任何能访问 8080 端口的人都能查看余额和资源信息 | MVP 阶段部署于内网防火墙后；生产使用前需加登录认证 |
| 3 | **数据库密码明文** | 数据库被未授权访问 | 修改默认密码；使用 Docker Secret 或 Vault 管理密钥 |
| 4 | **Webhook URL 泄漏** | 垃圾消息推送到企业微信群 | `.env` 不提交到版本控制；日志中不打印 Webhook URL |

### 🟠 中风险

| 序号 | 风险 | 影响 | 应对措施 |
|------|------|------|----------|
| 5 | **阿里云 API 限流** | 高频调用触发限流，数据采集失败 | 默认每小时采集一次，频率较低；如有报错会在日志记录 |
| 6 | **H2 控制台暴露** | dev 模式下 `/h2-console` 可被访问，数据库内容泄漏 | 仅限本地开发；生产环境不启用 H2 |
| 7 | **无数据备份** | 数据库损坏导致历史数据丢失 | 定期备份 PostgreSQL 数据卷；考虑配置 `pg_dump` 定时任务 |
| 8 | **单实例部署** | 应用故障无自动切换，监控中断 | 关键场景可用 Docker `restart: unless-stopped`；后续考虑多实例 |
| 9 | **无输入校验** | API 入参无 `@Valid` 校验，异常参数可能导致 500 错误 | Controller 层加 `@Valid` / `@Validated`；加全局异常处理器 |

### 🔵 低风险

| 序号 | 风险 | 影响 | 应对措施 |
|------|------|------|----------|
| 10 | **阿里云 API 变更** | SDK 版本过老，API 升级后采集失败 | 关注阿里云 API 变更公告；保持 SDK 版本更新 |
| 11 | **时区不一致** | 系统时区与阿里云账单时区不同，数据偏差 | 已配置 `Asia/Shanghai` 时区；注意容器时区设置 |
| 12 | **只采集 ECS/RDS/SWAS 资源** | 其他资源类型（SLB、Redis、OSS 等）到期无法感知 | V2 版本补充更多资源类型采集（代码中已有 TODO） |
| 13 | **Cron 表达式错误** | 修改配置后 cron 不生效，采集/告警停止 | 使用在线 Cron 工具验证；修改后观察日志确认任务执行 |
| 14 | **余额精度** | 金额使用 `NUMERIC(18,2)`，汇率换算等场景可能精度不足 | 常规场景足够；跨境场景需评估是否需要更高精度 |

---

## 常见问题

### Q: 部署后 Dashboard API 返回空数据？

确保：
1. `.env` 中阿里云 AK/SK 已正确配置
2. RAM 子账号已授予最小权限
3. 等待第一次定时任务执行（每小时整点），或手动触发采集
4. 查看日志：`docker compose logs -f app`

### Q: 企业微信收不到告警消息？

1. 检查 `WECOM_WEBHOOK_URL` 是否正确
2. 检查 `WECOM_ENABLED` 是否为 `true`
3. 告警有 24 小时冷却期，同一告警不会重复发送
4. 查看日志中的企微消息发送结果

### Q: 如何修改告警阈值？

通过 API 管理告警规则：
```bash
# 查看当前规则
curl http://localhost:8080/api/dashboard/alarm-rules

# 更新规则（将 id=1 的规则阈值改为 1000）
curl -X PUT http://localhost:8080/api/dashboard/alarm-rules/1 \
  -H "Content-Type: application/json" \
  -d '{"ruleName":"余额不足告警","alarmType":"BALANCE","threshold":1000.00,"operator":"LESS_THAN","enabled":true}'
```

### Q: 如何修改采集频率？

修改 `.env` 中的 cron 表达式，然后重启服务。例如改为每 30 分钟采集一次：
```bash
SCHEDULER_COLLECT_CRON=0 */30 * * * *
```

### Q: 能不能同时监控多个阿里云账号？

MVP 仅支持单账号。V2 计划支持多账号。

---

## 技术支持

- 项目路径：`boss/AIMIRA_MONITOR`
- 需求文档：`需求文档.md`
- 开发指南：`CLOUDE.md`
- 技术栈：Java 17 / Spring Boot 3.2.6 / PostgreSQL 16 / Docker
