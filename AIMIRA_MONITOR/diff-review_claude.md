# 代码评审任务

技术栈假设：

- Spring Boot 2.x
- Java 8
- MySQL 5.7
- 常见持久层：MyBatis / MyBatis-Plus / Mapper / JdbcTemplate
- 常见架构：Controller / Service / DAO / Mapper / Repository / Domain / DTO / VO / Config / Common / Util / Job / Listener / Client / Enum

---

## 第一步：前置检查（防止重复执行与无效执行）

```bash
# 1. 获取分支名，并将特殊字符替换为 - 用于文件命名（防路径错误）
#    兜底处理 detached HEAD：使用短 commit 作为分支别名
BRANCH=$(git branch --show-current)
if [ -z "$BRANCH" ]; then
  BRANCH="detached-$(git rev-parse --short HEAD)"
fi
# 替换 / : 空格 等不适合做文件名的字符
SAFE_BRANCH=$(echo "$BRANCH" | sed 's|[/: ]|-|g')
DATE=$(date +%Y%m%d)
REPORT_FILE="${SAFE_BRANCH}-review-${DATE}.md"

echo "=== 分支: $BRANCH ==="
echo "=== 报告文件: $REPORT_FILE ==="

# 2. 幂等检查：报告已存在则提示并退出
if [ -f "$REPORT_FILE" ]; then
  echo "⚠️  报告 $REPORT_FILE 已存在，如需重新生成请先删除该文件。"
  exit 0
fi

# 3. 确认 release 分支可达，如果远端 release 分支无法获取，则回退到本地 release 分支
git fetch origin 2>&1 | tail -3
MERGE_BASE=$(git merge-base HEAD origin/release 2>/dev/null)
if [ -z "$MERGE_BASE" ]; then
  echo "⚠️  origin/release 不可达，尝试本地 release 分支..."
  MERGE_BASE=$(git merge-base HEAD release 2>/dev/null)
fi
if [ -z "$MERGE_BASE" ]; then
  echo "❌ 无法找到与 release 的公共祖先，请确认 release 分支存在。"
  exit 1
fi
echo "=== Merge base: $MERGE_BASE ==="

# 4. 变更文件清单（无变更则提前退出）
CHANGED_FILES=$(git diff "$MERGE_BASE" HEAD --name-only)
if [ -z "$CHANGED_FILES" ]; then
  echo "✅ 当前分支与 release 无差异，无需评审。"
  exit 0
fi
echo "=== 变更文件 ==="
echo "$CHANGED_FILES"
CHANGED_COUNT=$(echo "$CHANGED_FILES" | wc -l | tr -d ' ')
echo "=== 共 $CHANGED_COUNT 个文件 ==="

# 5. 差异内容（超大 PR 保护：超过 2000 行时改为逐文件输出）
DIFF_LINES=$(git diff "$MERGE_BASE" HEAD | wc -l | tr -d ' ')
echo "=== diff 总行数: $DIFF_LINES ==="
if [ "$DIFF_LINES" -gt 2000 ]; then
  echo "⚠️  diff 较大，改为逐文件输出以防截断："
  echo "$CHANGED_FILES" | while read -r f; do
    [ -z "$f" ] && continue
    echo "====== $f ======"
    git diff "$MERGE_BASE" HEAD -- "$f" | head -300
    echo ""
  done
else
  git diff "$MERGE_BASE" HEAD
fi
```

---

## 第二步：项目约定与规范扫描

在开始评审前，请先扫描并理解当前项目已有的约定与规范，作为后续评审的唯一基准（优先级高于行业主流约定）。每类约定独立输出，关键文件完整读取，保证信噪比。

