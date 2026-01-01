# Delete Docker VHDX File (Nuclear Option)
# This completely removes Docker's data and frees up ALL space immediately

Write-Host "=== Delete Docker VHDX File ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "WARNING: This will delete ALL Docker data:" -ForegroundColor Red
Write-Host "  - All images" -ForegroundColor Yellow
Write-Host "  - All containers" -ForegroundColor Yellow
Write-Host "  - All volumes" -ForegroundColor Yellow
Write-Host "  - All networks" -ForegroundColor Yellow
Write-Host "  - Build cache" -ForegroundColor Yellow
Write-Host ""
Write-Host "This will FREE UP ~160 GB immediately!" -ForegroundColor Green
Write-Host "Docker will recreate a fresh VHDX (~2-5 GB) next time you start it." -ForegroundColor Cyan
Write-Host ""

$response = Read-Host "Are you SURE you want to delete all Docker data? (type 'yes' to confirm)"
if ($response -ne 'yes') {
    Write-Host "`nCancelled. No files were deleted." -ForegroundColor Yellow
    exit 0
}

$vhdxPath = "C:\Users\sings\AppData\Local\Docker\wsl\disk\docker_data.vhdx"

# 1. Force Docker shutdown
Write-Host "`nStep 1: Forcing Docker shutdown..." -ForegroundColor Yellow
& "$PSScriptRoot\force-docker-shutdown.ps1"

Start-Sleep -Seconds 3

# 2. Verify file exists
if (-not (Test-Path $vhdxPath)) {
    Write-Host "`nVHDX file not found at: $vhdxPath" -ForegroundColor Red
    Write-Host "It may have already been deleted or is in a different location." -ForegroundColor Yellow
    exit 1
}

# 3. Show current size
$sizeBefore = (Get-Item $vhdxPath).Length / 1GB
Write-Host "`nCurrent VHDX size: $([math]::Round($sizeBefore, 2)) GB" -ForegroundColor Yellow

# 4. Delete the file
Write-Host "`nStep 2: Deleting VHDX file..." -ForegroundColor Yellow
try {
    Remove-Item -Path $vhdxPath -Force
    Write-Host "VHDX file deleted successfully!" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Could not delete VHDX file: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "`nTroubleshooting:" -ForegroundColor Yellow
    Write-Host "  1. Make sure Docker is completely stopped" -ForegroundColor Gray
    Write-Host "  2. Make sure WSL is shutdown: wsl --shutdown" -ForegroundColor Gray
    Write-Host "  3. Reboot Windows and try again" -ForegroundColor Gray
    exit 1
}

# 5. Verify deletion
Start-Sleep -Seconds 2
if (Test-Path $vhdxPath) {
    Write-Host "`nWARNING: File still exists after deletion!" -ForegroundColor Red
    exit 1
}

Write-Host "`n=== VHDX Deleted Successfully ===" -ForegroundColor Green
Write-Host ""
Write-Host "Space freed: ~$([math]::Round($sizeBefore, 2)) GB" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Start Docker Desktop (it will recreate a fresh VHDX)" -ForegroundColor Gray
Write-Host "  2. Pull only the images you need" -ForegroundColor Gray
Write-Host "  3. Rebuild your containers: docker-compose up -d --build" -ForegroundColor Gray
Write-Host ""
Write-Host "The new VHDX will start at ~2-5 GB instead of 160 GB!" -ForegroundColor Green
