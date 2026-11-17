# 句阁 代码审查（阿里 OpenCodeReview 委派模式）

- 审查对象：`/Users/sunqiqi/ans`，分支 `refactor/drop-account-system`，HEAD = `ed4e277`
- 审查时间：2026-11-16
- 审查工具：**OpenCodeReview v1.13.6**（`@alibaba-group/open-code-review`，Apache-2.0，阿里开源）
- 审查范围：OCR 的工作区模式，89 个变更文件中 **59 个可审查**（9386 行新增 / 1800 行删除），30 个被默认规则排除（测试、文档、`.pem` 等）

---

## 一、工具链状态：装好了，但**默认模式跑不起来**

这是本次首先必须说清楚的事，否则"用 OCR 审查过了"就是一句假话。

| 项 | 实测 |
|---|---|
| `ocr --version` | `open-code-review v1.13.6 (891df1b6) darwin/arm64` |
| 配置目录 | `~/.opencodereview/` 只有一个 `last-update-check`，**无 `config.json`** |
| `ocr review` | `Error: resolve LLM endpoint: no valid LLM endpoint configured` |
| `ocr scan` | 同上（同一个 LLM 端点解析） |

OCR 的两种执行模式里，**「默认模式」需要你自己配一个 LLM 端点**（`OCR_LLM_URL`/`OCR_LLM_TOKEN`/`OCR_LLM_MODEL`，或 `ANTHROPIC_*`）。本机一个都没配，环境变量里也搜不到任何 provider 凭证。

因此本次走的是 OCR 官方为这种情况准备的**「委派模式」（Delegation Mode）**：

```bash
ocr delegate preview --format json    # OCR 决定审哪些文件（确定性）
ocr delegate rule <path>              # OCR 解析该文件适用的规则（模板引擎匹配）
```

- **OCR 负责的部分**（它明确声称"必须不能出错、由工程逻辑而非语言模型保证"的环节）：
  - 文件筛选：59 个可审 / 30 个排除，带 `exclude_reason`
  - 规则匹配：按 `.kt,.kts`、`.github/workflows/**/*.yml` 分组成 Rule Group
  - 上下文打包：`--background` / `--background-file` 可注入业务背景
- **我负责的部分**：真正读 diff、下判断、给结论。

> ⚠️ 这意味着一件事：**结论质量不再由 OCR 的 agent 保证**。OCR 在基准测试里相对通用 agent 的优势是 Precision 高、token 省 1/9，那部分能力本次没有用上（它需要模型）。本次的价值在于 OCR 的**规则集与文件筛选**是真实生效的，而阅读与判断由我完成。若要拿到 OCR 的完整能力，需要配一个 LLM 端点后重跑 `ocr review`。

**要真正跑起来，只需一步**：
```bash
export OCR_LLM_URL=https://api.deepseek.com/v1   # 或任何 OpenAI 兼容端点
export OCR_LLM_TOKEN=sk-...
export OCR_LLM_MODEL=deepseek-chat
ocr review --format json --output result.json
```

---

## 二、本次实测基线（可复现）

| 项目 | 命令 | 结果 |
|---|---|---|
| Android 单测 | `./gradlew :app:testDebugUnitTest --offline` | **BUILD SUCCESSFUL**，46 个测试类 / **263 用例 / 0 失败 / 0 跳过** |
| Android Lint | `./gradlew :app:lintDebug --offline` | **0 errors / 211 warnings / 7 hints** |
| 服务端单测 | `cd lkl-pay-server && ./mvnw -o test` | **7 个测试类 / 69 用例 / 0 失败 / 0 错误** |
| 服务端配置自检 | 启动日志 | `keyReady=true recoveryReady=false entitlementReady=true` |
| 代码规模（本分支改动） | `git diff --shortstat` | 59 文件 / +2482 / −1800（含未跟踪新文件后 +9386） |
| 静态检查工具链 | — | 仍只有 Android Lint；**无 ktlint / detekt / spotless** |

服务端日志里那句 `recoveryReady=false` 是本次最值得注意的信号，见下文 P0-1。

