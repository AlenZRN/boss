<#
.SYNOPSIS
    AIMIRA Monitor 一键部署脚本 (支持右键运行)
.DESCRIPTION
    自动检查环境 → 生成配置 → 构建镜像 → 启动服务
    支持右键"使用 PowerShell 运行"或终端调用
.EXAMPLE
    .\deploy.ps1                  # 一键部署
    .\deploy.ps1 status           # 查看服务状态
    .\deploy.ps1 restart          # 重启服务
#>

param(
    [ValidateSet("deploy", "start", "stop", "restart", "down", "logs", "status")]
    [string]$Action = "deploy",

    [switch]$NoBuild
)

# 设置窗口标题
$host.UI.RawUI.WindowTitle = "AIMIRA Monitor - 部署"

# 确保在脚本目录下执行 (修复右键运行时目录不对的问题)
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

# 右键运行时窗口不会自动关闭
$IsRightClick = $false
if ($MyInvocation.InvocationName -eq "&") {
    # 通过右键 / 双击 / -File 方式启动
    $IsRightClick = $true
    # 设置控制台编码为 UTF-8
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
}

# ========== 颜色函数 ==========
function Write-Step { Write-Host "`n>> $args" -ForegroundColor Cyan }
function Write-Ok   { Write-Host "   [OK] $args" -ForegroundColor Green }
function Write-Warn { Write-Host "   [!!] $args" -ForegroundColor Yellow }
function Write-Err  { Write-Host "   [XX] $args" -ForegroundColor Red }
function Write-Box  {
    $text = $args[0]
    $line = "-" * ([Math]::Min($text.Length + 4, 80))
    Write-Host "    $line" -ForegroundColor Magenta
    Write-Host "    | $text |" -ForegroundColor Magenta
    Write-Host "    $line" -ForegroundColor Magenta
}

# ========== 结尾暂停 ==========
function Wait-Exit {
    param([int]$Code = 0)
    if ($IsRightClick) {
        Write-Host ""
        Write-Host "   按任意键退出..." -ForegroundColor DarkGray
        $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    }
    exit $Code
}

# ========== 全局异常捕获 ==========
trap {
    Write-Host ""
    Write-Err "脚本异常终止: $_"
    Write-Host "   详细信息: $($_.Exception.Message)" -ForegroundColor DarkYellow
    Write-Host "   位置: $($_.InvocationInfo.ScriptLineNumber) 行" -ForegroundColor DarkYellow
    Wait-Exit -Code 1
}

# ========== 检查依赖 ==========
function Check-Prerequisites {
    Write-Step "检查运行环境..."

    # Docker
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) {
        Write-Err "未找到 Docker，请先安装 Docker Desktop"
        Write-Host "   下载: https://www.docker.com/products/docker-desktop" -ForegroundColor DarkYellow
        Wait-Exit -Code 1
    }

    $dockerInfo = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Docker 未运行，请先启动 Docker Desktop"
        Write-Host "   提示: 双击系统托盘 Docker 图标，等待状态变为 'Engine running'" -ForegroundColor DarkYellow
        Wait-Exit -Code 1
    }
    Write-Ok "Docker 已就绪 ($(docker --version))"

    # Docker Compose
    $compose = docker compose version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "docker compose 可用"
    } else {
        Write-Err "Docker Compose 不可用，请升级 Docker Desktop"
        Wait-Exit -Code 1
    }
}

# ========== 配置 .env ==========
function Setup-Env {
    Write-Step "检查配置文件..."

    if (-not (Test-Path ".env")) {
        Write-Warn ".env 文件不存在，请填写以下配置..."

        # 阿里云 AK
        Write-Host ""
        Write-Box "阿里云 AccessKey (必填)"
        $ak = Read-Host "   ALIYUN_ACCESS_KEY"
        while ([string]::IsNullOrWhiteSpace($ak)) {
            Write-Warn "AccessKey 不能为空!"
            $ak = Read-Host "   ALIYUN_ACCESS_KEY"
        }
        $sk = Read-Host "   ALIYUN_SECRET"
        while ([string]::IsNullOrWhiteSpace($sk)) {
            Write-Warn "Secret 不能为空!"
            $sk = Read-Host "   ALIYUN_SECRET"
        }
        $region = Read-Host "   ALIYUN_REGION (直接回车默认 cn-hangzhou)"
        if ([string]::IsNullOrWhiteSpace($region)) { $region = "cn-hangzhou" }

        # 企业微信
        Write-Host ""
        Write-Box "企业微信机器人 Webhook (可选, 回车跳过)"
        $wecomUrl = Read-Host "   WECOM_WEBHOOK_URL"
        $wecomEnabled = if ([string]::IsNullOrWhiteSpace($wecomUrl)) { "false" } else { "true" }

        # 数据库密码
        Write-Host ""
        $dbPassword = Read-Host "   DB_PASSWORD (直接回车默认 aimira123)"
        if ([string]::IsNullOrWhiteSpace($dbPassword)) { $dbPassword = "aimira123" }

        # 写入 .env
        $envContent = @"
# ===== AIMIRA Monitor 环境配置 =====
# 生成时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')

# 阿里云 AccessKey
ALIYUN_ACCESS_KEY=$ak
ALIYUN_SECRET=$sk
ALIYUN_REGION=$region

# PostgreSQL
DB_PASSWORD=$dbPassword

# 企业微信机器人
WECOM_WEBHOOK_URL=$wecomUrl
WECOM_ENABLED=$wecomEnabled

# 定时任务 (Cron)
SCHEDULER_COLLECT_CRON=0 0 * * * *
SCHEDULER_ALARM_CRON=0 0 * * * *
ALARM_COOLDOWN_SECONDS=86400
"@
        $envContent | Out-File -FilePath ".env" -Encoding utf8
        Write-Ok ".env 已生成"
    } else {
        Write-Ok ".env 已存在, 跳过配置"
        Write-Host "   (如需重新配置请删除 .env 后重新运行)" -ForegroundColor DarkGray
    }
}

