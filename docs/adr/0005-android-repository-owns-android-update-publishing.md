# Android repository owns Android update publishing

The Android source repository will own its update client, version definition, manifest generation, APK release tooling, and GitCode publication flow. Windows and Relay remain independent at runtime and build time; they share only the public GitCode distribution repository, using separate immutable release tags and stable pointers so no source repository depends on a neighboring checkout.
