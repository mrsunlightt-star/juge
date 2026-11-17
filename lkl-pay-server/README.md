# lkl-pay-server · 句阁 App 服务端

> Kotlin + Spring Boot 4 ｜ 所属项目：句阁 App（com.juge.app）
> 支付通道：**支付宝 App 支付**（alipay.trade.app.pay，原拉卡拉方案已放弃并彻底清理）
> 权益模型：**不设账号**。PRO 绑定付款人的支付宝用户号，换机靠支付宝授权找回

## ✅ 当前状态

| 项 | 状态 |
| --- | --- |
| 下单生成 orderStr（sdkExecute） | ✅ 已实现 |
| 应用公钥验签 orderStr | ✅ 本地验证通过 |
| 生产网关签名链路（alipay.trade.query） | ✅ 真实调用通过（返回 `40004 / ACQ.TRADE_NOT_EXIST`） |
| 异步通知验签（rsaCheckV1 + app_id 校验） | ✅ 已实现，伪造通知被拒 |
| HTTP 接口（下单/查单/回调） | ✅ 启动实测通过 |
| 订单落库 + 状态机（幂等 + 金额核对） | ✅ 已实现，含单元测试 |
| 换机找回（authInfo + auth_code 换 user_id + 按买家查单） | ⚠️ 代码完成、含单元测试；**`alipay.pid` 尚未配置，线上必返 503** |
| 账号体系 | ❌ 已移除（2026-11 回退到「权益绑支付宝账号」的原始设计） |
| 买家登录名脱敏 | ✅ 入库前统一脱敏（`Sensitive`），前 3 后 4 |
| 僵尸订单清理 | ✅ `OrderMaintenanceTask`，每小时清 48 小时前的 `CREATED` |

## 目录结构

```
lkl-pay-server/
├── certs/                              # ⚠️ 已加入 .gitignore，严禁提交仓库
│   ├── alipay_app_private_key.txt      # 支付宝应用私钥（本地加签，绝不上传，权限 600）
│   ├── alipay_app_public_key.txt       # 支付宝应用公钥（已上传至开放平台）
│   └── alipay_public_key.txt           # 支付宝公钥（验证支付宝响应/通知）
├── deploy/
│   ├── nginx-juge-api.conf             # 只含 location 段，include 进现有 443 server
│   ├── juge-pay.service                # systemd unit（回环监听 / 密钥只读 / setenv）
│   ├── setenv.example                  # EnvironmentFile 模板（全是占位符）
│   ├── juge-pay-backup.service         # 备份 oneshot
│   └── juge-pay-backup.timer           # 每日 03:00 触发备份
├── scripts/
│   └── backup-db.sh                    # H2 文件库备份（由上面的 timer 调用）
├── src/main/kotlin/com/juge/lklpay/
│   ├── config/
│   │   ├── AlipayProperties.kt         # 支付宝接入参数
│   │   ├── AlipayConfigStatus.kt       # 启动自检：密钥可读性 + pid 是否配置
│   │   ├── EntitlementProperties.kt   # 权益凭证密钥路径
│   │   ├── MaintenanceProperties.kt    # 维护任务参数（清理间隔由 @Scheduled 占位符直接读 yml）
│   │   └── PayProperties.kt            # 商品目录（价格以服务端为准）
│   ├── domain/
│   │   └── PayOrder.kt                 # 支付订单（含 buyer_user_id / 脱敏后的 buyer_logon_id）
│   ├── repository/                     # Spring Data JPA
│   ├── service/
│   │   ├── AlipayPayService.kt         # 下单 / 查单 / 通知验签
│   │   ├── AlipayIdentityService.kt    # 授权串生成 / auth_code 换 user_id
│   │   ├── PayOrderService.kt          # 订单状态机 + 按买家找回 + 僵尸单清理
│   │   ├── EntitlementSigner.kt        # RS256 签发权益凭证（v1.base64url(payload).base64url(sig)）
│   │   ├── OrderMaintenanceTask.kt     # @Scheduled 定期清理
│   │   ├── PayApiThrottle.kt           # 各接口按 IP 限流（原子判定）
│   │   └── Sensitive.kt                # 入库前脱敏 + 日志参数净化（safeLog）
│   └── web/
│       └── AlipayController.kt         # /api/alipay/*
├── src/main/resources/application.yml
└── data/paydb.mv.db                    # H2 文件库（备份 = 拷贝该文件）
```

## API

### 找回已购（换手机）

