# Security Policy

## Supported versions

Security updates are provided for the latest released CPAMP Mobile version. Development snapshots on the default branch may change without compatibility guarantees.

## Reporting a vulnerability

Do not open a public issue for a vulnerability that may expose Admin Keys, signing material, server data, or a reproducible exploit.

Use GitHub's private vulnerability reporting feature for this repository when it is available. Include:

- affected app version or commit;
- Android version and device type;
- whether the server used HTTP or HTTPS;
- reproduction steps and expected impact;
- logs or screenshots only after removing credentials, hostnames, account identifiers, and usage data.

If private reporting is unavailable, open a public issue containing no exploit details or sensitive data and request a private contact channel. Maintainers should acknowledge a complete report within seven days and coordinate disclosure after a fix is available.

Never send a real CPAMP Admin Key, signing key, Keystore, `local.properties`, production database, Auth File, or unredacted server log.

## Security design

- Admin Keys are encrypted with Android Keystore AES-GCM and stored separately from DataStore server metadata.
- Optional app lock migrates keys to a Keystore key requiring biometric or device-credential authorization.
- Screenshots and recent-task previews are blocked with `FLAG_SECURE`.
- Android backup and device-transfer extraction are disabled.
- HTTPS always uses the system certificate chain and hostname verification. There is no certificate bypass.
- HTTP requires explicit per-server consent and remains visibly marked as unencrypted.
- OkHttp follows no redirects, retries only selected idempotent GET responses once, and never automatically replays management writes.
- Switching or disconnecting invalidates the current client, cancels calls, evicts connections, and removes in-memory session state.
- Release builds use shrinking/obfuscation and include no application or HTTP logging pipeline.
- The app contains no telemetry, advertising SDK, analytics SDK, or third-party crash upload.

## Boundaries

CPAMP Mobile cannot secure an Admin Key transmitted over HTTP, a compromised Android operating system, a malicious or compromised Manager Server, or credentials revealed outside the app. Device credential strength, biometric enrollment, certificate trust anchors, VPN behavior, and local network security remain under user and operating-system control.

The app manages an existing CPA-Manager-Plus deployment. It does not harden, configure, update, or audit the server itself.