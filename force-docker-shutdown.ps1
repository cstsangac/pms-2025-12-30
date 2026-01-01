# Force Docker Shutdown Script
# Use this when Docker Desktop won't stop or keeps auto-restarting

Write-Host "=== Force Docker Shutdown ===" -ForegroundColor Cyan
Write-Host ""

# 1. Stop all Docker processes
Write-Host "Step 1: Stopping all Docker processes..." -ForegroundColor Yellow
$dockerProcesses = @(
    "Docker Desktop",
    "com.docker.backend",
    "com.docker.proxy",
    "com.docker.cli",
    "vpnkit",
    "dockerd",
    "docker-credential-desktop"
)

foreach ($proc in $dockerProcesses) {
    $processes = Get-Process -Name $proc -ErrorAction SilentlyContinue
    if ($processes) {
        $processes | Stop-Process -Force
        Write-Host "  Stopped: $proc" -ForegroundColor Green
    } else {
        Write-Host "  Not running: $proc" -ForegroundColor Gray
    }
}

Start-Sleep -Seconds 3

# 2. Stop Docker service and prevent auto-restart
Write-Host "`nStep 2: Stopping Docker service..." -ForegroundColor Yellow
$service = Get-Service -Name "com.docker.service" -ErrorAction SilentlyContinue
if ($service -and $service.Status -eq 'Running') {
    Stop-Service -Name "com.docker.service" -Force
    Write-Host "  Docker service stopped" -ForegroundColor Green
} else {
    Write-Host "  Docker service not running" -ForegroundColor Gray
}

# Set service to Manual to prevent auto-restart
try {
    Set-Service -Name "com.docker.service" -StartupType Manual -ErrorAction SilentlyContinue
    Write-Host "  Service set to Manual startup (prevents auto-restart)" -ForegroundColor Green
} catch {
    Write-Host "  Note: Could not set service to Manual (may need Admin rights)" -ForegroundColor Yellow
}

Start-Sleep -Seconds 2

# 3. Shutdown WSL
Write-Host "`nStep 3: Shutting down WSL..." -ForegroundColor Yellow
wsl --shutdown
Start-Sleep -Seconds 3

# 4. Verify WSL is down
$wslRunning = wsl -l --running 2>&1 | Out-String
if ($wslRunning -match "docker-desktop|Ubuntu|kali") {
    Write-Host "  WSL still running, forcing shutdown again..." -ForegroundColor Yellow
    wsl --shutdown
    Start-Sleep -Seconds 5
} else {
    Write-Host "  WSL shutdown complete" -ForegroundColor Green
}

# 5. Verify all stopped
Write-Host "`nStep 4: Verifying shutdown..." -ForegroundColor Yellow
$stillRunning = @()
foreach ($proc in $dockerProcesses) {
    if (Get-Process -Name $proc -ErrorAction SilentlyContinue) {
        $stillRunning += $proc
    }
}

if ($stillRunning.Count -gt 0) {
    Write-Host "  Warning: Some processes still running: $($stillRunning -join ', ')" -ForegroundColor Red
    Write-Host "  Attempting final force kill..." -ForegroundColor Yellow
    foreach ($proc in $stillRunning) {
        Get-Process -Name $proc -ErrorAction SilentlyContinue | Stop-Process -Force
    }
} else {
    Write-Host "  All Docker processes stopped" -ForegroundColor Green
}

Write-Host "`n=== Docker Fully Shutdown ===" -ForegroundColor Green
Write-Host "Docker Desktop is now completely stopped." -ForegroundColor Cyan
Write-Host "You can now safely:" -ForegroundColor Cyan
Write-Host "  - Compact the VHDX file" -ForegroundColor Cyan
Write-Host "  - Perform maintenance tasks" -ForegroundColor Cyan
