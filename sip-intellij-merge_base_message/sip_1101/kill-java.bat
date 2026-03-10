@echo off
chcp 65001 >nul
echo ====================================
echo 正在查找并终止所有Java进程...
echo ====================================
echo.

:: 显示当前运行的Java进程
echo 当前运行的Java进程：
tasklist /FI "IMAGENAME eq java.exe" /FI "IMAGENAME eq javaw.exe" 2>nul

echo.
echo 正在终止所有Java进程...
echo.

:: 强制终止所有java.exe进程
taskkill /F /IM java.exe >nul 2>&1
if %errorlevel% equ 0 (
    echo ✓ 已终止 java.exe 进程
) else (
    echo ✗ 没有找到 java.exe 进程
)

:: 强制终止所有javaw.exe进程
taskkill /F /IM javaw.exe >nul 2>&1
if %errorlevel% equ 0 (
    echo ✓ 已终止 javaw.exe 进程
) else (
    echo ✗ 没有找到 javaw.exe 进程
)

echo.
echo ====================================
echo 清理完成！
echo ====================================
echo.

:: 等待3秒后自动关闭
timeout /t 3 >nul
