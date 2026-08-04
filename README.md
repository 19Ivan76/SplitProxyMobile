# SplitProxyMobile 0.3.0

- HTTP proxy remains fixed: `195.209.210.144:28443`, no authentication.
- Fixes Android native loader failure reported as `NoClassDefFoundError`.
- `libtun2proxy.so` is now opened with `dlopen`, so the app shows the real Android linker error if the device cannot load it.
- Selected-app routing remains unchanged.

The mobile operator must be able to establish TCP/CONNECT to `195.209.210.144:28443`. A timeout before the VPN engine starts is a network/server reachability problem, not a TUN problem.
