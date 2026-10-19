$ErrorActionPreference = 'Stop'
$log = Join-Path $PSScriptRoot '../.local/firewall-result.txt'
try {
    if (-not (Get-NetFirewallRule -DisplayName 'PlanD LAN experiment' -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName 'PlanD LAN experiment' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080,8443 -RemoteAddress LocalSubnet -Profile Private | Out-Null
    }
    'OK: Private profile, LocalSubnet, TCP 8080/8443 only.' | Set-Content -LiteralPath $log
} catch { $_.Exception.Message | Set-Content -LiteralPath $log; exit 1 }
