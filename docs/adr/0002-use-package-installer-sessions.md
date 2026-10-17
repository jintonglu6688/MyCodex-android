# Use PackageInstaller sessions for update installation

Status: superseded by `0006-use-fileprovider-installer-handoff.md` after device validation showed that direct sessions bypassed the user's configured third-party installer on Xiaomi HyperOS.

MyCodex Android will hand verified update packages to Android through `PackageInstaller.Session`, with explicit system confirmation and a dedicated installation-status callback. We will not add the shorter legacy installation-intent path as a first-release fallback because it is deprecated, exposes less reliable status information, and would broaden the existing diagnostic FileProvider's responsibility.
