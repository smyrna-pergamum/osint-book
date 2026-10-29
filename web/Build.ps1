param([switch]$BundleRuntime)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { $env:JAVA_HOME = Split-Path (Split-Path $javaCommand.Source) }
    else {
        $candidate = Get-ChildItem (Join-Path $env:LOCALAPPDATA 'Programs/Java') -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Where-Object { Test-Path (Join-Path $_.FullName 'bin/javac.exe') } | Select-Object -First 1
        if ($candidate) { $env:JAVA_HOME = $candidate.FullName }
        else { throw 'Derleme icin JDK 21+ kurun ve JAVA_HOME ayarlayin.' }
    }
}
Push-Location (Split-Path $PSScriptRoot)
try {
    # llama.cpp b11934: CPU build for every server, Vulkan build for AMD, NVIDIA and Intel GPUs.
    $runtimes = @(
        @{ Folder = 'build/llama-runtime'; Archive = 'llama-b11934-bin-win-cpu-x64.zip'; Sha = 'a66878ddf32a2d9349bb66d3fb8a1d6d2a8187c249900276693ee105b66b2dc1' },
        @{ Folder = 'build/llama-runtime-gpu'; Archive = 'llama-b11934-bin-win-vulkan-x64.zip'; Sha = 'd5a4af38583ddeebbe7b7f21e91765ea2a1463990a3039423f683178731a9114' }
    )
    foreach ($r in $runtimes) {
        $aiRuntime = Join-Path $PSScriptRoot $r.Folder
        if (Test-Path (Join-Path $aiRuntime 'llama-server.exe')) { continue }
        $archivePath = Join-Path $PSScriptRoot ('build/' + $r.Archive)
        New-Item -ItemType Directory -Force -Path (Split-Path $archivePath) | Out-Null
        if (-not (Test-Path $archivePath)) { Invoke-WebRequest -Uri ('https://github.com/ggml-org/llama.cpp/releases/download/b11934/' + $r.Archive) -OutFile $archivePath }
        if ((Get-FileHash $archivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $r.Sha) { throw "AI runtime checksum mismatch: $($r.Archive)" }
        Expand-Archive -LiteralPath $archivePath -DestinationPath $aiRuntime -Force
        Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/ggml-org/llama.cpp/8542e9435/LICENSE' -OutFile (Join-Path $aiRuntime 'LICENSE')
    }
    & .\gradlew.bat -p web test installDist --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Derleme veya test basarisiz.' }
    if ($BundleRuntime) {
        $runtime = Join-Path $PSScriptRoot 'build/install/osint-book-web/runtime'
        if (-not (Test-Path $runtime)) {
            & (Join-Path $env:JAVA_HOME 'bin/jlink.exe') --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.management --strip-debug --no-header-files --no-man-pages --output $runtime
            if ($LASTEXITCODE -ne 0) { throw 'Java calisma ortami olusturulamadi.' }
        }
    }
    $output = Join-Path $PSScriptRoot 'build/distributions'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $archive = Join-Path $output 'OSINT-Book-Web-Windows.zip'
    # Runtime data must never enter a distribution or release archive.
    $source = Join-Path $PSScriptRoot 'build/install/osint-book-web'
    if (Test-Path (Join-Path $source 'data')) { throw 'Paket klasorunde kullanici verisi var. Temiz bir installDist klasorunde paketleyin.' }
    Compress-Archive -Path $source -DestinationPath $archive -Force
    Write-Host "Hazir: $archive"
} finally { Pop-Location }
