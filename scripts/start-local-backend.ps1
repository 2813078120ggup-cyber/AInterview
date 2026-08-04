param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $projectRoot 'backend'
$resolvedEnvFile = (Resolve-Path $EnvFile).Path

Get-Content $resolvedEnvFile | ForEach-Object {
    $line = $_.Trim()
    if (!$line -or $line.StartsWith('#')) {
        return
    }

    $separator = $line.IndexOf('=')
    if ($separator -lt 1) {
        return
    }

    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim().Trim('"')
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

if ([string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
    throw 'DEEPSEEK_API_KEY is empty. Update the project .env file before starting the backend.'
}

Set-Location $backendRoot
& mvn spring-boot:run