**没有账号体系**：不注册、不登录、不收集手机号。权益绑定的是**付款人的支付宝用户号**
（支付结果里的 `buyer_user_id`，已随订单落库）。换设备后由 App 发起一次支付宝授权即可核验。

| 接口 | 说明 |
| --- | --- |
| `POST /api/alipay/auth-info` | 返回签好名的 `auth_info`，交给 App 的 `AuthTask.authV2` 拉起授权页。未配置 `alipay.pid` 时返回 503 |
| `POST /api/alipay/recover` | 入参 `{authCode}`；服务端换出 `user_id` 后反查已支付订单，返回 `{found, paid_at, count, product_id}`，命中且凭证密钥就绪时额外返回 `credential` |

- **不签发任何令牌**：服务端不维护登录态，没有可注销的账号、没有可泄露的口令
- 签名在服务端完成（`alipay.open.auth.sdk.code.get`），应用私钥永不出服务器
- 找回必须同时满足三条：订单 `PAID`、`product_id == pro_permanent`、`amount_fen == 当前目录价`。
  只匹配 `PAID` 是为了挡「创建过但没付钱」；后两条是为了挡「买过别的商品」与「历史错价单」
- 代付场景（家人朋友付款）无法自动找回，需人工凭交易记录处理

### 支付

#### 下单 `POST /api/alipay/create`
```json
{ "productId": "pro_permanent" }
```
→ `{ "success": true, "data": { "out_trade_no": "...", "order_str": "..." } }`

**只传商品 ID、不传金额**：金额由服务端商品目录（`pay.products`）决定。若允许客户端传金额，任何人都能以 1 分钱下单并取得一笔真实「已支付」的订单。

**也不传任何用户标识**：这笔钱算谁的，由支付宝回调里的买家 `user_id` 决定，与 App 侧状态无关。

App 端拿到 `order_str` 后交给 `PayTask.payV2(orderStr, true)` 拉起支付宝。
**`order_str` 只在服务端加签生成，不产生网络请求**；客户端同步返回码（如 9000）不代表到账。

#### 查单 `GET /api/alipay/query?outTradeNo=xxx`
→ `trade_status`：`TRADE_SUCCESS` 已支付 / `TRADE_CLOSED` 已关闭 / `WAIT_BUYER_PAY` 待支付；
订单不存在返回 `code=40004`、`sub_code=ACQ.TRADE_NOT_EXIST`（属正常态，非故障）。
客户端返回 8000（处理中）/ 6004（结果未知）时，必须以此接口结论为准。

查单会顺带补齐本地订单状态——异步通知可能延迟甚至丢失。

#### 回调 `POST /api/alipay/notify`（支付宝服务端调用）
处理顺序不可调换：**先验签 → 再处理业务（幂等）→ 返回 `success`**。
返回非 `success` 时支付宝会按策略重投（25 小时内约 8 次）。

验签通过后一律回 `success`：业务侧异常（订单不存在、金额不符）重投也不会自愈，
只会刷日志，因此改以 ERROR 日志 + 订单留档供人工介入。

> 该地址须与开放平台「应用网关」填写一致，当前为 `https://puretxt.cn/api/alipay/notify`。
> **它依赖 Nginx 反代**，反代没配就等于通知永远收不到——见 `deploy/nginx-juge-api.conf`。

### 订单状态机

`CREATED → PAID / CLOSED`，三处防护：

1. 状态变更走 compare-and-set：只有把 `CREATED` 改成 `PAID` 的那一次才继续，重投与并发只有一个赢家
2. 金额核对——与商品目录不符、或金额缺失/不可解析，一律拒绝置为已支付（**幂等分支也不能绕过**）
3. 买家标识只在首次流转时写入；`buyer_logon_id` 与 `notify_raw` 在入库前脱敏

### 权益凭证（PRO）

本地那份激活记录可以改，所以「是不是 PRO」最终由服务端签发的 `credential` 决定：

```
v1.<base64url(payload)>.<base64url(SHA256withRSA 签名)>
```

payload = `{v, productId, buyerHash, paidAt, issuedAt, nonce}`，其中 `buyerHash`
是支付宝用户号的 SHA-256，不含明文。客户端用内置公钥
（`app/.../data/EntitlementVerifier.kt` 的 `BUILTIN_PUBLIC_KEY_PEM`）验签，
并校验 `productId == pro_permanent`；本地 prefs 只用来避免每次都验一遍。

- 签发点只有两处：查单确认已支付、换机找回命中。`productId` 两处都传
  `PayOrderService.PRODUCT_ID`，不能一处用订单里的值、一处用常量。