---

## 三、先说做对的部分（这些不是客套，是有证据的）

审查如果只列问题，会让人误判这个分支的风险。这个分支里**有几处改动质量明显高于平均值**，值得明确记下来：

### ✅ 1. 权益凭证把"谁说了算"从本机搬到了服务端

这是本次最核心的安全改造，方向完全正确。旧实现是三个布尔 + 用包签名当盐的 checksum，材料和算法都在 APK 里——反编译照抄就是永久 PRO。新实现：

- 服务端用私钥签 `v1.<base64url(payload)>.<base64url(RS256)>`
- 客户端只内置公钥（`EntitlementVerifier.BUILTIN_PUBLIC_KEY_PEM`）
- 本地那几个布尔键降级为"缓存"，`isActivated()` 的放行最后一票在验签

**这条防线落在攻击者拿不到的一侧**，是正确的密码学用法。而且注释里明确写了边界（"凭证可复制到第二台设备，因为产品定位就是一设备一次、不做设备绑定"）——这种把限制写清楚的诚实，比假装安全要好得多。

### ✅ 2. `PayApiThrottle` 的并发窗口修得对

旧写法是先 `compute` 里 `count++`、再在锁外读 `bucket.count <= limit`，两个并发请求可以同时读到自增后的同一个值一起放行，实际限额被放大到与并发度同阶。新写法把判定搬进 `compute` 的 lambda（对同一 key 串行），并用 `decision` 数组带出结果——这是 `ConcurrentHashMap` 的正确用法。

顺带补上了 `ensureBounded()`（超过 10 万桶整表清零）和 `auth-info` 的限流（每次调用一次 RSA 签名，原先是零成本放大器）。这两处都是真实缺口。

### ✅ 3. `clientIp()` 的 X-Forwarded-For 取法改对了

这是很有含量的一个修复：

- 先判断**直连对端是否是可信任反代**（只认回环），否则一律用 `remoteAddr`
- 采信时取 XFF 的**最后一个值**而非第一个

理由在注释里写得很清楚：`$proxy_add_x_forwarded_for`（追加语义）下最左值完全由客户端控制，取第一个值等于送无限配额；最后一个值才是 nginx 追加的真实来源。配套的 `nginx-juge-api.conf` 也改成覆写 `$remote_addr`，两边是一致的。

**这是本次最容易被漏掉、也最容易在真实攻击下兑现的一处。** 改对了。

### ✅ 4. 支付金额与权益的三重过滤

`findPaidByBuyer()` 加了 `productId == PRODUCT_ID` 和 `amountFen == 目录价` 两条过滤，文档写明了动机："只要这个 user_id 买过任何东西（哪怕是将来上架的 1 元小商品），都能凭它解锁 PRO"。这是把资损口子提前堵上，而不是等第二个 SKU 上架时才发现。

### ✅ 5. 一批"静默失败"改成了"明确失败"

- `check(editor.commit())` → 记日志 + 返回 null/提前 return（`onRestored`、`binding`、`restore` 三处）
- `catch (e: Exception)` → `catch (t: Throwable)`（渲染线程上的 OOM 是 `Error`，用 `Exception` 兜不住，会带走整个进程）
- `cachedPreview` 从组合线程同步解码 → `produceState` + `Dispatchers.IO`
- `appWidgetIds[page]` → `getOrNull(page)`（增长与滚动同帧会 `ArrayIndexOutOfBoundsException`）

### ✅ 6. `DbHelper` 的读-改-写加了事务、索引加了表存在性检查

`SELECT MAX(sort_order)` → `insert` 两步放进同一事务；`createIndexes` 在 `onOpen` 也跑一遍（给旧库补索引），并用 `tableExists` 防 `no such table` 让整个 App 起不来。这两处都是真实升级路径上的坑。

---

## 四、问题清单

### P1-1 客户端不校验凭证 payload 里的 `v`（格式版本）——版本闸门缺失

**位置**：`app/src/main/java/com/juge/app/data/EntitlementVerifier.kt:87`

