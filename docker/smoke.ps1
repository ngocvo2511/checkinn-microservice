param([ValidateSet('Infra', 'Platform', 'App', 'Full')][string]$Scope = 'Platform', [switch]$Test)
$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env'
if (!(Test-Path -LiteralPath $envFile)) { $envFile = Join-Path $PSScriptRoot '.env.example' }
$composeArgs = @('compose', '--project-directory', $PSScriptRoot, '--env-file', $envFile,
    '-f', (Join-Path $PSScriptRoot 'docker-compose.yml'), '--profile', 'app', '--profile', 'web', '--profile', 'config')
if ($Test) {
    $composeArgs += @('-p', 'checkinn-test', '--env-file', (Join-Path $PSScriptRoot '.env.test'),
        '-f', (Join-Path $PSScriptRoot 'docker-compose.test.yml'))
}
function Invoke-ContainerCheck([string]$Service, [string[]]$Command) {
    & docker @composeArgs exec -T $Service @Command
    if ($LASTEXITCODE -ne 0) { throw "Smoke check failed: $Service" }
    Write-Host "PASS $Service"
}
Invoke-ContainerCheck 'redis' @('redis-cli', 'ping')
Invoke-ContainerCheck 'rabbitmq' @('rabbitmq-diagnostics', '-q', 'check_running')
Invoke-ContainerCheck 'rabbitmq' @('rabbitmq-diagnostics', '-q', 'check_local_alarms')
if ($Test) {
    Invoke-ContainerCheck 'postgres-test' @('pg_isready', '-U', 'checkinn_test', '-d', 'postgres')
    Invoke-ContainerCheck 'mailpit' @('/mailpit', 'readyz')
}
if ($Scope -eq 'Infra') { return }
$ports = [ordered]@{'eureka-server'=8761; 'api-gateway'=8080}
if ($Scope -in @('App', 'Full')) {
    $ports += [ordered]@{'user-service'=8082; 'auth-service'=8081; 'hotel-service'=8083;
        'media-service'=8085; 'booking-service'=8084; 'revenue-service'=8088;
        'regulations-service'=8091}
}
foreach ($service in $ports.Keys) {
    # Root health also checks the configured DB/Redis/broker/SMTP contributors.
    Invoke-ContainerCheck $service @('curl', '-fsS', "http://localhost:$($ports[$service])/actuator/health")
}
Invoke-ContainerCheck 'admin-server' @('curl', '-fsS', '-o', '/dev/null', 'http://localhost:9095/')
if ($Scope -in @('App', 'Full')) {
    Invoke-ContainerCheck 'notification-service' @('bash', '/usr/local/bin/checkinn-notification-health.sh')
    $registryText = & docker @composeArgs exec -T api-gateway curl -fsS -H 'Accept: application/json' http://eureka-server:8761/eureka/apps
    if ($LASTEXITCODE -ne 0) { throw 'Cannot query Eureka from gateway.' }
    $registry = ($registryText -join "`n") | ConvertFrom-Json
    # notification-service is an AMQP worker without an Eureka client dependency.
    foreach ($service in $ports.Keys | Where-Object { $_ -like '*-service' }) {
        $application = @($registry.applications.application | Where-Object { $_.name -eq $service.ToUpperInvariant() })
        if (!@($application.instance | Where-Object { $_.status -eq 'UP' }).Count) {
            throw "Eureka registration not UP: $service (allow discovery propagation, then retry)."
        }
    }
    Invoke-ContainerCheck 'api-gateway' @('curl', '-fsS', '-o', '/dev/null', 'http://api-gateway:8080/cities')
    Invoke-ContainerCheck 'api-gateway' @('curl', '-fsS', '-o', '/dev/null', 'http://api-gateway:8080/hotels')
    # Verify cross-container gRPC listeners; this is not an RPC contract test.
    Invoke-ContainerCheck 'auth-service' @('bash', '-c', 'timeout 5 bash -c "true > /dev/tcp/user-service/9097"')
    Invoke-ContainerCheck 'hotel-service' @('bash', '-c', 'timeout 5 bash -c "true > /dev/tcp/media-service/9096"')
    Invoke-ContainerCheck 'revenue-service' @('bash', '-c', 'timeout 5 bash -c "true > /dev/tcp/hotel-service/9092"')
}
if ($Scope -eq 'Full') {
    Invoke-ContainerCheck 'config-server' @('curl', '-fsS', 'http://localhost:8888/actuator/health')
    Invoke-ContainerCheck 'frontend' @('node', '-e', "Promise.all(['http://127.0.0.1:3000/api/auth/providers','http://api-gateway:8080/actuator/health'].map(u=>fetch(u).then(r=>{if(!r.ok)throw Error(u)}))).catch(e=>{console.error(e);process.exit(1)})")
}
Write-Host 'Smoke checks passed. Booking/payment/email/storage operations require separate integration tests.'
