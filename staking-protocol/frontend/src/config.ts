import { defineChain, getAddress, type Address } from 'viem'

const requiredAddress = (name: string, value: string | undefined): Address => {
  if (!value) throw new Error(`${name} is required`)
  return getAddress(value)
}

export const chainId = Number(import.meta.env.VITE_CHAIN_ID ?? 11155111)
export const explorerUrl = import.meta.env.VITE_EXPLORER_URL ?? 'https://sepolia.etherscan.io'
export const vaultAddress = requiredAddress('VITE_VAULT_ADDRESS', import.meta.env.VITE_VAULT_ADDRESS)
export const stakingTokenAddress = requiredAddress(
  'VITE_STAKING_TOKEN_ADDRESS',
  import.meta.env.VITE_STAKING_TOKEN_ADDRESS,
)

export const appChain = defineChain({
  id: chainId,
  name: import.meta.env.VITE_CHAIN_NAME ?? 'Sepolia',
  nativeCurrency: { name: 'Ether', symbol: 'ETH', decimals: 18 },
  rpcUrls: {
    default: { http: [import.meta.env.VITE_RPC_URL ?? 'https://ethereum-sepolia-rpc.publicnode.com'] },
  },
  blockExplorers: {
    default: { name: 'Block Explorer', url: explorerUrl },
  },
  testnet: true,
})
