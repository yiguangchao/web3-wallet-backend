# Security Policy

## Supported versions

Security fixes are applied to the `main` branch. Releases created from older
commits should be upgraded to the latest release before investigating an issue.

## Reporting a vulnerability

Please report suspected vulnerabilities through a private GitHub Security
Advisory for this repository. Do not open a public issue or include secrets,
private keys, JWT tokens, database credentials, or production RPC endpoints in
the report.

Include, when safe to share:

- affected commit, component, and deployment profile;
- concise reproduction steps or a proof of concept;
- security impact and suggested mitigation;
- whether the issue is already exploitable in a production configuration.

We will acknowledge a report within 3 business days, provide an initial
severity assessment within 7 business days, and coordinate a fix and disclosure
timeline with the reporter. Reports that contain user funds or signing-key
exposure are treated as incidents and should be marked accordingly.

## Scope notes

The development Docker Compose stack, Anvil accounts, local signer, and mock
deposit endpoints are for development and test profiles only. They must not be
used as production credentials or exposed to the public network.
