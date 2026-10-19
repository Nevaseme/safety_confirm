$ErrorActionPreference = 'Stop'
$certPath = Join-Path $PSScriptRoot '../.local/PlanD-Lab-CA.cer'
$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new((Resolve-Path -LiteralPath $certPath).Path)
$target = 'Cert:\CurrentUser\Root\' + $cert.Thumbprint
if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target }
Write-Output 'Removed this experiment CA from CurrentUser trust.'
try {
    Get-NetFirewallRule -DisplayName 'PlanD LAN experiment' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    Write-Output 'Removed PlanD LAN experiment firewall rule.'
} catch { Write-Output 'Firewall rule removal needs an administrator: Get-NetFirewallRule -DisplayName "PlanD LAN experiment" | Remove-NetFirewallRule' }