- 密钥对放在 `certs/entitlement_{private,public}_key.pem`（与支付宝三把钥匙独立），
  私钥不入库。更换这一对会让**已发出的凭证全部失效**，用户从 PRO 掉回免费版，
  必须先让旧版本发完新凭证再换。
- 部署前逐字节核对生产公钥与客户端内置公钥：

  ```bash
  openssl pkey -in certs/entitlement_private_key.pem -pubout -outform DER | shasum -a 256
  # EntitlementSignerTest 在本机存在 certs 时会直接断言两者 DER 一致
  ```

- 单元测试用 `src/test/resources/entitlement-test-*.pem`，与
  `app/src/test/resources/entitlement-test-*.pem` 是**同一把测试密钥**
  （有断言防漂移）。生产私钥不入库，因此干净 clone / CI 也能跑全量测试。

## 运行

```bash
./mvnw spring-boot:run            # 只监听 127.0.0.1:8081
./mvnw test                       # 订单幂等 / 金额核对 / 脱敏 / 限流 / 找回过滤 / 权益凭证 / 配置自检
```

> 若 Kotlin 编译守护进程被沙箱拦截，加 `-Dkotlin.compiler.execution.strategy=in-process`。

## 部署

### 1. Nginx 反代（**当前线上缺这一步，所有 `/api/*` 都是 404**）

`deploy/nginx-juge-api.conf` 是**纯 location 片段**（不含 `server{}`），只能 include
进已经存在的 443 server 块。直接 `cp` 到 `conf.d/` 会与静态站点争抢
同一个 `server_name + listen`，nginx 只认先加载的那个，API 规则反而不生效。

```bash
sudo install -m 644 deploy/nginx-juge-api.conf /etc/nginx/snippets/juge-api-locations.conf
# 编辑 puretxt.cn 现存的 server { listen 443 ssl; ... }，在块内加一行：
#     include snippets/juge-api-locations.conf;
sudo nginx -t && sudo systemctl reload nginx
```

验证：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -X POST https://puretxt.cn/api/alipay/auth-info
# 期望 503（pid 未配）或 200；**不应是 404**。404 = 反代没生效

# 查一个不存在的单：期望 404 + JSON（ORDER_MISSING），**不应是 nginx 的 HTML 404**
curl -sS 'https://puretxt.cn/api/alipay/query?outTradeNo=__probe__' | head -c 120

# 下单会真实写一行 CREATED（由 OrderMaintenanceTask 48 小时后清理），
# 确认链路已通之后再跑这条，不要拿它当日常探针：
curl -sS -X POST https://puretxt.cn/api/alipay/create \
  -H 'Content-Type: application/json' -d '{"productId":"pro_permanent"}'
# 期望 JSON（含 order_str）；若返回 HTML，说明命中的是 nginx 错误页而非本服务
```

### 2. 应用只监听回环

`application.yml` 已设 `server.address: 127.0.0.1`。部署后确认：

```bash
ss -lntp | grep 8081        # 必须是 127.0.0.1:8081，不能是 0.0.0.0:8081 或 *:8081
```

### 3. 配置项（环境变量 / 启动参数）

| 变量 | 说明 |
| --- | --- |
| `PAY_DB_PATH` | H2 库文件绝对路径，如 `/opt/juge/data/paydb`。相对路径会随工作目录漂移 |
| `PAY_DB_USER` / `PAY_DB_PASSWORD` | 库账号口令，默认 `sa` / 空。**生产必须设口令** |
| `PAY_LOG_PATH` | 日志文件路径，默认 `./logs/lkl-pay-server.log`，自带 20MB×14 轮转 |
| `--alipay.pid` | 签约支付宝账号 PID（2088 开头），systemd 下由 `setenv` 的 `ALIPAY_PID` 展开。**留空 = 找回功能 100% 不可用** |
| `--alipay.private-key-path` | 建议 `/etc/juge/alipay_app_private_key.txt`，`chmod 600` |

启动后必须能在日志里看到自检结论：

```
支付宝配置自检完成 appId=... pid=2088******** recoveryReady=true ...
```

`recoveryReady=false` 说明找回功能不可用——不会影响下单，所以很容易被忽略。

### 4. 进程与备份（systemd）

```bash
sudo install -m 644 deploy/juge-pay.service /etc/systemd/system/juge-pay.service
sudo install -m 600 deploy/setenv.example /opt/juge/lkl-pay-server/setenv
sudo systemctl daemon-reload && sudo systemctl enable --now juge-pay.service
journalctl -u juge-pay -f     # 确认自检日志里 recoveryReady=true

