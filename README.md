# SunRelay

Stream from a [Sunshine](https://github.com/SunshineStream/Sunshine) host to any device on your phone's hotspot. The phone relays the stream over your mesh VPN.

```mermaid
flowchart LR
    C["Moonlight client"] -- "Connected to hotspot" --> P["Phone running SunRelay"]
    P -- "Mesh VPN / overlay network" --> S["Sunshine host"]
```

SunRelay forwards Sunshine's TCP and UDP ports from the hotspot to the host over the VPN. It advertises itself over mDNS Moonlight finds it on its own. With your Sunshine login, you can also pair Moonlight by typing the PIN on the phone.

## Requirements

- Android 12+
- A Sunshine host reachable over a VPN


## License

[MIT](LICENSE). Third-party names and logos are excluded, see below.

## Trademarks

Sunshine, Moonlight, Tailscale, ZeroTier, NetBird and Headscale are trademarks of their respective owners. SunRelay is an independent project and is not affiliated with, sponsored or endorsed by any of them.

The app icon uses the Sunshine logo, which is GPL-3.0 licensed by the Sunshine authors.
