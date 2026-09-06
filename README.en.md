# web3-wallet-backend

[简体中文](README.md) | **English**

Three runnable components that work together:

- a Java custodial wallet backend and an isolated [signer service](signer-service/README.md);
- the [Secure Staking Vault Solidity protocol](staking-protocol/README.md);
- a lightweight [React + viem dApp](staking-protocol/frontend/) for the protocol.

[![CI](https://github.com/yiguangchao/web3-wallet-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/yiguangchao/web3-wallet-backend/actions/workflows/ci.yml)
![Java 17](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![MySQL 8](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)

For security reporting and supported-version guidance, see [SECURITY.md](SECURITY.md).

A Web3 Java backend and blockchain custodial-wallet service built with Spring Boot and Web3j. It supports platform-controlled HD deposit-address allocation, ETH/ERC-20 deposit scanning and sweeping, an internal asset ledger, withdrawal review, isolated signing, transaction broadcasting, reconciliation, and operational monitoring.

## Technology stack

- Java 17
- Spring Boot 3.x
- Maven
- MySQL 8
- Redis
- MyBatis-Plus
- Web3j
- Spring Security and JWT
- Lombok
- Spring Validation
- Springdoc OpenAPI / Swagger
- Docker Compose
- Solidity, Foundry, Slither
- React, TypeScript, and viem

## Modules

- `module.user`: registration, login, roles, and user data
- `module.wallet`: custodial deposit addresses, address lifecycle, sweeping, and ETH/ERC-20 balance queries
- `module.asset`: server-side asset registry, user asset accounts, and balance flows
- `module.accounting`: V16 immutable balanced operational subledger and forensic queries
- `module.chain`: chain-head and transaction-receipt queries
- `module.deposit`: ETH/ERC-20 scanning, confirmation, crediting, and reorganization handling
- `module.withdraw`: withdrawal application, review, signing, broadcasting, and lifecycle synchronization
- `module.reconciliation`: internal-ledger, business-order, and on-chain solvency reconciliation
- `module.risk`: withdrawal policies, allowlists, user freezes, and platform circuit breakers
- `module.monitoring`: wallet-specific Micrometer metrics
- `infrastructure.web3`: Web3j RPC clients, retries, rate limiting, and two-provider quorum checks
- `infrastructure.security`: JWT authentication and Redis-backed API rate limiting
- `infrastructure.signer`: the only wallet-backend entry point to transaction signing

## Local development

Docker is optional for local development. A plain `mvn test` always runs unit and service tests. Testcontainers-based integration tests are skipped through JUnit assumptions when Docker is unavailable. GitHub Actions uses Docker to run the complete MySQL, Redis, Flyway, and Anvil integration suites and rejects skipped required tests. See [`docs/ci-testing.md`](docs/ci-testing.md).

```text
Local development: Docker is optional
CI: complete Docker-based integration testing
```

1. Install JDK 17 and Maven.
2. Start MySQL and Redis when running the application:

```bash
docker compose up -d mysql redis
```

3. Configure a Sepolia RPC endpoint:

```bash
export WEB3_RPC_URL=https://sepolia.infura.io/v3/your-key
```

Windows PowerShell:

```powershell
$env:WEB3_RPC_URL="https://sepolia.infura.io/v3/your-key"
```

The RPC client applies connection, read, write, and total-call timeouts. Network failures and HTTP 408/429/500/502/503/504 responses are retried with exponential backoff. The default instance-wide limit is ten RPC calls per second.

- `WEB3_CONNECT_TIMEOUT`: connection timeout, default `5000` ms
- `WEB3_READ_TIMEOUT`: read timeout, default `15000` ms
- `WEB3_WRITE_TIMEOUT`: write timeout, default `10000` ms
- `WEB3_CALL_TIMEOUT`: complete-call timeout, default `30000` ms
- `WEB3_MAX_RETRIES`: maximum retry count, default `2`
- `WEB3_RETRY_BACKOFF`: initial backoff, default `500` ms
- `WEB3_RETRY_MAX_BACKOFF`: maximum backoff, default `5000` ms
- `WEB3_MAX_REQUESTS_PER_SECOND`: per-instance RPC rate, default `10`

Deposit scanning supports an independent block-hash quorum. The primary provider returns the complete block and its transactions, while the secondary provider reads the header at the same height. A hash mismatch, secondary timeout, or missing block fails closed: the scanner does not persist the block, advance its cursor, or confirm deposits. The feature is disabled by default for local development, but the `prod` profile requires it and requires two different HTTPS endpoints.

- `WEB3_SECONDARY_RPC_URL`: preferably an endpoint from a different provider
- `WEB3_BLOCK_HASH_QUORUM_ENABLED`: must be `true` in production
- `WEB3_RPC_QUORUM_MAX_HEAD_LAG`: maximum primary/secondary head difference, default `2`; confirmation calculations use the lower head

Quorum verification currently covers:

- chain heads and confirmation calculations;
- deposit blocks, canonical-chain checks, and reorganization ancestor searches;
- every transaction receipt read through `Web3Service`;
- hot-wallet `pending` and `latest` nonces;
- `eth_getTransactionByHash` transaction presence;
- ETH and ERC-20 balances;
- EIP-1559 fee suggestions and gas estimates.

When both heads are within the configured lag, the wallet uses the lower height and verifies its canonical block hash again. A larger gap fails closed. Balances and EIP-1559 base fees are queried at a fixed canonical height so different provider interpretations of `latest` cannot create false disagreements. Canonical block-hash or base-fee disagreement rejects transaction preparation. Priority-fee and gas estimates may differ; the wallet conservatively selects the larger value and still applies per-transaction gas and total-fee limits.

Withdrawal balance preflight, transaction preparation, asset reconciliation, nonce allocation, Outbox recovery, and final settlement therefore do not accept a single-provider conclusion. If the primary provider explicitly fails, an already signed raw transaction may be safely replayed to the secondary provider as the exact same immutable payload. Every returned transaction hash must equal the locally calculated Keccak-256 hash. Other critical reads never bypass quorum through automatic failover.

4. Start the application:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The complete container environment starts the application, MySQL 8, Redis 7, and Anvil:

```bash
docker compose up --build -d
docker compose ps
```

Compose is intended for development and acceptance testing only. Its Anvil test keys must never be used in production. See [`docs/deployment.md`](docs/deployment.md) for environment layering and upgrade procedures, and complete [`docs/production-checklist.md`](docs/production-checklist.md) before release.

Phase 8 deliverables, remaining risks, state machines, test evidence, and the production-readiness conclusion are documented in [`docs/phase-8-qc-report.md`](docs/phase-8-qc-report.md).

## MySQL initialization

Docker Compose creates the `web3_wallet` database. At startup, Flyway executes unapplied migrations from `src/main/resources/db/migration` and records them in `flyway_schema_history`. An existing non-empty database receives a version `0` baseline before later migrations run.

Default development connection:

- URL: `jdbc:mysql://localhost:3306/web3_wallet`
- username: `root`
- password: `root123456`

## Redis

```bash
docker compose up -d redis
```

The default endpoint is `localhost:6379`.

## API documentation

After startup:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Swagger provides JWT Bearer authorization. The `prod` profile disables Swagger by default. It may be temporarily enabled through a controlled environment variable, but should never be exposed directly to the public internet.

JWT configuration is validated once at startup. `JWT_SECRET` must exist and contain at least 32 UTF-8 bytes, and `JWT_EXPIRATION` must be positive and no longer than 24 hours in production. Production has no default secret and explicitly rejects the documented development placeholder. Parsed tokens must also contain a positive `userId` and a non-blank username. Invalid configuration, weak/default secrets, excessively long-lived tokens, and incomplete identity claims are rejected before an authenticated identity can be established.

## Current capabilities

- user registration and login
- BCrypt password hashing
- JWT authentication and administrative RBAC
- BIP-44 platform-controlled deposit-address allocation
- derivation-index, derivation-path, and key-version management
- globally unique address lifecycle: active, disabled, and retired
- external-wallet challenge, EIP-191 ownership verification, and replay protection
- Ethereum-address validation
- ETH and ERC-20 balance queries
- Sepolia chain-head and transaction-receipt queries
- ETH/ERC-20 scanning, confirmations, resumable cursors, and reorganization handling
- independent deposit crediting and asynchronous sweep-order compensation
- ETH/ERC-20 sweeping, retries, and receipt confirmation
- idempotent withdrawal requests, balance checks, asset freezing, and freeze records
- withdrawal review, signing, broadcasting, receipt tracking, and immutable operation audit
- three-layer reconciliation, risk policies, API rate limiting, and operational metrics
- isolated signer service with Google Cloud KMS / Cloud HSM support, secp256k1 signing, mTLS, tamper-evident MySQL audit chains, and dual-control key-policy changes

## Custodial deposit addresses

Users do not provide a self-controlled external address as a deposit destination. The platform allocates a deposit address from its HD master wallet:

```text
platform mnemonic
  -> m/44'/60'/account'/0/index
  -> custody_deposit_address
  -> on-chain deposit scanning
  -> custody_sweep_order
  -> platform collection address
```

The database stores `key_version`, `derivation_index`, and `derivation_path`, but never child private keys. The repository includes a local HD implementation for development. Production must replace it with Vault, KMS, HSM, MPC, or an isolated signing service.

Allocate or query the current deposit address:

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

A user may have at most one `ACTIVE` address per chain. An administrator may change an address to `DISABLED` or `RETIRED`, after which a new address may be allocated. Historical addresses are never assigned to another user. The scanner only loads `PLATFORM_CUSTODY + DEPOSIT + ACTIVE` addresses, so business-side risk checks must be completed before disabling an address. `RETIRED` is terminal.

Administrative address and sweep endpoints:

- `PUT /api/admin/wallet/deposit-addresses/{id}/status`
- `GET /api/admin/wallet/sweeps`
- `POST /api/admin/wallet/sweeps/{id}/retry`
- `POST /api/admin/wallet/sweeps/run`

Only `OPERATOR` and `ADMIN` may call these endpoints.

## External-wallet ownership verification

External wallets only prove the relationship between a user and an on-chain address. They are not deposit-scanning or internal-crediting sources. Binding uses EIP-191 `personal_sign`.

```http
POST /api/wallet/external-addresses/challenge
Authorization: Bearer <token>
Content-Type: application/json

{"chain":"ETH_SEPOLIA","address":"0xYourWalletAddress"}
```

The client signs the exact returned `message` and submits the signature:

```http
POST /api/wallet/external-addresses/verify
Authorization: Bearer <token>
Content-Type: application/json

{"challengeId":123,"signature":"0x..."}
```

A challenge expires after five minutes by default and can succeed only once. Its message binds the purpose, user ID, chain, address, random nonce, issue time, and expiration time. Consumption uses an atomic conditional database update. The recovered signing address must exactly match the requested address. A unique `(chain, address)` constraint prevents the same on-chain address from being bound to multiple users. Verified addresses are available from `GET /api/wallet/external-addresses`.

Configure the lifetime in milliseconds with `WALLET_SIGNATURE_CHALLENGE_TTL`.

## Custody configuration

Custody is disabled by default. Never place a production mnemonic in the repository or a configuration file.

```powershell
$env:WALLET_CUSTODY_ENABLED="true"
$env:WALLET_CUSTODY_MNEMONIC="development mnemonic only"
$env:WALLET_CUSTODY_KEY_VERSION="v1"
$env:WALLET_CUSTODY_COLLECTION_ADDRESS="0xPlatformCollectionAddress"
$env:WALLET_CUSTODY_SWEEP_ENABLED="true"
```

The default path is `m/44'/60'/0'/0/index`. Change the account segment with `WALLET_CUSTODY_ACCOUNT`. Key rotation must retain signing capability for every historical `key_version`, otherwise funds at old deposit addresses cannot be swept.

An ETH sweep sends the balance minus gas and the configured reserve. An ERC-20 sweep sends the address's current token balance; that address must already hold enough ETH for gas. Gas funding should be handled by an independent workflow so it does not share an unmanaged nonce domain with the withdrawal hot wallet.

## Withdrawal freezing

`POST /api/withdraw/apply` requires a unique client `requestId` and a server-registered `assetCode`. Repeating the same `requestId` for the same user returns the original order and never freezes funds twice. Chain, token address, symbol, decimals, limits, and platform fee are loaded from `supported_asset`. The frozen amount is `amount + platformWithdrawFee`. Legacy chain and token fields can still be deserialized for compatibility but are deprecated and ignored by business logic.

```json
{
  "requestId": "withdraw-20260706-001",
  "assetCode": "ETH",
  "toAddress": "0x1111111111111111111111111111111111111111",
  "amount": 0.01
}
```

Status `0` means funds are frozen and the order is awaiting review. An application error or insufficient balance rolls back both the order and every asset mutation in one transaction.

V9 introduced `asset_freeze_detail`. Principal, platform fee, and total frozen amount are persisted by the server. A freeze record only permits these transitions:

```text
FROZEN -> CONFIRMED
FROZEN -> RELEASED
```

`CONFIRMED` and `RELEASED` are mutually exclusive terminal states. Settlement does not accept amounts from a client. It locks the freeze record and account with `SELECT FOR UPDATE`, then uses the persisted values. Account updates, freeze transitions, and asset-flow inserts share one transaction. A unique `(business_type, business_id)` constraint makes fund flows idempotent.

Every balance mutation verifies these invariants, with V9 MySQL `CHECK` constraints as a final guard:

```text
available >= 0
frozen >= 0
total = available + frozen
```

Before upgrading an existing production database, run the read-only preflight script `docs/sql/V9__asset_ledger_preflight.sql`.

## Withdrawal state machine and permissions

V10 defines a one-way state machine:

```text
PENDING_REVIEW(0) -> APPROVED(6) -> SIGNING(7) -> SIGNED(8)
                  -> BROADCASTING(1) -> BROADCASTED(2)
                  -> MINED(9) -> CONFIRMED(3)
PENDING_REVIEW(0) -> REJECTED(5)
uncertain failure from a non-terminal state -> MANUAL_REVIEW(4)
```

`CONFIRMED` and `REJECTED` are terminal. `MANUAL_REVIEW` keeps funds frozen and may transition only through the V15 dual-administrator resolution process. Every transition uses `WHERE id = ? AND status = ?`, verifies the affected-row count, and rejects undeclared edges. Successful transitions record the actor, role, IP address, previous/new states, and remarks. Review rejection is only valid from `PENDING_REVIEW` and releases the freeze in the same transaction.

Manual resolution requires one administrator to propose `CONFIRM` or `RELEASE` with evidence and a different administrator to execute it:

- `CONFIRM` accepts only the order's original signed transaction. Its receipt must be successful, canonical, and sufficiently confirmed.
- `RELEASE` is permitted only when RPC providers cannot identify the transaction and its original nonce has not been consumed.
- proposals, actors, evidence, and fund transitions are all audited.

```text
POST /api/admin/withdraw/manual-reviews/orders/{orderId}/proposals
POST /api/admin/withdraw/manual-reviews/proposals/{resolutionId}/execute
GET  /api/admin/withdraw/manual-reviews/proposals
```

V11/V12 separated nonce allocation, signing, and broadcasting. The request path allocates a nonce, invokes the signer, and atomically writes `withdraw_chain_transaction` plus `transaction_outbox`, then returns a locally calculated `txHash`. A background broadcaster submits the persisted `raw_transaction`. A successful receipt first moves the order to `MINED`; a later synchronization transaction settles the frozen balance and moves it to `CONFIRMED`. This keeps “included on-chain” separate from “finalized in the internal ledger.”

Administrative roles:

- `REVIEWER` or `ADMIN`: approve and reject reviews
- `OPERATOR` or `ADMIN`: broadcast and synchronize chain state
- `ADMIN` only: query withdrawal audit records

Global switches are controlled by environment variables. Asset-specific switches remain in `supported_asset`; both levels must be enabled.

```powershell
$env:WALLET_DEPOSIT_ENABLED="true"
$env:WALLET_WITHDRAW_ENABLED="true"
```

Run `docs/sql/V10__withdraw_state_preflight.sql` before V10. A legacy `PROCESSING` order cannot prove its broadcast outcome, so migration moves it to `MANUAL_REVIEW` while keeping funds frozen.

## Nonce management, signing isolation, and Outbox

`wallet_nonce` stores the next nonce for each `(chain_id, hot_wallet_address)`. Allocation locks the withdrawal order and nonce row with `SELECT FOR UPDATE`, then selects `max(database next_nonce, chain pending nonce)`. A unique `(chain_id, hot_wallet_address, nonce)` constraint ensures one nonce per order. Repeated preparation does not call RPC or sign again.

`TransactionSigner` is the only signing interface. `LocalDevSigner` is registered only under `dev` or `test`; every other profile uses `RemoteSignerClient`. The backend decodes and independently verifies every remote result: sender, chain ID, nonce, gas, recipient, amount, and calldata. It calculates `txHash` locally from `rawTransaction` and never trusts a signer-declared hash. Raw transactions must have a `0x` prefix, contain even-length hexadecimal data, and remain at or below 128 KiB. Both response verification and broadcasting enforce this limit before decoding.

Broadcasting calculates the hash again and fails closed if a successful primary RPC response returns another hash. Only an explicit primary failure or network error permits the same raw transaction to be sent to the secondary provider. No re-signing or new nonce allocation occurs.

Minimum production signer settings:

```powershell
$env:WALLET_SIGNER_HOT_WALLET_ADDRESS="0xPlatformHotWallet"
$env:WALLET_SIGNER_KEY_ID="withdraw-v1"
$env:WALLET_SIGNER_REMOTE_URL="https://internal-signer-service"
```

Outbox transitions are `PENDING -> PROCESSING -> SENT`. Recoverable failures return to `PENDING` until the attempt limit is reached. Every retry sends the exact persisted raw transaction. If an RPC response times out, the worker queries the local transaction hash; a visible transaction is accepted as success, otherwise the configured retry delay applies. Expired `PROCESSING` leases recover after restart. An exhausted Outbox becomes `DEAD`, the order enters `MANUAL_REVIEW`, and funds remain frozen.

Tune the worker with `WALLET_WITHDRAW_BROADCAST_ENABLED`, `WALLET_WITHDRAW_BROADCAST_MAX_ATTEMPTS`, `WALLET_WITHDRAW_BROADCAST_RETRY_DELAY`, and `WALLET_WITHDRAW_BROADCAST_PROCESSING_TIMEOUT`. Before production upgrades, run `docs/sql/V11__wallet_nonce_preflight.sql` followed by `docs/sql/V12__withdraw_outbox_preflight.sql`.

## Gas, receipts, confirmations, and reorganizations

V13 uses EIP-1559 transactions. Preparation calls `eth_estimateGas`, applies an upward safety multiplier, then caps both gas limit and `gasLimit * maxFeePerGas`. Before signing, the wallet checks available hot-wallet ETH; an ERC-20 withdrawal also verifies the raw token balance. The current implementation targets one configured EVM chain, native ETH, and enabled ERC-20 assets from `supported_asset` (Sepolia USDC is seeded by default). It does not implement multi-chain routing.

The receipt worker advances:

```text
BROADCASTED -> MINED -> CONFIRMED
```

Frozen funds are settled only after `confirmation_blocks`. A failed receipt, a disappearing or non-canonical mined receipt, RPC uncertainty, an excessive pending duration, or a missing original transaction whose nonce was consumed by another transaction moves the order to `MANUAL_REVIEW`. A same-nonce replacement hash is recorded in the chain-transaction snapshot. Broadcast and receipt-tracking switches are independent, so pausing new broadcasts does not stop confirmation of already broadcast orders.

The deposit scanner stores block and parent hashes at each height. On a fork at its cursor, it searches backward within `reorg-depth` for a common ancestor and rewinds. An uncredited orphaned deposit becomes `REORGED`. If a previously credited deposit disappears, the system moves up to the deposit amount from the user's available balance into risk-frozen funds and records both the frozen amount and any shortfall in `asset_risk_freeze_detail`, while preserving `total = available + frozen`.

Key settings:

```powershell
$env:WALLET_WITHDRAW_RECEIPT_ENABLED="true"
$env:WALLET_WITHDRAW_GAS_SAFETY_MULTIPLIER="1.20"
$env:WALLET_WITHDRAW_MAX_GAS_LIMIT="300000"
$env:WALLET_WITHDRAW_MAX_TOTAL_FEE_WEI="20000000000000000"
$env:WALLET_WITHDRAW_PENDING_TIMEOUT="1800000"
$env:WALLET_WITHDRAW_REPLACEMENT_LOOKBACK_BLOCKS="128"
```

Run `docs/sql/V13__chain_lifecycle_preflight.sql` before the Flyway migration.

## Reconciliation, risk control, and monitoring

V14 adds scheduled three-layer reconciliation:

```text
asset_account <-> latest asset_flow balance snapshot
deposit/withdrawal orders <-> required asset flows
platform on-chain assets >= internal user liabilities
```

Each run writes `reconciliation_run`; discrepancies write `reconciliation_difference`. A user-level ledger or order discrepancy freezes that user's withdrawals. Any critical discrepancy or reconciliation failure pauses the platform `WITHDRAW` switch. A later clean run closes historical differences but never resumes withdrawals automatically; an administrator must confirm and call the resume endpoint.

Configure on-chain custody addresses with the comma-separated `WALLET_RECONCILIATION_ASSET_ADDRESSES`. When empty, only the withdrawal hot wallet is counted. Production must include the hot wallet, collection wallets, and every other custody address backing user liabilities. Missing addresses create a solvency discrepancy and automatically pause withdrawals.

The default V14 policy requires withdrawal-address allowlisting. Sepolia ETH defaults to `10 ETH` per user and `100 ETH` platform-wide per day; USDC defaults to `10000 USDC` and `100000 USDC`. Per-transaction limits remain in `supported_asset`. Daily limits are checked while locking the asset-policy row; rejected orders consume no quota. Every withdrawal still begins at `PENDING_REVIEW`. The reviewer is persisted as `reviewer_user_id`, and the signer/broadcast operator as `operator_user_id`. The same user cannot perform both steps, including administrators.

`ADMIN`-only endpoints:

- `POST /api/admin/risk/withdraw-addresses`: allowlist an address
- `DELETE /api/admin/risk/withdraw-addresses/{id}`: disable an allowlisted address
- `GET|POST /api/admin/risk/withdraw-policies`: query or change daily limits and allowlist requirements
- `POST /api/admin/risk/users/{userId}/freeze|release`: freeze or release a user's withdrawals
- `POST /api/admin/risk/withdrawals/pause|resume`: pause or resume platform withdrawals
- `POST /api/admin/reconciliation/run`: run reconciliation immediately
- `GET /api/admin/reconciliation/differences`: query reconciliation differences

Login and business APIs use atomic Redis rate limiting. The defaults are ten login attempts per IP per minute and 120 other API calls per user or IP per minute. Redis failures fail closed with `503`; exceeded limits return `429` and a standard `Retry-After` header containing the wait time in seconds. Configure this behavior with `WALLET_LOGIN_RATE_LIMIT`, `WALLET_API_RATE_LIMIT`, `WALLET_API_RATE_LIMIT_WINDOW_SECONDS`, and `WALLET_API_RATE_LIMIT_FAIL_OPEN`.

Actuator exposes `health`, `metrics`, and `prometheus`. Everything except health requires `ADMIN`; production should also restrict monitoring endpoints at the network layer. The `prod` readiness group checks the database, Redis, signer-service readiness through the same mTLS client, and cached primary/secondary RPC chain ID, head lag, and common block hash. A signer, KMS, or RPC quorum failure prevents the wallet instance from becoming Ready.

RPC preflight refreshes every 30 seconds by default through `WEB3_RPC_QUORUM_PREFLIGHT_FIXED_DELAY`. Signer probing is configured with `WALLET_SIGNER_REMOTE_HEALTH_PATH`, `WALLET_SIGNER_REMOTE_CONNECT_TIMEOUT`, and `WALLET_SIGNER_REMOTE_READ_TIMEOUT`.

Important metrics include:

- `wallet.scan.block.lag`, `wallet.rpc.requests`, `wallet.rpc.errors`
- `wallet.rpc.quorum.enabled`, `wallet.rpc.block.hash.quorum.enabled/matches/mismatches/errors`
- `wallet.rpc.chain.id.quorum.matches/mismatches/errors`
- `wallet.rpc.preflight.up/consecutive_failures/failures`
- `wallet.rpc.receipt.quorum.matches/mismatches/errors`
- `wallet.rpc.nonce.pending.quorum.matches/mismatches/errors`
- `wallet.rpc.nonce.latest.quorum.matches/mismatches/errors`
- `wallet.rpc.transaction.quorum.matches/mismatches/errors`
- `wallet.rpc.balance.native.quorum.matches/mismatches/errors`
- `wallet.rpc.balance.erc20.quorum.matches/mismatches/errors`
- `wallet.rpc.head.quorum.accepted/mismatches/errors` and `wallet.rpc.head.quorum.lag`
- `wallet.rpc.fee.quorum.accepted/mismatches/errors`
- `wallet.rpc.fee.quorum.secondary.priority.selected`
- `wallet.rpc.gas.estimate.quorum.accepted/errors`
- `wallet.rpc.gas.estimate.quorum.secondary.selected`
- `wallet.rpc.broadcast.fallback.attempts/accepted/errors/hash.mismatches`
- `wallet.outbox.backlog`, `wallet.withdraw.pending`, `wallet.nonce.gap`
- `wallet.hot_wallet.asset.balance`, `wallet.hot_wallet.gas.balance`
- `wallet.ledger.anomalies`, `wallet.reconciliation.differences`
- `wallet.chain.reorganizations`, `wallet.monitoring.collection.errors`

Run `docs/sql/V14__reconciliation_risk_preflight.sql` before production migration. Configure reconciliation addresses and risk policies, add user allowlist entries, and only then enable `WALLET_RECONCILIATION_ENABLED`.

## V16 balanced operational subledger

Every `asset_flow` causes a database trigger to create one immutable `accounting_journal` and three signed entries:

```text
USER_AVAILABLE + USER_FROZEN + SYSTEM_CLEARING = 0
total_debit = total_credit
```

A deposit increases user available funds and decreases the system clearing position. A withdrawal freeze transfers value between the user's available and frozen accounts. Withdrawal confirmation decreases frozen funds and increases the system clearing position. Journals and entries cannot be updated or deleted, and unique `source_flow_id` prevents duplicate accounting for one asset flow. This is an operational wallet subledger, not a statutory general ledger.

Administrators can investigate by business identifier or exact asset-flow ID and count imbalanced journals:

```text
GET /api/admin/accounting/journals/{businessType}/{businessId}
GET /api/admin/accounting/journals/by-flow/{sourceFlowId}
GET /api/admin/accounting/imbalances/count
```

## Mock-deposit isolation

`POST /api/deposit/mock-confirm` exists only for local development and automated tests. It requires all of the following:

- the active Spring profile is `dev` or `test`; the Controller and Service beans are absent elsewhere, and any active `prod` profile suppresses them even if `dev` or `test` is also set;
- the authenticated caller has `OPERATOR` or `ADMIN`; normal users are rejected;
- the production `DepositService` API does not expose a mock-credit method, so production funds can only be credited through confirmed chain scanning.

Never activate `dev` or `test` in production. Gate internal administrative endpoints at the gateway or security-group layer.

## Deposit-scanner configuration

Scanning is disabled by default. Flyway creates the database structures. Set `initial-block` close to the current chain height before enabling it. ETH/ERC-20 allowlists, decimals, and confirmation counts come from `supported_asset`, not YAML token metadata.

```yaml
wallet:
  confirm-blocks: 12
  scan:
    enabled: true
    chain: ETH_SEPOLIA
    initial-block: the Sepolia height where scanning should begin
    batch-size: 100
    reorg-depth: 24
    fixed-delay: 15000
    lock-key-prefix: wallet:deposit-scan:lock:
    lock-lease: 300000
```

A renewable Redis distributed lock ensures one scanner per chain. `lock-lease` must exceed the longest expected batch-processing time. Only registered assets with deposits enabled create deposit orders. Status `0` waits for confirmation, `1` is credited, and `2` is invalidated by a reorganization. User crediting is independent of sweep-order creation. If sweeping is disabled, orders remain eligible for compensation and are created by the worker after sweeping is re-enabled. `chain_block_scan_record` stores progress and block hashes so scanning resumes after restart.

## Supported-asset registry

V8 introduced `supported_asset`, initially seeding Sepolia ETH and USDC. `asset_code` is globally unique. Native assets use unique `(chain_id, NATIVE)` identity, while ERC-20 assets use `(chain_id, normalized_token_address)`. `asset_account` has `UNIQUE(user_id, asset_id)`, preventing duplicate native accounts caused by nullable MySQL token addresses.

Run `docs/sql/V8__supported_asset_preflight.sql` before upgrading. Duplicate accounts and invalid addresses require manual cleanup; the migration never merges accounts with potentially different balances.

## Solidity protocol and dApp

The repository also contains a security-focused staking protocol under `staking-protocol`:

- stake, withdraw, claim, and exit flows;
- EIP-2612 permit-based staking;
- reward funding and proportional accrual;
- pause and emergency-withdraw paths;
- fee-on-transfer token rejection;
- reentrancy protection;
- two-step ownership and delayed reward-distributor changes;
- unit, fuzz, invariant, coverage, formatting, and Slither checks;
- a lightweight React + viem dApp that demonstrates the complete interaction flow.

See [`staking-protocol/README.md`](staking-protocol/README.md) for contract details and deployment instructions.

## Delivery status

The repository includes a non-root container image, complete MySQL/Redis/Anvil Compose stack, dev/docker/prod configuration, readiness checks, full CI integration tests, SBOM generation, container vulnerability gates, Solidity fuzz/invariant tests, Slither analysis, and a controlled production checklist.

Production deployment still requires real external key infrastructure, alert delivery, backup and recovery procedures, incident response, independent security review, and jurisdiction-specific compliance. A green CI run is evidence that automated checks passed; it is not authorization to hold public funds.

- Phase 1: user accounts and custodial deposit-address allocation — complete
- Phase 2: asset accounts, flows, and dev/test-only mock deposits — complete
- Phase 3: real deposit scanning, confirmations, and reorganization handling — complete
- Phase 4: withdrawal freezing, review, signing, and broadcasting — complete
- Phase 5: nonce management, signing isolation, transaction snapshots, and Outbox recovery — complete
- Phase 6: EIP-1559, gas controls, receipt confirmation, and deposit-reorg risk freezing — complete
- Phase 7: three-layer reconciliation, withdrawal risk controls, rate limiting, and monitoring — complete
- Phase 8: end-to-end tests, Docker deployment, environment layering, health checks, and final QC — complete

## Roadmap

- automated ERC-20 gas funding with risk controls
- one nonce domain when withdrawals and deposit sweeps share a hot wallet
- controlled replacement transactions for stuck withdrawals
- Kubernetes and cloud-provider deployment templates
- image signing, deployment provenance, and stronger supply-chain attestations
- disaster-recovery automation and repeatable recovery exercises

## Production boundary

This project is suitable for local development, CI, testnet deployment, architecture demonstrations, and Web3 backend interviews. Before accepting real public funds, complete at least the following outside this repository:

- deploy the isolated signer against a real Google Cloud KMS / Cloud HSM secp256k1 key;
- enforce mTLS identities, network isolation, least-privilege IAM, quotas, and emergency signing shutdown;
- establish dual-control key creation, backup, rotation, recovery, and destruction procedures;
- deploy independent RPC providers, monitoring, paging, log retention, and tamper-evident audit storage;
- prove database, Redis, configuration, and key-metadata backup and restoration;
- run signer outage, RPC disagreement, chain reorganization, stuck transaction, database recovery, and regional-failure exercises;
- complete independent application, smart-contract, cloud, and operational security audits;
- obtain legal, licensing, AML/KYC, sanctions, privacy, and custody approvals for the target jurisdiction.
