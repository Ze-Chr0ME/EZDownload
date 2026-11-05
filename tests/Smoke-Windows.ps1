param([string]$Url = 'https://www.w3schools.com/html/mov_bbb.mp4')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$tempRoot = Join-Path $env:TEMP ('ezsocial-smoke-' + [Guid]::NewGuid().ToString('N'))
$settingsPath = Join-Path $tempRoot 'EzSocialToVideo\settings.json'
New-Item -ItemType Directory -Path (Split-Path $settingsPath -Parent) -Force | Out-Null
@{folder=(Join-Path $tempRoot 'videos');quality='720p'} | ConvertTo-Json | Set-Content -LiteralPath $settingsPath -Encoding ascii
$start = New-Object Diagnostics.ProcessStartInfo
$start.FileName = Join-Path $projectRoot 'dist\EzSocialToVideo\EzSocialNative.exe'
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardInput = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$start.EnvironmentVariables['APPDATA'] = $tempRoot
$start.EnvironmentVariables.Remove('_JAVA_OPTIONS')
$process = [Diagnostics.Process]::Start($start)
function Read-Exact($stream, [int]$size) {
    $buffer = New-Object byte[] $size
    $offset = 0
    while ($offset -lt $size) {
        $task = $stream.ReadAsync($buffer, $offset, $size - $offset)
        if (-not $task.Wait(45000)) { throw 'Timed out waiting for native-host feedback' }
        $count = $task.Result
        if ($count -eq 0) { throw 'Native host closed before completing the protocol' }
        $offset += $count
    }
    return ,$buffer
}
try {
    $payload = [Text.Encoding]::UTF8.GetBytes((@{url=$Url;stream=$true;reveal=$false} | ConvertTo-Json -Compress))
    $process.StandardInput.BaseStream.Write([BitConverter]::GetBytes([int]$payload.Length),0,4)
    $process.StandardInput.BaseStream.Write($payload,0,$payload.Length)
    $process.StandardInput.BaseStream.Flush()
    $progressCount = 0
    while ($true) {
        $header = Read-Exact $process.StandardOutput.BaseStream 4
        $size = [BitConverter]::ToInt32($header,0)
        if ($size -le 0 -or $size -gt 1048576) { throw "Invalid response length: $size" }
        $body = Read-Exact $process.StandardOutput.BaseStream $size
        $message = [Text.Encoding]::UTF8.GetString($body) | ConvertFrom-Json
        if ($message.type -eq 'progress') { $progressCount++; Write-Host "$($message.percent)% $($message.message)" }
        if ($message.type -eq 'done') {
            if (-not $message.ok) { throw $message.error }
            if (-not (Test-Path -LiteralPath $message.path)) { throw 'Reported video does not exist' }
            $length = (Get-Item -LiteralPath $message.path).Length
            if ($length -lt 1000) { throw 'Video file is too small' }
            if ($progressCount -lt 2) { throw 'No live progress was received' }
            Write-Host "Verified packaged Java native host: $progressCount progress messages; saved $length bytes."
            break
        }
    }
    if (-not $process.WaitForExit(15000)) { throw 'Native host did not exit after completion' }
    if ($process.ExitCode -ne 0) { throw "Native host exited with code $($process.ExitCode)" }
} finally {
    if (-not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
    # All smoke-test state is kept here for inspection; user settings are untouched.
    Write-Host "Smoke-test files: $tempRoot"
}
