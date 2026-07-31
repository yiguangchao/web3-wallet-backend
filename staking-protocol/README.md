# Secure Staking Vault

这是钱包后端作品集配套的 Solidity 协议与轻量 dApp。协议用于展示 EVM 资金模型、EIP-712 授权、权限治理、安全测试和测试网部署能力；它不承载真实公众资金。

## 核心能力

- 用户质押 ERC-20，并按 `rewardPerToken` 累计独立奖励；
- 普通 `approve + stake` 和 EIP-2612 `permit + stake` 两种路径；
- CEI、重入锁和安全 ERC-20 返回值处理；
- 拒绝 fee-on-transfer 资产，避免内部份额大于真实本金；
- 质押币和奖励币强制分离，奖励不能侵占本金；
- 暂停期间禁止新增风险，但普通提现始终可用；
- 紧急提现返还本金并显式放弃奖励；
- 所有权两步交接；奖励分发者变更需等待 48 小时；
- 管理员不能取回质押币或奖励币，只能找回误转的其他资产。

## 资金状态机

```text
ERC-20 wallet balance
       │ approve / permit
       ▼
stake ──────► user stake + totalStaked
       │              │
       │              ├── rewardPerToken 按时间累计
       │              │
       ├── withdraw ──┴──► 返还部分本金
       ├── getReward ─────► 领取奖励
       └── exit ──────────► 全部本金 + 奖励
```

奖励分发者先把奖励币转入金库，再开启奖励周期。剩余旧奖励会和新增奖励一起重新计算 `rewardRate`；合约检查未来奖励负债不超过奖励币余额。

## 安全不变量

1. `stakingToken.balanceOf(vault) >= totalStaked`；
2. 所有测试用户的仓位之和等于 `totalStaked`；
3. 尚未释放的未来奖励不超过金库奖励币余额；
4. fee-on-transfer 质押会整体回滚，不生成虚假仓位；
5. 管理员无法提取本金币和奖励币；
6. 暂停不会阻断用户撤回本金。

## 测试

```bash
forge fmt --check
forge test -vv
forge test --match-contract SecureStakingVaultInvariantTest -vvv
forge coverage
```

当前测试包含普通流程、交错用户奖励、EIP-2612 签名、暂停逃生、紧急提现、手续费代币、权限延迟、两步所有权、保护资产、1000 轮模糊测试以及 3 组状态机不变量测试。

## 测试网部署

使用专用的 Sepolia 测试账户：

```bash
export DEPLOYER_PRIVATE_KEY=...
export STAKING_TOKEN_ADDRESS=0x...
export REWARD_TOKEN_ADDRESS=0x...
export VAULT_OWNER_ADDRESS=0x...
export REWARD_DISTRIBUTOR_ADDRESS=0x...
export REWARD_DURATION_SECONDS=604800

forge script script/Deploy.s.sol:DeploySecureStakingVault \
  --rpc-url "$SEPOLIA_RPC_URL" --broadcast --verify
```

生产密钥不得写入 `.env`、GitHub Variables、日志或仓库；CI 部署使用受保护 Environment Secret，所有者建议配置为多签而不是 EOA。

## dApp

```bash
cd frontend
cp .env.example .env.local
npm ci
npm run dev
```

dApp 支持 MetaMask/Rabby、网络切换、读取余额/收益、授权、普通质押、EIP-712 Permit 质押、提现、领取、退出和交易浏览器链接。生产静态构建使用 `npm run build`。

## 已知边界

- 这是教育和面试项目，未经过独立第三方审计；
- 未实现治理代币、投票、代理升级和复杂代币经济模型；
- 不支持 rebasing、fee-on-transfer 和带回调的非标准资产；
- 前端默认假设质押代币的 Permit domain version 为 `1`；不支持 Permit 的代币使用普通授权流程；
- 部署到测试网不代表可以承载真实公众资金。

更完整的攻击面、信任假设和上线检查见 [SECURITY.md](SECURITY.md)。
