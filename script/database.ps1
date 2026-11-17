param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Migrate', 'Validate', 'SeedDemo', 'Check', 'InitializeDemo')]
    [string]$Action
)

$ErrorActionPreference = 'Stop'
foreach ($taskKey in @('LIANA_DB_HOST', 'LIANA_DB_PORT', 'LIANA_DB_USER', 'LIANA_DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($taskKey))) {
        throw "Set $taskKey explicitly before running database operations."
    }
}

$taskCommands = switch ($Action) {
    'Migrate' { @('migrate') }
    'Validate' { @('validate') }
    'SeedDemo' { @('seed-demo') }
    'Check' { @('check') }
    'InitializeDemo' { @('initialize-demo') }
}

Push-Location (Join-Path $PSScriptRoot '..')
try {
    foreach ($taskCommand in $taskCommands) {
        & mvn -Pdatabase -pl database compile exec:java "-Dexec.args=$taskCommand"
        if ($LASTEXITCODE -ne 0) {
            throw "Database operation '$taskCommand' failed (exit $LASTEXITCODE). Subsequent operations were not run."
        }
    }
} finally {
    Pop-Location
}
