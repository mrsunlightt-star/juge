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
| 账号注册 / 登录 / 登出 / 令牌校验 | ✅ 已实现，含单元测试 |
| 支付成功后按订单归属给账号开 PRO | ✅ 已实现，含单元测试 |

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
│   │   ├── PayOrderService.kt          # 订单状态机 + 开通 PRO
│   │   ├── UserService.kt              # 注册 / 登录 / 登录态校验
│   │   ├── PasswordHasher.kt           # PBKDF2-HMAC-SHA256
│   │   └── LoginThrottle.kt            # 登录失败节流
│   └── web/
│       ├── AlipayController.kt         # /api/alipay/*
│       └── AuthController.kt           # /api/auth/*
├── src/main/resources/application.yml
└── data/paydb.mv.db                    # H2 文件库（备份 = 拷贝该文件）
```

## API

### 账号

账号是**可选**能力：不登录也能完整使用 App，登录的唯一收益是换机/重装后找回已购 PRO。

| 接口 | 说明 |
| --- | --- |
| `POST /api/auth/register` | 入参 `{username, password, nickname?}`，返回 `{token, user}` |
| `POST /api/auth/login` | 入参 `{username, password}`，返回 `{token, user}` |
| `POST /api/auth/logout` | 需 `Authorization: Bearer <token>`，作废当前令牌 |
| `GET /api/auth/me` | 需 `Authorization: Bearer <token>`，返回账号信息（含 `pro`） |

- 用户名：3~32 位字母、数字或下划线，统一小写存储
- 口令：6~64 位；PBKDF2-HMAC-SHA256（210k 次迭代 + 16 字节随机盐），存储串形如 `pbkdf2$迭代次数$盐$摘要`
- 失败响应用 `code` 表达原因：`USERNAME_TAKEN`(409) / `BAD_CREDENTIALS`(401) / `RATE_LIMITED`(429) / `UNAUTHORIZED`(401) / `INVALID_ARGUMENT`(400)
- 同一用户名连续失败 5 次即锁定 10 分钟（内存计数，重启清零）
- 口令**无法自助找回**（无短信/邮件通道），需凭支付宝交易记录人工处理

### 支付

#### 下单 `POST /api/alipay/create`
```json
{ "productId": "pro_permanent", "token": "可选，登录令牌" }
```
→ `{ "success": true, "data": { "out_trade_no": "...", "order_str": "..." } }`

**只传商品 ID、不传金额**：金额由服务端商品目录（`pay.products`）决定。若允许客户端传金额，任何人都能以 1 分钱下单并取得一笔真实「已支付」的订单。

带上 `token` 时订单会归属到该账号，支付成功后 PRO 直接开到账号上；令牌无效按游客处理（过期登录不该拦住付款）。

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

支付宝侧（详见 `.scratch/alipay-app-pay/issues/01-pre-launch-checklist.md`）：

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
- 口令比对使用恒定时间比较；账号不存在时也走一次哈希校验，避免用响应时间枚举用户名