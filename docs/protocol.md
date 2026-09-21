# Notifling Protocol v1 (UDP-only, LAN)

Port: `51234/udp` (configurable, must match on both sides).
Encoding: JSON, UTF-8, single datagram < 8KB.
Auth: pre-shared key (32 random bytes, base64url). Never sent in clear.
On the wire only `key_hash = hex(SHA256(key_bytes))` is sent. PC drops mismatches.

QR pairing format (what the PC shows, what the phone scans):

```
NOTIFLING:1:<base64url-key>
```

Example: `NOTIFLING:1:K7vX2hJ8sD9fG3hJ6kL0pQ1rT4yU7wE0aZ5cB8dF2gH4k`

## Packets

### 1. DISCOVER — Phone -> broadcast `255.255.255.255:51234`
```json
{"v":1,"type":"DISCOVER","key_hash":"<hex64>","phone_id":"Pixel-8-AB12","port":51234}
```
`port` = phone's reply port (socket it listens on for OFFER). Phone should
listen on an ephemeral port and put it here; if 0/absent, PC replies to the
source port of the DISCOVER datagram.

### 2. OFFER — PC -> unicast to phone
```json
{"v":1,"type":"OFFER","key_hash":"<hex64>","pc_name":"DESKTOP-X"}
```

### 3. NOTIFY — Phone -> unicast to PC (cached IP from OFFER)
```json
{
  "v":1,"type":"NOTIFY","key_hash":"<hex64>",
  "id":"<uuid>","pkg":"com.whatsapp",
  "title":"Mom","text":"Call me","ts":1737320000
}
```
- `id`: uuid v4 per notification, used for dedup (PC keeps 5-min window).
- `title`/`text`: truncated to 256/512 chars by sender.
- `ts`: unix seconds.

## Flow
1. Phone keeps `pcIp` cache (10 min TTL). If empty/expired → broadcast DISCOVER, wait 1.5s for OFFER (2 retries).
2. Phone sends NOTIFY to `pcIp:port`. On send error → invalidate cache, re-discover once.
3. PC: parse → check `key_hash` (constant-time compare) → route by `type`.

## Security notes (v1)
- LAN-trusted only. Payload is plaintext; hash is not a MAC. Anyone on the
  same WiFi can sniff notification text and replay packets.
- Do not use on public/untrusted WiFi. Upgrade path: TCP + HMAC-SHA256
  (challenge-response) without changing the UI.
