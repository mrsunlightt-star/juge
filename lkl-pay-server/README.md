# lkl-pay-server · 句阁 App 服务端

> Kotlin + Spring Boot 4 ｜ 所属项目：句阁 App（com.juge.app）
> 支付通道：**支付宝 App 支付**（alipay.trade.app.pay，原拉卡拉方案已放弃并彻底清理）
> 账号体系：**可选登录**（用户名 + 口令），仅用于跨设备找回已购 PRO

## ✅ 当前状态

| 项 | 状态 |
| --- | --- |
| 下单生成 orderStr（sdkExecute） | ✅ 已实现 |
| 应用公钥验签 orderStr | ✅ 本地验证通过 |
| 生产网关签名链路（alipay.trade.query） | ✅ 真实调用通过（返回 `40004 / ACQ.TRADE_NOT_EXIST`） |
| 异步通知验签（rsaCheckV1 + app_id 校验） | ✅ 已实现，伪造通知被拒 |
| HTTP 接口（下单/查单/回调） | ✅ 启动实测通过 |
| 订单落库 + 状态机（幂等 + 金额核对） | ✅ 已实现，含单元测试 |
| 换机找回（authInfo + auth_code 换 user_id + 按买家查单） | ✅ 代码完成，含单元测试；**待真机联调** |
| 账号体系 | ❌ 已移除（2026-11 回退到「权益绑支付宝账号」的原始设计） |

## 目录结构

```
lkl-pay-server/
├── certs/                              # ⚠️ 已加入 .gitignore，严禁提交仓库
│   ├── alipay_app_private_key.txt      # 支付宝应用私钥（本地加签，绝不上传）
│   ├── alipay_app_public_key.txt       # 支付宝应用公钥（已上传至开放平台）
│   └── alipay_public_key.txt           # 支付宝公钥（验证支付宝响应/通知）
├── src/main/kotlin/com/juge/lklpay/
│   ├── config/
│   │   ├── AlipayProperties.kt         # 支付宝接入参数
│   │   ├── AuthProperties.kt           # 账号体系参数（令牌有效期、登录节流）
│   │   └── PayProperties.kt            # 商品目录（价格以服务端为准）
│   ├── domain/
│   │   ├── PayOrder.kt                 # 支付订单
│   │   ├── UserAccount.kt              # 用户账号（PRO 的权威归属）
│   │   └── AuthToken.kt                # 登录令牌（只存摘要）
│   ├── repository/                     # Spring Data JPA
│   ├── service/
│   │   ├── AlipayPayService.kt         # 下单 / 查单 / 通知验签
│   │   ├── PayOrderService.kt          # 订单状态机 + 按买家找回
│   │   ├── AlipayIdentityService.kt    # 授权串生成 / auth_code 换 user_id
│   │   └── PayApiThrottle.kt           # 下单/查单/找回的频率限制
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
| `POST /api/alipay/recover` | 入参 `{authCode}`；服务端换出 `user_id` 后反查已支付订单，返回 `{found, paid_at, count}` |

- **不签发任何令牌**：服务端不维护登录态，没有可注销的账号、没有可泄露的口令
- 签名在服务端完成（`alipay.open.auth.sdk.code.get`），应用私钥永不出服务器
- 只匹配 `PAID` 订单：`CREATED` / `CLOSED` 都不能用来恢复权益
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

### 订单状态机

`CREATED → PAID / CLOSED`，三处防护：

1. 已是 `PAID` 直接短路——同一订单会被重投多次，重复处理等于重复发货
2. 金额核对——与商品目录不符一律拒绝置为已支付
3. 开通 PRO 只走一次——账号已是 PRO 直接跳过

## 运行

```bash
./mvnw spring-boot:run            # 使用 8081 端口
./mvnw test                       # 16 项测试：订单幂等/金额核对 + 账号流程
```

> 若 Kotlin 编译守护进程被沙箱拦截，加 `-Dkotlin.compiler.execution.strategy=in-process`。

## 上线前 Checklist

支付宝侧（逐步清单原先记在本地工单 `.scratch/alipay-app-pay/issues/01-pre-launch-checklist.md`——**该目录不入库**，仓库里看不到；下表的 4 条是它的现役摘要）：

1. 配置服务器 IP 白名单（选**全量接口**，IP `1.15.174.33`）——服务端部署到服务器后再配
2. 开放平台「应用网关」填 `https://puretxt.cn/api/alipay/notify`（与请求里的 `notify_url` 同值）
3. Nginx 将 `/api/*` 转发至 Spring Boot 8081（**账号接口 `/api/auth/*` 与支付接口 `/api/alipay/*` 都要放行**）
4. 做一笔 0.01 元真实支付，确认收到异步通知且验签通过

## 安全提醒

- `certs/` 整个目录已在 .gitignore，**私钥绝不能进仓库**
- 异步通知**必须先验签再信任参数**，未验签的参数可被任意伪造
- 通知处理必须幂等（同一订单会收到多次通知）
- 服务端私钥不得下发到 App 端；App 端只持有服务端返回的 `order_str`
- 令牌表只存 SHA-256 摘要，数据库被拖走也无法反推出可用令牌
- 口令比对使用恒定时间比较；账号不存在时也走一次哈希校验，避免用响应时间枚举用户名- 下单金额只取服务端商品目录；回调金额缺失或与订单不符一律拒绝置为已支付（`AmountMissing`/`AmountMismatch`）
- 下单/查单接口已按来源 IP 限流（`PayApiThrottle`：下单 20 次/分钟，查单 60 次/分钟）

## 部署安全基线（2026-10-02 审查后新增）

以下两项代码无法兜底，部署时必须人工确认：

1. **腾讯云安全组只放行 80/443**：应用层 8081 为明文 HTTP，若安全组放行 8081，
   Bearer 令牌可绕过 TLS 直达应用被中间人截获。8081 应仅本机回环可达。
2. **Nginx 必须覆写（而非透传）X-Forwarded-For**：
   `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`
   否则客户端可伪造该头绕过登录节流与支付接口限流。
3. 建议：服务器上私钥移出应用目录（如 `/etc/juge/`）、`chmod 600`、属主为运行账户，
   并用启动参数 `--alipay.private-key-path=/etc/juge/alipay_app_private_key.txt` 指定。
4. 建议：为 `OrderMissing`/`AmountMismatch`/`AmountMissing` 的 ERROR 日志配置告警
   （日志关键字 webhook 即可），这两类是真实攻击信号，没有人看等于没有防线。