```kotlin
val parts = credential.split('.')
if (parts.size != 3 || parts[0] != PREFIX) return null
// ...验签...
val json = JSONObject(String(payloadBytes, StandardCharsets.UTF_8))
Entitlement(
    productId = json.optString("productId", ""),   // 只校验了这一个
    ...
)
```

服务端签进去的 payload 是 `{"v":1,"productId":...}`（`EntitlementSigner.kt:71`），但客户端**从头到尾没读过 `v` 这个字段**。`PREFIX`（外层 `"v1"`）是 URL 格式版本，`payload.v` 是声明格式版本——两者不是一件事。

**后果**：将来若把 payload 从 `v:1` 升到 `v:2`（比如加 `expiresAt` 或改 `buyerHash` 语义），老客户端会**默默按 v1 语义解析 v2 的声明并通过验签**，而不是明确拒绝。这正是版本字段存在的意义——它现在是个装饰。

**修法**（3 行）：
```kotlin
val version = json.optInt("v", 0)
if (version != 1) return null          // 不认识的版本明确拒绝
```

**为什么算 P1 而不是 P2**：这是唯一挡在"格式演进"前面的一道闸。没有它，格式升级那天会变成一次无法回滚的静默行为变更。

---

### P1-2 凭证没有 `issuedAt` 合理性校验，也没有任何失效手段

**位置**：`EntitlementVerifier.kt:99-102`

`issuedAt` 被解析出来（`optLong`），但**不参与任何判断**。`paidAt` 同理。

这意味着：
- 一段 10 年前签的、当时合法的凭证，今天照样通过
- 服务端如果误签了一批（比如错误的 buyerHash 绑定），**没有任何吊销路径**——凭证是自包含的，服务端不维护名单

代码注释里说"不做重放防护"、"不做设备绑定"，这些取舍是明确的、可接受的。但"**没有任何吊销/失效手段**"这件事在注释里没有被承认。

对一个 ￥3.99 买断产品，这个风险可以接受，但**应当写进文档**，而不是留成隐含假设。最低成本的改进是给 `Entitlement` 加一个可选的 `expiresAt`（服务端留空 = 永不过期），客户端只在非空时校验——这给未来留了门，且不改变现有行为。

---

### P1-3 `confirmedPaidFields` 在 `buyerUserId` 缺失时用 `outTradeNo` 占位，会污染对账数据

**位置**：`lkl-pay-server/.../web/AlipayController.kt:118-130`

```kotlin
if (buyerUserId.isNullOrBlank()) {
    log.warn("查单确认已支付但订单缺少 buyerUserId，凭证 buyerHash 用订单号占位 ...")
}
if (configStatus.entitlementReady) {
    entitlementSigner.issue(
        buyerUserId = buyerUserId?.takeIf { it.isNotBlank() } ?: outTradeNo,
        ...
    )
}
```

判断"用订单号占位不会削弱授权"是对的（`buyerHash` 不参与放行）。但**注释只说了一半**：`buyerHash` 的用途是"日志/客服对账"（`EntitlementVerifier` 的文档原话），拿订单号占位之后，客服看到这串 hash 既对不上支付宝账号、也认不出是订单号——**对账价值归零，但看起来仍然像一个正常的 buyerHash**。

**更好的做法**：要么明确写一个可识别的哨兵值（如 `"missing:" + sha256(outTradeNo)`），让客服一眼看出这是缺数据的凭证；要么干脆不签发，让客户端走"服务端暂时无法签发"的路径（`ProPurchase.finishPaid` 已经处理了 `credential == null` 的情况）。

---

### P2-1 `livePendingOrders` 的"读-判断-写回"跨了锁边界（**探针未复现，仅记录**）

**位置**：`app/src/main/java/com/juge/app/pay/ProPurchase.kt:238-249`

```kotlin
private fun livePendingOrders(context: Context): List<String> {
    val entries = readPendingEntries(context)        // ← 锁外读
    val live = entries.filter { ... }
    if (live.size != entries.size) {
        synchronized(pendingLock) {
            writePendingEntries(context, live)       // ← 锁内写回旧快照
        }
    }
    ...
}
```

