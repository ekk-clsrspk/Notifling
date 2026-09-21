package main

import (
	"encoding/json"
	"net"
	"testing"
	"time"
)

func testPacket(t *testing.T, typ string, hash string) Packet {
	t.Helper()
	return Packet{V: 1, Type: typ, KeyHash: hash, PhoneID: "test", ID: "id-1", Pkg: "com.example", Title: "Hi", Text: "Hello", Ts: 1}
}

func TestDiscoverOfferRoundtrip(t *testing.T) {
	const key = "dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleTE" // 32 bytes b64url-ish
	want := keyHash(key)
	dd := newDedup(5 * time.Minute)
	from, _ := net.ResolveUDPAddr("udp", "192.168.1.50:40000")
	reply, _, _ := handlePacket(testPacket(t, "DISCOVER", want), want, "PC", dd, from)
	if reply == nil || reply.Type != "OFFER" || reply.PCName != "PC" {
		t.Fatalf("expected OFFER, got %+v", reply)
	}
	raw, _ := json.Marshal(reply)
	var back Packet
	if err := json.Unmarshal(raw, &back); err != nil {
		t.Fatal(err)
	}
}

func TestKeyMismatchDropped(t *testing.T) {
	const key = "dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleTE"
	want := keyHash(key)
	dd := newDedup(5 * time.Minute)
	from, _ := net.ResolveUDPAddr("udp", "192.168.1.50:40000")
	reply, title, _ := handlePacket(testPacket(t, "NOTIFY", "0bad"), want, "PC", dd, from)
	if reply != nil || title != "" {
		t.Fatalf("mismatched key must be dropped, got %+v %q", reply, title)
	}
}

func TestNotifyDedup(t *testing.T) {
	const key = "dGVzdGtleXRlc3RrZXl0ZXN0a2V5dGVzdGtleTE"
	want := keyHash(key)
	dd := newDedup(5 * time.Minute)
	from, _ := net.ResolveUDPAddr("udp", "192.168.1.50:40000")
	_, title1, _ := handlePacket(testPacket(t, "NOTIFY", want), want, "PC", dd, from)
	_, title2, _ := handlePacket(testPacket(t, "NOTIFY", want), want, "PC", dd, from)
	if title1 == "" || title2 != "" {
		t.Fatalf("first must toast, second (dup id) must not: %q %q", title1, title2)
	}
}
