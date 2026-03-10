# 设置控制台编码为UTF-8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "正在查找并终止所有Java进程..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 查找所有Java进程
$javaProcesses = Get-Process -Name "java", "javaw" -ErrorAction SilentlyContinue

if ($javaProcesses) {
    Write-Host "找到以下Java进程：" -ForegroundColor Yellow
    Write-Host ""

    $javaProcesses | Format-Table -AutoSize @{
        Label="进程ID"; Expression={$_.Id}
    }, @{
        Label="进程名"; Expression={$_.ProcessName}
    }, @{
        Label="内存(MB)"; Expression={[math]::Round($_.WorkingSet64/1MB, 2)}
    }, @{
        Label="启动时间"; Expression={$_.StartTime}
    }

    Write-Host ""
    Write-Host "正在终止这些进程..." -ForegroundColor Yellow

    foreach ($process in $javaProcesses) {
        try {
            Stop-Process -Id $process.Id -Force
            Write-Host "✓ 已终止进程: $($process.ProcessName) (PID: $($process.Id))" -ForegroundColor Green
        } catch {
            Write-Host "✗ 无法终止进程: $($process.ProcessName) (PID: $($process.Id))" -ForegroundColor Red
        }
    }

    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "清理完成！共终止 $($javaProcesses.Count) 个Java进程" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "没有找到运行中的Java进程" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "3秒后自动关闭..." -ForegroundColor Gray
Start-Sleep -Seconds 3