旧实现的 `pendingOrders()` 是 `= synchronized(pendingLock) { ... }`，**整个读都在锁内**。新实现为了"顺带清过期"把读挪到了锁外，写回时用的是锁外读到的快照。

**理论上**：若 A 线程在 `livePendingOrders` 读完快照后、`writePendingEntries` 之前，B 线程在锁内 `appendPendingOrder` 追加了新单，A 的写回会覆盖掉 B 的新单。

**实测**：我写了探针测试（`ProbePendingQueueRaceTest`，已在验证后删除）复现该时序——结果 `kept=[new-pro_permanent, recent]`，**未复现丢单**。原因是 `recoverPending` 的调用时序与 `purchase` 的互斥（`recoveryMutex` / `purchaseMutex`）在实际路径上把这个窗口挤掉了。

**结论**：这是**不变量被破坏**（"所有访问与读改写都使用同一把锁"这句注释已不再成立），但当前无可达触发路径。属于 P2 技术债，建议把读也放进锁内（或至少把 `if` 与写回合并为一个 `synchronized` 块），成本 2 行。

> 我把探针删掉了，因为一个红不起来的测试留在仓库里只会增加维护负担。这条记录的价值是：**下次若有人把这个函数改成并发调用，问题会立刻变成真的。**

---

### P2-2 CI 缺 `permissions`、`timeout-minutes`、`concurrency`

按 OCR 的 GitHub Actions 规则集逐条对照 `.github/workflows/ci.yml`：

| 规则 | 现状 | 判断 |
|---|---|---|
| `pull_request_target` 误用 | 未使用 | ✅ |
| secrets 打印到日志 | 无 secrets | ✅ |
| **`permissions` 最小权限** | **完全缺失** | ⚠️ 默认给 `contents: read/write`，对只跑测试的 workflow 过宽 |
| 第三方 action pin 到 SHA | 全部 `@v4`/`@v3` | 可接受（规则原文："first-party `actions/*` pinned to `v4` is acceptable"），但 `android-actions/setup-android@v3` 是第三方，tag 可变 |
| **`timeout-minutes`** | **缺失** | ⚠️ 卡住的 job 会烧满 6 小时（公有仓库免费额度上限） |
| **`concurrency`** | **缺失** | ⚠️ 连续 push 会并行跑多份，浪费额度 |
| script injection | 无 `${{ github.event.* }}` 进 `run:` | ✅ |
| 硬编码凭证 | 无 | ✅ |
| `fetch-depth: 0` | 不需要（无 git history 依赖） | ✅ |
| 缓存 | Maven `cache: maven` ✅；Gradle 由 `setup-gradle` 提供 ✅ | ✅ |

**建议**（三行，收益/成本比最高）：
```yaml
permissions:
  contents: read
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
# 每个 job 加 timeout-minutes: 20
```

---

### P2-3 `versionCode` 恒为 1，且注释与实际语义不符

**位置**：`app/build.gradle.kts:42`

```kotlin
// 版本号从 1 起步的那个值是脚手架默认值，一直没改过——每次发版都会
// 被商店判成「同一个包重复上传」。这里是第一次正式发布前的复位。
versionCode = 1
```

注释说"这是第一次正式发布前的复位"——**但复位到 1 并不能解决它描述的问题**。只要发过一次，下一次发版仍然必须递增。真正需要的是**发版流程里递增 `versionCode`**（或改用 Gradle 的 `abiFilter`/时间戳派生），而不是把值改成 1 并在注释里声称问题解决了。

这条是"注释承诺了它没做到的事"。建议：要么删掉"每次发版都会被判重复上传"这句（因为改 1 不解决它），要么加一条 CI 检查：`versionCode` 必须大于 `main` 分支上的值。

---

### P2-4 `PayApiThrottle.ensureBounded()` 在洪水期会清空所有人的配额

**位置**：`lkl-pay-server/.../service/PayApiThrottle.kt:73-80`

