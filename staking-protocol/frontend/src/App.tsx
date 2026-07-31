import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  createPublicClient,
  createWalletClient,
  custom,
  formatUnits,
  http,
  maxUint256,
  parseSignature,
  parseUnits,
  type Address,
  type Abi,
  type Hash,
} from 'viem'
import { appChain, chainId, explorerUrl, stakingTokenAddress, vaultAddress } from './config'
import { erc20Abi, vaultAbi } from './contracts'

type VaultState = {
  walletBalance: bigint
  allowance: bigint
  staked: bigint
  earned: bigint
  totalStaked: bigint
  rewardRate: bigint
  periodFinish: bigint
  paused: boolean
  decimals: number
  symbol: string
}

type ContractWriteRequest = {
  address: Address
  abi: Abi
  functionName: string
  args?: readonly unknown[]
}

const emptyState: VaultState = {
  walletBalance: 0n,
  allowance: 0n,
  staked: 0n,
  earned: 0n,
  totalStaked: 0n,
  rewardRate: 0n,
  periodFinish: 0n,
  paused: false,
  decimals: 18,
  symbol: 'STK',
}

const publicClient = createPublicClient({ chain: appChain, transport: http() })

function shortAddress(value: string) {
  return `${value.slice(0, 6)}…${value.slice(-4)}`
}

