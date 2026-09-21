package main

import (
	"encoding/json"
	"log"
	"net"
	"os/exec"
	"time"
)

func openWithDefaultViewer(path string) {
	// Windows only; exe targets Windows.
	_ = exec.Command("rundll32", "url.dll,FileProtocolHandler", path).Start()
}

func serveUDP(cfg *Config) error {
	wantHash := keyHash(cfg.AuthKey)
	addr := &net.UDPAddr{IP: net.IPv4zero, Port: cfg.Port}
	conn, err := net.ListenUDP("udp4", addr)
	if err != nil {
		return err
	}
	defer conn.Close()
	log.Printf("listening on UDP %d as %q", cfg.Port, cfg.PCName)

	dd := newDedup(5 * time.Minute)
	buf := make([]byte, 8192)
	for {
		n, from, err := conn.ReadFromUDP(buf)
		if err != nil {
			log.Printf("read: %v", err)
			continue
		}
		var p Packet
		if err := json.Unmarshal(buf[:n], &p); err != nil {
			log.Printf("bad packet from %s: %v", from, err)
			continue
		}
		reply, title, msg := handlePacket(p, wantHash, cfg.PCName, dd, from)
		if reply != nil {
			out, _ := json.Marshal(reply)
			// Reply to the phone's port: explicit field wins, else source port.
			dest := &net.UDPAddr{IP: from.IP, Port: from.Port}
			if p.Port > 0 && p.Port < 65536 {
				dest.Port = p.Port
			}
			if _, err := conn.WriteToUDP(out, dest); err != nil {
				log.Printf("reply OFFER to %s: %v", dest, err)
			}
			continue
		}
		if title != "" {
			showToast(title, msg)
		}
	}
}
