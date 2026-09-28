# HexaPass 🔐

A privacy-focused Android password manager designed for secure local storage of passwords, links, and sensitive notes.

HexaPass keeps vault data locally on the device using encrypted storage and does not require a backend, cloud database, or online account.

## ✨ Features

- 🔐 Master password protected vault
- 🔑 Secure password storage
- 🔗 Secure link storage
- 📝 Encrypted secure notes
- 🔎 Search and organization
- 🎲 Password generator
- 📦 Encrypted export and import
- 🔒 Automatic vault locking
- 📋 Secure clipboard clearing
- 🛡️ Screenshot protection with `FLAG_SECURE`
- 🌐 Secure URL validation
- 💾 Local Room SQLite database
- 🚫 No cloud synchronization
- 🚫 No analytics or tracking
- 🚫 No internet/network dependency

## 📊 Entry Limits

HexaPass is intentionally designed as a lightweight password manager.

| Type | Maximum |
|------|---------|
| Passwords | 10 |
| Links | 10 |
| Total | 20 |

The limits are enforced at the application/data layer, not only in the UI.

- Adding an 11th password is rejected.
- Adding an 11th link is rejected.
- Existing entries can still be edited.
- Deleting an entry frees its slot.
- Imports cannot bypass the limits.
- Imports that exceed the limits are rejected without partial import.

## 🔐 Security

HexaPass uses multiple security controls to protect vault data.

### Encryption

- AES-256-GCM authenticated encryption
- Fresh 12-byte IVs for encryption operations
- 128-bit GCM authentication tag
- PBKDF2-HMAC-SHA256 for password-based key derivation
- 100,000 PBKDF2 iterations
- Android Keystore integration where applicable

### Application Security

- Automatic vault locking
- Active encryption key cleared when the vault is locked
- Sensitive password data cleared from memory where applicable
- Ownership-aware clipboard clearing
- Screenshot protection
- Secure URL handling
- Backup protection
- Release R8/minification hardening

## 🏗️ Architecture

HexaPass follows a local-first Android architecture:

```text
┌──────────────────────────┐
│       Android UI         │
│     Jetpack Compose      │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│       ViewModel          │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│     VaultRepository      │
│  Security / Validation   │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│      Room SQLite         │
│   Local Vault Database   │
└──────────────────────────┘
