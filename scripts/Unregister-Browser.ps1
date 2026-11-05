$ErrorActionPreference = 'Stop'
foreach ($browserKey in @('Google\Chrome', 'Microsoft\Edge')) {
    $key = "HKCU:\Software\$browserKey\NativeMessagingHosts\com.ezsocial.video"
    if (Test-Path -LiteralPath $key) { Remove-Item -LiteralPath $key }
}
Write-Host 'Browser integration removed. You can now delete the portable app folder.'
