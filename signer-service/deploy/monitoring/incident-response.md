# Signer incident-response runbook

## Universal first response

1. Record the incident ID, UTC start time, deployment digest, affected signer instances and alert expression.
2. Preserve signer application logs, KMS audit logs, MySQL evidence and wallet-service/RPC evidence before any intervention.
3. Do not retry, re-sign, delete or edit a `PROCESSING` idempotency reservation manually.
4. Require two operators for every decision that can affect signing availability or a pending transaction.

## Signer unavailable

1. Check `/actuator/health/readiness` on the affected instance and keep it out of load balancing while it is not `UP`.
2. Check MySQL connectivity, mTLS certificate validity, KMS workload identity and the KMS preflight alert status.
3. Do not bypass readiness or route wallet traffic to a signer whose dependencies have not been verified.
4. If any transaction outcome is uncertain, use the stale-signing-request procedure after evidence is preserved.

## Signer HTTP errors

1. Identify the failing endpoint and correlate request IDs or idempotency keys with signer audit-chain entries.
2. For signing failures after an idempotency reservation is created, treat the request as uncertain; do not resend it.
3. Check KMS preflight, policy-rejection events, database health and the remote wallet service before restoring traffic.

## Signer latency

1. Check KMS request latency, MySQL lock waits and connection-pool saturation.
2. Do not increase client retry concurrency; retries can amplify idempotency contention.
3. If latency reaches the signing timeout, inspect for newly stale `PROCESSING` requests.

## KMS preflight failure

1. Keep the signer out of load balancing while `/actuator/health/readiness` is `DOWN`.
2. Inspect the health reason code only; obtain sensitive KMS details from restricted KMS audit logs, not the public health endpoint.
3. Verify the active key version, `EC_SIGN_SECP256K1_SHA256` algorithm, `HSM` protection level, public-key CRC32C and configured Ethereum address.
4. Verify workload identity and KMS IAM permissions. Do not replace the key version or weaken protection-level checks during an incident.
5. Resume traffic only after the preflight has been continuously `UP` and two operators have reviewed the recovery evidence.

## Stale signing request

1. Query `GET /api/v1/admin/signing-resolutions/stale` using a key-admin mTLS identity and preserve the returned idempotency key, timestamps and request context.
2. Check signer audit-chain events, KMS audit logs, wallet outbox state and RPC transaction/nonce evidence to determine whether a signature may have been produced.
3. Never retry the same idempotency key or create a replacement signature before the original outcome is known.
4. If the request must be terminally closed, use the dual-control signing-resolution proposal and approval workflow. It only marks the reservation `FAILED`; any later business request must use a new idempotency key.

## Stale-request monitoring failure

1. Treat the stale-request count as unknown while `wallet_signer_idempotency_stale_collection_up` is `0`.
2. Check MySQL connectivity and the signer metric-collection logs; do not assume there are no stuck requests.
3. Restore metric collection, then review the stale-request endpoint before closing the incident.

## Token-policy expiration failure

1. Treat every proposal past `approval_expires_at` as ineligible for approval even if its database status is still `PENDING`.
2. Check MySQL connectivity, scheduler health and audit-chain writes; never extend a proposal deadline by editing the row.
3. Restore the expiration job and confirm `wallet_signer_token_policy_expiration_up` returns to `1`.
4. Require the original administrator to submit a new proposal if the change is still needed, then repeat independent approval.
