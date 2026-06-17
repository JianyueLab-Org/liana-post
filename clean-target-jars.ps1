<#
 .SYNOPSIS
   Remove packaged jar/war files in all 'target' directories under the repo root.

 .DESCRIPTION
   By default the script performs a dry-run and lists matched files. Pass -Delete to actually remove them.

 .EXAMPLE
   ./clean-target-jars.ps1           # list files (dry-run)
   ./clean-target-jars.ps1 -Delete  # actually delete
#>

# Add option to remove entire target directories or files
param(
    [switch]$Delete,
    [switch]$DeleteTargets
)

$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
Write-Host "Repository root: $root"
if ($DeleteTargets) { Write-Host "ACTION: entire target directories will be deleted" -ForegroundColor Yellow }
elseif ($Delete) { Write-Host "ACTION: matched files will be deleted" -ForegroundColor Yellow } else { Write-Host "DRY-RUN: listing files only (use -Delete to remove files or -DeleteTargets to remove whole target directories)" -ForegroundColor Cyan }

# Collect all 'target' directories
$targets = Get-ChildItem -Path $root -Directory -Recurse -Filter target -ErrorAction SilentlyContinue
if (-not $targets) {
    Write-Host "No 'target' directories found under $root" -ForegroundColor Green
    Write-Host "`nDone."
    return
}

foreach ($tobj in $targets) {
    $t = $tobj.FullName
    # compute size of target dir (sum of file lengths)
    $size = Get-ChildItem -Path $t -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum
    $sum = 0
    if ($size) { $sum = $size.Sum }
    $human = "${sum} bytes"
    if ($sum -ge 1MB) { $human = "{0:N2} MB" -f ($sum/1MB) }
    elseif ($sum -ge 1KB) { $human = "{0:N2} KB" -f ($sum/1KB) }

    Write-Host "`nTarget: $t   (size: $human)"

    if ($DeleteTargets) {
        try {
            Remove-Item -LiteralPath $t -Recurse -Force -ErrorAction Stop
            Write-Host "Deleted target directory: $t" -ForegroundColor Yellow
        } catch {
            Write-Host "Failed to delete target directory: $t - $_" -ForegroundColor Red
        }
        continue
    }

    # search recursively inside target to catch jars in subfolders (e.g. target/dependency, target/lib)
    $files = Get-ChildItem -Path $t -File -Recurse -Include *.jar, *.jar.original, *.war -ErrorAction SilentlyContinue
    if (-not $files -or $files.Count -eq 0) {
        Write-Host "  No jar/war files found in this target."
        continue
    }

    foreach ($f in $files) {
        if ($Delete) {
            try {
                Remove-Item -LiteralPath $f.FullName -Force -ErrorAction Stop
                Write-Host "  Deleted: $($f.FullName)"
            } catch {
                Write-Host "  Failed to delete: $($f.FullName) - $_" -ForegroundColor Red
            }
        } else {
            Write-Host "  $($f.FullName)"
        }
    }
}

Write-Host "`nDone."

