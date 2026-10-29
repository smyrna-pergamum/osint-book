param([switch]$LAN)
$ErrorActionPreference = 'Stop'
$package = $PSScriptRoot
if (-not (Test-Path (Join-Path $package 'bin/osint-book-web.bat'))) {
    $package = Join-Path (Split-Path $PSScriptRoot) 'build/install/osint-book-web'
}
$launcher = Join-Path $package 'bin/osint-book-web.bat'
if (-not (Test-Path $launcher)) { throw 'Run web/Build.ps1 first, or extract the Windows ZIP package.' }
if (Test-Path (Join-Path $package 'runtime/bin/java.exe')) {
    $env:JAVA_HOME = Join-Path $package 'runtime'
} elseif (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { $env:JAVA_HOME = Split-Path (Split-Path $javaCommand.Source) }
    else {
        $javaCandidates = Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Programs/Java') -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending
        $javaFolder = $javaCandidates | Where-Object { Test-Path (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1
        if ($javaFolder) { $env:JAVA_HOME = $javaFolder.FullName }
        else { throw 'Java 21+ is required. Set JAVA_HOME to your Java installation.' }
    }
}
if (-not $env:OSINT_DATA) { $env:OSINT_DATA = Join-Path $package 'data' }
$env:OSINT_HOST = if ($LAN) { '0.0.0.0' } else { '127.0.0.1' }
if ($LAN) {
    Write-Host 'LAN mode: use the user panel port shown below with the server IPv4 address.'
    Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -ne '127.0.0.1' -and $_.IPAddress -notlike '169.254.*' } | ForEach-Object { Write-Host "Server IPv4: $($_.IPAddress)" }
    Write-Host 'Allow only Private networks in Windows Firewall. Do not forward the port on your router.'
    Write-Host 'LAN connections use HTTP. Use only on a trusted local network.'
}
Write-Host 'First startup asks for both ports and an administrator account. Press Ctrl+C to stop.'
Push-Location $package
try { & $launcher }
finally { Pop-Location; $env:OSINT_PASSWORD = $null }
