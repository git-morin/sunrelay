# SunRelay

Reach your [Sunshine](https://github.com/SunshineStream/Sunshine) host securely from anywhere your phone has internet. Your devices connect to the phone's hotspot, and the phone relays the stream to the host over your mesh VPN.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/how-it-works-dark.svg">
  <img src="docs/how-it-works-light.svg" width="100%" alt="A Moonlight client on the phone's hotspot connects to SunRelay on the phone, which relays the stream over the mesh VPN to the Sunshine host.">
</picture>

## Why

A mesh VPN (Tailscale, ZeroTier, NetBird or Headscale) is the safe way to reach Sunshine without opening ports to the internet. But many devices you'd stream to, like a Switch, a PS Vita, an Xbox or a smart TV, can't run a mesh VPN client. SunRelay lets them use the phone's.

If a device can run your mesh VPN's app, including the phone itself, it can reach Sunshine directly without SunRelay.

## Features

- Relays Sunshine's TCP and UDP ports from the hotspot to the host over the mesh VPN, only for devices on the hotspot.
- Shows up in Moonlight automatically over mDNS.
- Pairs Moonlight from the phone with your Sunshine login.
- Works with Wi-Fi, USB and Bluetooth tethering.
- Finds your host through the Tailscale, ZeroTier, NetBird or Headscale API, or by scanning any other mesh VPN.
- No changes to Sunshine or Moonlight.

## Getting started

Requires Android 12 or later. Download the APK from the [latest release](https://github.com/git-morin/sunrelay/releases/latest).

1. Connect the phone to your mesh VPN and turn on its hotspot or tethering.
2. Open SunRelay, pick your mesh VPN and your Sunshine host.
3. Tap **Start relay**, or use the quick settings tile.
4. Open Moonlight on the device on your hotspot and pick **SunRelay**. To pair, enter Moonlight's PIN in SunRelay or in Sunshine's web UI.

## License

[MIT](LICENSE). Third-party names and logos, including the Sunshine logo used in the app icon and diagram (GPL-3.0), are covered in [NOTICE](NOTICE). SunRelay is not affiliated with Sunshine, Moonlight or any mesh VPN provider.
