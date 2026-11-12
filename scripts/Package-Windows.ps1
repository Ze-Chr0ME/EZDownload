$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath = Join-Path $projectRoot 'dist\EzSocialToVideo-Windows.zip'
$file = [IO.File]::Open($zipPath, [IO.FileMode]::Create)
$zip = New-Object IO.Compression.ZipArchive($file, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($folder in @('engine','desktop','android','browser-extension','scripts','tests','docs','dist\EzSocialToVideo')) {
        Get-ChildItem -LiteralPath (Join-Path $projectRoot $folder) -Recurse -File | ForEach-Object {
            $relative = $_.FullName.Substring($projectRoot.Length + 1).Replace('\','/')
            if ($relative -match '(^|/)(build|\.gradle|__pycache__)/' -or $_.Name -in @('local.properties','native-host.json') -or $_.Extension -in @('.py','.pyc','.jks')) { return }
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $relative, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    }
    foreach ($name in @('README.md','VALIDATION.md','THIRD_PARTY.md','LICENSE','.gitignore')) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $projectRoot $name), $name, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $zip.Dispose(); $file.Dispose() }
Write-Host "Created $zipPath ($((Get-Item -LiteralPath $zipPath).Length) bytes)"
