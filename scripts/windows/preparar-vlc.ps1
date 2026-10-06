# Prepara VLC (versión portable oficial de 64 bits) para meterlo dentro del instalador de Aurora:
# así quien instale Aurora no tiene que instalar VLC aparte. Lo llama build-windows.bat.
# La descarga se comprueba con la huella SHA-256 que publica VideoLAN y se guarda para la próxima vez.
param([Parameter(Mandatory = $true)] [string] $Out)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # con la barra de progreso, Invoke-WebRequest es muy lento

$ver = '3.0.24'
$sha = 'fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323'
$url = "https://get.videolan.org/vlc/$ver/win64/vlc-$ver-win64.zip"

$base = if ($env:LOCALAPPDATA) { $env:LOCALAPPDATA } else { [IO.Path]::GetTempPath() }
$cache = Join-Path $base 'Aurora-compilar'
New-Item -ItemType Directory -Force $cache | Out-Null
$zip = Join-Path $cache "vlc-$ver-win64.zip"

function Hash($f) { (Get-FileHash $f -Algorithm SHA256).Hash.ToLower() }

if (-not (Test-Path $zip) -or (Hash $zip) -ne $sha) {
    Write-Host "       Descargando VLC $ver (83 MB, solo la primera vez)..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest $url -OutFile $zip -UseBasicParsing
    if ((Hash $zip) -ne $sha) { Remove-Item $zip; throw 'La descarga de VLC llego danada (SHA-256 distinto). Vuelve a intentarlo.' }
}

$tmp = Join-Path $cache "vlc-$ver"
$src = Join-Path $tmp "vlc-$ver"
if (-not (Test-Path (Join-Path $src 'libvlc.dll'))) {
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    Expand-Archive $zip $tmp
}

if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
$plugins = Join-Path $Out 'plugins'
New-Item -ItemType Directory -Force (Join-Path $plugins 'video_output') | Out-Null
Copy-Item (Join-Path $src 'libvlc.dll'), (Join-Path $src 'libvlccore.dll') $Out
Copy-Item (Join-Path $src 'COPYING.txt') (Join-Path $Out 'LICENCIA-VLC.txt')

# Solo lo necesario para reproducir audio y video y sacar fotogramas (sin interfaz, Lua, red ni grabacion).
foreach ($d in 'access', 'audio_filter', 'audio_mixer', 'audio_output', 'codec', 'demux', 'misc', 'packetizer', 'stream_filter', 'video_chroma', 'video_filter') {
    Copy-Item -Recurse (Join-Path (Join-Path $src 'plugins') $d) (Join-Path $plugins $d)
}
Copy-Item (Join-Path (Join-Path (Join-Path $src 'plugins') 'video_output') 'libvmem_plugin.dll') (Join-Path $plugins 'video_output')
$fuera = 'libx26*', 'libqsv*', 'libcrystalhd*', 'libzvbi*', 'libaribsub*', 'libkate*', 'libaom*', 'libsmb*', 'libnfs*', 'libsftp*',
    'libdtv*', 'libsatip*', 'libdshow*', 'libbluray*', 'libdvdnav*', 'libdvdread*', 'libvcd*', 'libcdda*', 'libaccess_srt*', 'libsrt*',
    'librdp*', 'libvnc*', 'libscreen*', 'libshine*', 'libtwolame*', 'libsecret*', 'libdc1394*', 'libdv1394*', 'liblinsys*', 'liblibbluray*', 'libdcp*'
Get-ChildItem $plugins -Recurse -File -Include $fuera | Remove-Item -Force

# Indice de complementos (plugins.dat): sin el, VLC abre los ~240 complementos en cada arranque y la primera
# vez el antivirus revisa cada uno (mas de un minuto). El indice guarda la fecha de cada complemento; se les pone
# una fecha fija (la app la restaura si el empaquetado o el instalador la cambian, ver BundledVlc.kt).
# vlc-cache-gen.exe solo funciona en Windows y necesita la ruta completa (con una relativa genera un indice vacio).
if (-not $IsLinux -and -not $IsMacOS) {
    $fija = [DateTime]::new(2020, 1, 1, 0, 0, 0, [DateTimeKind]::Utc)
    Get-ChildItem $plugins -Recurse -File -Filter *.dll | ForEach-Object { $_.LastWriteTimeUtc = $fija }
    $abs = (Resolve-Path $plugins).Path
    & (Join-Path $src 'vlc-cache-gen.exe') $abs | Out-Null
    $dat = Join-Path $abs 'plugins.dat'
    if (-not (Test-Path $dat) -or (Get-Item $dat).Length -lt 10KB) { throw 'vlc-cache-gen no genero el indice de complementos.' }
}

$mb = [math]::Round(((Get-ChildItem $Out -Recurse -File | Measure-Object Length -Sum).Sum) / 1MB)
$n = (Get-ChildItem $plugins -Recurse -File -Filter *.dll).Count
Write-Host "       VLC $ver listo: $mb MB, $n complementos."
