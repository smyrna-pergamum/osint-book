$package = $PSScriptRoot
if (-not $env:OSINT_DATA) { $env:OSINT_DATA = Join-Path $package 'data' }
$port = $env:OSINT_ADMIN_PORT
if (-not $port) {
    $file = Join-Path $env:OSINT_DATA 'ports.properties'
    if (Test-Path $file) { $line = Get-Content $file | Where-Object { $_ -match '^admin=' } | Select-Object -First 1; if ($line) { $port = $line.Substring(6).Trim() } }
}
if (-not $port) { $port = '8081' }
if ($port -notmatch '^\d{1,5}$' -or [int]$port -lt 1 -or [int]$port -gt 65535) { throw 'Invalid admin port.' }
Start-Process "http://localhost:$port/"
