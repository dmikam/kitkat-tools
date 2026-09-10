# KitKat Tools: Custom Browser & Downloader

A lightweight, reproducible Android toolchain built specifically for **Android 4.4 (KitKat / API 19)** devices. This project provides a minimal WebKit browser paired with a specialized external downloader to bypass legacy Android OS download limitations.

---

## 🎯 Purpose & The Origin of the Problem

### The Legacy Constraint
Running web browsers on Android 4.4 (API 19) presents severe operational hurdles:
1. **Broken Native DownloadManager:** KitKat’s built-in `DownloadManager` fails on modern HTTPS endpoints that enforce updated TLS certificates, strict content-disposition headers, or complex redirect chains.
2. **JCenter & Dependency Rot:** Existing open-source browsers for KitKat (like original Lightning Browser forks) fail to compile on modern build pipelines due to the permanent decommissioning of repository hosts like JCenter, dead annotation processors, and incompatible legacy Gradle plugins.
3. **Heavy Web Engines:** Modern browsers (Chrome, Firefox) no longer support API 19 or consume far more RAM than low-spec KitKat hardware can handle.

### The Solution
Instead of fighting abandoned 2014-era dependencies, this repository implements a zero-dependency, native `WebView` browser and offloads **all file downloads to an external `KitKatDownloader` via Android `ACTION_VIEW` intents**.

---

## 🏗️ System Architecture


```

[ Web Page / Direct Link ]
│
▼
┌──────────────────────────┐
│      KitKat Browser      │
│  - Intercepts downloads  │
│  - Parses extensions     │
│  - Handles context menus │
└──────────┬───────────────┘
│
│  Sends ACTION_VIEW Intent
│  (URL, MimeType, User-Agent, Content-Disposition)
▼
┌──────────────────────────┐
│     KitKatDownloader     │
│  - Custom HTTP client    │
│  - TLS-compatible fetch  │
│  - Direct storage save   │
└──────────────────────────┘

```

---

## 🚀 Key Features

### KitKat Browser
- **Explicit Intent Dispatch:** All download triggers (HTTP header attachments, long-press context menus, and non-HTML direct links) bypass `DownloadManager` and dispatch a system chooser.
- **Inverted Extension Logic:** Treats all files as downloadable by default, excluding only standard web page extensions (`.html`, `.php`, `.asp`, `.jsp`, etc.).
- **KitKat UI Polish:** Zero window transition animations, soft-keyboard auto-suppression, horizontal loading progress bar, and address bar clear controls.
- **Zero Third-Party Dependencies:** Built entirely with AndroidX / Support libraries for guaranteed reproducible builds without remote maven rot.

### KitKatDownloader (Target Companion)
- Receives standard `Intent.ACTION_VIEW` payloads.
- Preserves pass-through HTTP `User-Agent` and `Content-Disposition` metadata extracted during browser interception.

---

## 🛠️ Build & Environment Setup

The build environment is fully dockerized to ensure deterministic builds across modern developer machines without altering host SDKs.

### Prerequisites
- Docker & Docker Compose
- Java 8 source compatibility (configured in Gradle)

### Building the Project

1. **Build the Docker Builder Image:**
   ```bash
   docker compose build android-builder

```

2. **Compile the App:**
```bash
./build.sh kitkat-browser

```


3. The generated APK will be output to:
`projects/kitkat-browser/app/build/outputs/apk/`

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.
