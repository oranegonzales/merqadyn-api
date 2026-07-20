[CmdletBinding()]
param(
    [switch]$Detach,
    [switch]$SkipBuild,
    [ValidateRange(15, 300)]
    [int]$EngineTimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Test-DockerEngine {
    & docker info --format "{{.ServerVersion}}" *> $null
    return $LASTEXITCODE -eq 0
}

function Start-DockerEngine {
    Write-Host "Docker Desktop is installed, but its Linux engine is not running. Starting it now..."

    & docker desktop start --timeout $EngineTimeoutSeconds *> $null
    if ($LASTEXITCODE -ne 0) {
        $desktopPaths = @(
            (Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"),
            (Join-Path $env:LOCALAPPDATA "Docker\Docker Desktop.exe")
        )
        $desktopPath = $desktopPaths | Where-Object { Test-Path $_ } | Select-Object -First 1

        if (-not $desktopPath) {
            throw "Docker Desktop could not be started. Open Docker Desktop from the Windows Start menu, wait for it to finish starting, and run this command again."
        }

        Start-Process -FilePath $desktopPath
    }

    $deadline = (Get-Date).AddSeconds($EngineTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerEngine) {
            return
        }
        Start-Sleep -Seconds 3
    }

    throw "Docker Desktop did not become ready within $EngineTimeoutSeconds seconds. Open Docker Desktop, use Troubleshoot > Restart, and run this command again."
}

function New-RandomPassword {
    $bytes = New-Object byte[] 24
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return ([BitConverter]::ToString($bytes)).Replace("-", "").ToLowerInvariant()
}

function Initialize-EnvironmentFile {
    param([string]$Path)

    if (Test-Path $Path) {
        return
    }

    $databasePassword = New-RandomPassword
    $adminPassword = New-RandomPassword
    $content = @"
DATABASE_USER=merqadyn
DATABASE_PASSWORD=$databasePassword
MERQADYN_ADMIN_USER=merqadyn
MERQADYN_ADMIN_PASSWORD=$adminPassword
"@
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [IO.File]::WriteAllText($Path, $content, $encoding)
    Write-Host "Created .env with unique local passwords."
}

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) {
    throw "Docker is not installed or is not on PATH. Install Docker Desktop for Windows, reopen PowerShell, and run this command again: https://docs.docker.com/desktop/setup/install/windows-install/"
}

& docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose v2 is unavailable. Update Docker Desktop, reopen PowerShell, and run this command again."
}

if (-not (Test-DockerEngine)) {
    Start-DockerEngine
}

$engineType = (& docker info --format "{{.OSType}}" 2>$null).Trim()
if ($LASTEXITCODE -ne 0 -or $engineType -ne "linux") {
    throw "Merqadyn requires Linux containers. In Docker Desktop, switch to Linux containers and run this command again."
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repositoryRoot
try {
    Initialize-EnvironmentFile -Path (Join-Path $repositoryRoot ".env")

    & docker compose config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose rejected the project configuration."
    }

    $arguments = @("compose", "up")
    if (-not $SkipBuild) {
        $arguments += "--build"
    }
    if ($Detach) {
        $arguments += "--detach"
        $arguments += "--wait"
        $arguments += "--wait-timeout"
        $arguments += $EngineTimeoutSeconds
    }

    & docker @arguments
    if ($LASTEXITCODE -ne 0) {
        $composeExitCode = $LASTEXITCODE
        & docker compose ps --all
        & docker compose logs --no-color --tail 200 app
        exit $composeExitCode
    }

    if ($Detach) {
        Write-Host "Merqadyn is starting at http://localhost:8080"
        Write-Host "Check readiness with: curl.exe http://localhost:8080/actuator/health"
    }
}
finally {
    Pop-Location
}
