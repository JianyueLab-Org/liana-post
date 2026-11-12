param(
    [string]$BaseUrl = $env:TRANSPORT_BASE_URL,
    [string]$Token = $env:LIANA_TOKEN,
    [long]$DispatchBagId = 1,
    [long]$FromDispatchBagId = 0,
    [switch]$StrictDependencyEndpoints,
    [int]$TimeoutSec = 20
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($BaseUrl)) {
    $BaseUrl = 'http://localhost:8086'
}

$BaseUrl = $BaseUrl.TrimEnd('/')
$runId = Get-Date -Format 'yyyyMMddHHmmss'
$results = New-Object System.Collections.Generic.List[object]
$created = [ordered]@{}

function Add-Result {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )
    $results.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    }) | Out-Null
    $prefix = if ($Status -eq 'PASS') { '[PASS]' } elseif ($Status -eq 'WARN') { '[WARN]' } else { '[FAIL]' }
    Write-Host "$prefix $Name - $Detail"
}

function ConvertTo-QueryString {
    param([hashtable]$Query)
    if ($null -eq $Query -or $Query.Count -eq 0) {
        return ''
    }
    $pairs = New-Object System.Collections.Generic.List[string]
    foreach ($key in $Query.Keys) {
        $value = $Query[$key]
        if ($null -eq $value -or [string]::IsNullOrWhiteSpace([string]$value)) {
            continue
        }
        $encodedKey = [uri]::EscapeDataString([string]$key)
        $encodedValue = [uri]::EscapeDataString([string]$value)
        $pairs.Add("$encodedKey=$encodedValue") | Out-Null
    }
    if ($pairs.Count -eq 0) {
        return ''
    }
    return '?' + ($pairs -join '&')
}

function Invoke-Transport {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Query,
        [object]$Body
    )

    $headers = @{ Accept = 'application/json' }
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers.Authorization = "Bearer $Token"
    }

    $uri = $BaseUrl + $Path + (ConvertTo-QueryString $Query)
    $params = @{
        Method = $Method
        Uri = $uri
        Headers = $headers
        TimeoutSec = $TimeoutSec
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = ($Body | ConvertTo-Json -Depth 12)
    }

    $response = Invoke-RestMethod @params
    if ($null -ne $response -and $response.PSObject.Properties.Name -contains 'code' -and [int]$response.code -ne 200) {
        throw "API code=$($response.code), message=$($response.message)"
    }
    if ($null -ne $response -and $response.PSObject.Properties.Name -contains 'data') {
        return $response.data
    }
    return $response
}

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action,
        [switch]$Dependency
    )

    try {
        $value = & $Action
        Add-Result -Name $Name -Status 'PASS' -Detail 'ok'
        return $value
    } catch {
        $message = $_.Exception.Message
        if ($Dependency -and -not $StrictDependencyEndpoints) {
            Add-Result -Name $Name -Status 'WARN' -Detail $message
            return $null
        }
        Add-Result -Name $Name -Status 'FAIL' -Detail $message
        return $null
    }
}

function Assert-HasId {
    param([object]$Value, [string]$Name)
    if ($null -eq $Value -or -not ($Value.PSObject.Properties.Name -contains 'id') -or $null -eq $Value.id) {
        throw "$Name response has no id"
    }
}

function Get-CreatedValue {
    param([string]$Key)
    if (-not $created.Contains($Key)) {
        throw "missing prerequisite value: $Key"
    }
    return $created[$Key]
}

$assetCode = "T-API-$runId"
$routeCode = "TR-API-$runId"
$scheduleCode = "TS-API-$runId"
$taskCode = "TT-API-$runId"

Invoke-Step 'dashboardSummary' {
    Invoke-Transport -Method GET -Path '/api/transport/dashboard/summary'
} | Out-Null

Invoke-Step 'listAssets(frontend query)' {
    Invoke-Transport -Method GET -Path '/api/transport/assets' -Query @{ page = 1; pageSize = 10; keyword = ''; status = '' }
} | Out-Null

$asset = Invoke-Step 'createAsset(frontend payload)' {
    Invoke-Transport -Method POST -Path '/api/transport/assets' -Body @{
        code = $assetCode
        name = "API Test Truck $runId"
        type = 'TRUCK'
        capacity = 80
        status = 'AVAILABLE'
    }
}
if ($asset) {
    Assert-HasId $asset 'createAsset'
    $created.assetId = [long]$asset.id
}

Invoke-Step 'getAsset' {
    Invoke-Transport -Method GET -Path "/api/transport/assets/$([uri]::EscapeDataString($assetCode))"
} | Out-Null

Invoke-Step 'updateAsset(frontend payload)' {
    Invoke-Transport -Method PUT -Path "/api/transport/assets/$([uri]::EscapeDataString($assetCode))" -Body @{
        code = $assetCode
        name = "API Test Truck Updated $runId"
        type = 'TRUCK'
        capacity = 81
        status = 'IN_SERVICE'
    }
} | Out-Null

