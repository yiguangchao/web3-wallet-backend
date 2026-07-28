# web3-wallet-backend

Web3 Java 后端/区块链托管钱包服务。当前版本基于 Spring Boot + Web3j，提供平台 HD 充值地址分配、ETH/ERC-20 充值扫描与归集、内部资产账本、提现审核与广播等能力。

## 技术栈

- Java 17
- Spring Boot 3.x
- Maven
- MySQL 8
- Redis
- MyBatis-Plus
- Web3j
- Spring Security + JWT
- Lombok
- Spring Validation
- Springdoc OpenAPI / Swagger
- Docker Compose

## 模块说明

- `module.user`: 注册、登录、用户数据
- `module.wallet`: 托管充值地址分配、地址生命周期、充值归集、ETH/ERC-20 余额查询
- `module.asset`: 服务端资产注册表、资产账户、资产流水
- `module.chain`: 当前区块、交易回执查询
- `module.deposit`: ETH/ERC-20 扫块、确认入账、链重组处理
- `module.withdraw`: 提现申请、审核、签名广播、状态同步
- `infrastructure.web3`: Web3j Sepolia RPC 能力
- `infrastructure.security`: JWT 鉴权

## 本地运行

1. 安装 JDK 17 和 Maven。
2. 启动 MySQL 与 Redis：

```bash
docker compose up -d mysql redis
```

3. 配置 Sepolia RPC：

```bash
export WEB3_RPC_URL=https://sepolia.infura.io/v3/your-key
```

Windows PowerShell：

```powershell
$env:WEB3_RPC_URL="https://sepolia.infura.io/v3/your-key"
```

RPC 客户端统一应用连接、读写和总调用超时，并对网络异常、HTTP 408/429/500/502/503/504 进行指数退避重试。默认限制为每秒 10 个请求，可通过以下环境变量调整：

- `WEB3_CONNECT_TIMEOUT`：连接超时，默认 `5000` 毫秒
- `WEB3_READ_TIMEOUT`：读取超时，默认 `15000` 毫秒
- `WEB3_WRITE_TIMEOUT`：写入超时，默认 `10000` 毫秒
- `WEB3_CALL_TIMEOUT`：单次完整调用超时，默认 `30000` 毫秒
- `WEB3_MAX_RETRIES`：最大重试次数，默认 `2`
- `WEB3_RETRY_BACKOFF`：首次退避时间，默认 `500` 毫秒
- `WEB3_RETRY_MAX_BACKOFF`：最大退避时间，默认 `5000` 毫秒
- `WEB3_MAX_REQUESTS_PER_SECOND`：实例级 RPC 请求速率，默认 `10`

4. 启动应用：

```bash
mvn spring-boot:run
```

## MySQL 初始化

Docker Compose 只负责创建 `web3_wallet` 数据库。应用启动时 Flyway 会自动执行 `src/main/resources/db/migration` 中尚未执行的版本迁移，并通过 `flyway_schema_history` 记录数据库版本。已有非空数据库会自动建立版本 `0` 基线，再执行后续迁移。

默认连接配置：

- URL: `jdbc:mysql://localhost:3306/web3_wallet`
- Username: `root`
- Password: `root123456`

## Redis 启动

```bash
docker compose up -d redis
```

默认地址：`localhost:6379`。

## API 文档

启动后访问：

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## 当前功能

- 用户注册、登录
- BCrypt 密码加密
- JWT 鉴权和后台 RBAC
- 基于 BIP-44 的平台托管充值地址分配
- 地址派生索引、路径和密钥版本管理
- 地址全局唯一、启用、停用和退役
- 外部钱包 challenge、EIP-191 签名验权和防重放绑定
- 校验以太坊地址格式
- 查询 ETH 余额
- 查询 ERC-20 Token 余额
- 查询 Sepolia 当前区块与交易回执
- ETH/ERC-20 扫块、确认数、断点续扫和链重组处理
- 充值确认独立入账，归集补偿任务异步补建归集单
- ETH/ERC-20 充值地址归集、失败重试和回执确认
- 提现申请幂等、余额校验、资产冻结与冻结流水
- 提现审核、广播、回执同步和操作审计

