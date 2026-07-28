# CI 测试

本项目采用“本地轻量、CI 完整”的测试策略。本地开发只要求 JDK 17 和 Maven；Docker、MySQL、Redis 与本地 EVM 节点不是执行普通单元测试的前置条件。

## 本地测试

```bash
mvn test
```

没有 Docker 时，带有 `@Testcontainers(disabledWithoutDocker = true)` 的 MySQL、Redis 和 Flyway 集成测试会由 JUnit assumption 跳过。没有设置 `EVM_RPC_URL` 时，Anvil 区块链集成测试也会跳过。这样可以快速运行业务单元测试和 Service 测试，不要求每位开发者安装 Docker。

本地已安装 Docker 时，Testcontainers 会自动拉起隔离的 MySQL 8 和 Redis 7 容器，并执行相同的数据库及服务集成测试。若还要本地执行链上测试，需要启动 Anvil、编译 `src/test/evm/src/TestToken.sol`，并设置：

```bash
export EVM_RPC_URL=http://127.0.0.1:8545
export EVM_CHAIN_ID=31337
export EVM_ERC20_ARTIFACT=/absolute/path/to/TestToken.json
mvn test
```

## GitHub Actions

`.github/workflows/ci.yml` 在推送到 `main` 或创建/更新 Pull Request 时运行：

```text
GitHub Actions (ubuntu-latest, JDK 17)
  -> Docker availability check
  -> MySQL 8 + Redis 7 health checks
  -> Anvil (chainId 31337)
  -> compile test ERC-20 with Forge
  -> mvn clean test
  -> Unit + Service + Flyway + MySQL + Redis + Blockchain checks
  -> Surefire result summary
```

CI 运行器提供 Docker。MySQL/Redis 服务容器先通过健康检查，Testcontainers 再为测试创建隔离实例；Anvil 用于真实验证 ETH 转账、ERC-20 `Transfer`、交易 receipt 和确认数。

`WalletBusinessE2EIntegrationTest` 进一步把真实链与 Spring 业务服务、MySQL 账本和 Redis 串联，覆盖：

- ETH/ERC-20 充值扫描、确认、重复扫描幂等；
- ETH/ERC-20 提现审核、EIP-1559 签名、Outbox 广播、Receipt 确认；
- 并发提现、重复广播、过期 Outbox 恢复；
- 对账异常自动冻结和暂停提现；
- Anvil snapshot/revert 触发的已入账充值重组风险冻结。

## CI 禁止跳过

本地保留 Docker 不可用时跳过的行为，但 CI 有两道强制检查：

- `CiDockerAvailabilityTest` 在 `CI=true` 时要求 Testcontainers 能连接 Docker；
- `scripts/ci_test_summary.py --fail-on-skipped` 汇总 Surefire XML，只要有任何 skipped test 就使工作流失败。

因此 CI 不会出现 `Skipped because Docker unavailable` 后仍然显示成功的情况。工作流也不传递 `skipTests`、`maven.test.skip` 或跳过集成测试的参数。

## Flyway 迁移验证

`FlywayMySqlMigrationTest` 从空 MySQL 8 数据库顺序执行 `V1` 到当前最新 `V14`，并覆盖已知历史版本升级、约束冲突和账本不变量。迁移重复版本、SQL 冲突或 checksum 不匹配都会使 `mvn clean test` 失败。

## 测试报告

工作流在 GitHub Step Summary 输出：

```text
Tests run: ...
Tests passed: ...
Tests failed: ...
Tests skipped: ...
```

构建失败或出现 skipped test 时，`target/surefire-reports` 会作为 `surefire-reports` Artifact 上传并保留 14 天。可在对应 GitHub Actions run 的 Artifacts 区域下载 XML 与失败详情。