# ========== 构建并部署 ==========
function Deploy-Services {
    Write-Step "构建并启动所有服务..."

    if ($NoBuild) {
        Write-Warn "跳过镜像构建 (--NoBuild)"
        docker compose up -d 2>&1 | ForEach-Object { Write-Host "   $_" }
    } else {
        Write-Host "   正在构建应用镜像 (首次需要下载依赖, 请耐心等待)..."
        docker compose up -d --build 2>&1 | ForEach-Object { Write-Host "   $_" }
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Box "部署成功! 等待服务就绪..."
        # 等待几秒让 App 启动
        Start-Sleep -Seconds 5
        Show-Status
    } else {
        Write-Err "部署失败，请检查上方错误信息"
        Wait-Exit -Code 1
    }
}

# ========== 查看状态 ==========
function Show-Status {
    Write-Step "服务运行状态..."
    docker compose ps 2>&1 | ForEach-Object { Write-Host "   $_" }

    Write-Host ""
    Write-Host "   ========================================" -ForegroundColor Green
    Write-Host "     Dashboard API  : http://localhost:8080/api/dashboard/overview" -ForegroundColor Yellow
    Write-Host "     Health Check   : http://localhost:8080/actuator/health" -ForegroundColor Yellow
    Write-Host "   ========================================" -ForegroundColor Green
}

# ========== 查看日志 ==========
function Show-Logs {
    Write-Step "最近 100 行日志 (Ctrl+C 回到菜单)..."
    docker compose logs --tail=100 2>&1 | ForEach-Object { Write-Host "   $_" }
}

# ========== 交互式菜单 (右键运行时) ==========
function Show-InteractiveMenu {
    Write-Host ""
    Write-Host "  ╔══════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "  ║     AIMIRA Monitor - 阿里云监控系统      ║" -ForegroundColor Cyan
    Write-Host "  ╚══════════════════════════════════════════╝" -ForegroundColor Cyan

    Check-Prerequisites

    do {
        Write-Host ""
        Write-Host "  请选择操作:" -ForegroundColor White
        Write-Host "    [1] 一键部署 (检查环境 -> 配置 -> 构建 -> 启动)" -ForegroundColor Yellow
        Write-Host "    [2] 仅启动 (跳过构建)" -ForegroundColor DarkYellow
        Write-Host "    [3] 查看状态" -ForegroundColor Green
        Write-Host "    [4] 查看日志" -ForegroundColor Green
        Write-Host "    [5] 重启服务" -ForegroundColor Cyan
        Write-Host "    [6] 停止服务" -ForegroundColor Cyan
        Write-Host "    [7] 停止并清理" -ForegroundColor Red
        Write-Host "    [Q] 退出" -ForegroundColor White
        Write-Host ""

        $choice = Read-Host "   输入选项"

        switch ($choice) {
            "1" {
                Setup-Env
                Deploy-Services
            }
            "2" {
                Write-Step "启动服务 (跳过构建)..."
                docker compose up -d 2>&1 | ForEach-Object { Write-Host "   $_" }
                Start-Sleep -Seconds 3
                Show-Status
            }
            "3" { Show-Status }
            "4" { Show-Logs }
            "5" {
                Write-Step "重启所有服务..."
                docker compose restart 2>&1 | ForEach-Object { Write-Host "   $_" }
                Start-Sleep -Seconds 3
                Show-Status
            }
            "6" {
                Write-Step "停止所有服务..."
                docker compose stop 2>&1 | ForEach-Object { Write-Host "   $_" }
                Write-Ok "已停止"
            }
            "7" {
                Write-Step "停止并清理容器..."
                docker compose down 2>&1 | ForEach-Object { Write-Host "   $_" }
                Write-Ok "已清理 (数据卷 pgdata 保留)"
                Write-Warn "完全清除数据: docker compose down -v"
            }
            "Q" { Write-Host "   再见!" -ForegroundColor Green; Wait-Exit -Code 0 }
            "q" { Write-Host "   再见!" -ForegroundColor Green; Wait-Exit -Code 0 }
            default { Write-Warn "无效选项, 请重新输入" }
        }
    } while ($true)
}

# ========== 主流程 ==========
if ($IsRightClick) {
    # 右键运行 -> 显示交互菜单 (不闪退)
    Show-InteractiveMenu
} else {
    # 终端带参数调用 -> 直接执行对应操作
    Write-Host ""
    Write-Host "  ╔══════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "  ║     AIMIRA Monitor - 阿里云监控系统      ║" -ForegroundColor Cyan
    Write-Host "  ╚══════════════════════════════════════════╝" -ForegroundColor Cyan

    switch ($Action) {
        "deploy" {
            Check-Prerequisites
            Setup-Env
            Deploy-Services
        }
        "start" {
            Write-Step "启动服务..."
            docker compose up -d 2>&1 | ForEach-Object { Write-Host "   $_" }
            Show-Status
        }
        "stop" {
            Write-Step "停止服务..."
            docker compose stop 2>&1 | ForEach-Object { Write-Host "   $_" }
            Write-Ok "已停止"
        }
        "restart" {
            Write-Step "重启服务..."
            docker compose restart 2>&1 | ForEach-Object { Write-Host "   $_" }
            Show-Status
        }
        "down" {
            Write-Step "停止并清理容器..."
            docker compose down 2>&1 | ForEach-Object { Write-Host "   $_" }
            Write-Ok "已清理 (数据卷 pgdata 保留)"
        }
        "logs" { Show-Logs }
        "status" { Show-Status }
    }
}
