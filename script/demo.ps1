param(
    [ValidateSet('Up','Build','Start','Stop','Check')]
    [string]$Action = 'Up'
)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskEnv = Join-Path $taskRoot '.env.demo'
if (-not (Test-Path -LiteralPath $taskEnv)) {
    throw 'Copy .env.demo.example to .env.demo and set the four credentials first.'
}
function Invoke-DemoCompose {
    param([string[]]$Arguments)
    & docker compose --env-file $taskEnv -p liana-post -f (Join-Path $taskRoot 'docker-compose.demo.yml') @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Demo Compose failed (exit $LASTEXITCODE)." }
}
Push-Location $taskRoot
try {
    if ($Action -in @('Up','Build')) {
        & mvn -Pdatabase -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Packaging failed; deployment was not started.' }
        $taskBuild = Join-Path $taskRoot '.dist/demo'
        [IO.Directory]::CreateDirectory($taskBuild) | Out-Null
        foreach ($taskModule in @('liana-gateway','liana-auth-service','liana-facility-service',
                'liana-oms-service','liana-dispatch-service','liana-sorting-service',
                'liana-transport-service','liana-tracking-service','liana-syncer','liana-postage-service')) {
            Copy-Item -LiteralPath (Join-Path $taskRoot "$taskModule/target/$taskModule-1.0.0-exec.jar") `
                -Destination (Join-Path $taskBuild "$taskModule.jar")
        }
        Copy-Item -LiteralPath (Join-Path $taskRoot 'database/target/liana-database-1.0.0.jar') `
            -Destination (Join-Path $taskBuild 'liana-database.jar')
        $taskLib = Join-Path $taskBuild 'lib'
        [IO.Directory]::CreateDirectory($taskLib) | Out-Null
        Copy-Item -Path (Join-Path $taskRoot 'database/target/lib/*.jar') -Destination $taskLib
        Invoke-DemoCompose -Arguments @('build')
    }
    if ($Action -in @('Up','Start')) {
        # An explicit one-shot operation also handles existing versioned volumes.
        Invoke-DemoCompose -Arguments @('up','-d','--wait','mysql')
        Invoke-DemoCompose -Arguments @('run','--rm','--no-deps','database-init','initialize-demo')
        Invoke-DemoCompose -Arguments @('up','-d','--remove-orphans')
    } elseif ($Action -eq 'Stop') {
        Invoke-DemoCompose -Arguments @('stop')
    } elseif ($Action -eq 'Check') {
        Invoke-DemoCompose -Arguments @('run','--rm','--no-deps','database-init','check')
    }
} finally {
    Pop-Location
}
