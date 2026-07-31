# Testnet deployment evidence

此文件是部署验收模板。只有真实执行并交叉核验后才能填写，禁止用示例值冒充部署结果。

| 字段 | 结果 |
|---|---|
| Git commit SHA | 待填写 |
| Network / chainId | Sepolia / 11155111 |
| Compiler | Solidity 0.8.28 |
| Staking token | 待填写 |
| Reward token | 待填写 |
| Vault address | 待填写 |
| Deployment tx | 待填写 |
| Verified source URL | 待填写 |
| Owner / Safe | 待填写 |
| Reward distributor | 待填写 |
| CI run URL | 待填写 |
| Slither report | 待填写 |

## Acceptance transactions

| 场景 | Transaction hash | Checked result |
|---|---|---|
| approve + stake | 待填写 | 仓位和本金储备一致 |
| permit + stake | 待填写 | nonce 增加且一次交易入仓 |
| notify reward | 待填写 | rewardRate/periodFinish 正确 |
| withdraw | 待填写 | 部分本金到账 |
| claim | 待填写 | 奖励到账且 earned 清零 |
| pause and withdraw | 待填写 | 暂停未锁死本金 |
| delayed distributor rotation | 待填写 | 延迟前失败、延迟后成功 |

## Evidence rules

- 每个地址和交易哈希必须链接到独立区块浏览器；
- 使用第二个 RPC 复查 code hash、owner、token 地址、总质押和余额；
- 保存 CI artifact、部署广播 JSON、合约标准 JSON 和前端构建哈希；
- 不记录私钥、助记词、签名原文或 Secret 值。
