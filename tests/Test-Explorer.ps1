param([string]$VideoFolder)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$testClasses = Join-Path $projectRoot 'build\explorer-test'
New-Item -ItemType Directory -Path $testClasses -Force | Out-Null
$libraryPath = Join-Path $projectRoot 'desktop\build\install\desktop\lib\*'
& javac -encoding UTF-8 -cp $libraryPath -d $testClasses (Join-Path $PSScriptRoot 'ExplorerSmoke.java')
if ($LASTEXITCODE -ne 0) { throw 'Could not compile Explorer smoke test' }
if ($VideoFolder) {
    $expected = Get-ChildItem -LiteralPath $VideoFolder -File -Filter '*.mp4' | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
    if (-not $expected) { throw 'No MP4 in the selected video folder' }
    & java -cp "$testClasses;$libraryPath" com.ezsocial.desktop.ExplorerSmoke --folder $VideoFolder
} else {
    $testRoot = Join-Path $env:TEMP ('ezsocial-explorer-' + [Guid]::NewGuid().ToString('N'))
    $emoji = [char]::ConvertFromUtf32(0x1F633)
    $accent = [char]0xE9
    # Avoid shell expansion of the literal dollar sign in the filename.
    $expected = Join-Path (Join-Path $testRoot "Folder, with spaces $accent $emoji") ('clip, $500 ' + $emoji + '.mp4')
    & java -cp "$testClasses;$libraryPath" com.ezsocial.desktop.ExplorerSmoke --fixture $testRoot
}
if ($LASTEXITCODE -ne 0) { throw 'Windows file-selection API failed' }
$shell = New-Object -ComObject Shell.Application
$found = $false
for ($attempt = 0; $attempt -lt 24; $attempt++) {
    foreach ($window in @($shell.Windows())) {
        try {
            if ($window.Document.Folder.Self.Path -eq (Split-Path $expected -Parent)) {
                foreach ($item in @($window.Document.SelectedItems())) { if ($item.Path -eq $expected) { $found = $true } }
            }
        } catch {}
    }
    if ($found) { break }
    Start-Sleep -Milliseconds 250
}
if (-not $found) { throw "Explorer did not confirm the expected folder and selected file: $expected" }
Write-Output "Verified Explorer folder and selected file: $expected"
if (-not $VideoFolder) {
    foreach ($window in @($shell.Windows())) {
        try { if ($window.Document.Folder.Self.Path -eq (Split-Path $expected -Parent)) { $window.Quit() } } catch {}
    }
}