```bash
# ── 2.0 多模块结构识别 ────────────────────────────────────────
echo "=== 多模块结构 ==="
find . -name "pom.xml" -not -path "*/target/*" -print0 \
  | xargs -0 -r grep -l "<modules>" 2>/dev/null

# ── 2.1 项目分层目录索引（只列路径，不读内容）────────────────
echo "=== 分层目录索引 ==="
find . -type d \( -name "controller" -o -name "service" -o -name "mapper" \
  -o -name "repository" -o -name "dto" -o -name "vo" -o -name "entity" \
  -o -name "domain" -o -name "enums" -o -name "config" -o -name "constant" \
  -o -name "util" -o -name "validation" -o -name "job" -o -name "listener" \
  -o -name "client" -o -name "convertor" -o -name "converter" \) \
  | grep -v target | sort

# ── 2.2 关键约定文件索引（只列路径，不读内容）────────────────
echo "=== 关键约定文件索引 ==="

# 统一返回体
find . -type f -name "*.java" -print0 \
  | xargs -0 -r grep -l "class Result\|class ApiResult\|class BaseResponse\|class CommonResult" 2>/dev/null \
  | grep -v target | head -3

# 全局异常
find . -type f -name "*.java" -print0 \
  | xargs -0 -r grep -l "@ControllerAdvice\|GlobalExceptionHandler\|CodedException" 2>/dev/null \
  | grep -v target | head -3

# 安全认证
find . -type f -name "*.java" -print0 \
  | xargs -0 -r grep -l "SecurityFilterChain\|SaInterceptor\|JwtFilter\|OncePerRequestFilter" 2>/dev/null \
  | grep -v target | head -3

# 枚举
find . \( -path "*/enums/*.java" -o -path "*/enum/*.java" \) -not -path "*/target/*" | head -5

# 代码生成器产物（后续排除）
echo "=== 生成器产物（排除评审）==="
find . -type f -name "*.java" -print0 \
  | xargs -0 -r grep -l "@mbg.generated\|Do not modify\|该文件由.*生成" 2>/dev/null \
  | grep -v target

# ── 2.3 依赖关键词扫描（单次 grep，不读整个 pom）────────────
echo "=== 可用依赖库 ==="
find . -name "pom.xml" -not -path "*/target/*" -print0 \
  | xargs -0 -r grep -hE \
    "lombok|mapstruct|hutool|mybatis|sa-token|jwt|guava|commons-lang|PageHelper|redisson|caffeine|rocketmq|kafka|xxl-job" \
    2>/dev/null | grep -v "<!--" | sed 's/^[[:space:]]*//' | sort -u

# ── 2.4 约定特征一次性扫描（单次多模式 grep，只列文件名）────
echo "=== 约定特征分布 ==="
find . -type f -name "*.java" -not -path "*/target/*" -print0 \
  | xargs -0 -r grep -l \
    "@Slf4j\|@Transactional\|@Valid\|@Validated\|PageHelper\|IPage\|@JsonValue\|@EnumValue" \
  2>/dev/null \
  | xargs -r grep -oP "@Slf4j|@Transactional|@Valid|@Validated|PageHelper|IPage|@JsonValue|@EnumValue" \
  2>/dev/null \
  | sed 's|.*:||' | sort | uniq -c | sort -rn

# ── 2.5 按需读取代表性文件（每类仅读 1 个，超过 200 行截断）─
echo "=== 代表性文件内容（按需读取）==="

_read_sample() {
  local label="$1" file="$2"
  if [ -n "$file" ] && [ -f "$file" ]; then
    echo "--- [$label] $file ---"
    head -200 "$file"
  fi
}

# 统一返回体（最重要，决定返回值写法）
_read_sample "统一返回体" "$(find . -type f -name "*.java" -print0 | xargs -0 -r grep -l "class Result\|class ApiResult\|class BaseResponse" 2>/dev/null | grep -v target | head -1)"

# 全局异常处理
_read_sample "全局异常" "$(find . -type f -name "*.java" -print0 | xargs -0 -r grep -l "@ControllerAdvice" 2>/dev/null | grep -v target | head -1)"

# Controller 样本
_read_sample "Controller" "$(find . -path "*/controller/*.java" -not -path "*/test/*" -not -path "*/target/*" | head -1)"

# Service 实现样本
_read_sample "ServiceImpl" "$(find . -path "*/service/impl/*.java" -not -path "*/test/*" -not -path "*/target/*" | head -1)"

# Mapper XML 样本（SQL 约定）
_read_sample "Mapper XML" "$(find . -type f -name "*.xml" | grep -i mapper | grep -v target | head -1)"

# 枚举样本（序列化约定）
_read_sample "枚举" "$(find . \( -path "*/enums/*.java" -o -path "*/enum/*.java" \) -not -path "*/target/*" | head -1)"

# Entity 样本（公共字段约定）
_read_sample "Entity" "$(find . -path "*/entity/*.java" -not -path "*/target/*" | head -1)"

# 配置文件样本（profile / 数据源约定）
_read_sample "主配置" "$(find . -type f \( -name "application.yml" -o -name "application.yaml" -o -name "application.properties" \) -not -path "*/target/*" | head -1)"
```