## 托管充值地址

用户不再把自己控制私钥的外部地址作为充值地址。系统从平台 HD 主钱包为用户分配充值地址：

```text
平台助记词
  -> m/44'/60'/account'/0/index
  -> custody_deposit_address
  -> 扫描地址入账
  -> custody_sweep_order
  -> 归集到平台资金地址
```

数据库只保存 `key_version`、`derivation_index` 和 `derivation_path`，不保存子私钥。当前仓库提供本地 HD 签名实现，生产环境应将 `CustodyKeyService` 替换为 Vault、KMS、HSM 或独立签名服务。

分配或查询当前充值地址：

```http
POST /api/wallet/deposit-address
Authorization: Bearer <token>
Content-Type: application/json

{"chain":"ETH_SEPOLIA"}
```

```http
GET /api/wallet/deposit-addresses
Authorization: Bearer <token>
```

同一用户、同一链最多有一个 `ACTIVE` 地址。后台可以将地址设为 `DISABLED` 或 `RETIRED`，之后用户可以获得新地址。历史地址永不分配给其他用户；扫描器只加载 `PLATFORM_CUSTODY + DEPOSIT + ACTIVE` 地址，停用前必须完成业务侧风险确认。`RETIRED` 是终态，不能恢复为启用或停用。

后台地址状态与归集接口：

- `PUT /api/admin/wallet/deposit-addresses/{id}/status`
- `GET /api/admin/wallet/sweeps`
- `POST /api/admin/wallet/sweeps/{id}/retry`
- `POST /api/admin/wallet/sweeps/run`

`OPERATOR` 或 `ADMIN` 才能调用这些接口。

## 外部钱包所有权验证

外部钱包仅用于证明用户与链上地址的关系，不能作为充值扫描和内部资产入账依据。绑定过程使用 EIP-191 `personal_sign`：

```http
POST /api/wallet/external-addresses/challenge
Authorization: Bearer <token>
Content-Type: application/json

{"chain":"ETH_SEPOLIA","address":"0x用户钱包地址"}
```

客户端必须原样签署响应中的 `message`，然后提交签名：

```http
POST /api/wallet/external-addresses/verify
Authorization: Bearer <token>
Content-Type: application/json

{"challengeId":123,"signature":"0x..."}
```

challenge 默认 5 分钟过期且只能成功使用一次。消息固定绑定用途、用户 ID、链、地址、随机 nonce、签发时间和过期时间，并通过数据库条件更新原子消费；签名恢复地址必须与请求地址完全一致。`wallet_address` 使用 `(chain, address)` 全局唯一约束，同一个链上地址不能绑定给多个用户。已验证地址可通过 `GET /api/wallet/external-addresses` 查询。

可通过 `WALLET_SIGNATURE_CHALLENGE_TTL` 修改有效期，单位为毫秒。

## 托管钱包配置

功能默认关闭。不要把生产助记词写入仓库或配置文件：

```powershell
$env:WALLET_CUSTODY_ENABLED="true"
$env:WALLET_CUSTODY_MNEMONIC="平台助记词"
$env:WALLET_CUSTODY_KEY_VERSION="v1"
$env:WALLET_CUSTODY_COLLECTION_ADDRESS="0x平台归集地址"
$env:WALLET_CUSTODY_SWEEP_ENABLED="true"
```

默认派生路径为 `m/44'/60'/0'/0/index`。可以通过 `WALLET_CUSTODY_ACCOUNT` 修改 account 段。增加或轮换主密钥时必须保留旧 `key_version` 的签名能力，否则旧充值地址将无法归集。

ETH 归集发送“余额减 Gas 和配置保留额”。ERC-20 归集发送地址中的当前 Token 余额，充值地址必须预先有足够 ETH 支付 Gas；Gas 补给应由独立任务管理，避免与提现热钱包共用未经管理的 nonce。

## 提现冻结

