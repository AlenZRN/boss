# AIMIRA_MONITOR — Aliyun Resource Monitor (ARM)

阿里云资源监控与费用管理系统 MVP，用于自动采集阿里云账户余额、账单、云资源信息，提供 Dashboard REST API 和企业微信告警通知。

---

## 1. 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.6 |
| ORM | Spring Data JPA (Jakarta Persistence) | — |
| 数据库(生产) | PostgreSQL | 16 |
| 数据库(开发) | H2 (PostgreSQL 兼容模式) | — |
| 迁移 | Flyway | — |
| 阿里云 SDK | aliyun-java-sdk-core | 4.6.4 |
| 通知 | 企业微信机器人 Webhook (Markdown) | — |
| 构建 | Maven | 3.9+ |
| 部署 | Docker + docker-compose | — |
| 工具 | Lombok, Jackson, Actuator | — |

---

## 2. 项目结构

```text
boss/AIMIRA_MONITOR/
├── pom.xml                                    # Maven 构建描述
├── Dockerfile                                 # 多阶段 Docker 构建
├── docker-compose.yml                         # 本地开发/部署编排
├── deploy.ps1                                 # Windows 部署脚本(交互式)
├── .env.example                               # 环境变量模板
├── .gitignore
├── 需求文档.md                                 # MVP 需求文档(中文)
├── diff-review_claude.md                      # 代码评审任务模板(通用参考)
├── CLOUDE.md                                  # 本文件 — 项目开发指南
└── src/
    ├── main/java/com/aimira/monitor/
    │   ├── ArmApplication.java                # 主入口 @SpringBootApplication + @EnableScheduling
    │   ├── config/                            # 配置属性绑定
    │   │   ├── AliyunConfig.java              # aliyun.* → accessKey/secret/region
    │   │   ├── SchedulerConfig.java           # scheduler.* → cron表达式/冷却时间
    │   │   └── WeComConfig.java               # wecom.* → webhook地址/开关
    │   ├── collector/                         # 阿里云 API 采集层
    │   │   ├── AliyunClientFactory.java       # ACS 客户端工厂
    │   │   ├── BalanceCollector.java          # 余额采集 (BSSOpenApi QueryAccountBalance)
    │   │   ├── BillingCollector.java          # 账单采集 (BSSOpenApi QueryBill)
    │   │   └── ResourceCollector.java         # 资源采集 (ECS DescribeInstances)
    │   ├── scheduler/                         # 定时任务调度
    │   │   └── CollectScheduler.java          # 每小时采集调度 (balance + billing + resource)
    │   ├── alarm/                             # 告警模块
    │   │   ├── AlarmDetector.java             # 告警检测引擎(余额阈值 + 到期检测 + 冷却去重)
    │   │   └── AlarmScheduler.java            # 每小时告警检测调度
    │   ├── notifier/                          # 通知模块
    │   │   └── WeComNotifier.java             # 企业微信机器人 Markdown 消息推送
    │   ├── service/                           # 业务服务层
    │   │   ├── BalanceService.java            # 余额 CRUD + 趋势查询
    │   │   ├── BillingService.java            # 账单 CRUD + 趋势查询
    │   │   ├── ResourceService.java           # 资源 CRUD + 到期查询(分页/搜索/去重upsert)
    │   │   └── AlarmService.java              # 告警规则 CRUD + 发送记录查询
    │   ├── repository/                        # Spring Data JPA 仓储
    │   │   ├── BalanceHistoryRepository.java
    │   │   ├── BillingHistoryRepository.java
    │   │   ├── ResourceInfoRepository.java
    │   │   ├── AlarmRuleRepository.java
    │   │   └── AlarmRecordRepository.java
    │   ├── entity/                            # JPA 实体 (5张表)
    │   │   ├── BalanceHistory.java            # 余额历史
    │   │   ├── BillingHistory.java            # 账单历史
    │   │   ├── ResourceInfo.java              # 云资源信息
    │   │   ├── AlarmRule.java                 # 告警规则
    │   │   └── AlarmRecord.java               # 告警发送记录(用于去重)
    │   ├── dashboard/                         # Dashboard REST API
    │   │   └── DashboardController.java       # /api/dashboard/* 全部端点
    │   └── dto/                               # 数据传输对象
    │       ├── ApiResponse.java               # 统一响应体 {code, message, data}
    │       └── DashboardDTO.java              # Dashboard 嵌套 DTO 结构
    ├── main/resources/
    │   ├── application.yml                    # 主配置(共享)
    │   ├── application-dev.yml                # 开发环境(H2 + debug)
    │   ├── application-prod.yml               # 生产环境(PostgreSQL)
    │   └── db/migration/
    │       └── V1__init_schema.sql            # Flyway 初始迁移(5张表 + 4条默认规则)
    └── test/java/com/aimira/monitor/
        └── ArmApplicationTests.java           # Spring Boot 上下文加载测试
```

