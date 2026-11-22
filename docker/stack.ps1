param(
    [ValidateSet('Validate', 'Build', 'Up', 'Status', 'Logs', 'Down')]
    [string]$Action = 'Status',
    [ValidateSet('Infra', 'Platform', 'App', 'Full', 'Config')]
    [string]$Scope = 'Full',
    [ValidateSet('Supabase', 'Test')]
    [string]$Environment = 'Supabase',
    [switch]$Test,
    [switch]$SupabaseDev
)
$ErrorActionPreference = 'Stop'
if ($Test -and $SupabaseDev) { throw 'Choose one environment: Supabase or Test.' }
if ($Test) {
    if ($PSBoundParameters.ContainsKey('Environment') -and $Environment -ne 'Test') { throw 'Conflicting environment selectors.' }
    $Environment = 'Test'
}
if ($SupabaseDev -and $Environment -ne 'Supabase') { throw 'Conflicting environment selectors.' }
if ($Environment -eq 'Supabase') {
    if ($PSBoundParameters.ContainsKey('Scope') -and $Scope -ne 'Full') { throw 'Scope is for Test only; Supabase selects the complete application group.' }
    if ($Action -eq 'Logs') { throw 'Use Docker Desktop logs; output can contain private application data.' }
    $dockerAction = @{Validate='prepare'; Build='build'; Up='up'; Status='status'; Down='down'}[$Action]
    & python (Join-Path $PSScriptRoot '../tools/supabase-runner/run-docker-stack.py') $dockerAction
    if ($LASTEXITCODE -ne 0) { throw 'Supabase Docker operation failed; review private build logs when applicable.' }
    return
}
# Test never reads the main Supabase .env or silently falls back to an example.
$envFile = Join-Path $PSScriptRoot '.env.test'
if (!(Test-Path -LiteralPath $envFile)) { throw 'Configure docker/.env.test from .env.test.example first.' }
$composeArgs = @('compose', '--project-directory', $PSScriptRoot, '--env-file', $envFile,
    '-p', 'checkinn-test', '-f', (Join-Path $PSScriptRoot 'docker-compose.yml'),
    '-f', (Join-Path $PSScriptRoot 'docker-compose.test.yml'))
if ($Scope -in @('App', 'Full')) { $composeArgs += @('--profile', 'app') }
if ($Scope -eq 'Full') { $composeArgs += @('--profile', 'web', '--profile', 'config') }
if ($Scope -eq 'Config') { $composeArgs += @('--profile', 'config') }
$services = @()
if ($Scope -eq 'Infra') {
    $services = @('redis', 'rabbitmq')
    $services += @('postgres-test', 'mailpit')
}
if ($Scope -eq 'Config') { $services = @('config-server') }

if ($Action -in @('Validate', 'Up')) {
    # Inspect resolved configuration without printing credentials. Shell env overrides .env.
    $configText = & docker @composeArgs config --format json
    if ($LASTEXITCODE -ne 0) { throw 'Compose configuration failed.' }
    $config = ($configText -join "`n") | ConvertFrom-Json
    foreach ($owner in @('user','hotel','booking','revenue','regulations')) {
        $serviceEnv = $config.services."$owner-service".environment
        if ($null -eq $serviceEnv) { continue } # Infra/Config scopes contain no SQL services.
        if ($serviceEnv.SPRING_DATASOURCE_URL -cne "jdbc:postgresql://postgres-test:5432/checkinn_$owner" -or
            $serviceEnv.SPRING_DATASOURCE_USERNAME -cne 'checkinn_test') {
            throw 'Test requires the isolated postgres-test container; refused another database target.'
        }
    }
}
if ($Action -eq 'Up' -and $Scope -in @('App','Full','Platform')) {
    # Both daily modes use the same loopback app ports. Never switch implicitly.
    # Label filters instead of a --format template: Windows PowerShell 5.1 strips embedded quotes from native arguments.
    $onPort = @(& docker ps -q --filter publish=8080)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect existing Docker application ports.' }
    $ownOnPort = @(& docker ps -q --filter publish=8080 --filter label=com.docker.compose.project=checkinn-test)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect existing Docker application ports.' }
    if ($onPort.Count -gt $ownOnPort.Count) { throw 'Another application stack is using port 8080; stop it before starting Test.' }
}
if ($Action -eq 'Up' -and $Scope -in @('App', 'Full')) {
    $secret = $config.services.'auth-service'.environment.SECRET_KEY
    if ([string]::IsNullOrWhiteSpace($secret) -or $secret.Length -lt 32) {
        throw 'Set a separate SECRET_KEY of at least 32 ASCII characters in docker/.env.test.'
    }
    foreach ($service in @('user-service', 'hotel-service', 'booking-service', 'revenue-service', 'regulations-service', 'place-service', 'travel-social-service')) {
        $serviceEnv = $config.services.$service.environment
        if ([string]::IsNullOrWhiteSpace($serviceEnv.SPRING_DATASOURCE_PASSWORD)) {
            throw "Configure TEST_POSTGRES_PASSWORD in docker/.env.test for $service."
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
