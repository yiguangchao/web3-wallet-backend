# Isolated Google Cloud HSM signer

This service is a separate security boundary. It accepts only mTLS-authenticated wallet-service requests, evaluates database policies, calls a Google Cloud KMS `EC_SIGN_SECP256K1_SHA256` key version, verifies the returned signature locally and returns an EIP-1559 raw transaction.

## Security invariants

- No private key or mnemonic is accepted by configuration or API.
- Production TLS requires client certificates (`client-auth: NEED`).
- A SHA-256 hashed service token is checked in addition to mTLS.
- Every request requires a timestamp and idempotency key.
- An idempotency reservation is committed before KMS is called. An interrupted request stays `PROCESSING` and is not signed again automatically.
- A stuck `PROCESSING` request can only be terminally marked `FAILED` through a two-person, audit-chained admin workflow; it is never automatically re-signed.
- KMS public-key and signing responses must report the `HSM` protection level; software-backed keys fail closed.
- KMS public keys, request digests and returned signatures are bound to the requested key version and verified with CRC32C before use.
- Readiness includes MySQL plus a cached startup/periodic KMS preflight. It stays down if no active key exists or any address/integrity check fails.
- Native recipients and decoded ERC-20 recipients must be allowlisted. Recipient allowlist additions and disablements require two-person, audit-chained approval. ERC-20 contract allowlisting, raw-amount limit changes and disablements use a separate two-person workflow. Arbitrary contract calls are rejected.
- Native and token limits are reserved with database row locks.
- Signing starts emergency-stopped.
- Key rotation, activation, disablement and resume require two different certificate identities. Emergency stop is immediate.
- Audit rows are append-only and chained with SHA-256. Verification failure automatically stops signing.

## Google Cloud setup

Apply `deploy/terraform/google-kms.tf` through an independently approved infrastructure pipeline. The resulting key version must use `EC_SIGN_SECP256K1_SHA256` and `HSM`. Use GKE Workload Identity; do not mount a service-account JSON key.

## Required runtime secrets

- `SIGNER_MYSQL_URL`, `SIGNER_MYSQL_USERNAME`, `SIGNER_MYSQL_PASSWORD`
- `SIGNER_WALLET_TOKEN_SHA256`, `SIGNER_ADMIN_TOKEN_SHA256` (distinct tokens)
- `SIGNER_TLS_KEY_STORE`, `SIGNER_TLS_KEY_STORE_PASSWORD`
- `SIGNER_TLS_TRUST_STORE`, `SIGNER_TLS_TRUST_STORE_PASSWORD`

`SIGNER_KMS_PREFLIGHT_FIXED_DELAY` controls the cached KMS readiness refresh interval in milliseconds and defaults to 60 seconds. Health responses expose only a stable reason code, never the KMS exception or resource name.

The Prometheus endpoint exposes `wallet_signer_kms_preflight_up`, `wallet_signer_kms_preflight_consecutive_failures`, and `wallet_signer_kms_preflight_failures_total{reason=...}`. Alert when `up` is `0` or when the consecutive failure gauge is non-zero for longer than the refresh interval.

`GET /api/v1/admin/signing-resolutions/stale` lists at most 100 `PROCESSING` requests whose `updated_at` exceeds `SIGNER_PROCESSING_ALERT_SECONDS` (default 300 seconds). It is read-only and requires the key-admin mTLS identity. Prometheus exposes `wallet_signer_idempotency_stale`, `wallet_signer_idempotency_stale_collection_up`, and `wallet_signer_idempotency_stale_collection_errors_total`; alert when the stale count is positive or collection is down. Use the dual-control resolution procedure for every returned request.

Deploy `deploy/monitoring/prometheus-alerts.yml` through the monitored-infrastructure pipeline. The signer alert rules and response steps are documented in `deploy/monitoring/incident-response.md`.

The MySQL identity should only access the signer schema. The wallet backend must have no access to this schema or Google KMS.

## Stuck signing-request procedure

1. Preserve KMS, RPC and wallet-service evidence for the idempotency key; do not resend the signing request.
2. The first administrator submits `POST /api/v1/admin/signing-resolutions` with the idempotency key and a detailed reason.
3. A different administrator approves `POST /api/v1/admin/signing-resolutions/{resolutionId}/approve`.
4. The signer records an immutable audit event and changes only the reservation status to `FAILED`. A new business request must use a new idempotency key after independent investigation.

## Wallet client mTLS

Inject the client PKCS12 key/trust stores into the wallet workload and set JVM TLS properties through the deployment secret. Set `WALLET_SIGNER_REMOTE_API_TOKEN` to the unhashed token and `WALLET_SIGNER_REMOTE_URL=https://wallet-signer`.

## Bootstrap

1. Deploy with signing stopped.
2. Propose a `ROTATE` key change containing the full KMS version name, derived Ethereum address, chain and native limits.
3. Approve it using a different client certificate identity.
4. Add the first withdrawal recipient through `POST /api/v1/admin/address-policy-changes`, then approve it with a different key-admin identity.
5. Add each ERC-20 contract and its raw-amount limits through `POST /api/v1/admin/token-policy-changes`, then approve it with a different key-admin identity.
6. Verify a test signature and recovered sender on a non-production key.
7. Propose `RESUME`; approve with another identity.

## Token policy dual control

`POST /api/v1/admin/token-policy-changes` accepts `ADD`, `UPDATE_LIMITS`, and `DISABLE`. `ADD` creates or re-enables a token policy and replaces its limits. `UPDATE_LIMITS` and `DISABLE` require an active existing policy. A different key-admin certificate identity must call `POST /api/v1/admin/token-policy-changes/{changeId}/approve` before the policy takes effect.

Limits use the token's raw integer unit, not a decimal display amount. Both limits must be positive, the daily limit must be at least the single-transfer limit, and `DISABLE` must omit both limit fields. Only one pending change per key, chain and token is allowed. Every proposal, approval and cancellation is written to the signer audit chain.

Administrators can query the oldest 100 pending proposals through `GET /api/v1/admin/token-policy-changes/pending`. The original proposer may withdraw a pending proposal through `POST /api/v1/admin/token-policy-changes/{changeId}/cancel`; another administrator cannot cancel it. Cancellation takes the same database row lock used by approval, releases the pending uniqueness slot, and appends `TOKEN_POLICY_CHANGE_CANCELLED` to the audit chain.

Each proposal stores an immutable `approvalExpiresAt`. `SIGNER_TOKEN_POLICY_APPROVAL_TTL_SECONDS` defaults to 86400 seconds and must remain between 300 seconds and seven days. Approval checks the stored deadline before applying a policy and repeats it in the conditional database update. A scheduled worker claims at most 100 overdue rows with `FOR UPDATE SKIP LOCKED`, marks them `EXPIRED`, and appends `TOKEN_POLICY_CHANGE_EXPIRED`; an expired change must be proposed again instead of having its deadline extended.

`SIGNER_TOKEN_POLICY_EXPIRATION_FIXED_DELAY` controls the worker interval and defaults to 60 seconds. Prometheus exposes `wallet_signer_token_policy_expiration_up`, `wallet_signer_token_policy_expired_total`, and `wallet_signer_token_policy_expiration_errors_total`. Alert rules and recovery steps are included under `deploy/monitoring`.
