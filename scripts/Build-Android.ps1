param([string]$AndroidSdk = $env:ANDROID_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $AndroidSdk) { throw 'Pass -AndroidSdk with your Android SDK folder, or set ANDROID_HOME. Install SDK platform 35 and build-tools 35.0.0.' }
$resolvedSdk = (Resolve-Path -LiteralPath $AndroidSdk).Path.Replace('\', '/')
Set-Content -LiteralPath (Join-Path $projectRoot 'android\local.properties') -Value "sdk.dir=$resolvedSdk" -Encoding ascii
Push-Location (Join-Path $projectRoot 'android')
try {
    & .\gradlew.bat assembleCompact
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    $outputPath = Join-Path $projectRoot 'dist'
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
    Copy-Item -LiteralPath 'app\build\outputs\apk\compact\app-compact.apk' -Destination (Join-Path $outputPath 'EzSocialToVideo-Android.apk')
    foreach ($abi in @('arm64-v8a', 'armeabi-v7a', 'x86_64')) {
        $outdated = Join-Path $outputPath "EzSocialToVideo-Android-$abi.apk"
        if (Test-Path -LiteralPath $outdated) { Remove-Item -LiteralPath $outdated }
    }
    Write-Host 'Ready: dist\EzSocialToVideo-Android.apk (all Android architectures, no native runtimes).'
} finally { Pop-Location }
