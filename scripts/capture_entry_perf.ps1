<#
.SYNOPSIS
    Capture entry-addition timing logs (tag FudAIPerf) from a running debug build,
    natively on Windows where the USB device is attached.

.DESCRIPTION
    Clears logcat, optionally launches MainActivity, then streams the FudAIPerf
    tag to android\build\perf-entry\<stamp>\entry_perf.log. Exercise the add-entry
    flows on the device (text, photo + Save, manual, barcode) while it records,
    then press Ctrl-C to stop. Runs the Python summarizer if python is available.

.PARAMETER Package
    App package. Default: org.codeberg.fitguy.nofud.debug

.PARAMETER Launch
    Force-stop and launch MainActivity before recording.

.EXAMPLE
    scripts\capture_entry_perf.ps1
    scripts\capture_entry_perf.ps1 -Launch
#>
param(
    [string]$Package = "org.codeberg.fitguy.nofud.debug",
    [switch]$Launch
)

$ErrorActionPreference = "Stop"
$adb = if ($env:ADB_BIN) { $env:ADB_BIN } else { "adb" }
$activity = "$Package/org.codeberg.fitguy.nofud.MainActivity"
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"

# Resolve repo-root\android\build\perf-entry\<stamp> regardless of CWD.
$repoRoot = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $repoRoot "android\build\perf-entry\$stamp"
$log = Join-Path $outDir "entry_perf.log"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host "Using package: $Package"
Write-Host "Using adb:     $adb"
Write-Host "Writing to:    $log"

& $adb get-state | Out-Null

Write-Host "Clearing logcat buffer..."
& $adb logcat -c

if ($Launch) {
    Write-Host "Launching $activity..."
    & $adb shell am force-stop $Package
    & $adb shell am start -n $activity | Out-Null
}

Write-Host ""
Write-Host ">>> Now add entries on the device (text / photo+Save / manual / barcode)."
Write-Host ">>> Recording... press Ctrl-C when done."
Write-Host ""

# -v epoch = parseable absolute timestamp per line; filter to our tag only.
try {
    & $adb logcat -v epoch -s "FudAIPerf:V" | Tee-Object -FilePath $log
} finally {
    Write-Host ""
    Write-Host "Saved raw log: $log"
    $summarizer = Join-Path $PSScriptRoot "summarize_entry_perf.py"
    $py = Get-Command python3 -ErrorAction SilentlyContinue
    if (-not $py) { $py = Get-Command python -ErrorAction SilentlyContinue }
    if ((Test-Path $log) -and ((Get-Item $log).Length -gt 0) -and $py -and (Test-Path $summarizer)) {
        Write-Host "=== Summary ==="
        & $py.Source $summarizer $log
    } else {
        Write-Host "(no FudAIPerf lines, or no python found — run: python summarize_entry_perf.py $log)"
    }
}
