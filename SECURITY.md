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

### Temporary Spring MVC finding triage (2026-10-09)

The image uses `spring-webmvc:6.2.19`. The dependency is affected by
`CVE-2026-47884` and `CVE-2026-47890`; this triage does **not** patch the library.
The official advisories require XSLT view rendering with attacker-controlled
implicit view names, or SSE streams containing view fragments, respectively:

- https://spring.io/security/cve-2026-47884/
- https://spring.io/security/cve-2026-47890/

The wallet API returns `Result` JSON envelopes and does not use either feature.
`RestOnlyMvcGuard` fails application startup if an application MVC handler is
not a REST controller returning `Result` (optionally inside `ResponseEntity`),
declares SSE output, or an XSLT view/resolver bean is registered. Swagger's
framework resource/redirect endpoints remain available. Guard regression tests
run as part of the required backend CI job before image scanning.

The blocking scan reads `.trivyignore.yaml`, restricted to these two CVEs and
the exact Maven package/version, expiring on **2026-11-09**. The SARIF report
remains unfiltered so findings are still visible. All other HIGH/CRITICAL
findings continue to block CI. Exceptions must be reassessed if MVC configuration,
third-party endpoints, rendering, or streaming changes; do not extend the date
without review. Spring 6.2.20 is commercial-only; the public fix is 7.0.9 and
requires a compatible Spring Boot migration. Plan that migration before expiry
and remove these exceptions after upgrading.

The development Docker Compose stack, Anvil accounts, local signer, and mock
deposit endpoints are for development and test profiles only. They must not be
used as production credentials or exposed to the public network.