Invoke-Step 'listRoutes(frontend query)' {
    Invoke-Transport -Method GET -Path '/api/transport/routes' -Query @{ page = 1; pageSize = 10; keyword = ''; status = ''; transportType = '' }
} | Out-Null

$route = Invoke-Step 'createRoute(frontend payload with facility codes)' {
    Invoke-Transport -Method POST -Path '/api/transport/routes' -Body @{
        routeCode = $routeCode
        originFacilityCode = 'A1'
        destinationFacilityCode = 'B1'
        transportType = 'LAND'
        estimatedHours = 2.5
        status = 'ACTIVE'
    }
}
if ($route) {
    Assert-HasId $route 'createRoute'
    $created.routeId = [long]$route.id
}

Invoke-Step 'getRoute' {
    Invoke-Transport -Method GET -Path "/api/transport/routes/$([uri]::EscapeDataString($routeCode))"
} | Out-Null

Invoke-Step 'updateRoute(frontend payload with facility codes)' {
    Invoke-Transport -Method PUT -Path "/api/transport/routes/$([uri]::EscapeDataString($routeCode))" -Body @{
        routeCode = $routeCode
        originFacilityCode = 'A1'
        destinationFacilityCode = 'B1'
        transportType = 'LAND'
        estimatedHours = 3
        status = 'PLANNED'
    }
} | Out-Null

Invoke-Step 'listSchedules(frontend query)' {
    Invoke-Transport -Method GET -Path '/api/transport/schedules' -Query @{ page = 1; pageSize = 10; keyword = ''; status = '' }
} | Out-Null

$departure = (Get-Date).AddHours(1).ToString('yyyy-MM-ddTHH:mm:00')
$arrival = (Get-Date).AddHours(4).ToString('yyyy-MM-ddTHH:mm:00')
$schedule = Invoke-Step 'createSchedule(frontend payload)' {
    Invoke-Transport -Method POST -Path '/api/transport/schedules' -Body @{
        scheduleCode = $scheduleCode
        assetId = (Get-CreatedValue 'assetId')
        routeId = (Get-CreatedValue 'routeId')
        departureTime = $departure
        arrivalTime = $arrival
        weekday = 'MON'
        status = 'PLANNED'
    }
}
if ($schedule) {
    Assert-HasId $schedule 'createSchedule'
    $created.scheduleId = [long]$schedule.id
}

Invoke-Step 'getSchedule' {
    Invoke-Transport -Method GET -Path "/api/transport/schedules/$([uri]::EscapeDataString($scheduleCode))"
} | Out-Null

Invoke-Step 'updateSchedule(frontend payload)' {
    Invoke-Transport -Method PUT -Path "/api/transport/schedules/$([uri]::EscapeDataString($scheduleCode))" -Body @{
        scheduleCode = $scheduleCode
        assetId = (Get-CreatedValue 'assetId')
        routeId = (Get-CreatedValue 'routeId')
        departureTime = $departure
        arrivalTime = $arrival
        weekday = 'TUE'
        status = 'ACTIVE'
    }
} | Out-Null

Invoke-Step 'listTasks(frontend query)' {
    Invoke-Transport -Method GET -Path '/api/transport/tasks' -Query @{ page = 1; pageSize = 10; keyword = ''; status = '' }
} | Out-Null

$task = Invoke-Step 'createTask(frontend manual payload)' {
    Invoke-Transport -Method POST -Path '/api/transport/tasks' -Body @{
        taskCode = $taskCode
        dispatchBagId = $DispatchBagId
        assetId = (Get-CreatedValue 'assetId')
        routeId = (Get-CreatedValue 'routeId')
        scheduleId = (Get-CreatedValue 'scheduleId')
        status = 'CREATED'
    }
}

Invoke-Step 'getTask' {
    Invoke-Transport -Method GET -Path "/api/transport/tasks/$([uri]::EscapeDataString($taskCode))"
} | Out-Null

Invoke-Step 'updateTaskStatus(frontend payload)' {
    Invoke-Transport -Method POST -Path "/api/transport/tasks/$([uri]::EscapeDataString($taskCode))/status" -Body @{
        status = 'ASSIGNED'
    }
} | Out-Null

$bagForDispatchFlow = if ($FromDispatchBagId -gt 0) { $FromDispatchBagId } else { $DispatchBagId + 1000000000 }
Invoke-Step 'createTaskFromDispatchBag(dependency endpoint)' -Dependency {
    Invoke-Transport -Method POST -Path "/api/transport/tasks/from-dispatch/$bagForDispatchFlow"
} | Out-Null

$failed = @($results | Where-Object { $_.Status -eq 'FAIL' })
$warned = @($results | Where-Object { $_.Status -eq 'WARN' })

Write-Host ''
Write-Host "Transport API smoke test finished: pass=$(@($results | Where-Object { $_.Status -eq 'PASS' }).Count), warn=$($warned.Count), fail=$($failed.Count)"
if ($warned.Count -gt 0) {
    Write-Host 'Warnings are usually dependency endpoints that need dispatch/tracking data or services.'
}
if ($failed.Count -gt 0) {
    exit 1
}
