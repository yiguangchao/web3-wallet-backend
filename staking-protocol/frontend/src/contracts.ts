import { parseAbi } from 'viem'

export const vaultAbi = parseAbi([
  'function stakingToken() view returns (address)',
  'function rewardToken() view returns (address)',
  'function totalStaked() view returns (uint256)',
  'function balanceOf(address) view returns (uint256)',
  'function earned(address) view returns (uint256)',
  'function rewardRate() view returns (uint256)',
  'function periodFinish() view returns (uint256)',
  'function paused() view returns (bool)',
  'function stake(uint256 amount)',
  'function stakeWithPermit(uint256 amount,uint256 deadline,uint8 v,bytes32 r,bytes32 s)',
  'function withdraw(uint256 amount)',
  'function getReward()',
  'function exit()',
  'event Staked(address indexed user,uint256 amount)',
  'event Withdrawn(address indexed user,uint256 amount)',
  'event RewardPaid(address indexed user,uint256 reward)',
])

export const erc20Abi = parseAbi([
  'function name() view returns (string)',
  'function symbol() view returns (string)',
  'function decimals() view returns (uint8)',
  'function balanceOf(address) view returns (uint256)',
  'function allowance(address,address) view returns (uint256)',
  'function nonces(address) view returns (uint256)',
  'function approve(address spender,uint256 amount) returns (bool)',
])
