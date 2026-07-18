# SoundGroove desktop — lance le mode dev Tauri (Windows)
# Prérequis : Rust, Node, WebView2, et idéalement mpv dans le PATH.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
  Write-Error "npm introuvable. Installe Node.js puis réessaie."
}

if (-not (Test-Path "node_modules")) {
  npm install
}

Write-Host "SoundGroove desktop — tauri dev" -ForegroundColor Cyan
npm run tauri:dev:win
