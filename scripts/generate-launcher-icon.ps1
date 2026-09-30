<#
  Gera todos os recursos de ícone do Super Launcher a partir da arte versionada.

  Uso:
    powershell -ExecutionPolicy Bypass -File scripts/generate-launcher-icon.ps1

  Saída (em ZalithLauncher/src/main/res):
    drawable-nodpi/ic_launcher_foreground.png   640x640, arte a 70% centrada, alpha
    drawable-nodpi/ic_launcher_monochrome.png   640x640, silhueta branca alpha-only
    mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round}.png
    values/ic_launcher_background.xml           cor amostrada das quinas da arte
  O script também apaga os .webp legados.
#>
param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$artPath  = Join-Path $RepoRoot 'docs\assets\dbc-super-icon-1024.png'
$resDir   = Join-Path $RepoRoot 'ZalithLauncher\src\main\res'
$canvas   = 640
$artRatio = 0.70

if (-not (Test-Path $artPath)) { throw "arte-fonte não encontrada: $artPath" }

$art = [System.Drawing.Bitmap]::new($artPath)
try {
    # --- 1. cor de fundo: média das quinas (16x16 cada) -------------------
    $sr = 0L; $sg = 0L; $sb = 0L; $sn = 0
    foreach ($pt in @(@(0, 0), @(1007, 0), @(0, 1007), @(1007, 1007))) {
        for ($dx = 0; $dx -lt 16; $dx++) {
            for ($dy = 0; $dy -lt 16; $dy++) {
                $c = $art.GetPixel($pt[0] + $dx, $pt[1] + $dy)
                $sr += $c.R; $sg += $c.G; $sb += $c.B; $sn++
            }
        }
    }
    $bg = [System.Drawing.Color]::FromArgb(255, [int]($sr / $sn), [int]($sg / $sn), [int]($sb / $sn))
    $bgHex = '#{0:X2}{1:X2}{2:X2}' -f $bg.R, $bg.G, $bg.B

    $artPx = [int]($canvas * $artRatio)   # 448
    $offset = [int](($canvas - $artPx) / 2)  # 96
    $dst = [System.Drawing.Rectangle]::new($offset, $offset, $artPx, $artPx)

    $nodpi = Join-Path $resDir 'drawable-nodpi'
    New-Item -ItemType Directory -Force -Path $nodpi | Out-Null

    # --- 2. foreground do ícone adaptável ---------------------------------
    $fg = [System.Drawing.Bitmap]::new($canvas, $canvas, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gp = [System.Drawing.Graphics]::FromImage($fg)
    $gp.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gp.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $gp.DrawImage($art, $dst)
    $gp.Dispose()
    $fg.Save((Join-Path $nodpi 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $fg.Dispose()

    # --- 3. monochrome: silhueta branca alpha-only ------------------------
    $mc = [System.Drawing.Bitmap]::new($canvas, $canvas, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gp = [System.Drawing.Graphics]::FromImage($mc)
    $gp.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gp.DrawImage($art, $dst)
    $gp.Dispose()
    for ($y = 0; $y -lt $canvas; $y++) {
        for ($x = 0; $x -lt $canvas; $x++) {
            $p = $mc.GetPixel($x, $y)
            $lum = [int](0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B)
            if ($p.A -gt 0 -and $lum -ge 110) {
                $mc.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, 255, 255, 255))
            } else {
                $mc.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            }
        }
    }
    $mc.Save((Join-Path $nodpi 'ic_launcher_monochrome.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $mc.Dispose()

    # --- 4. densidades legadas --------------------------------------------
    $densities = [ordered]@{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }
    foreach ($density in $densities.Keys) {
        $size = [int]$densities[$density]
        foreach ($round in @($false, $true)) {
            $bmp = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            $gp = [System.Drawing.Graphics]::FromImage($bmp)
            $gp.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $gp.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

            $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
            if ($round) {
                $path.AddEllipse(0, 0, $size, $size)
            } else {
                $r = [int]($size * 0.2)
                $d = 2 * $r
                $path.AddArc(0, 0, $d, $d, 180, 90)
                $path.AddArc($size - $d, 0, $d, $d, 270, 90)
                $path.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
                $path.AddArc(0, $size - $d, $d, $d, 90, 90)
                $path.CloseFigure()
            }
            $gp.SetClip($path)
            $gp.Clear($bg)

            $a = [int]($size * $artRatio)
            $o = [int](($size - $a) / 2)
            $gp.DrawImage($art, [System.Drawing.Rectangle]::new($o, $o, $a, $a))
            $gp.Dispose()
            $path.Dispose()

            $fileName = if ($round) { 'ic_launcher_round.png' } else { 'ic_launcher.png' }
            $bmp.Save((Join-Path (Join-Path $resDir "mipmap-$density") $fileName),
                      [System.Drawing.Imaging.ImageFormat]::Png)
            $bmp.Dispose()
        }

        # apaga os .webp antigos (mesmo nome de recurso → duplicidade no aapt2)
        foreach ($old in @('ic_launcher.webp', 'ic_launcher_round.webp')) {
            $oldPath = Join-Path (Join-Path $resDir "mipmap-$density") $old
            if (Test-Path $oldPath) { Remove-Item $oldPath -Force }
        }
    }

    # --- 5. cor de fundo do adaptável -------------------------------------
    $bgXml = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">$bgHex</color>
</resources>
"@
    [System.IO.File]::WriteAllText((Join-Path $resDir 'values\ic_launcher_background.xml'), $bgXml)

    Write-Host "OK  cor de fundo: $bgHex"
    Write-Host "OK  foreground e monochrome: ${canvas}x${canvas} (arte ${artPx}px centrada em ${offset}px)"
    Write-Host "OK  5 densidades legadas geradas, .webp removidos"
} finally {
    $art.Dispose()
}
