# Wrapper Windows pour `tauri dev`.
# Usage (depuis desktop-web/) :
#   .\dev.ps1
#   npm run tauri:dev:win
#
# Prérequis Windows :
#   - Node.js 18+
#   - Rust (rustup) + Visual Studio Build Tools (C++)
#   - WebView2 Runtime
#   - mpv dans le PATH (lecture audio)
#   - npm install (une fois)

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

if (-not (Test-Path "node_modules")) {
  Write-Host "Installation des dépendances npm..."
  npm install
}

Write-Host "Lancement Tauri dev (Windows)..."
npm run tauri dev
