$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $destination = [IO.Path]::GetFullPath((Join-Path $projectRoot 'dist\EzSocialToVideo'))
    $expected = [IO.Path]::GetFullPath("$projectRoot\dist\EzSocialToVideo")
    if ($destination -ne $expected) { throw 'Unexpected Windows output directory' }
    $activeApps = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -like 'EzSocial*' -and $_.ExecutablePath -and $_.ExecutablePath.StartsWith($destination + '\', [StringComparison]::OrdinalIgnoreCase)
    })
    if ($activeApps.Count) { throw 'EzSocial is running. Finish the active download or close the app before rebuilding; the installed files were left untouched.' }
    $manifest = $null
    $manifestPath = Join-Path $destination 'native-host.json'
    if (Test-Path -LiteralPath $manifestPath) { $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json }
    & .\android\gradlew.bat -p android :desktop:installDist :engine:test
    if ($LASTEXITCODE -ne 0) { throw 'Java build/tests failed' }
    $staging = Join-Path $projectRoot ('build\windows-native-' + [Guid]::NewGuid().ToString('N'))
    $launcherFile = Join-Path $projectRoot 'build\native-launcher.properties'
    @('win-console=true', 'arguments=--native-host') | Set-Content -LiteralPath $launcherFile -Encoding ascii
    & jpackage --type app-image --name EzSocialToVideo --app-version 2.0.0 --input desktop\build\install\desktop\lib --main-jar desktop.jar --main-class com.ezsocial.desktop.App --dest $staging --add-modules java.base,java.desktop,java.logging,java.xml,java.scripting,jdk.crypto.ec,java.net.http,java.management,jdk.unsupported --jlink-options '--strip-debug --no-man-pages --no-header-files --compress=2' --java-options '-Dfile.encoding=UTF-8' --add-launcher "EzSocialNative=$launcherFile"
    if ($LASTEXITCODE -ne 0) { throw 'Windows packaging failed' }
    if (Test-Path -LiteralPath $destination) { Remove-Item -LiteralPath $destination -Recurse -Force }
    New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $staging 'EzSocialToVideo') -Destination $destination -Recurse
    if ($manifest) {
        $manifest.path = Join-Path $destination 'EzSocialNative.exe'
        [IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))
    }
    Write-Host 'Ready: dist\EzSocialToVideo\EzSocialToVideo.exe (Java, no Python)'
} finally { Pop-Location }