---

## 3. 架构与数据流

```text
┌─────────────────────────────────────────────────────────────────┐
│                     Spring Scheduler                            │
│                                                                 │
│  CollectScheduler (每小时)         AlarmScheduler (每小时)        │
│  ├─ BalanceCollector ──→ BSSOpenApi     │                       │
│  ├─ BillingCollector  ──→ BSSOpenApi     │                       │
│  └─ ResourceCollector ──→ ECS API        │                       │
│         │                                ▼                       │
│         ▼                         AlarmDetector                  │
│    [PostgreSQL] ◄───────────  ├─ 余额阈值检测                    │
│         │                      ├─ 资源到期检测                    │
│         │                      ├─ 冷却去重 (24h)                  │
│         ▼                      └─ WeComNotifier ──→ 企业微信      │
│   DashboardController                                            │
│   GET /api/dashboard/overview                                    │
│   GET /api/dashboard/expiring-resources                          │
│   GET /api/dashboard/trend/balance                               │
│   GET /api/dashboard/trend/billing                               │
│   CRUD /api/dashboard/alarm-rules                                │
└─────────────────────────────────────────────────────────────────┘
```

**分层约定：** Controller → Service → Repository → Entity，严格分层。Collector/Alarm/Notifier/Scheduler 作为独立组件由 Spring DI 装配。

---

## 4. 数据库 (5张表)

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `balance_history` | 账户余额历史 | balance, available_amount, currency, sync_time |
| `billing_history` | 账单历史 | daily_cost, monthly_cost, currency, bill_date, sync_time |
| `resource_info` | 云资源信息 | resource_id, resource_name, resource_type, region, status, expire_time |
| `alarm_rule` | 告警规则 | rule_name, alarm_type(BALANCE/EXPIRY), threshold, operator, enabled |
| `alarm_record` | 告警发送记录(去重) | rule_id, resource_id, message, sent_time, status(SUCCESS/FAILED/SKIPPED) |

**默认告警规则 (V1__init_schema.sql):**
- 余额 < 500 元
- 到期 ≤ 30 天
- 到期 ≤ 15 天
- 到期 ≤ 7 天

**迁移工具：** Flyway，脚本位于 `src/main/resources/db/migration/`。开发环境(H2)使用 `ddl-auto: create-drop`，生产环境(PostgreSQL)使用 `ddl-auto: update` 配合 Flyway。

---

## 5. API 端点

### Dashboard

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard/overview` | 概览卡片 + 30天趋势 + 到期资源(第1页) |
| GET | `/api/dashboard/expiring-resources?keyword=&page=0&size=10` | 到期资源分页+搜索 |
| GET | `/api/dashboard/trend/balance?days=30` | 余额趋势 |
| GET | `/api/dashboard/trend/billing?days=30` | 费用趋势 |

### 告警规则管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard/alarm-rules` | 列出所有规则 |
| POST | `/api/dashboard/alarm-rules` | 创建规则 |
| PUT | `/api/dashboard/alarm-rules/{id}` | 更新规则 |
| DELETE | `/api/dashboard/alarm-rules/{id}` | 删除规则 |

