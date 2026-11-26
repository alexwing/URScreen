<#
.SYNOPSIS
    Sube el limite de objetos GDI por proceso de Windows (GDIProcessHandleQuota) de 10000 a 65536.

.DESCRIPTION
    El servidor de Unified Remote pierde un objeto GDI por cada fotograma de pantalla que sirve y, al llegar a 10000
    (el limite por defecto de Windows), la pantalla se cuelga hasta reiniciar el servidor. Con 65536 aguanta unas
    6,5 veces mas. No arregla la fuga: solo la retrasa.

    Es un cambio de sistema (HKLM): hay que ejecutarlo como administrador y, para que tenga efecto, REINICIAR Windows.
    Afecta a todos los procesos del equipo, no solo al servidor. Cada objeto GDI ocupa memoria del sistema, asi que un
    programa con fugas puede gastar mas antes de que Windows lo frene; en un equipo de uso normal no se nota.

    Por defecto solo MUESTRA lo que haria. Con -Aplicar hace el cambio, y con -Revertir vuelve a 10000.

.EXAMPLE
    .\subir-limite-gdi.ps1             # muestra el valor actual y lo que cambiaria
    .\subir-limite-gdi.ps1 -Aplicar    # sube el limite a 65536 (administrador)
    .\subir-limite-gdi.ps1 -Revertir   # vuelve a 10000 (administrador)
#>
param(
    [switch]$Aplicar,
    [switch]$Revertir
)

$clave = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Windows'
$nombre = 'GDIProcessHandleQuota'
$porDefecto = 10000
$nuevoValor = 65536

$actual = (Get-ItemProperty -Path $clave -Name $nombre -ErrorAction SilentlyContinue).$nombre
if ($null -eq $actual) { $actual = $porDefecto }
$destino = if ($Revertir) { $porDefecto } else { $nuevoValor }

Write-Host "Clave:   $clave"
Write-Host "Valor:   $nombre"
Write-Host "Actual:  $actual"
Write-Host "Destino: $destino"

if (-not ($Aplicar -or $Revertir)) {
    Write-Host ""
    Write-Host "No se ha cambiado nada. Para aplicarlo, ejecuta este script como administrador con -Aplicar."
    exit 0
}

$esAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $esAdmin) {
    Write-Host ""
    Write-Host "Hace falta ejecutarlo como administrador. No se ha cambiado nada." -ForegroundColor Yellow
    exit 1
}

if ($actual -eq $destino) {
    Write-Host ""
    Write-Host "Ya esta en $destino. No hay nada que cambiar."
    exit 0
}

Set-ItemProperty -Path $clave -Name $nombre -Type DWord -Value $destino
$leido = (Get-ItemProperty -Path $clave -Name $nombre).$nombre
Write-Host ""
Write-Host "Hecho: $nombre = $leido (antes $actual)." -ForegroundColor Green
Write-Host "Reinicia Windows para que tenga efecto. Para deshacerlo: .\subir-limite-gdi.ps1 -Revertir"
