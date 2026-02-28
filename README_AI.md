# README_AI — Persistent Architectural Memory

## Project Overview
- App name: SmartDL
- Type: Full 1DM-style Android browser + download manager
- Priority: Backend correctness and stability over UI
- minSdk = 26
- targetSdk = 34

## Architecture Summary
- Single-tab in-app WebView browser
- Full header capture (cookies, referer, user-agent)
- Per-domain session defaults
- Per-download header storage
- Unified FIFO download queue
- Single Foreground Service managing all downloads
- Parallel HTTP engine (chunk-based, resumable)
- yt-dlp engine (domain cookie export, ARM64)
- Room persistence (downloads, chunks, headers, history)
- Temp private cache during download
- MediaStore move to public Downloads on completion
- Orphan temp detection on startup (manual cleanup)

## Storage Rules
- Never write partial files to public Downloads
- Temp file naming: dl_{id}_{originalName}.part
- yt-dlp naming: dl_{id}_%(title)s.%(ext)s

## CI Automation
- GitHub private repo
- GitHub Actions builds APK
- `auto-fix.sh` handles:
  - commit
  - push
  - monitor CI
  - fetch logs
  - call codex to fix
  - repeat until success

## Strict Rules For Future AI Sessions
- Do not simplify architecture
- Do not remove header handling
- Do not change storage strategy
- Do not download full Gradle distribution locally
- Fix only relevant errors when CI fails
- Preserve clean architecture layers

## When Resuming Work
- If you are a new AI session:
  - Read this file fully
  - Inspect project structure
  - Check latest commit
  - Continue from last stable state