> 📌 **重要**：以上扫描结果作为本次评审的项目基准。凡是与项目现有约定不符的改动，无论是否符合行业主流，均须标记为问题。代码生成器产物不参与风格评审。

---

## 第三步：变更文件关联上下文读取

仅靠 diff 难以判断业务意图，需读取变更文件的完整当前内容及其直接依赖。**为控制上下文成本，超大文件（>800 行）只读关键片段；已删除文件跳过内容读取。**

```bash
echo "$CHANGED_FILES" | while read -r f; do
  [ -z "$f" ] && continue
  echo "====== 文件: $f ======"

  if [ ! -f "$f" ]; then
    echo "(文件已删除，跳过内容读取)"
    continue
  fi

  # 超大文件保护：>800 行只读头尾，并打印 diff 上下文
  LINE_COUNT=$(wc -l < "$f" | tr -d ' ')
  if [ "$LINE_COUNT" -gt 800 ]; then
    echo "--- 文件较大($LINE_COUNT 行)，仅读前 200 行与后 100 行 ---"
    head -200 "$f"
    echo "...(中间省略)..."
    tail -100 "$f"
  else
    cat "$f"
  fi

  # 若是 ServiceImpl，尝试找到对应 Service 接口
  if echo "$f" | grep -q "ServiceImpl"; then
    IFACE=$(echo "$f" | sed 's|ServiceImpl|Service|g; s|/impl||')
    [ -f "$IFACE" ] && echo "--- 对应接口: $IFACE ---" && cat "$IFACE"
  fi

  # 若是 Mapper Java，尝试找到对应 XML
  if echo "$f" | grep -qi "mapper.*\.java$"; then
    BASENAME=$(basename "$f" .java)
    XML_FILE=$(find . -name "${BASENAME}.xml" -not -path "*/target/*" | head -1)
    [ -n "$XML_FILE" ] && echo "--- 对应 XML: $XML_FILE ---" && cat "$XML_FILE"
  fi

  # 若是 Mapper XML，反向找到对应 Java 接口
  if echo "$f" | grep -qi "mapper.*\.xml$"; then
    BASENAME=$(basename "$f" .xml)
    JAVA_FILE=$(find . -name "${BASENAME}.java" -not -path "*/target/*" | head -1)
    [ -n "$JAVA_FILE" ] && echo "--- 对应 Java 接口: $JAVA_FILE ---" && cat "$JAVA_FILE"
  fi

  # 若是 Controller，尝试读取对应 DTO/VO
  if echo "$f" | grep -q "Controller\.java$"; then
    echo "--- 提取引用的 DTO/VO/Req/Resp ---"
    grep -oE "[A-Z][A-Za-z0-9]+(DTO|VO|Req|Resp|Request|Response|Param|Query)" "$f" 2>/dev/null | sort -u | head -10
  fi
done

# 专项：DB 变更脚本（DDL/迁移）
echo "=== 数据库变更脚本扫描 ==="
echo "$CHANGED_FILES" | grep -E "\.(sql)$|/(db|migration|flyway|liquibase)/" || echo "(无)"

# 专项：配置文件变更
echo "=== 配置文件变更扫描 ==="
echo "$CHANGED_FILES" | grep -E "\.(yml|yaml|properties|xml)$" | grep -v "/test/" || echo "(无)"

# 专项：测试同步情况
echo "=== 测试文件同步情况 ==="
echo "$CHANGED_FILES" | grep -E "/test/|Test\.java$|Tests\.java$" || echo "(本次变更未涉及测试文件 ⚠️)"
```

---

## 第四步：全量静态分析与业务逻辑分析

针对所有变更文件，结合整个项目上下文，执行以下维度的完整分析：

### 4.1 与项目现有规范的一致性（最高优先级）

- 命名规范（类名、方法名、变量名、常量名）是否与项目现有风格一致
- 包结构与分层是否符合项目现有架构分层约定
- 统一返回体使用是否与项目现有方式一致
- 异常处理方式是否与项目现有全局异常处理机制对齐
- 注解使用习惯（如 Lombok、Swagger、Validation）是否与项目已有用法一致
- 日志记录方式是否与项目约定一致
- 事务管理方式是否与项目现有约定一致
- DTO/VO/Entity 边界是否清晰（是否存在 Entity 直接作为接口出入参的越界）

### 4.2 Java 8 + SpringBoot 2.x 特性合规性

