# Disable Docker Auto-Start
# Prevents Docker Desktop from restarting automatically

Write-Host "=== Disable Docker Auto-Start ===" -ForegroundColor Cyan
Write-Host ""

# 1. Disable Docker Desktop startup
Write-Host "Step 1: Disabling Docker Desktop from Windows startup..." -ForegroundColor Yellow

$dockerStartupPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
$dockerStartupName = "Docker Desktop"

try {
    $existing = Get-ItemProperty -Path $dockerStartupPath -Name $dockerStartupName -ErrorAction SilentlyContinue
    if ($existing) {
        Remove-ItemProperty -Path $dockerStartupPath -Name $dockerStartupName -Force
        Write-Host "  Removed Docker Desktop from startup registry" -ForegroundColor Green
    } else {
        Write-Host "  Docker Desktop not in startup registry" -ForegroundColor Gray
    }
} catch {
    Write-Host "  Could not modify registry: $($_.Exception.Message)" -ForegroundColor Yellow
}

# 2. Disable Docker Desktop service auto-start
Write-Host "`nStep 2: Setting Docker service to Manual start..." -ForegroundColor Yellow

try {
    $service = Get-Service -Name "com.docker.service" -ErrorAction SilentlyContinue
    if ($service) {
        Set-Service -Name "com.docker.service" -StartupType Manual
        Write-Host "  Docker service set to Manual startup" -ForegroundColor Green
    } else {
        Write-Host "  Docker service not found" -ForegroundColor Gray
    }
} catch {
    Write-Host "  Could not modify service: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "  You may need to run this script as Administrator" -ForegroundColor Yellow
}

# 3. Check Task Scheduler for Docker tasks
Write-Host "`nStep 3: Checking Task Scheduler for Docker tasks..." -ForegroundColor Yellow

try {
    $dockerTasks = Get-ScheduledTask | Where-Object { $_.TaskName -like "*Docker*" }
    if ($dockerTasks) {
        Write-Host "  Found Docker scheduled tasks:" -ForegroundColor Yellow
        foreach ($task in $dockerTasks) {
            Write-Host "    - $($task.TaskName): $($task.State)" -ForegroundColor Gray
            if ($task.State -eq "Ready" -or $task.State -eq "Running") {
                try {
                    Disable-ScheduledTask -TaskName $task.TaskName -ErrorAction SilentlyContinue
                    Write-Host "      Disabled task: $($task.TaskName)" -ForegroundColor Green
                } catch {
                    Write-Host "      Could not disable (may need Admin): $($task.TaskName)" -ForegroundColor Yellow
                }
            }
        }
    } else {
        Write-Host "  No Docker scheduled tasks found" -ForegroundColor Gray
    }
} catch {
    Write-Host "  Could not check scheduled tasks: $($_.Exception.Message)" -ForegroundColor Yellow
}

# 4. Disable Docker Desktop auto-update
Write-Host "`nStep 4: Disabling Docker Desktop auto-update (reduces auto-starts)..." -ForegroundColor Yellow

$dockerSettingsPath = "$env:APPDATA\Docker\settings.json"
if (Test-Path $dockerSettingsPath) {
    try {
        $settings = Get-Content $dockerSettingsPath -Raw | ConvertFrom-Json
        $settings | Add-Member -NotePropertyName "autoStart" -NotePropertyValue $false -Force
        $settings | Add-Member -NotePropertyName "openUIOnStartupDisabled" -NotePropertyValue $true -Force
        $settings | ConvertTo-Json -Depth 10 | Set-Content $dockerSettingsPath
        Write-Host "  Updated Docker settings.json" -ForegroundColor Green
    } catch {
        Write-Host "  Could not modify settings.json: $($_.Exception.Message)" -ForegroundColor Yellow
    }
} else {
    Write-Host "  Docker settings.json not found at $dockerSettingsPath" -ForegroundColor Gray
}

# 5. Current status
Write-Host "`nStep 5: Current Docker status..." -ForegroundColor Yellow

$dockerProcesses = Get-Process -Name "Docker Desktop" -ErrorAction SilentlyContinue
if ($dockerProcesses) {
    Write-Host "  Docker Desktop is currently running ($($dockerProcesses.Count) processes)" -ForegroundColor Cyan
    Write-Host "  To stop it now, run: .\force-docker-shutdown.ps1" -ForegroundColor Yellow
} else {
    Write-Host "  Docker Desktop is not running" -ForegroundColor Green
}

Write-Host "`n=== Auto-Start Disabled ===" -ForegroundColor Green
Write-Host ""
Write-Host "Docker Desktop will no longer start automatically on:" -ForegroundColor Cyan
Write-Host "  - Windows startup" -ForegroundColor Gray
Write-Host "  - System reboot" -ForegroundColor Gray
Write-Host "  - Service restart" -ForegroundColor Gray
Write-Host ""
Write-Host "To start Docker manually when needed:" -ForegroundColor Cyan
Write-Host "  Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe'" -ForegroundColor Yellow
Write-Host ""
Write-Host "To re-enable auto-start later, run Docker Desktop and:" -ForegroundColor Cyan
Write-Host "  Settings > General > Start Docker Desktop when you log in" -ForegroundColor Gray