`POST /api/withdraw/apply` 需要客户端提供唯一 `requestId` 和服务端注册的 `assetCode`。相同用户重复提交同一个 `requestId` 时返回原订单，不会重复冻结。链、合约地址、symbol、decimals、限额和手续费全部从 `supported_asset` 读取，冻结金额为 `amount + platformWithdrawFee`。旧版 `chain`、`tokenSymbol`、`tokenAddress`、`tokenDecimals` 和 `fee` 字段暂时可以反序列化，但已废弃且业务层完全忽略。

```json
{
  "requestId": "withdraw-20260706-001",
  "assetCode": "ETH",
  "toAddress": "0x1111111111111111111111111111111111111111",
  "amount": 0.01
}
```

订单状态 `0` 表示资产已冻结、等待审核。申请失败或余额不足时，订单和资产变更会在同一事务中回滚。

V9 新增 `asset_freeze_detail`，提现本金、平台手续费和实际冻结金额都由服务端固化。冻结明细只允许以下单向状态迁移：

```text
FROZEN -> CONFIRMED
FROZEN -> RELEASED
```

`CONFIRMED` 与 `RELEASED` 互斥且都是终态。确认和释放不再接收客户端金额或手续费，而是在事务内通过 `SELECT FOR UPDATE` 锁定冻结明细与资产账户，并使用冻结时保存的金额结算。账户更新、冻结状态迁移和资金流水写入处于同一事务；资金流水通过 `(business_type, business_id)` 唯一约束保证幂等。

所有资金操作在更新前后都检查以下不变量，数据库也通过 V9 `CHECK` 约束兜底：

```text
available >= 0
frozen >= 0
total = available + frozen
```

升级生产数据库前，先执行只读检查脚本 `docs/sql/V9__asset_ledger_preflight.sql`，确认历史账户、提现订单和流水满足 V9 约束后再执行 Flyway 迁移。

## 提现状态机与权限

V10 将提现订单状态固定为以下单向流程：

```text
PENDING_REVIEW(0) -> APPROVED(6) -> SIGNING(7) -> SIGNED(8)
                  -> BROADCASTING(1) -> BROADCASTED(2)
                  -> MINED(9) -> CONFIRMED(3)
PENDING_REVIEW(0) -> REJECTED(5)
任意非终态发生不确定异常 -> MANUAL_REVIEW(4)
```

`CONFIRMED`、`REJECTED` 和 `MANUAL_REVIEW` 当前都是终态。每次迁移使用 `WHERE id = ? AND status = ?` 条件更新并严格校验影响行数，未声明的状态边直接拒绝；成功迁移后记录操作人、角色、IP、前后状态和备注。审核拒绝只允许从 `PENDING_REVIEW` 发生，并与冻结资金释放处于同一事务；进入 `MANUAL_REVIEW` 时不自动释放资金。

V11/V12 已将 Nonce、签名和广播拆分。接口调用只分配 Nonce、调用签名器并在同一数据库事务中写入 `withdraw_chain_transaction` 与 `transaction_outbox`，随后返回本地计算的 `txHash`；后台广播器再投递已经固化的 `raw_transaction`。成功回执先把订单推进到 `MINED`，下一次同步才在同一事务内扣减冻结资金并推进到 `CONFIRMED`，避免“链上已打包”和“内部账本已最终结算”混成一个状态。

后台权限：

- `REVIEWER` 或 `ADMIN`：审核通过、审核拒绝。
- `OPERATOR` 或 `ADMIN`：广播、同步链上状态。
- 仅 `ADMIN`：查询提现审计日志。

全局开关通过环境变量控制，币种级开关继续使用 `supported_asset.deposit_enabled` 和 `supported_asset.withdraw_enabled`；全局和币种开关必须同时开启：

```powershell
$env:WALLET_DEPOSIT_ENABLED="true"
$env:WALLET_WITHDRAW_ENABLED="true"
```

升级 V10 前先执行 `docs/sql/V10__withdraw_state_preflight.sql`，确认历史失败订单的冻结明细已经释放。旧版 `PROCESSING` 无法证明广播结果，迁移时会进入 `MANUAL_REVIEW` 并继续冻结资金。

## 提现 Nonce、签名隔离与 Outbox