- 是否有不适用于 Java 8 的语法（如 Java 9+ 特性：`var`、`List.of`、`Optional.ifPresentOrElse`、文本块等）
- Optional、Stream、Lambda 使用是否正确且合理（避免在 Stream 中产生副作用、滥用 `forEach`、误用 `Collectors.toMap` 的 null value 抛错等）
- SpringBoot 2.x 配置方式是否过时或错误使用（如 `WebMvcConfigurerAdapter` 已废弃应改用 `WebMvcConfigurer`）

### 4.3 代码质量与潜在缺陷

- 空指针风险（NPE）：链式调用、自动拆箱、`map.get()` 直接拆箱
- 资源泄漏（IO、数据库连接、线程等）：未使用 try-with-resources
- 集合判空遗漏（`isEmpty` vs `size==0` vs `null` 混用）
- 魔法值（Magic Number / Magic String）：应抽取为常量或枚举
- 重复代码（与项目现有工具类或方法重复，"造轮子"）
- 不合理的 catch 吞异常（`catch (Exception e) {}` 或仅 `log.error("err")` 无堆栈）
- 无意义的 try-catch 或过宽的异常捕获
- 无效注释或误导性注释（注释与代码不一致）
- 浮点数比较使用 `==`、金额计算使用 `double/float`（应使用 `BigDecimal` 并指定舍入模式）
- 时间日期处理混用（`Date`/`Calendar` 与 `LocalDateTime`、时区缺失）

### 4.4 安全性

- SQL 注入风险（MyBatis 中 `${}` 的使用，特别是用于排序、动态表名时需校验白名单）
- 敏感信息泄漏检查：配置文件明文保存密钥、日志打印密码/Token/身份证/手机号、返回体中含敏感字段
- 权限校验缺失：新增接口是否补充了认证/鉴权注解
- 接口幂等性缺失（涉及写操作，特别是支付、下单、扣减库存场景）
- 变更是否涉及公共基类、工具类、常量类（影响面广，需重点标注）
- yaml/properties 配置的变更是否会导致某些功能或者类不执行（如 `@ConditionalOnProperty` 开关、定时任务 cron、数据源切换）
- 越权风险：是否做了数据归属校验（如用户只能查询自己的订单）
- 文件上传/下载：是否做了路径穿越、文件类型、大小限制

### 4.5 性能风险

- 循环内 DB 查询（N+1 问题）
- 大批量数据未分页（特别是 `selectList` 全表扫描）
- 不合理的全量加载（关联表 join 过深）
- 缓存使用不当或缓存穿透/击穿/雪崩风险
- 无必要的同步（`synchronized` 滥用、锁粒度过大、锁了静态资源）
- 反射、深拷贝、大对象序列化在热点路径中的使用
- Stream 在大集合上的并行流误用（`parallelStream` 共享 ForkJoinPool）
- 字符串拼接在循环中使用 `+` 而非 `StringBuilder`

### 4.6 业务逻辑正确性

- 根据变更文件的方法名、注释、接口定义及关联文件，综合推断业务意图
- 逻辑分支是否覆盖完整（边界条件、空入参、极值、负数、超长字符串）
- 状态流转是否合理（如订单状态机、审批流等），是否存在非法状态跳变
- 数据一致性（涉及多表操作是否有事务保障）
- 并发场景下的数据竞争风险（先查后改、库存超卖、TOCTOU）
- 事务边界：`@Transactional` 是否覆盖正确范围（是否存在事务内调用远程接口、事务方法被同类内部方法调用导致失效）

### 4.7 接口与契约

- 新增/修改接口是否与已有接口风格一致（URL、HTTP 方法、命名）
- 入参校验（`@Valid` / `@Validated`）是否完整
- 出参是否与接口文档或调用方约定匹配
- **破坏性变更检测**：对外接口的字段删除、字段类型变更、枚举值删除、必填变可选/可选变必填——需明确标注影响的调用方

### 4.8 数据库与持久层

- DDL 变更：新增/修改字段是否有默认值、是否兼容存量数据、是否考虑大表锁表风险
- 迁移脚本（Flyway/Liquibase）是否可重入、是否与代码同步发布
- 索引使用：新增查询是否命中索引，`LIKE '%xxx'` 等不走索引的写法
- MyBatis：`<foreach>` 大批量插入是否有数量上限、`resultMap` 与 Entity 字段是否对齐

### 4.9 日志、监控与可观测性