**统一响应格式：**
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```
`ApiResponse<T>` 位于 `com.aimira.monitor.dto`，所有 Controller 方法必须使用此格式返回。

---

## 6. 配置项

所有敏感配置通过环境变量注入，`.env.example` 提供模板：

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `ALIYUN_ACCESS_KEY` | — | 阿里云 AccessKey |
| `ALIYUN_SECRET` | — | 阿里云 Secret |
| `ALIYUN_REGION` | `cn-hangzhou` | 阿里云 Region |
| `DB_HOST` | `localhost` | PostgreSQL 主机 |
| `DB_PORT` | `5432` | PostgreSQL 端口 |
| `DB_NAME` | `aimira_monitor` | 数据库名 |
| `DB_USERNAME` | `aimira` | 数据库用户 |
| `DB_PASSWORD` | `aimira123` | 数据库密码 |
| `WECOM_WEBHOOK_URL` | — | 企业微信机器人 Webhook |
| `WECOM_ENABLED` | `true` | 是否启用企微通知 |
| `SCHEDULER_COLLECT_CRON` | `0 0 * * * *` | 采集 cron (默认每小时整点) |
| `SCHEDULER_ALARM_CRON` | `0 0 * * * *` | 告警检测 cron |
| `ALARM_COOLDOWN_SECONDS` | `86400` | 告警冷却秒数 (默认24h) |

---

## 7. 开发指南

### 启动 (开发环境 — H2)

```bash
# 1. 确保 Java 17 + Maven 3.9+ 已安装
# 2. 设置阿里云凭证环境变量
export ALIYUN_ACCESS_KEY="your-access-key"
export ALIYUN_SECRET="your-secret"

# 3. 启动 (默认 dev profile)
mvn spring-boot:run

# 访问 H2 控制台: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:aimira_monitor
```

### 构建

```bash
mvn clean package -DskipTests
# JAR 输出: target/aimira-monitor-1.0.0-MVP.jar
```

### Docker 部署

```bash
# 1. 复制并编辑环境变量
cp .env.example .env

# 2. 使用部署脚本 (Windows PowerShell)
.\deploy.ps1

