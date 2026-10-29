param(
    [ValidateSet('Validate', 'Build', 'Up', 'Status', 'Logs', 'Down')]
    [string]$Action = 'Status',
    [ValidateSet('Infra', 'Platform', 'App', 'Full', 'Config')]
    [string]$Scope = 'Platform',
    [switch]$Test
)
$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env'
if (!(Test-Path -LiteralPath $envFile)) { $envFile = Join-Path $PSScriptRoot '.env.example' }
$composeArgs = @('compose', '--project-directory', $PSScriptRoot, '--env-file', $envFile,
    '-f', (Join-Path $PSScriptRoot 'docker-compose.yml'))
if ($Test) {
    $composeArgs += @('-p', 'checkinn-test', '--env-file', (Join-Path $PSScriptRoot '.env.test'),
        '-f', (Join-Path $PSScriptRoot 'docker-compose.test.yml'))
}
if ($Scope -in @('App', 'Full')) { $composeArgs += @('--profile', 'app') }
if ($Scope -eq 'Full') { $composeArgs += @('--profile', 'web', '--profile', 'config') }
if ($Scope -eq 'Config') { $composeArgs += @('--profile', 'config') }
$services = @()
if ($Scope -eq 'Infra') {
    $services = @('redis', 'rabbitmq')
    if ($Test) { $services += @('postgres-test', 'mailpit') }
}
if ($Scope -eq 'Config') { $services = @('config-server') }

if ($Action -eq 'Up' -and $Scope -in @('App', 'Full')) {
    # Inspect resolved configuration without printing credentials. Shell env overrides .env.
    $configText = & docker @composeArgs config --format json
    if ($LASTEXITCODE -ne 0) { throw 'Compose configuration failed.' }
    $config = ($configText -join "`n") | ConvertFrom-Json
    $secret = $config.services.'auth-service'.environment.SECRET_KEY
    if ([string]::IsNullOrWhiteSpace($secret) -or $secret.Length -lt 32) {
        throw 'Set SECRET_KEY to a private random value of at least 32 ASCII characters in docker/.env.'
    }
    foreach ($service in @('user-service', 'hotel-service', 'booking-service', 'revenue-service', 'regulations-service')) {
        $serviceEnv = $config.services.$service.environment
        if ([string]::IsNullOrWhiteSpace($serviceEnv.SPRING_DATASOURCE_PASSWORD)) {
            throw "Configure the test database password for $service in docker/.env. No database was created automatically."
        }
    }
    $mediaEnv = $config.services.'media-service'.environment
    if (!$mediaEnv.SUPABASE_URL -or !$mediaEnv.SUPABASE_SERVICE_ROLE_KEY) {
        throw 'Configure test Supabase storage credentials before starting App/Full.'
    }
    if (!$config.services.'notification-service'.environment.SPRING_MAIL_HOST) {
        throw 'Configure MAIL_HOST and SMTP credentials before starting App/Full.'
    }
    if ($Scope -eq 'Full' -and !$config.services.frontend.environment.NEXTAUTH_SECRET) {
        throw 'Set NEXTAUTH_SECRET before starting Full.'
    }
}

switch ($Action) {
    Validate { & docker @composeArgs config --quiet }
    Build { & docker @composeArgs build @services }
    Up { & docker @composeArgs up -d --wait --wait-timeout 300 @services }
    Status { & docker @composeArgs ps -a @services }
    Logs { & docker @composeArgs logs --tail 100 @services }
    Down {
        # All profiles, no -v: stop this project and preserve persistent data.
        & docker @composeArgs --profile app --profile web --profile config down
    }
}
if ($LASTEXITCODE -ne 0) { throw "Docker command failed (exit $LASTEXITCODE)." }
