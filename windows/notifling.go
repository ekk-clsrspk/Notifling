package main

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"os"
	"path/filepath"
	"time"
)

// Packet is the v1 wire format. See docs/protocol.md.
type Packet struct {
	V       int    `json:"v"`
	Type    string `json:"type"`
	KeyHash string `json:"key_hash"`
	PhoneID string `json:"phone_id,omitempty"`
	Port    int    `json:"port,omitempty"`
	PCName  string `json:"pc_name,omitempty"`
	ID      string `json:"id,omitempty"`
	Pkg     string `json:"pkg,omitempty"`
	Title   string `json:"title,omitempty"`
	Text    string `json:"text,omitempty"`
	Ts      int64  `json:"ts,omitempty"`
}

// Config lives in %APPDATA%\Notifling\config.json
type Config struct {
	AuthKey string `json:"auth_key"` // base64url of 32 random bytes
	Port    int    `json:"port"`
	PCName  string `json:"pc_name"`
}

func appDir() string {
	if d := os.Getenv("APPDATA"); d != "" {
		return filepath.Join(d, "Notifling")
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".notifling")
}

func configPath() string { return filepath.Join(appDir(), "config.json") }
func qrPath() string     { return filepath.Join(appDir(), "qr.png") }
func logPath() string    { return filepath.Join(appDir(), "notifling.log") }

func defaultPCName() string {
	if h, err := os.Hostname(); err == nil && h != "" {
		return h
	}
	return "Windows-PC"
}

func loadOrCreateConfig() (*Config, bool, error) {
	dir := appDir()
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, false, err
	}
	p := configPath()
	data, err := os.ReadFile(p)
	if err == nil {
		var c Config
		if err := json.Unmarshal(data, &c); err != nil {
			return nil, false, fmt.Errorf("bad config %s: %w", p, err)
		}
		if c.Port == 0 {
			c.Port = 51234
		}
		if c.PCName == "" {
			c.PCName = defaultPCName()
		}
		return &c, false, nil
	}
	if !os.IsNotExist(err) {
		return nil, false, err
	}
	// First run: generate 32 random bytes.
	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return nil, false, err
	}
	c := &Config{
		AuthKey: base64.RawURLEncoding.EncodeToString(raw),
		Port:    51234,
		PCName:  defaultPCName(),
	}
	out, _ := json.MarshalIndent(c, "", "  ")
	if err := os.WriteFile(p, out, 0o600); err != nil {
		return nil, false, err
	}
	return c, true, nil
}

// keyHash returns hex(SHA256(key_bytes)). Accepts base64url or raw string.
func keyHash(authKey string) string {
	raw, err := base64.RawURLEncoding.DecodeString(authKey)
	if err != nil {
		raw = []byte(authKey)
	}
	sum := sha256.Sum256(raw)
	return hex.EncodeToString(sum[:])
}

func validKeyHash(got, want string) bool {
	if len(got) != len(want) {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(got), []byte(want)) == 1
}

// dedup drops replays/retries: same NOTIFY id within window.
type dedup struct {
	seen map[string]time.Time
	ttl  time.Duration
}

func newDedup(ttl time.Duration) *dedup { return &dedup{seen: map[string]time.Time{}, ttl: ttl} }

func (d *dedup) dup(id string) bool {
	if id == "" {
		return false
	}
	now := time.Now()
	if t, ok := d.seen[id]; ok && now.Sub(t) < d.ttl {
		return true
	}
	d.seen[id] = now
	// opportunistic cleanup
	if len(d.seen) > 10000 {
		for k, t := range d.seen {
			if now.Sub(t) > d.ttl {
				delete(d.seen, k)
			}
		}
	}
	return false
}

func trim(s string, n int) string {
	r := []rune(s)
	if len(r) > n {
		return string(r[:n])
	}
	return s
}

func handlePacket(p Packet, wantHash, pcName string, dd *dedup, from *net.UDPAddr) (reply *Packet, toastTitle, toastMsg string) {
	if p.V != 1 || !validKeyHash(p.KeyHash, wantHash) {
		log.Printf("drop %s from %s: bad version or key", p.Type, from)
		return nil, "", ""
	}
	switch p.Type {
	case "DISCOVER":
		return &Packet{V: 1, Type: "OFFER", KeyHash: wantHash, PCName: pcName}, "", ""
	case "NOTIFY":
		if dd.dup(p.ID) {
			log.Printf("dup NOTIFY %s from %s", p.ID, from)
			return nil, "", ""
		}
		title := trim(p.Title, 256)
		if title == "" {
			title = p.Pkg
		}
		if title == "" {
			title = "Phone notification"
		}
		msg := trim(p.Text, 512)
		log.Printf("NOTIFY from %s [%s] %s: %s", from, p.Pkg, title, msg)
		return nil, title, msg
	default:
		log.Printf("unknown type %q from %s", p.Type, from)
		return nil, "", ""
	}
}
