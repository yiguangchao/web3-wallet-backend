# web3-wallet-backend

Web3 Java 后端/区块链钱包服务。当前版本基于 Spring Boot + Web3j，提供钱包地址绑定、余额查询、资产账户与流水、模拟充值、提现申请等基础能力。

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
- `module.wallet`: 钱包地址绑定、ETH/ERC-20 余额查询
- `module.asset`: 资产账户、资产流水
- `module.chain`: 当前区块、交易回执查询
- `module.deposit`: 充值订单查询、模拟入账
- `module.withdraw`: 提现申请、提现订单查询
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
- JWT 鉴权
- 绑定与查询以太坊钱包地址
- 校验以太坊地址格式
- 查询 ETH 余额
- 查询 ERC-20 Token 余额
- 查询 Sepolia 当前区块与交易回执
- 初始化资产账户、资产流水、区块扫描进度等表
- 模拟充值确认入账
- 提现申请记录

## 后续计划

- ERC-20 Transfer 事件监听
- 区块扫描与确认数处理
- 真实充值确认入账
- 提现交易签名与广播
- 交易状态同步
- Redis 分布式锁
- 后端应用 Docker 镜像与完整部署编排


## 说明

后端应用暂未放入 Docker 镜像。后续可新增 `Dockerfile` 并在 `docker-compose.yml` 中增加 `app` 服务。

Phase 1：基础账户与钱包地址绑定，已完成
Phase 2：资产账户、流水、模拟充值，已完成
Phase 3：真实充值扫描，待开发
Phase 4：提现冻结、签名、广播，待开发
Phase 5：Redis 锁、任务调度、交易状态同步，待开发
Phase 6：Docker 镜像、部署脚本、监控告警，待开发
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