- 日志级别合理：debug/info/warn/error 是否对应使用，避免 error 滥用导致告警噪声
- 日志参数化：使用 `log.info("user={}", id)` 而非字符串拼接
- 关键业务路径是否埋点（traceId、关键参数）
- 敏感信息脱敏

### 4.10 异步、消息与定时任务

- 异步线程池是否使用项目统一定义的、是否传递了 traceId
- MQ 消息：是否考虑消费幂等、重试、死信、顺序性
- 定时任务（@Scheduled / xxl-job）：cron 表达式正确性、单机/集群执行、漏跑/重跑处理

### 4.11 测试覆盖

- 新增 Service 方法是否有对应单元测试
- 边界条件、异常路径是否覆盖
- 现有测试是否因本次变更而失效（mock 数据、断言）

### 4.12 配置变更影响面

- application.yml/properties 变更：哪些 Bean 的加载条件受影响（`@ConditionalOnProperty`）
- 数据源、Redis、MQ 连接参数变更是否需要同步运维
- 灰度/Feature Flag 是否正确配置

---

## 第五步：生成代码评审报告

报告文件命名规则：分支名中的特殊字符已在前置步骤替换为 `-`，格式为：

```
{safe-branch-name}-review-{YYYYMMDD}.md
```

例如：`feature-user-login-review-20240801.md`

**生成方式**：直接将报告内容写入到工作目录下的 `${REPORT_FILE}` 文件（使用文件写入工具或 `cat > "$REPORT_FILE" << 'EOF' ... EOF`），不要仅在对话中输出。

**报告归档**：报告已写入工作目录，需额外归档一份。为避免相对路径与异常中断问题，请按以下方式执行（任何失败都打印信息并跳过，不得影响后续）：

```bash
# 用绝对路径传参，避免归档脚本继承不同 CWD 时找不到文件
REPORT_ABS="$(pwd)/${REPORT_FILE}"
if [ -f .claude/scripts/save-review-report.sh ]; then
  bash .claude/scripts/save-review-report.sh "$REPORT_ABS" \
    || echo "⚠️  归档失败（权限/路径/环境差异），已跳过，不影响评审结果。报告仍在工作目录：${REPORT_FILE}"
else
  echo "ℹ️  未找到归档脚本 .claude/scripts/save-review-report.sh，跳过归档。报告位于：${REPORT_FILE}"
fi
```
### 报告结构要求

---

# 代码评审报告

**分支：** `{BRANCH}`
**基准分支：** `origin/release`（或本地 `release`）
**Merge base commit：** `{MERGE_BASE 前 8 位}`
**评审日期：** `{DATE}`
**变更文件数：** `{N}` 个
**发现问题数：** 严重 {N} / 高 {N} / 中 {N} / 低 {N} / 建议 {N}

---

## 一、变更文件清单

| #    | 文件路径 | 变更类型（新增/修改/删除） | 影响面                         |
| ---- | -------- | -------------------------- | ------------------------------ |
| 1    | ...      | ...                        | 局部 / 公共基础设施 / 对外接口 / DB 结构 / 配置 |

> **影响面说明**：公共基础设施（被广泛引用的工具类、基类、常量）、对外接口、DB 结构、配置变更需重点关注。

---

## 二、项目现有规范基准（本次评审依据）

| 约定类型   | 识别结果                                  | 来源文件                          |
| ---------- | ----------------------------------------- | --------------------------------- |
| 统一返回体 | `Result<T>`                               | `xxx/Result.java`                 |
| 全局异常   | `GlobalExceptionHandler`                  | `xxx/GlobalExceptionHandler.java` |
| 日志       | `@Slf4j`                                  | 全局                              |
| ORM        | MyBatis XML                               | `resources/mapper/*.xml`          |
| 分页       | `PageHelper`                              | —                                 |
| 事务       | Service 层 `@Transactional`               | —                                 |
| 枚举序列化 | `@JsonValue` on `getCode()`               | —                                 |
| 参数校验   | `@Validated` + 自定义 ConstraintValidator | —                                 |
| 数据对象   | Entity / DTO / VO 三层分离                | —                                 |

> ⚠️ **代码生成器产物**（不纳入风格评审）：`{文件列表，无则填"无"}`

---

## 三、问题清单（按严重性排序）

