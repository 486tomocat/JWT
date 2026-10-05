<#
.SYNOPSIS
    一键启动 / 停止本项目依赖的中间件（Redis、Elasticsearch、MinIO）。

.DESCRIPTION
    MySQL 和 RabbitMQ 已经注册为 Windows 服务（StartType = Automatic），开机自动运行，
    所以本脚本不管它们 —— 只负责另外三个以「前台进程」方式跑的服务，
    它们关掉窗口就停，重启电脑也不会自己起来，最容易忘。

    默认在**独立的新窗口**里启动，这样每个服务的日志都能单独看到。
    启动前会先检查端口，已经在跑的就跳过，不会重复启动。

.PARAMETER Stop
    停止这三个服务（按端口定位进程，比按名字可靠）。

.PARAMETER Status
    只查看状态，不做任何启动/停止。

.EXAMPLE
    .\start-services.ps1              # 启动全部
    .\start-services.ps1 -Status      # 看看现在开着哪些
    .\start-services.ps1 -Stop        # 全部停掉

.NOTES
    如果本机安装路径和下面不一致，改「路径配置」那一段即可。
    MinIO 的用户名/密码不写在这里（避免明文进版本库），
    而是从 Jwt-backend/src/main/resources/application-local.yml 读取。
#>
param(
    [switch]$Stop,
    [switch]$Status
)

$ErrorActionPreference = 'Continue'

# ==================== 路径配置（与本机不符时改这里） ====================
$RedisHome  = 'D:\app\Programme\Redis'
$MinioHome  = 'D:\app\Programme\minio'
$MinioData  = 'D:\minio-data'
$BackendDir = Join-Path $PSScriptRoot 'Jwt-backend'
$LocalYml   = Join-Path $BackendDir 'src\main\resources\application-local.yml'

# Elasticsearch 目录带版本号，自动找
$EsHome = (Get-ChildItem 'D:\app\Programme' -Directory -Filter 'elasticsearch-*' -ErrorAction SilentlyContinue |
           Select-Object -First 1).FullName

# ==================== 端口 ====================
$Ports = @{ Redis = 6379; Elasticsearch = 9200; MinIO = 9000 }

function Test-Port([int]$Port) {
    $hit = netstat -ano | Select-String (':' + $Port + '\s+.*LISTENING')
    return [bool]$hit
}

function Get-PortPid([int]$Port) {
    $line = netstat -ano | Select-String (':' + $Port + '\s+.*LISTENING') | Select-Object -First 1
    if (-not $line) { return $null }
    return [int](($line.ToString().Trim() -split '\s+')[-1])
}

function Show-Status {
    Write-Host ''
    Write-Host '中间件状态' -ForegroundColor Cyan
    Write-Host ('-' * 46)
    foreach ($name in 'Redis', 'Elasticsearch', 'MinIO') {
        $port = $Ports[$name]
        if (Test-Port $port) {
            Write-Host ("  {0,-16} 运行中   (:{1})" -f $name, $port) -ForegroundColor Green
        } else {
            Write-Host ("  {0,-16} 未启动   (:{1})" -f $name, $port) -ForegroundColor DarkGray
        }
    }
    # 这两个是 Windows 服务，顺带显示一下
    foreach ($svc in 'MySQL80', 'RabbitMQ') {
        $s = Get-Service -Name $svc -ErrorAction SilentlyContinue
        if ($s) {
            $color = if ($s.Status -eq 'Running') { 'Green' } else { 'Yellow' }
            Write-Host ("  {0,-16} {1}   (Windows 服务)" -f $svc, $s.Status) -ForegroundColor $color
        }
    }
    Write-Host ''
}

# ==================== 停止 ====================
if ($Stop) {
    Write-Host ''
    Write-Host '正在停止中间件...' -ForegroundColor Yellow
    foreach ($name in 'MinIO', 'Elasticsearch', 'Redis') {
        $port = $Ports[$name]
        $procId = Get-PortPid $port
        if ($procId) {
            Write-Host ("  停止 {0} (PID={1})" -f $name, $procId)
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        } else {
            Write-Host ("  {0} 本来就没在运行" -f $name) -ForegroundColor DarkGray
        }
    }
    Start-Sleep -Seconds 2
    Show-Status
    return
}