`wallet_nonce` 按 `(chain_id, hot_wallet_address)` 保存下一可用 Nonce。分配时先用 `SELECT FOR UPDATE` 锁定提现订单和 Nonce 行，并取 `max(database next_nonce, chain pending nonce)`；订单通过 `(chain_id, hot_wallet_address, nonce)` 唯一约束保证一笔订单只拥有一个 Nonce，重复准备不会再次访问 RPC 或重新签名。

`TransactionSigner` 是唯一签名入口。`LocalDevSigner` 只在 `dev`/`test` Profile 注册；其他 Profile 只注册 `RemoteSignerClient`。远程签名结果会在应用内重新解码并校验发送方、chainId、Nonce、Gas、接收方、金额和 data，同时以 `rawTransaction` 本地计算 `txHash`，不能信任远程服务声明的哈希。

生产环境至少需要配置：

```powershell
$env:WALLET_SIGNER_HOT_WALLET_ADDRESS="0x平台热钱包地址"
$env:WALLET_SIGNER_KEY_ID="withdraw-v1"
$env:WALLET_SIGNER_REMOTE_URL="https://内部签名服务"
```

Outbox 状态为 `PENDING -> PROCESSING -> SENT`，失败在达到上限前回到 `PENDING`。广播器每次都发送数据库中同一份 `raw_transaction`；RPC 响应超时时会查询本地 `txHash`，已经可查则按成功处理，否则按配置延迟重试。服务重启后，超出租约时间的 `PROCESSING` 会自动恢复。超过最大重试次数后 Outbox 进入 `DEAD`，订单进入 `MANUAL_REVIEW`，冻结资金不会自动释放。

可通过 `WALLET_WITHDRAW_BROADCAST_ENABLED`、`WALLET_WITHDRAW_BROADCAST_MAX_ATTEMPTS`、`WALLET_WITHDRAW_BROADCAST_RETRY_DELAY` 和 `WALLET_WITHDRAW_BROADCAST_PROCESSING_TIMEOUT` 调整广播任务。生产升级前依次执行 `docs/sql/V11__wallet_nonce_preflight.sql` 和 `docs/sql/V12__withdraw_outbox_preflight.sql`。

## Gas、Receipt、确认数与链重组

V13 将提现交易固定为 EIP-1559 格式。准备交易时通过 `eth_estimateGas` 获取预估 Gas，向上应用安全系数，并同时限制 Gas Limit 和 `gasLimit * maxFeePerGas`；签名前检查热钱包可用 ETH，ERC-20 提现还会检查 Token 原始单位余额。当前实现只面向配置的单条 EVM 链、原生 ETH 和 `supported_asset` 中启用的 ERC-20（默认种子数据为 Sepolia USDC），不包含多链路由。

后台 Receipt 任务按以下状态推进提现：

```text
BROADCASTED -> MINED -> CONFIRMED
```

达到币种的 `confirmation_blocks` 后才扣减冻结资金。Receipt 失败、已挖出 Receipt 消失或不在规范链、RPC 无法判断、交易长时间 Pending、原交易消失且 Nonce 已被其他交易消耗时，订单进入 `MANUAL_REVIEW`，资金保持冻结；同 Nonce 替换交易的哈希会写入链上交易快照。广播开关与 Receipt 追踪开关相互独立，暂停新广播不会停止已广播订单的确认。

充值扫描会为每个高度保存区块哈希和父哈希。扫描游标处发生分叉时，扫描器在配置的 `reorg-depth` 内向后寻找共同祖先并回退游标；未入账的孤块充值直接标记为 `REORGED`。若已经入账的充值被重组移除，系统会将用户当前可用余额中最多等于充值金额的部分转入风险冻结，并在 `asset_risk_freeze_detail` 记录冻结额和缺口，始终保持 `total = available + frozen`。

主要配置：