| 优先级 | 严重性 | 是否阻塞合并 | 文件 | 行号/方法 | 问题类型 | 问题描述 | 修改建议 |
| ------ | ------ | ------------ | ---- | --------- | -------- | -------- | -------- |
| P0     | 🔴 严重 | 是           |      |           |          |          |          |
| P1     | 🟠 高   | 是           |      |           |          |          |          |
| P2     | 🟡 中   | 否           |      |           |          |          |          |
| P3     | 🔵 低   | 否           |      |           |          |          |          |
| P4     | ⚪ 建议 | 否           |      |           |          |          |          |

**严重性定义：**

| 等级      | 触发条件示例                                   |
| --------- | ---------------------------------------------- |
| 🔴 P0 严重 | SQL 注入、生产数据丢失风险、安全漏洞、死锁、对外接口破坏性变更未标注、敏感信息明文泄漏 |
| 🟠 P1 高   | 功能错误、业务逻辑缺陷、事务缺失或失效、NPE 必现路径、并发数据竞争、缓存与 DB 不一致 |
| 🟡 P2 中   | 偏离项目规范、潜在性能问题、NPE 低概率路径、N+1 查询、日志缺失关键信息 |
| 🔵 P3 低   | 轻微命名不一致、冗余代码、可读性问题、魔法值   |
| ⚪ P4 建议 | 可选优化，不影响功能和规范                     |

> **P0/P1 问题必须有可执行的修改建议**（具体代码、配置或方案），不可仅描述"建议优化"。

---

## 四、逐文件详细分析

> 按问题严重性、同一文件问题数量优先排序，没有问题的文件不需要列出。

### `{文件路径}`

**变更类型：** 新增 / 修改 / 删除
**影响面：** 局部 / 公共基础设施 / 对外接口 / DB 结构 / 配置
**业务意图：** （基于方法名、注释、关联接口综合推断）
**关联文件：** （如 Service 接口、Mapper XML、DTO 等）

#### 发现问题

**[P{N}] {问题标题}**

- **位置：** 第 {行号} 行 / `{方法名}` 方法
- **问题：** {具体描述，引用代码片段}
- **依据：** 违反项目约定 `{约定描述}` / 存在 {风险类型} 风险
- **建议：** {具体修改方案，可附伪代码}

---

## 五、总体评审结论

**本次变更整体质量评级：** `通过 / 需整改后通过 / 建议重新设计`

**评级触发规则：**
- **通过**：无 P0/P1 问题，P2 ≤ 3 条
- **需整改后通过**：存在 P0/P1 问题但范围可控（≤ 5 条），整改后可合并
- **建议重新设计**：存在以下任一情况——架构/分层严重违反项目约定、对外接口大面积破坏性变更、核心业务逻辑设计错误、P0 ≥ 3 条或 P1 ≥ 8 条

**必须整改项（P0/P1）：** {N} 项
**建议整改项（P2/P3）：** {N} 项
**历史技术债说明：** （如有，单独列出，不计入本次变更问题数）

{整体评价总结，2-4 句话}

---

## ⚠️ 特别注意事项

- **本次评审仅做只读分析，未修改任何文件，未运行任何测试**
- 所有问题判断以当前项目现有约定为准，如有与行业主流不符但项目已统一的约定，不计入问题
- 代码生成器产物（已在第二步识别）不参与风格评审
- 如发现项目本身存在历史技术债，单独在"总体评审结论"中说明，不计入本次变更的问题数
- 如 diff 超过 2000 行，部分文件仅分析前 300 行差异，建议将大 PR 拆分后重新评审
- 超大文件（>800 行）仅读取头尾片段，可能遗漏中段问题，已在对应文件分析中标注

---

## 自我校验清单（输出报告前必须逐项确认）

- [ ] 变更文件清单与 `git diff --name-only` 输出完全一致
- [ ] 每个有问题的变更文件均有对应的逐文件详细分析章节
- [ ] 问题清单中的文件名和行号来自实际 diff 内容，非推测
- [ ] 报告首部的问题总计数（严重/高/中/低/建议）与问题清单条目数一致
- [ ] 报告文件名中不含 `/` 等特殊字符，格式为 `{safe-branch}-review-{YYYYMMDD}.md`
- [ ] 未对代码生成器产物提出风格类问题
- [ ] 所有 P0/P1 问题均给出具体可执行的修改建议
- [ ] DB 变更、配置变更、对外接口破坏性变更已单独标注影响面
- [ ] 整体评级与评级触发规则匹配

---

**现在请开始执行以上步骤，完成代码评审并输出报告文件。**