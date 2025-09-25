param (
    [Parameter(Mandatory=$true)][string]$FileListPath,
    [Parameter(Mandatory=$true)][string]$OutputFile,
    [Parameter(Mandatory=$false)][switch]$NoHash= $false
)

# Helper: Get real path and correct case
function Get-RealPathWithCase([string]$path) {
    $item = Get-Item -LiteralPath $path -ErrorAction SilentlyContinue
    if ($item) {
        return $item.FullName
    }
    return $null
}

# Helper: Recursively expand directories/wildcards to real files
function Expand-Input {
    param(
        [string]$inputPath
    )
    # Expand wildcards and get files/dirs
    $items = Get-ChildItem -LiteralPath $inputPath -Recurse -Force -ErrorAction SilentlyContinue
    foreach ($item in $items) {
        if (-not $item.PSIsContainer) {
            $real = Get-RealPathWithCase $item.FullName
            if ($real) { $real }
        }
    }
}

# Collect all real files
$allFiles = @()

# Read filelist, ignore empty lines
Get-Content $FileListPath | ForEach-Object {
    $line = $_.Trim()
    if ($line) {
        # Expand wildcards, resolve links, expand directories recursively
        $files = @()
        try {

            if (Test-Path $line -PathType Container) {
                $files = Get-ChildItem -Path $line -Recurse -Force -ErrorAction Stop | Where-Object { -not $_.PSIsContainer }
            }
            elseif ($line -match "\*") {
                $files = Get-ChildItem -Path $line -Force -ErrorAction Stop | Where-Object { -not $_.PSIsContainer }
            }
            else {
                $files = $line
            }

            foreach ($file in $files) {
                $real = Get-RealPathWithCase $file.FullName
                if ($real) { $allFiles += $real }
            }
        } catch {
            # Maybe it's a single file or a bad path
            $real = Get-RealPathWithCase $line
            if ($real -and (Test-Path $real -PathType Leaf)) { $allFiles += $real }
        }
    }
}

# Remove duplicates, sort using Ordinal sort order
$allFiles = $allFiles | Select-Object -Unique

[Collections.Generic.List[string]] $lines= $allFiles
$lines.Sort([StringComparer]::Ordinal)
$allFiles= $lines

# Calculate hashes and write output
$hashLines = @()
foreach ($file in $allFiles) {

    if ($NoHash) {
        $resultLine = $file
    }
    else {
        $hasher = [System.Security.Cryptography.SHA256]::Create()
        $stream = [System.IO.File]::OpenRead($file)
        $hashBytes = $hasher.ComputeHash($stream)
        $stream.Close()
        $hash = ($hashBytes | ForEach-Object { $_.ToString("x2") }) -join ""
        $resultLine = "$hash *$file".Replace("\","/")
    }

    $resultLine = $resultLine.Replace("\","/")

    Write-Host $resultLine
    $hashLines += $resultLine
}

# Write hashes to output file
$hashLines | Set-Content -Encoding ascii $OutputFile

if (-not $NoHash) {
    # Calculate SHA256 of output file
    $finalHash = Get-FileHash -Path $OutputFile -Algorithm SHA256
    Write-Host "`nSHA256 of hash list file ($OutputFile):"
    Write-Host "$($finalHash.Hash.ToLower()) *$($finalHash.Path)".Replace("\","/")
}