```powershell
$env:WALLET_WITHDRAW_RECEIPT_ENABLED="true"
$env:WALLET_WITHDRAW_GAS_SAFETY_MULTIPLIER="1.20"
$env:WALLET_WITHDRAW_MAX_GAS_LIMIT="300000"
$env:WALLET_WITHDRAW_MAX_TOTAL_FEE_WEI="20000000000000000"
$env:WALLET_WITHDRAW_PENDING_TIMEOUT="1800000"
$env:WALLET_WITHDRAW_REPLACEMENT_LOOKBACK_BLOCKS="128"
```

生产升级前先执行只读检查脚本 `docs/sql/V13__chain_lifecycle_preflight.sql`，再执行 Flyway 迁移。

## 后续计划

- ERC-20 Gas 自动补给与风控
- 提现与充值归集共享热钱包时的统一 Nonce 域
- 链上余额、内部账本和资产流水三方对账
- 卡单加速交易生成与人工风险冻结解冻流程
- 后端应用 Docker 镜像与完整部署编排


## 说明

后端应用暂未放入 Docker 镜像。后续可新增 `Dockerfile` 并在 `docker-compose.yml` 中增加 `app` 服务。

Phase 1：基础账户与托管充值地址分配，已完成
Phase 2：资产账户、流水、仅 dev/test 可用的模拟充值，已完成
Phase 3：真实充值扫描、确认、重组处理，已完成
Phase 4：提现冻结、审核、签名、广播，已完成
Phase 5：Nonce、签名隔离、链上交易快照与 Outbox 广播恢复，已完成
Phase 6：EIP-1559、Gas 风控、Receipt 确认与充值重组风险冻结，已完成
Phase 7：Docker 镜像、部署脚本、监控告警，待开发

### 模拟充值安全隔离

`POST /api/deposit/mock-confirm` 仅用于本地开发和自动化测试，同时满足以下条件才会生效：

- Spring Profile 必须为 `dev` 或 `test`，其他环境不会注册模拟充值 Controller 和 Service Bean；只要激活了 `prod`，即使误配了 `dev/test` 也不会注册。
- 调用方必须已登录并具有 `OPERATOR` 或 `ADMIN` 角色，普通用户会被拒绝。
- 正式 `DepositService` 不暴露模拟入账方法，生产业务只能通过链上扫描确认充值。

生产部署禁止激活 `dev`、`test` Profile，并应在网关或安全组中限制内部管理接口的访问范围。

## 充值扫描配置

扫描任务默认关闭。数据库结构由 Flyway 自动升级；在 `application.yml` 中设置接近当前高度的 `initial-block` 后再启用扫描。ETH 和 ERC-20 白名单、decimals 与确认数来自 `supported_asset`，不再从 YAML 接收 Token 元数据：

```yaml
wallet:
  confirm-blocks: 12
  scan:
    enabled: true
    chain: ETH_SEPOLIA
    initial-block: 需要开始扫描的 Sepolia 区块高度
    batch-size: 100
    reorg-depth: 24
    fixed-delay: 15000
    lock-key-prefix: wallet:deposit-scan:lock:
    lock-lease: 300000
```

扫描器使用 Redis 分布式锁保证同一条链同一时间仅有一个实例工作，并在每个扫描批次后续租；`lock-lease` 应大于单批次最大处理时间。只有启用充值的注册资产才会创建充值订单。充值状态为 `0` 时等待确认，`1` 表示已确认入账，`2` 表示因链重组失效。用户入账事务不依赖归集任务创建；关闭归集时订单保持待补偿，重新启用后 worker 会补建历史归集单。扫描进度和区块哈希保存在 `chain_block_scan_record`，重启后会从上次高度继续。

## 资产注册表

V8 新增 `supported_asset`，初始注册 Sepolia ETH 与 USDC。`asset_code` 全局唯一，原生资产使用 `(chain_id, NATIVE)` 唯一定位，ERC-20 使用 `(chain_id, normalized_token_address)` 唯一定位。`asset_account` 使用 `UNIQUE(user_id, asset_id)`，避免 MySQL 可空合约地址导致重复原生资产账户。升级前先执行只读检查脚本 `docs/sql/V8__supported_asset_preflight.sql`；脚本发现重复账户或非法地址时必须人工清洗，迁移不会自动合并存在资金差异的账户。
