$cores = @("mgba", "snes9x", "mupen64plus_next", "ppsspp", "nestopia")
$base_url = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/"
$dest_dir = "cores_tmp"

if (!(Test-Path $dest_dir)) { New-Item -ItemType Directory $dest_dir }

foreach ($c in $cores) {
    $filename = "${c}_libretro_android.so.zip"
    $url = "${base_url}${filename}"
    $dest = Join-Path $dest_dir $filename
    Write-Host "Downloading $url..."
    try {
        Invoke-WebRequest -Uri $url -OutFile $dest -ErrorAction Stop
        Write-Host "Success: $c"
    } catch {
        Write-Host "Failed: $c - $($_.Exception.Message)"
    }
}
