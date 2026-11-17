param(
    [ValidateSet('auth','facility','oms','dispatch','tracking','transport','sorting','postage')]
    [string[]]$Services = @('auth','facility','oms','dispatch','tracking','transport','sorting','postage')
)

# Host-side equivalent of compose.services.yml for machines with limited Docker memory.
# Only the dedicated r2 acceptance MySQL/Redis and local ports are used.
$ErrorActionPreference = 'Stop'
foreach ($taskKey in @('LIANA_DB_PASSWORD','LIANA_PACKING_TOKEN','LIANA_MANIFEST_TOKEN')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($taskKey))) {
        throw "Set $taskKey explicitly."
    }
}
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskLogs = Join-Path $env:TEMP 'liana-manifest-accept-r2'
[IO.Directory]::CreateDirectory($taskLogs) | Out-Null
$env:SPRING_CONFIG_IMPORT = 'optional:file:./no-remote-config.yml'
$env:SPRING_CLOUD_NACOS_CONFIG_ENABLED = 'false'
$env:SPRING_CLOUD_NACOS_DISCOVERY_ENABLED = 'false'
$env:SPRING_CLOUD_NACOS_CONFIG_IMPORT_CHECK_ENABLED = 'false'
$env:SPRING_CLOUD_SENTINEL_ENABLED = 'false'
$env:SPRING_SQL_INIT_MODE = 'never'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = $env:LIANA_DB_PASSWORD
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '13379'
$env:PACKING_INTERNAL_TOKEN = $env:LIANA_PACKING_TOKEN
$env:MANIFEST_INTERNAL_TOKEN = $env:LIANA_MANIFEST_TOKEN
$env:POSTAGE_SEED_INITIAL_TARIFF = 'false'
$env:LOGGING_LEVEL_ROOT = 'WARN'
$env:MYBATIS_PLUS_CONFIGURATION_LOG_IMPL = 'org.apache.ibatis.logging.nologging.NoLoggingImpl'
$taskPorts = @{auth=18081;facility=18082;oms=18083;dispatch=18084;tracking=18085;transport=18086;sorting=18087;postage=18090}
foreach ($taskService in $taskPorts.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable(($taskService.Key.ToUpper() + '_SERVICE_URL'),
        ('http://127.0.0.1:' + $taskService.Value), 'Process')
}
foreach ($taskName in $Services) {
    $env:SERVER_PORT = [string]$taskPorts[$taskName]
    $env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:13328/${taskName}_db?serverTimezone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false"
    $taskJar = Join-Path $taskRoot "liana-$taskName-service/target/liana-$taskName-service-1.0.0-exec.jar"
    $taskRun = [DateTime]::UtcNow.ToString('yyyyMMddHHmmss')
    $taskProcess = Start-Process -FilePath java -ArgumentList @('-Duser.timezone=UTC','-Xmx192m','-jar',$taskJar) `
        -WindowStyle Hidden -WorkingDirectory $taskRoot `
        -RedirectStandardOutput (Join-Path $taskLogs "$taskName-$taskRun.log") `
        -RedirectStandardError (Join-Path $taskLogs "$taskName-$taskRun.err") -PassThru
    [PSCustomObject]@{Service=$taskName; Port=$env:SERVER_PORT; LauncherPid=$taskProcess.Id; LogDirectory=$taskLogs}
}
