<div align="center">

<img src="docs/assets/icon.png" alt="Narcic Gateway Logo" width="128" height="128" />

# 🛡️ Narcic Gateway Legacy (نرسیس گیت‌وی لگسی)

**An ultra-fast, multi-core, privacy-first VPN & Proxy client for Android**  
*Built for uncensored, secure, and resilient internet access.*

[![Release](https://img.shields.io/github/v/release/valid7996/Narcic-Getway-Legecy?style=for-the-badge&color=00D26A)](https://github.com/valid7996/Narcic-Getway-Legecy/releases)
[![Build Status](https://img.shields.io/github/actions/workflow/status/valid7996/Narcic-Getway-Legecy/android-release.yml?style=for-the-badge)](https://github.com/valid7996/Narcic-Getway-Legecy/actions)
[![License](https://img.shields.io/badge/License-GPL%203.0-blue.svg?style=for-the-badge)](LICENSE)
[![Telegram](https://img.shields.io/badge/Telegram-Narcic_team-2CA5E0?style=for-the-badge&logo=telegram)](https://t.me/Narcic_team)

[**فارسی (Persian Documentation)**](README.fa.md) • [**Download APK**](https://github.com/valid7996/Narcic-Getway-Legecy/releases/latest) • [**Report Issue**](https://github.com/valid7996/Narcic-Getway-Legecy/issues)

</div>

---

## ⚡ Overview

**Narcic Gateway** is a modern, unified proxy client engineered to bypass advanced deep packet inspection (DPI) and severe internet censorship. Combining cutting-edge cryptographic protocols, automated multi-tunnel failover, and a modern Compose Multiplatform UI, Narcic Gateway delivers an uncompromising combination of speed, security, and elegance.

---

## ✨ Key Highlights

- 🚀 **Next-Gen Multi-Core Engine:** Powered by Xray-core, Sing-box, Psiphon Tunnel, Aether, Tor, and WireGuard.
- 🔗 **Native `narcicgeteway://` Deep Links:** One-click import and export for complex subscription schemes and proxy profiles.
- 🌐 **Interactive Route & Country Map:** Live visual traceroute showing your real connection path, ISP country routing, and IP details.
- 🛰️ **Cyber Radar & Auto Server Selection:** Continuous background ping checks, latency metrics, and instant failover to the lowest-ping nodes.
- 🔒 **Encrypted Vault (.zsx):** Share and store sensitive profiles with zero-knowledge AES/ChaCha encryption.
- 🎨 **Adaptive Liquid UI:** Beautiful Material 3 & Jetpack Compose interface with dark mode, AMOLED black, and dynamic accent themes.
- 🛡️ **Per-App Proxy & Routing:** Custom split tunneling, domain/IP rule engine, and bypass for local services.

---

## 🛠️ Supported Protocols

| Protocol / Core | Features & Transports |
|:---|:---|
| **V2Ray / Xray** | VLESS, VMess, Trojan, Shadowsocks (`TCP`, `WS`, `gRPC`, `HTTPUpgrade`, `REALITY`) |
| **Aether** | WireGuard over MASQUE, WARP-in-WARP (`gool`), auto-scan IP, delay probe |
| **Hysteria 2 (hy2)** | High-speed UDP congestion-resistant proxy over HTTP/3 QUIC |
| **Psiphon** | Multi-hop fronted tunnels & automated censorship bypass |
| **Tor & Bridges** | Tor circuit routing, Snowflake, obfs4, and Conjure pluggable transports |
| **WireGuard / AWG** | WireGuard & AmneziaWG obfuscated UDP tunnels |
| **DNS Tunnels** | DNSTT, VayDNS, MasterDNS for connectivity when all TCP/UDP traffic is blocked |
| **OpenConnect & IKEv2** | Cisco AnyConnect & native enterprise IPSec tunnels |

---

## 📲 Quick Installation

1. Navigate to the **[Releases](https://github.com/valid7996/Narcic-Getway-Legecy/releases/latest)** section.
2. Download the latest APK corresponding to your device architecture (`arm64-v8a` recommended for almost all modern phones, or universal).
3. Install the APK, open Narcic Gateway, and import your subscription via:
   - Deep-link (`narcicgeteway://...`)
   - Clipboard paste
   - QR code scanner
4. Tap **Connect** and enjoy uncensored internet!

---

## 🤝 Community & Credits

- **Author & Maintainer:** [Rabin Hood (valid7996)](https://github.com/valid7996)
- **Official Telegram Channel:** [@Narcic_team](https://t.me/Narcic_team)

---

## 📜 License

This project is licensed under the **GNU General Public License v3.0**. Core libraries are subject to their respective open-source licenses (GPLv3, LGPLv3, MPL 2.0).
