<#
.SYNOPSIS
    Muestra cuantos objetos GDI usa el servidor de Unified Remote y cuanto margen le queda.

.DESCRIPTION
    Lee los objetos GDI de RemoteServerWin cada 'Segundos' segundos (con -Continuo, hasta pulsar Ctrl+C). Si el numero
    se acerca al limite (10000 por defecto, o el valor de GDIProcessHandleQuota), la pantalla del servidor esta a punto
    de colgarse: conviene reiniciarlo desde su panel.

.EXAMPLE
    .\medir-gdi-unified.ps1
    .\medir-gdi-unified.ps1 -Continuo -Segundos 5
#>
param(
    [switch]$Continuo,
    [int]$Segundos = 5
)

Add-Type -Namespace W -Name G -MemberDefinition '[DllImport("user32.dll")] public static extern uint GetGuiResources(System.IntPtr h, uint flags);'

$clave = 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Windows'
$limite = (Get-ItemProperty -Path $clave -Name GDIProcessHandleQuota -ErrorAction SilentlyContinue).GDIProcessHandleQuota
if ($null -eq $limite) { $limite = 10000 }

do {
    $p = Get-Process RemoteServerWin -ErrorAction SilentlyContinue
    if (-not $p) {
        Write-Host "RemoteServerWin no esta en ejecucion."
        break
    }
    $gdi = [W.G]::GetGuiResources($p.Handle, 0)
    $pct = [math]::Round(100 * $gdi / $limite)
    $aviso = if ($pct -ge 80) { "  <-- reinicia el servidor pronto" } else { "" }
    Write-Host ("{0:HH:mm:ss}  GDI {1} de {2} ({3} %){4}" -f (Get-Date), $gdi, $limite, $pct, $aviso)
    if ($Continuo) { Start-Sleep -Seconds $Segundos }
} while ($Continuo)