# ==================== 只看状态 ====================
if ($Status) {
    Show-Status
    return
}

# ==================== 启动 ====================
Write-Host ''
Write-Host '启动中间件（每项各开一个新窗口，日志在那里看）' -ForegroundColor Cyan
Write-Host ('-' * 46)

# ---- Redis ----
if (Test-Port $Ports.Redis) {
    Write-Host '  Redis           已经在跑，跳过' -ForegroundColor DarkGray
} else {
    $redisExe  = Join-Path $RedisHome 'redis-server.exe'
    $redisConf = Join-Path $RedisHome 'redis.windows.conf'
    if (-not (Test-Path $redisExe)) {
        Write-Host ("  Redis           找不到 {0}" -f $redisExe) -ForegroundColor Red
    } else {
        Start-Process -FilePath $redisExe -ArgumentList $redisConf -WorkingDirectory $RedisHome
        Write-Host '  Redis           已启动（新窗口）' -ForegroundColor Green
    }
}

# ---- Elasticsearch（启动最慢，先发出去） ----
if (Test-Port $Ports.Elasticsearch) {
    Write-Host '  Elasticsearch   已经在跑，跳过' -ForegroundColor DarkGray
} elseif (-not $EsHome) {
    Write-Host '  Elasticsearch   在 D:\app\Programme 下没找到 elasticsearch-* 目录' -ForegroundColor Red
} else {
    Start-Process -FilePath (Join-Path $EsHome 'bin\elasticsearch.bat') -WorkingDirectory $EsHome
    Write-Host '  Elasticsearch   已启动（新窗口，首次约 30 秒才就绪）' -ForegroundColor Green
}

# ---- MinIO ----
if (Test-Port $Ports.MinIO) {
    Write-Host '  MinIO           已经在跑，跳过' -ForegroundColor DarkGray
} else {
    $minioExe = Join-Path $MinioHome 'minio.exe'
    if (-not (Test-Path $minioExe)) {
        Write-Host ("  MinIO           找不到 {0}" -f $minioExe) -ForegroundColor Red
    } else {
        # 账号密码从 application-local.yml 读（那个文件不进版本库，避免明文写死在这里）
        $user = 'minioadmin'
        $pass = 'minioadmin'
        if (Test-Path $LocalYml) {
            $yml = Get-Content $LocalYml -Raw
            $m = [regex]::Match($yml, 'access-key:\s*(\S+)')
            if ($m.Success) { $user = $m.Groups[1].Value }
            $m = [regex]::Match($yml, 'secret-key:\s*(\S+)')
            if ($m.Success) { $pass = $m.Groups[1].Value }
        } else {
            Write-Host '                 （没读到 application-local.yml，用默认凭据）' -ForegroundColor Yellow
        }

        # 用 cmd /k 起一个新窗口，先把环境变量设好再启动
        $cmdLine = 'set MINIO_ROOT_USER=' + $user + ' && set MINIO_ROOT_PASSWORD=' + $pass +
                   ' && "' + $minioExe + '" server "' + $MinioData + '" --console-address ":9001"'
        Start-Process -FilePath 'cmd.exe' -ArgumentList '/k', $cmdLine -WorkingDirectory $MinioHome
        Write-Host '  MinIO           已启动（新窗口）' -ForegroundColor Green
    }
}

# ---- 等一等，然后报告结果 ----
Write-Host ''
Write-Host '等待服务就绪...' -ForegroundColor DarkGray
$deadline = (Get-Date).AddSeconds(40)
foreach ($name in 'Redis', 'MinIO', 'Elasticsearch') {
    $port = $Ports[$name]
    while (-not (Test-Port $port) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 700
    }
}

Show-Status

Write-Host '接下来就可以启动后端了：' -ForegroundColor Cyan
Write-Host '  cd Jwt-backend'
Write-Host '  mvn spring-boot:run' -ForegroundColor White
Write-Host ''
Write-Host '提示：MinIO 控制台 http://localhost:9001 ，ES 用 curl http://localhost:9200 验证' -ForegroundColor DarkGray
Write-Host ''