export default function App() {
  const [account, setAccount] = useState<Address>()
  const [amount, setAmount] = useState('')
  const [state, setState] = useState<VaultState>(emptyState)
  const [status, setStatus] = useState('等待连接钱包')
  const [pending, setPending] = useState(false)
  const [lastHash, setLastHash] = useState<Hash>()

  const walletClient = useMemo(() => {
    if (!window.ethereum) return undefined
    return createWalletClient({ chain: appChain, transport: custom(window.ethereum) })
  }, [])

  const refresh = useCallback(async (activeAccount = account) => {
    if (!activeAccount) return
    const [walletBalance, allowance, staked, earned, totalStaked, rewardRate, periodFinish, paused, decimals, symbol] =
      await Promise.all([
        publicClient.readContract({ address: stakingTokenAddress, abi: erc20Abi, functionName: 'balanceOf', args: [activeAccount] }),
        publicClient.readContract({ address: stakingTokenAddress, abi: erc20Abi, functionName: 'allowance', args: [activeAccount, vaultAddress] }),
        publicClient.readContract({ address: vaultAddress, abi: vaultAbi, functionName: 'balanceOf', args: [activeAccount] }),
        publicClient.readContract({ address: vaultAddress, abi: vaultAbi, functionName: 'earned', args: [activeAccount] }),
        publicClient.readContract({ address: vaultAddress, abi: vaultAbi, functionName: 'totalStaked' }),
        publicClient.readContract({ address: vaultAddress, abi: vaultAbi, functionName: 'rewardRate' }),
        publicClient.readContract({ address: vaultAddress, abi: vaultAbi, functionName: 'periodFinish' }),
        publicClient.readContract({ address: vaultAddress, abi: vaultAbi, functionName: 'paused' }),
        publicClient.readContract({ address: stakingTokenAddress, abi: erc20Abi, functionName: 'decimals' }),
        publicClient.readContract({ address: stakingTokenAddress, abi: erc20Abi, functionName: 'symbol' }),
      ])
    setState({ walletBalance, allowance, staked, earned, totalStaked, rewardRate, periodFinish, paused, decimals, symbol })
  }, [account])

  const connect = async () => {
    if (!walletClient || !window.ethereum) {
      setStatus('未检测到 MetaMask 或 Rabby')
      return
    }
    try {
      const currentChainId = await walletClient.getChainId()
      if (currentChainId !== chainId) {
        await walletClient.switchChain({ id: chainId })
      }
      const [connected] = await walletClient.requestAddresses()
      setAccount(connected)
      setStatus('钱包已连接')
      await refresh(connected)
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '连接失败')
    }
  }

  const write = async (label: string, request: ContractWriteRequest) => {
    if (!walletClient || !account) return
    setPending(true)
    setStatus(`${label}：等待钱包确认`)
    try {
      const hash = await walletClient.writeContract({ ...request, account, chain: appChain })
      setLastHash(hash)
      setStatus(`${label}：等待链上确认`)
      const receipt = await publicClient.waitForTransactionReceipt({ hash })
      if (receipt.status !== 'success') throw new Error('交易执行失败')
      setStatus(`${label}：已确认`)
      setAmount('')
      await refresh()
    } catch (error) {
      setStatus(error instanceof Error ? error.message : `${label}失败`)
    } finally {
      setPending(false)
    }
  }

  const parsedAmount = () => parseUnits(amount || '0', state.decimals)

  const approve = () => write('授权', {
    address: stakingTokenAddress,
    abi: erc20Abi,
    functionName: 'approve',
    args: [vaultAddress, maxUint256],
  })

  const stake = () => write('质押', {
    address: vaultAddress,
    abi: vaultAbi,
    functionName: 'stake',
    args: [parsedAmount()],
  })

  const stakeWithPermit = async () => {
    if (!walletClient || !account) return
    setPending(true)
    setStatus('Permit：等待 EIP-712 签名')
    try {
      const value = parsedAmount()
      const [name, nonce] = await Promise.all([
        publicClient.readContract({ address: stakingTokenAddress, abi: erc20Abi, functionName: 'name' }),
        publicClient.readContract({ address: stakingTokenAddress, abi: erc20Abi, functionName: 'nonces', args: [account] }),
      ])
      const deadline = BigInt(Math.floor(Date.now() / 1000) + 3600)
      const signature = await walletClient.signTypedData({
        account,
        domain: { name, version: '1', chainId, verifyingContract: stakingTokenAddress },
        types: { Permit: [
          { name: 'owner', type: 'address' }, { name: 'spender', type: 'address' },
          { name: 'value', type: 'uint256' }, { name: 'nonce', type: 'uint256' },
          { name: 'deadline', type: 'uint256' },
        ] },
        primaryType: 'Permit',
        message: { owner: account, spender: vaultAddress, value, nonce, deadline },
      })
      const { v, r, s } = parseSignature(signature)
      if (v === undefined) throw new Error('钱包未返回可恢复签名')
      setPending(false)
      await write('Permit 质押', {
        address: vaultAddress, abi: vaultAbi, functionName: 'stakeWithPermit',
        args: [value, deadline, Number(v), r, s],
      })
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Permit 质押失败')
      setPending(false)
    }
  }

  const withdraw = () => write('提现本金', {
    address: vaultAddress, abi: vaultAbi, functionName: 'withdraw', args: [parsedAmount()],
  })
  const claim = () => write('领取奖励', {
    address: vaultAddress, abi: vaultAbi, functionName: 'getReward',
  })
  const exit = () => write('全部退出', {
    address: vaultAddress, abi: vaultAbi, functionName: 'exit',
  })

  useEffect(() => {
    if (!window.ethereum?.on) return
    const reload = () => window.location.reload()
    window.ethereum.on('accountsChanged', reload)
    window.ethereum.on('chainChanged', reload)
    return () => {
      window.ethereum?.removeListener?.('accountsChanged', reload)
      window.ethereum?.removeListener?.('chainChanged', reload)
    }
  }, [])

  const display = (value: bigint) => Number(formatUnits(value, state.decimals)).toLocaleString(undefined, { maximumFractionDigits: 6 })

  return (
    <main>
      <header>
        <div>
          <p className="eyebrow">SEPOLIA · SECURITY-FIRST DEMO</p>
          <h1>Secure Staking Vault</h1>
          <p className="subtitle">一个可验证本金偿付能力、支持 EIP-712 Permit 的非托管质押演示。</p>
        </div>
        <button className="connect" onClick={connect}>{account ? shortAddress(account) : '连接钱包'}</button>
      </header>

      <section className="notice">
        <span className={state.paused ? 'dot danger' : 'dot'} />
        <strong>{state.paused ? '协议已暂停' : '协议运行中'}</strong>
        <span>{status}</span>
        {lastHash && <a href={`${explorerUrl}/tx/${lastHash}`} target="_blank" rel="noreferrer">查看交易 ↗</a>}
      </section>

      <section className="metrics">
        <article><span>钱包余额</span><strong>{display(state.walletBalance)} {state.symbol}</strong></article>
        <article><span>我的质押</span><strong>{display(state.staked)} {state.symbol}</strong></article>
        <article><span>待领奖励</span><strong>{display(state.earned)} RWD</strong></article>
        <article><span>协议总质押</span><strong>{display(state.totalStaked)} {state.symbol}</strong></article>
      </section>

      <section className="panel">
        <div className="panel-heading">
          <div><p className="eyebrow">POSITION CONTROL</p><h2>管理质押仓位</h2></div>
          <button className="secondary" disabled={!account || pending} onClick={() => refresh()}>刷新数据</button>
        </div>
        <label htmlFor="amount">数量</label>
        <div className="amount-row">
          <input id="amount" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="0.0" inputMode="decimal" />
          <button className="max" onClick={() => setAmount(formatUnits(state.walletBalance, state.decimals))}>MAX</button>
          <span>{state.symbol}</span>
        </div>
        <div className="actions">
          <button disabled={!account || pending || state.paused} onClick={approve}>1. 授权</button>
          <button disabled={!account || pending || state.paused} onClick={stake}>2. 质押</button>
          <button disabled={!account || pending || state.paused} onClick={stakeWithPermit}>Permit 一步质押</button>
          <button className="secondary" disabled={!account || pending} onClick={withdraw}>提现本金</button>
          <button className="secondary" disabled={!account || pending} onClick={claim}>领取奖励</button>
          <button className="secondary" disabled={!account || pending} onClick={exit}>全部退出</button>
        </div>
        <div className="details">
          <span>当前授权：{display(state.allowance)} {state.symbol}</span>
          <span>每秒奖励：{formatUnits(state.rewardRate, 18)} RWD</span>
          <span>奖励结束：{state.periodFinish ? new Date(Number(state.periodFinish) * 1000).toLocaleString() : '未启动'}</span>
        </div>
      </section>

      <footer>
        <span>测试网演示，不承载真实资金</span>
        <a href={`${explorerUrl}/address/${vaultAddress}`} target="_blank" rel="noreferrer">Vault {shortAddress(vaultAddress)} ↗</a>
      </footer>
    </main>
  )
}
