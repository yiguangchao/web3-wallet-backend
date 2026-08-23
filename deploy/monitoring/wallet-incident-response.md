# Wallet production incident runbook

## Universal first response

1. Record the incident ID, UTC start time, on-call owner and current deployment digest.
2. Do not edit balances, nonce rows, orders, scan cursors or outbox rows manually.
3. Pause withdrawals through the authenticated risk API when funds may be at risk.
4. Preserve application, database, signer, RPC and gateway logs.
5. Require two operators for every fund-affecting recovery decision.

## Application down

Check readiness dependencies independently. Keep the instance out of load balancing until MySQL and Redis are healthy. Do not start background jobs on a replacement instance until its configuration, chain ID and database migration version are verified.

## Ledger or reconciliation difference

Keep global withdrawal paused. Export the affected account, immutable flows, deposit/withdraw order and on-chain evidence. Run reconciliation again only after preserving the first result. Resolve the accounting cause before an administrator explicitly resumes withdrawals.

## Outbox backlog

Check signer and RPC availability, then inspect the oldest outbox lease and transaction hash. Query the stored hash before any retry. Never create or sign a second transaction for the same order. `DEAD` or uncertain transactions must use the dual-control manual-review workflow.

## Nonce anomaly

Pause withdrawals. Compare database next nonce, chain latest nonce and chain pending nonce from two independent RPC providers. Identify every transaction for the affected sender/nonce range. Do not decrement `wallet_nonce` or reuse a nonce.

## Scan lag

Pause deposit crediting when the canonical chain cannot be determined. Verify chain ID, head height and block hashes using an independent RPC. Do not move the cursor forward manually. A cursor rollback must remain inside the configured reorg window and be approved by two operators.

## RPC block-hash quorum failure

Keep deposit scanning and crediting paused. Record the block height and both provider responses, then verify chain ID, provider status and the block hash using a third independent source. Do not disable quorum, move the scan cursor or choose the hash returned by the faster provider. Resume only after both configured providers agree on the canonical hash and the affected range has been rescanned and reconciled. Persistent disagreement requires replacing the unhealthy provider through the reviewed production configuration pipeline.

## RPC receipt quorum failure

Pause automatic withdrawal settlement and custody sweep finalization. Preserve both provider responses, the locally derived transaction hash, sender, nonce and stored raw transaction. Do not confirm, release, replace or rebroadcast the transaction while receipt existence, block identity or execution status differs. Verify the transaction and canonical block through a third independent provider; uncertain outcomes must enter the dual-control manual-review workflow. Resume automatic processing only after providers agree and affected orders have been reconciled.

## RPC nonce quorum failure

Pause new withdrawal signing and nonce allocation for the affected hot wallet. Preserve both providers' `latest` and `pending` transaction counts, the database `wallet_nonce` row and every locally stored transaction in the disputed nonce range. Do not lower the database nonce, reuse a nonce, replace a transaction or select the larger response automatically. Check each nonce and transaction through a third independent provider, then reconcile the outbox and withdrawal state under dual control. Resume only after the configured providers agree and all affected signed transactions are accounted for.

## RPC transaction quorum failure

Pause automatic outbox recovery, withdrawal lifecycle decisions and manual release for the affected transaction. Preserve both `eth_getTransactionByHash` responses, the signed raw transaction, sender, nonce, outbox lease and withdrawal state. Do not classify the transaction as dropped, release frozen funds, create a replacement or broadcast a different payload while providers disagree. Verify the hash through a third independent provider and inspect the sender's pending/latest nonce. Resume only after the configured providers agree or a dual-control incident decision has reconciled every possible on-chain outcome.

## RPC balance quorum failure

Pause withdrawal preparation and do not close reconciliation differences for the affected wallet and asset. Preserve the fixed block number/hash, both `eth_getBalance` or `eth_call balanceOf` responses, token address and internal liability snapshot. Do not choose the larger or smaller balance, change internal balances or disable quorum. Verify the same block through a third independent archive-capable provider and determine whether either configured provider is lagging, pruned or on a non-canonical fork. Resume only after providers agree at a canonical block and affected withdrawal and reconciliation checks have been rerun.

## RPC chain-head quorum failure

Pause withdrawal, custody-sweep and manual-review confirmation decisions. Preserve both `eth_blockNumber` responses, the configured maximum lag, the conservative height and block hashes around the disputed range. Do not use the higher head, increase the lag limit during the incident or calculate confirmations from a single provider. Verify height and canonical hashes with a third independent provider, then replace or recover the unhealthy provider through the reviewed configuration process. Resume only after the configured providers remain within the approved lag and agree on the conservative block hash.

## Manual-review resolution

One administrator submits a resolution proposal with evidence. A different administrator executes it. `CONFIRM` requires a successful canonical receipt with the configured confirmation count. `RELEASE` is rejected while the transaction is successful, pending or otherwise known by the RPC.

## Recovery evidence

Every incident record must contain queries used, RPC provider and response time, block height/hash, transaction hash, affected business IDs, before/after balances, approvers and final reconciliation run ID.