sudo install -m 644 deploy/juge-pay-backup.service /etc/systemd/system/
sudo install -m 644 deploy/juge-pay-backup.timer   /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now juge-pay-backup.timer
systemctl list-timers juge-pay-backup.timer
```

`backup-db.sh` 自带 H2 热备与 14 天保留，但**备份和库在同一台机器上**：
服务器整盘丢失时它救不了，请定期把备份文件拉回本地另存一份。

## 上线前 Checklist

（逐步清单原先记在本地工单 `.scratch/alipay-app-pay/issues/01-pre-launch-checklist.md`——**该目录不入库**，仓库里看不到；下表是它的现役摘要。前 3 条不做，付费与找回 **100% 不可用**。）

1. **Nginx 把 `/api/*` 反代到 8081**：把 `deploy/nginx-juge-api.conf` include 进现有 443 server。
   账号接口 `/api/auth/*` 已随账号体系移除，不需要放行。
   部署后跑上面「验证」里的三条无副作用 curl，任一返回 nginx HTML 404 都算没通
2. **填 `alipay.pid`**（`setenv` 的 `ALIPAY_PID`），否则「换机找回」上线即不可用，
   而下单/查单照常、自测发现不了。启动日志必须出现 `recoveryReady=true`
3. **装 systemd**：`juge-pay.service`（回环监听 + 密钥只读）与 `juge-pay-backup.timer`（每日备份）。
   `ss -lntp | grep 8081` 必须是 `127.0.0.1:8081`
4. 配置服务器 IP 白名单（选**全量接口**，IP `1.15.174.33`）——服务端部署到服务器后再配
5. 开放平台「应用网关」填 `https://puretxt.cn/api/alipay/notify`（与请求里的 `notify_url` 同值）
6. 做一笔 0.01 元真实支付，确认收到异步通知且验签通过；同时确认 CI 两侧测试全绿
7. 把备份文件定期拉回本地另存——服务器整盘丢失时，同机备份没有意义

## 安全提醒

- `certs/` 整个目录已在 .gitignore，**私钥绝不能进仓库**
- 异步通知**必须先验签再信任参数**，未验签的参数可被任意伪造
- 通知处理必须幂等（同一订单会收到多次通知）
- 服务端私钥不得下发到 App 端；App 端只持有服务端返回的 `order_str`
- 下单金额只取服务端商品目录；回调金额缺失或与订单不符一律拒绝置为已支付（`AmountMissing`/`AmountMismatch`）
- 买家登录名（`buyer_logon_id`）与通知留档在**入库前**脱敏；原始值不落库、不写日志
- 外部可控参数（`out_trade_no` / `app_id` / `total_amount` / XFF 等）写日志前统一过
  `Sensitive.safeLog()`（Controller 的 `safe()` 只是它的委托），连限流桶与金额校验日志也覆盖，防 CRLF 日志注入
- 下单/查单/找回/auth-info/通知均已按来源 IP 限流（`PayApiThrottle`：20 / 60 / 10 / 10 / 300 每分钟）

## 部署安全基线

以下是代码兜不住、必须部署时人工确认的项：

1. **腾讯云安全组只放行 80/443**：8081 是明文 HTTP。
   应用侧已改为只监听 `127.0.0.1`，但安全组仍应确认没放行 8081。
2. **Nginx 必须覆写（而非追加）`X-Forwarded-For`**：
   ```nginx
   proxy_set_header X-Forwarded-For $remote_addr;
   ```
   ⚠️ 不要用 `$proxy_add_x_forwarded_for` —— 那是**追加**语义，客户端自带的值会留在最左边，
   任何人都能靠加一个请求头拿到无限配额。
   代码侧已双保险：只有直连对端是回环地址时才采信转发头，且取其**最后一个**值。
3. 服务器上私钥移出应用目录（如 `/etc/juge/`）、`chmod 600`、属主为运行账户，
   并用启动参数 `--alipay.private-key-path=/etc/juge/alipay_app_private_key.txt` 指定。
4. 为 `OrderMissing`/`AmountMismatch`/`AmountMissing` 的 ERROR 日志配置告警
   （日志关键字 webhook 即可），这几类是真实攻击信号，没有人看等于没有防线。
5. `PAY_DB_PASSWORD` 必须设：库里是全部买家的支付宝用户号与订单。
