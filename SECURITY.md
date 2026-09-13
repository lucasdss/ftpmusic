# Security Policy

## Reporting a vulnerability

Please report security issues privately, not in public issues.

- Preferred: GitHub Security Advisories ("Report a vulnerability" on the
  repository Security tab).
- Email: lucas.dss.santos@outlook.com

Include reproduction steps, affected version/commit, and impact. You should
receive a response within a few days.

## Scope notes

- Server credentials and the Last.fm API key are stored on-device in
  encrypted shared preferences; they are never bundled with the app.
- Debug logs redact credential-bearing query parameters. If you find a log,
  crash report, or exported file that leaks credentials, treat it as a
  security issue.
- Self-signed server certificates are supported via an explicit user-confirmed
  trust flow; report any path that silently bypasses certificate validation.

## Supported versions

Security fixes target the latest release on the default branch.