```kotlin
private fun ensureBounded() {
    if (buckets.size <= MAX_BUCKETS) return
    log.warn("...整表清零。通常意味着正在被大范围 IP 洪水刷接口。")
    buckets.clear()
}
```

代码注释承认这是"等价于一次重启"。这个取舍本身合理（内存上界确定 > 精确限流），但有一个后果没写：**洪水期间清空等于所有正常用户的配额同时归零**，攻击者只要持续制造 >10 万个不同 IP 的桶，就能在每次 `sweep` 时让限流整体失效。

在"单实例 + 内存计数"这个前提下，这是权衡的必然代价，**可以接受**，但应当在 README 的部署章节里写明"这套限流不是抗 DDoS 手段，粗粒度防洪请用 nginx `limit_req`"（`nginx-juge-api.conf` 末尾已有可选片段，建议把它从"可选"提升为"生产建议开启"）。

---

### P2-5 测试覆盖率很好，但**缺两类关键回归**

现有 263 个 Android 用例里，支付链路的覆盖相当扎实（限流、并发、队列上限、TTL、旧格式兼容都有）。但缺：

1. **凭证版本闸门**（对应 P1-1）：没有"payload `v=2` 必须被拒绝"的用例
2. **`buyerUserId` 缺失时的签发行为**（对应 P1-3）：服务端 `EntitlementSignerTest` 与 `AlipayControllerTest` 都没覆盖"订单缺买家标识"这条分支

第 1 条尤其值得补——它是本次安全改造里唯一"加了字段但没用"的地方。

---

## 五、总评

**这个分支的工程质量明显高于同规模个人项目。**

支付与权益链路上，几个最容易出错的地方都做对了：金额校验用 `BigDecimal.movePointRight(2).longValueExact()` 精确到分、CAS 幂等落库、限流判定与自增在同一原子段、XFF 只信可信反代、凭证验签落在服务端私钥侧。这些不是"看起来对"，是**在真实攻击与并发下能站住**的那种对。

问题集中在两类：

1. **"加了字段但没用"**（P1-1 的 `v`、P1-2 的 `issuedAt`）——安全设计里最典型的一类隐性缺口：代码看起来考虑到了，实际那道闸没合上。
2. **"注释承诺超过代码实现"**（P2-3 的 `versionCode`、P2-1 的锁不变量、P1-3 的对账语义）——注释写得越详细，越容易让人误以为已经做到。

**能上线吗？** 就代码本身而言，比 11-14 那份审查时的状态好了很多。但要真正上线，仍有**代码之外**的阻断项没解决（那份审查记录里的 P0：生产 443 上 `/api/*` 反代、`alipay.pid` 为空导致找回 100% 不可用）。本次审查在服务端启动日志里**再次实测确认 `recoveryReady=false`**：

```
支付宝配置自检完成 appId=2021007103686388 pid=<未配置> keyReady=true
recoveryReady=false entitlementReady=true
```

也就是说：**下单能用，找回不能用，且下单路径不会暴露这个缺口**。这与 11-14 的结论一致，至今未修。

---

## 六、附：如何用完整 OCR 能力重跑

```bash
# 1. 配 LLM（任选一个 OpenAI 兼容端点）
export OCR_LLM_URL=https://api.deepseek.com/v1
export OCR_LLM_TOKEN=sk-xxx
export OCR_LLM_MODEL=deepseek-chat

# 2. 只审本次改动（推荐，比全库 scan 快得多）
ocr review --format json --output /tmp/ocr-result.json --audience agent

# 3. 或只审某几个关键文件的全量内容
ocr scan --path lkl-pay-server/src/main/kotlin/com/juge/lklpay/web/AlipayController.kt,app/src/main/java/com/juge/app/pay/ProPurchase.kt

# 4. 注入业务背景可显著提升相关性
ocr review --background-file docs/安卓桌面自定义提醒组件APP开发需求说明书.md
```

> 注：本机 git 版本为 2.39.5，OCR 要求 ≥ 2.41.0，每次运行会打印一条 warning。不影响本次审查（仅用到 diff 与文件读取），但建议升级：`brew install git`。
