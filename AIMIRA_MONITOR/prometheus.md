# Prometheus 余额采集接口

## 背景

当前项目已通过定时任务（每小时）采集阿里云和火山引擎的账户余额，数据持久化到 `balance_history` 表，并通过 REST API 提供查询。现需新增 Prometheus 指标暴露接口，以便 Grafana 直接 scrape 并展示余额趋势。

## 目标

暴露 `/actuator/prometheus` 端点，输出以下 Gauge 指标，按 `cloud_provider` 和 `currency` 区分标签：

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `aimira_cloud_balance` | Gauge | `cloud_provider`, `currency` | 账户当前余额 |
| `aimira_cloud_available_amount` | Gauge | `cloud_provider`, `currency` | 可用金额 |

---

## 实施方案

### 1. 添加 Prometheus 依赖 (`pom.xml`)

在 `spring-boot-starter-actuator` 下方新增：

```xml
<!-- Micrometer Prometheus Registry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

版本由 `spring-boot-starter-parent:3.2.6` BOM 统一管理，无需显式指定。

### 2. 暴露 Prometheus 端点 (`application.yml`)

在 `spring:` 块下新增 Actuator 端点配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    prometheus:
      enabled: true
```

### 3. 新建 `BalanceMetrics` 组件

**文件**: `src/main/java/com/aimira/monitor/metrics/BalanceMetrics.java`

**设计要点**:
- 使用 `ConcurrentHashMap<String, BalanceSnapshot>` 持有每个云厂商的最新余额快照
- Gauge 在首次遇到新 cloudProvider 时注册一次（通过 `ConcurrentHashMap.newKeySet()` 跟踪已注册的 provider）
- Gauge 的值函数从 `snapshots` map 惰性读取 —— Prometheus 每次 scrape 时获取最新值
- 通过 `@EventListener(ApplicationReadyEvent.class)` 在启动后从数据库加载最新余额初始化指标
- `recordBalance(BalanceHistory)` 是唯一的外部入口，由 `CollectScheduler` 调用

**核心方法**:
```java
public void recordBalance(BalanceHistory history)   // 更新快照 + 按需注册 Gauge
private double toDoubleValue(BigDecimal value)      // null-safe 转 double
```

### 4. 修改 `CollectScheduler`

**文件**: `src/main/java/com/aimira/monitor/scheduler/CollectScheduler.java`

在 `collectFromCloud()` 方法中，余额采集成功后，增加一行调用。注入 `BalanceMetrics`，采集后推送余额数据到指标。

---

## 影响范围

| 文件 | 操作 | 说明 |
|------|------|------|
| `pom.xml` | 修改 | 添加 `micrometer-registry-prometheus` 依赖 |
| `application.yml` | 修改 | 暴露 prometheus 端点 |
| `metrics/BalanceMetrics.java` | **新增** | Prometheus 指标注册与更新组件 |
| `scheduler/CollectScheduler.java` | 修改 | 注入 `BalanceMetrics`，采集后推送余额 |

## 不修改的部分

- 不改变现有采集逻辑、API 接口、数据库结构
- 不修改 `CloudCollector` 接口及其实现
- 不改变定时任务调度频率（用户可自行调整 `SCHEDULER_COLLECT_CRON` 环境变量）

---

## 验证方式

1. **启动应用**（dev profile，使用 H2 内存库）:
   ```bash
   mvn spring-boot:run
   ```

2. **手动触发采集**（填充数据）:
   ```bash
   curl -X POST http://localhost:8080/api/dashboard/trigger/collect
   ```

3. **访问 Prometheus 端点**:
   ```bash
   curl http://localhost:8080/actuator/prometheus
   ```
   预期输出包含类似：
   ```
   # HELP aimira_cloud_balance Current cloud account balance
   # TYPE aimira_cloud_balance gauge
   aimira_cloud_balance{cloud_provider="ALIYUN",currency="CNY"} 12345.67
   aimira_cloud_balance{cloud_provider="VOLCENGINE",currency="CNY"} 890.12
   # HELP aimira_cloud_available_amount Current cloud available amount
   # TYPE aimira_cloud_available_amount gauge
   aimira_cloud_available_amount{cloud_provider="ALIYUN",currency="CNY"} 12345.67
   aimira_cloud_available_amount{cloud_provider="VOLCENGINE",currency="CNY"} 890.12
   ```

4. **Grafana 配置**: 在 Grafana 中添加 Prometheus 数据源，使用指标 `aimira_cloud_balance` 即可展示各云厂商余额趋势。
