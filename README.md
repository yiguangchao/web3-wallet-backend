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
- `module.asset`: 资产账户、资产流水
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
- 校验以太坊地址格式
- 查询 ETH 余额
- 查询 ERC-20 Token 余额
- 查询 Sepolia 当前区块与交易回执
- ETH/ERC-20 扫块、确认数、断点续扫和链重组处理
- 充值确认后创建异步归集任务
- ETH/ERC-20 充值地址归集、失败重试和回执确认
- 提现申请幂等、余额校验、资产冻结与冻结流水
- 提现审核、广播、回执同步和操作审计

## 托管充值地址

用户不再绑定自己控制私钥的外部地址。系统从平台 HD 主钱包为用户分配充值地址：

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

同一用户、同一链最多有一个 `ACTIVE` 地址。后台可以将地址设为 `DISABLED` 或 `RETIRED`，之后用户可以获得新地址。历史地址永不分配给其他用户，并会继续参与扫描，避免迟到充值丢失。

后台地址状态与归集接口：

- `PUT /api/admin/wallet/deposit-addresses/{id}/status`
- `GET /api/admin/wallet/sweeps`
- `POST /api/admin/wallet/sweeps/{id}/retry`
- `POST /api/admin/wallet/sweeps/run`

`OPERATOR` 或 `ADMIN` 才能调用这些接口。

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

`POST /api/withdraw/apply` 需要客户端提供唯一 `requestId`。相同用户重复提交同一个 `requestId` 时返回原订单，不会重复冻结。当前手续费按提现资产同币种收取，冻结金额为 `amount + fee`。

```json
{
  "requestId": "withdraw-20260706-001",
  "chain": "ETH_SEPOLIA",
  "tokenSymbol": "ETH",
  "tokenAddress": null,
  "toAddress": "0x1111111111111111111111111111111111111111",
  "amount": 0.01,
  "fee": 0.0001
}
```

订单状态 `0` 表示资产已冻结、等待审核。申请失败或余额不足时，订单和资产变更会在同一事务中回滚。
## 后续计划

- Vault/KMS/HSM 或独立签名服务
- ERC-20 Gas 自动补给与风控
- 提现和归集统一 Nonce 管理
- 链上余额、内部账本和资产流水三方对账
- EIP-1559、卡单加速与替换交易
- 后端应用 Docker 镜像与完整部署编排


## 说明

后端应用暂未放入 Docker 镜像。后续可新增 `Dockerfile` 并在 `docker-compose.yml` 中增加 `app` 服务。

Phase 1：基础账户与托管充值地址分配，已完成
Phase 2：资产账户、流水、仅 dev/test 可用的模拟充值，已完成
Phase 3：真实充值扫描、确认、重组处理，已完成
Phase 4：提现冻结、审核、签名、广播，已完成
Phase 5：Redis 锁、归集任务、交易状态同步，已完成
Phase 6：Docker 镜像、部署脚本、监控告警，待开发

### 模拟充值安全隔离

`POST /api/deposit/mock-confirm` 仅用于本地开发和自动化测试，同时满足以下条件才会生效：

- Spring Profile 必须为 `dev` 或 `test`，其他环境不会注册模拟充值 Controller 和 Service Bean；只要激活了 `prod`，即使误配了 `dev/test` 也不会注册。
- 调用方必须已登录并具有 `OPERATOR` 或 `ADMIN` 角色，普通用户会被拒绝。
- 正式 `DepositService` 不暴露模拟入账方法，生产业务只能通过链上扫描确认充值。

生产部署禁止激活 `dev`、`test` Profile，并应在网关或安全组中限制内部管理接口的访问范围。

## 充值扫描配置

扫描任务默认关闭。数据库结构由 Flyway 自动升级；在 `application.yml` 中设置接近当前高度的 `initial-block` 后再启用扫描：

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
    tokens:
      - symbol: USDC
        address: Sepolia Token 合约地址
        decimals: 6
```

扫描器使用 Redis 分布式锁保证同一条链同一时间仅有一个实例工作，并在每个扫描批次后续租；`lock-lease` 应大于单批次最大处理时间。扫描器支持 ETH 转账和配置白名单内的 ERC-20 `Transfer` 日志。充值状态为 `0` 时等待确认，`1` 表示已确认入账，`2` 表示因链重组失效。扫描进度和区块哈希保存在 `chain_block_scan_record`，重启后会从上次高度继续。
