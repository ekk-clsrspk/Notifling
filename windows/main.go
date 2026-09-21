package main

import (
	"flag"
	"fmt"
	"log"
	"os"

	"github.com/go-toast/toast"
	"github.com/skip2/go-qrcode"
)

func showToast(title, msg string) {
	if title == "" {
		return
	}
	n := toast.Notification{
		AppID:   "Notifling.App",
		Title:   title,
		Message: msg,
	}
	// Best-effort: toasts fail without a Start-Menu shortcut; log either way.
	if err := n.Push(); err != nil {
		log.Printf("toast failed: %v", err)
	}
}

func writeQRFile(authKey, path string) error {
	content := "NOTIFLING:1:" + authKey
	return qrcode.WriteFile(content, qrcode.Medium, 256, path)
}

func openFile(path string) {
	// Best-effort: open PNG with default viewer so the phone can scan it.
	openWithDefaultViewer(path)
}

func main() {
	minimized := flag.Bool("minimized", false, "run without extra console output (for Startup shortcut)")
	showQR := flag.Bool("show-qr", false, "regenerate qr.png and open it, then exit")
	flag.Parse()

	// Log to file as well as console. Ensure dir exists first.
	if err := os.MkdirAll(appDir(), 0o700); err != nil {
		log.Printf("mkdir: %v", err)
	} else if f, err := os.OpenFile(logPath(), os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o600); err == nil {
		defer f.Close()
		log.SetOutput(f)
	} else {
		log.Printf("log file: %v", err)
	}

	cfg, created, err := loadOrCreateConfig()
	if err != nil {
		log.Fatalf("config: %v", err)
	}
	if created || *showQR {
		if err := writeQRFile(cfg.AuthKey, qrPath()); err != nil {
			log.Fatalf("qr: %v", err)
		}
		fmt.Printf("Config: %s\n", configPath())
		fmt.Printf("QR (scan with Notifling app): %s\n", qrPath())
		fmt.Printf("Key (manual entry fallback): %s\n", cfg.AuthKey)
		fmt.Printf("Content: NOTIFLING:1:%s\n", cfg.AuthKey)
		if !*minimized {
			openFile(qrPath())
		}
		if *showQR {
			return
		}
	} else if !*minimized {
		fmt.Printf("Notifling listening on UDP %d as %q\n", cfg.Port, cfg.PCName)
		fmt.Printf("Config: %s  (run with --show-qr to re-show pairing code)\n", configPath())
	}

	if err := serveUDP(cfg); err != nil {
		log.Fatalf("udp: %v", err)
	}
}
