# Slither finding triage

CI always generates `slither-report.json` with every detector enabled. A second
Slither invocation is the release gate. It excludes only the detector families
below because every current occurrence has been reviewed against this vault's
design and tests. Any finding from another detector family still fails CI.

| Detector | Review disposition |
| --- | --- |
| `reentrancy-balance` | The pre/post balance checks deliberately reject fee-on-transfer tokens. Both entry points hold the vault-wide `nonReentrant` lock. |
| `reentrancy-no-eth` | `stake`, `stakeWithPermit`, `withdraw`, `exit`, reward funding, and recovery share the same lock. Effects are applied before outbound value transfers; the inbound transfer checks are reverted atomically on mismatch. Malicious-token reentry is covered by tests. |
| `reentrancy-benign` | Same reviewed `_stake` inbound-transfer pattern and vault-wide lock as above. |
| `divide-before-multiply` | Integer division intentionally leaves reward dust in the contract. The reconstructed amount is used only as a conservative reserve check and cannot exceed the funded balance. |
| `incorrect-equality` | Equality is used only for explicit zero-value guards, not for authorization, randomness, or token-balance identity. |
| `timestamp` | Timestamps control reward streaming and the 48-hour configuration delay. They do not choose winners or depend on exact block time. |
| `low-level-calls` | `SafeToken` intentionally uses a checked low-level call to support ERC-20 tokens that return either no value or a boolean. Failure and false return values revert. |

This is not a permanent acceptance of future findings. A change that introduces
a new occurrence in an excluded family must update this document with a specific
review rationale and add a regression, fuzz, or invariant test where applicable.
Before mainnet use, an independent auditor must re-evaluate these dispositions.