# 或手动 docker-compose
docker compose up -d
```

---

## 8. 代码约定与模式

### 项目现有规范基准

| 约定类型 | 当前实践 | 说明 |
|----------|----------|------|
| 统一返回体 | `ApiResponse<T>` | `dto/ApiResponse.java`，含 `success(data)` / `error(code, msg)` 工厂方法 |
| 日志 | `@Slf4j` (Lombok) | 全局使用，参数化日志 `log.info("k={}", v)` |
| ORM | Spring Data JPA | Repository 接口继承 `JpaRepository`，无 XML Mapper |
| 事务 | 暂无显式声明 | 当前为单表操作；涉及多表操作时应在 Service 层加 `@Transactional` |
| 参数校验 | 暂无 | 建议在 Controller 入参加 `@Valid` / `@Validated` |
| 数据对象 | Entity / DTO 分离 | Entity 不直接作为接口出参，通过 DTO 转换 |
| 构造注入 | 构造器注入 (无 `@Autowired`) | Spring 4.3+ 单构造器自动注入，Lombok 未生成构造器 |
| 枚举序列化 | 暂无枚举类 | 告警类型/操作符当前以字符串存储 |
| Builder 模式 | Lombok `@Builder` | Entity 和 DTO 均使用 Builder 构造 |
| 日期时间 | `LocalDateTime` (Java 8+) | 全局使用，时区 `Asia/Shanghai` |

### Java 17 特性可用

项目使用 Java 17，以下特性允许使用：
- **文本块 (Text Blocks):** `"""..."""` — `AlarmDetector.java` 中已用于构建 Markdown 消息
- **Switch 表达式:** `switch (op) { case "X" -> ...; }` — `AlarmDetector.isTriggered()` 中已使用
- **Records:** 适合 DTO 场景（当前未使用，可考虑引入）
- **Pattern Matching for instanceof:** 简化类型判断
- **Sealed Classes:** 适合状态机场景

### 禁止使用的 Java 8 过时模式

- ❌ `Date` / `Calendar` → ✅ 使用 `LocalDateTime`
- ❌ `SimpleDateFormat` → ✅ 使用 `DateTimeFormatter`
- ❌ `Vector` / `Hashtable` → ✅ 使用 `ArrayList` / `HashMap` / `ConcurrentHashMap`
- ❌ `Optional.get()` 不判空 → ✅ 使用 `Optional.orElse()` / `orElseThrow()`

---

## 9. 代码评审检查清单

> 以下清单改编自 `diff-review_claude.md`，针对本项目技术栈做了适配。

### 9.1 与项目规范一致性 (最高优先级)

- [ ] 命名规范与项目现有风格一致 (类名 PascalCase、方法名 camelCase、常量 UPPER_SNAKE_CASE)
- [ ] 包结构符合分层约定 (controller → service → repository → entity)
- [ ] 统一使用 `ApiResponse<T>` 作为返回值
- [ ] 使用 `@Slf4j` + 参数化日志，避免字符串拼接
- [ ] Entity 不直接暴露为 API 出参，通过 DTO 转换
- [ ] 构造器注入，不使用字段 `@Autowired`

### 9.2 Java 17 + Spring Boot 3.x 合规性

- [ ] 无 Java 8 遗留 API (Date/Calendar/SimpleDateFormat)
- [ ] 未使用 Spring Boot 2.x 已废弃的类 (如 `WebMvcConfigurerAdapter`)
- [ ] Jakarta 命名空间正确 (`jakarta.persistence.*` 而非 `javax.persistence.*`)
- [ ] 文本块和 Switch 表达式使用得当

### 9.3 空指针与异常处理

- [ ] 链式调用无 NPE 风险 (如 `a.b().c()` 中间结果可能为 null)
- [ ] `BigDecimal` 比较使用 `compareTo()` 而非 `equals()`
- [ ] 金额计算使用 `BigDecimal` + `RoundingMode`，不使用 `double/float`
- [ ] `catch (Exception e)` 有明确的日志和堆栈 (`log.error("msg", e)`)
- [ ] Collector 层 API 调用失败不影响下一次调度执行

### 9.4 JPA 与数据库

- [ ] 无 N+1 查询 (注意 `@OneToMany` / `@ManyToOne` 的 fetch 策略)
- [ ] Repository 查询方法名符合 Spring Data JPA 规范
- [ ] Flyway 迁移脚本可重入，字段变更兼容存量数据
- [ ] 新增查询考虑索引覆盖

### 9.5 安全

- [ ] 敏感配置 (AccessKey/Secret/密码) 不硬编码，不打印到日志
- [ ] 企业微信 Webhook URL 不暴露到日志
- [ ] API 入参有基本校验
- [ ] 无 SQL 注入风险 (JPA + 参数化查询默认安全)

### 9.6 定时任务

- [ ] `@Scheduled` cron 表达式正确，默认每小时执行
- [ ] 采集和告警任务相互独立，单任务异常不阻塞其他任务
- [ ] 告警冷却机制生效，不会重复刷屏

### 9.7 测试

- [ ] 新增 Service 方法应有单元测试
- [ ] Mock 阿里云 API 响应，避免测试依赖外部服务
- [ ] 测试覆盖正常路径 + 异常路径 (API 失败、空数据)

### 问题严重性分级

| 等级 | 触发条件 |
|------|----------|
| 🔴 P0 严重 | 安全漏洞、数据丢失、AK/SK 泄漏、对外接口破坏性变更 |
| 🟠 P1 高 | 功能错误、NPE 必现路径、告警失效、金额计算错误 |
| 🟡 P2 中 | 偏离项目规范、N+1 查询、日志缺失关键信息 |
| 🔵 P3 低 | 命名不一致、魔法值、冗余代码 |
| ⚪ P4 建议 | 可选优化，不影响功能和规范 |

---

## 10. 已知问题与 TODO

| 序号 | 位置 | 问题描述 | 优先级 |
|------|------|----------|--------|
| 1 | `ResourceCollector.java` | 当前仅采集 ECS 实例，RDS/SLB/Redis 待 V2 支持 (代码中有 TODO) | P2 |
| 2 | `BillingCollector.java` | 仅处理按量付费(PayAsYouGoBill)，未覆盖包年包月 | P2 |
| 3 | `diff-review_claude.md` | 技术栈描述过时 (仍写 SB 2.x / Java 8 / MySQL)，仅作评审流程参考 | P3 |
| 4 | 项目整体 | 无全局异常处理器 (`@ControllerAdvice`)，异常直接抛给 Tomcat 默认处理 | P2 |
| 5 | 项目整体 | 无 API 入参校验 (`@Valid`/`@Validated`)，Controller 缺少输入校验 | P2 |
| 6 | 项目整体 | 无事务管理 (单表操作暂不影响，多表操作时需补充 `@Transactional`) | P2 |
| 7 | 项目整体 | Entity 使用 `@PrePersist`/`@PreUpdate` 设置时间戳，但未使用 `@MappedSuperclass` 提取公共字段 | P3 |
| 8 | 项目整体 | 无单元测试覆盖 (仅一个空的上下文加载测试) | P1 |

---

## 11. 后续扩展方向 (V2)

- 支持更多资源类型 (RDS, SLB, Redis, OSS)
- 支持包年包月账单采集
- 多阿里云账号支持
- 多通知渠道 (钉钉、飞书、邮件)
- 登录认证 + RBAC 权限
- Prometheus / Grafana 集成
- 前端 Dashboard (当前仅有 REST API)
