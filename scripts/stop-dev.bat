@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
rem ============================================================
rem  机器学习可视化平台 - Windows 停止脚本
rem  用法: scripts\stop-dev.bat            只关后端与前端
rem        scripts\stop-dev.bat --mysql    连项目数据库一起关
rem ============================================================

set "ROOT=%~dp0.."
set "DEV=%ROOT%\.dev"
set "WITH_MYSQL="
if /I "%~1"=="--mysql" set "WITH_MYSQL=1"

echo 停止项目进程
call :kill_port 8080 "后端"
call :kill_port 5174 "前端"
call :kill_port 5173 "主平台前端"
if defined WITH_MYSQL call :stop_mysql

echo.
echo 端口检查：
for %%p in (8080 5174 5173) do (
  call :port_busy %%p
  if errorlevel 1 (echo   %%p 已释放) else (echo   %%p 仍被占用)
)
echo.
exit /b 0

:kill_port
set "PORT=%~1"
set "NAME=%~2"
set "FOUND="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /C:":%PORT% " ^| findstr /C:LISTENING') do (
  taskkill /F /PID %%p >nul 2>&1
  echo   已停止 %NAME%（端口 %PORT%，PID %%p）
  set "FOUND=1"
)
if not defined FOUND echo   %NAME% 未在运行（端口 %PORT%）
exit /b 0

:port_busy
netstat -ano | findstr /C:":%~1 " | findstr /C:LISTENING >nul 2>&1
exit /b %errorlevel%

:stop_mysql
set "ENV_FILE=%DEV%\env"
if exist "%ENV_FILE%" for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
  if not "%%a"=="" set "%%a=%%b"
)
if "%DB_PORT%"=="" set "DB_PORT=3308"

set "MYSQL_BASE="
if defined MYSQL_HOME if exist "%MYSQL_HOME%\bin\mysqladmin.exe" set "MYSQL_BASE=%MYSQL_HOME%"
if not defined MYSQL_BASE for /d %%d in ("%ProgramFiles%\MySQL\MySQL Server 8.*") do if not defined MYSQL_BASE set "MYSQL_BASE=%%~d"
if not defined MYSQL_BASE (
  echo   找不到 MySQL 安装目录，跳过数据库
  exit /b 0
)
"%MYSQL_BASE%\bin\mysqladmin.exe" -h127.0.0.1 -P%DB_PORT% -uroot shutdown >nul 2>&1
if errorlevel 1 (
  echo   项目数据库未运行（端口 %DB_PORT%）
) else (
  echo   已停止项目数据库（端口 %DB_PORT%）
)
exit /b 0
