param([Parameter(Mandatory=$true)][ValidatePattern('^[a-p]{32}$')][string]$ExtensionId)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$appPath = Join-Path $projectRoot 'dist\EzSocialToVideo\EzSocialNative.exe'
if (-not (Test-Path -LiteralPath $appPath)) { throw 'Build the Windows app first: scripts\Build-Windows.ps1' }
$manifestPath = Join-Path (Split-Path $appPath -Parent) 'native-host.json'
@{
    name = 'com.ezsocial.video'
    description = 'EzSocial video downloader'
    path = $appPath
    type = 'stdio'
    allowed_origins = @("chrome-extension://$ExtensionId/")
} | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding ascii
foreach ($browserKey in @('Google\Chrome', 'Microsoft\Edge')) {
    $key = "HKCU:\Software\$browserKey\NativeMessagingHosts\com.ezsocial.video"
    New-Item -Path $key -Force | Out-Null
    Set-Item -Path $key -Value $manifestPath
}
Write-Host 'Registered for Edge and Chrome. Keep the app in its current folder.'
