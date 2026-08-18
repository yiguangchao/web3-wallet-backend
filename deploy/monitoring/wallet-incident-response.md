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

## Manual-review resolution

One administrator submits a resolution proposal with evidence. A different administrator executes it. `CONFIRM` requires a successful canonical receipt with the configured confirmation count. `RELEASE` is rejected while the transaction is successful, pending or otherwise known by the RPC.

## Recovery evidence

Every incident record must contain queries used, RPC provider and response time, block height/hash, transaction hash, affected business IDs, before/after balances, approvers and final reconciliation run ID.
