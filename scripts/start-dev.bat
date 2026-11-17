@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
rem ============================================================
rem  机器学习可视化平台 - Windows 启动脚本
rem  用法: scripts\start-dev.bat            数据库 + 后端 + 前端
rem        scripts\start-dev.bat --platform 再加主平台前端(5173)
rem  停止: scripts\stop-dev.bat [--mysql]
rem  依赖: JDK 17 / Maven / Node 18+ / MySQL 8（只需安装，无需建库建号）
rem  任何依赖缺失或端口被别的程序占用，脚本会直接报错退出
rem ============================================================

set "ROOT=%~dp0.."
pushd "%ROOT%"
set "DEV=%ROOT%\.dev"
set "ENV_FILE=%DEV%\env"
set "WITH_PLATFORM="
if /I "%~1"=="--platform" set "WITH_PLATFORM=1"
if not exist "%DEV%" mkdir "%DEV%"

echo [1/6] 准备数据库账号
if not exist "%ENV_FILE%" (
  echo       生成 .dev\env（随机数据库口令）
  powershell -NoProfile -Command "$p = -join ((48..57)+(65..90)+(97..122) | Get-Random -Count 24 | ForEach-Object {[char]$_}); Set-Content -Path '%ENV_FILE%' -Value @('# 本地开发数据库账号（已 gitignore）','DB_USERNAME=ct_app','DB_PASSWORD=' + $p,'DB_PORT=3308') -Encoding ASCII"
)
for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
  if not "%%a"=="" set "%%a=%%b"
)
if "%DB_USERNAME%"=="" set "DB_USERNAME=ct_app"
if "%DB_PORT%"=="" set "DB_PORT=3308"
if "%DB_PASSWORD%"=="" (
  echo [错误] .dev\env 里缺少 DB_PASSWORD
  goto :fail
)

echo [2/6] 检查 JDK 17
set "JDK17="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
  "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /C:"version \"17" >nul && set "JDK17=%JAVA_HOME%"
)
if not defined JDK17 for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-17*") do if not defined JDK17 set "JDK17=%%~d"
if not defined JDK17 for /d %%d in ("%ProgramFiles%\Java\jdk-17*") do if not defined JDK17 set "JDK17=%%~d"
if not defined JDK17 for /d %%d in ("%ProgramFiles%\Microsoft\jdk-17*") do if not defined JDK17 set "JDK17=%%~d"
if not defined JDK17 for /d %%d in ("%ProgramFiles%\Zulu\zulu-17*") do if not defined JDK17 set "JDK17=%%~d"
if not defined JDK17 (
  echo [错误] 找不到 JDK 17。请安装 Temurin 17，或把 JAVA_HOME 指向它。
  goto :fail
)
set "JAVA_HOME=%JDK17%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo       使用 %JDK17%

echo [3/6] 检查 Maven / Node / MySQL
where mvn >nul 2>&1
if errorlevel 1 (
  echo [错误] 找不到 mvn，请安装 Maven 并加入 PATH。
  goto :fail
)
where node >nul 2>&1
if errorlevel 1 (
  echo [错误] 找不到 node，请安装 Node.js 18+ 并加入 PATH。
  goto :fail
)
where npm >nul 2>&1
if errorlevel 1 (
  echo [错误] 找不到 npm，请检查 Node.js 安装。
  goto :fail
)

set "MYSQL_BASE="
if defined MYSQL_HOME if exist "%MYSQL_HOME%\bin\mysqld.exe" set "MYSQL_BASE=%MYSQL_HOME%"
if not defined MYSQL_BASE for /d %%d in ("%ProgramFiles%\MySQL\MySQL Server 8.*") do if not defined MYSQL_BASE set "MYSQL_BASE=%%~d"
if not defined MYSQL_BASE (
  echo [错误] 找不到 MySQL 8。请安装 MySQL Community Server 8，或用 MYSQL_HOME 指向安装目录。
  goto :fail
)
echo       MySQL: %MYSQL_BASE%

echo       端口预检
call :port_busy 8080
if not errorlevel 1 (
  curl.exe -s -m 2 http://127.0.0.1:8080/api/health 2>nul | findstr /C:"ct-platform-backend" >nul
  if errorlevel 1 (
    echo [错误] 端口 8080 已被其它程序占用（不是本项目的后端），请先释放。
    goto :fail
  )
)
call :port_busy 5174
if not errorlevel 1 (
  curl.exe -s -m 2 http://127.0.0.1:5174/src/views/lab/ExperimentView.vue 2>nul | findstr /C:"VisualizationCanvas" >nul
  if errorlevel 1 (
    echo [错误] 端口 5174 已被其它程序占用（不是本项目的页面），请先释放。
    goto :fail
  )
)
echo       端口检查通过

echo [4/6] 准备项目数据库（端口 %DB_PORT%，不需要管理员权限）
call :port_busy %DB_PORT%
if not errorlevel 1 (
  "%MYSQL_BASE%\bin\mysqladmin.exe" -h127.0.0.1 -P%DB_PORT% -uroot ping >nul 2>&1
  if errorlevel 1 (
    echo [错误] 端口 %DB_PORT% 已被其它程序占用（不是本项目的数据库实例）。
    echo        请释放该端口，或在 .dev\env 里改 DB_PORT。
    goto :fail
  )
  echo       数据库已在运行
  goto :db_ready
)
if not exist "%DEV%\mysql\data\mysql" (
  echo       初始化数据目录 .dev\mysql\data
  "%MYSQL_BASE%\bin\mysqld.exe" --no-defaults --initialize-insecure --basedir="%MYSQL_BASE%" --datadir="%DEV%\mysql\data" --log-error="%DEV%\mysql\init.err"
  if errorlevel 1 (
    echo [错误] 数据库初始化失败，请查看 .dev\mysql\init.err
    goto :fail
  )
)
echo       启动 mysqld
start "ML-MySQL" /min "%MYSQL_BASE%\bin\mysqld.exe" --no-defaults --basedir="%MYSQL_BASE%" --datadir="%DEV%\mysql\data" --port=%DB_PORT% --bind-address=127.0.0.1 --mysqlx=OFF --log-error="%DEV%\mysql\mysqld.log"
call :wait_mysql
if errorlevel 1 goto :fail

:db_ready
> "%DEV%\bootstrap.sql" echo CREATE DATABASE IF NOT EXISTS ct_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
>> "%DEV%\bootstrap.sql" echo CREATE USER IF NOT EXISTS '%DB_USERNAME%'@'%%' IDENTIFIED BY '%DB_PASSWORD%';
>> "%DEV%\bootstrap.sql" echo GRANT ALL PRIVILEGES ON ct_platform.* TO '%DB_USERNAME%'@'%%';
>> "%DEV%\bootstrap.sql" echo FLUSH PRIVILEGES;
"%MYSQL_BASE%\bin\mysql.exe" -h127.0.0.1 -P%DB_PORT% -uroot < "%DEV%\bootstrap.sql"
if errorlevel 1 (
  echo [错误] 建库 / 建账号失败
  goto :fail
)
"%MYSQL_BASE%\bin\mysql.exe" -h127.0.0.1 -P%DB_PORT% -uroot < backend\src\main\resources\schema.sql
if errorlevel 1 (
  echo [错误] 导入 schema.sql 失败
  goto :fail
)

echo [5/6] 构建并启动后端
call :port_busy 8080
if not errorlevel 1 (
  curl.exe -s -m 2 http://127.0.0.1:8080/api/health 2>nul | findstr /C:"ct-platform-backend" >nul
  if errorlevel 1 (
    echo [错误] 端口 8080 已被其它程序占用（不是本项目的后端）。
    echo        请释放该端口后再运行。
    goto :fail
  )
  echo       后端已在运行
  goto :backend_ready
)
pushd backend
call mvn -B -q -DskipTests package
if errorlevel 1 (
  popd
  echo [错误] 后端构建失败
  goto :fail
)
popd
set "SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:%DB_PORT%/ct_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
pushd backend
start "ML-Backend" /min cmd /c "java -jar target\ct-platform-backend-1.0.0.jar > ..\.dev\backend.log 2>&1"
popd
call :wait_http 8080 120
if errorlevel 1 (
  echo [错误] 后端启动超时，请查看 .dev\backend.log
  goto :fail
)

:backend_ready
echo [6/6] 启动前端
call :port_busy 5174
if not errorlevel 1 (
  curl.exe -s -m 2 http://127.0.0.1:5174/src/views/lab/ExperimentView.vue 2>nul | findstr /C:"VisualizationCanvas" >nul
  if errorlevel 1 (
    echo [错误] 端口 5174 已被其它程序占用（不是本项目的页面）。
    echo        请释放该端口后再运行。
    goto :fail
  )
  echo       前端已在运行
  goto :frontend_ready
)
if not exist "frontend\node_modules" (
  echo       安装前端依赖（首次较慢）
  pushd frontend
  call npm install --no-audit --no-fund
  if errorlevel 1 (
    popd
    echo [错误] npm install 失败
    goto :fail
  )
  popd
)
pushd frontend
start "ML-Frontend" /min cmd /c "npm run dev -- --port 5174 --strictPort > ..\.dev\frontend.log 2>&1"
popd
call :wait_http 5174 90
if errorlevel 1 (
  echo [错误] 前端启动超时，请查看 .dev\frontend.log
  goto :fail
)

:frontend_ready
if defined WITH_PLATFORM (
  set "PLATFORM_DIR=%CT_PLATFORM_DIR%"
  if "!PLATFORM_DIR!"=="" set "PLATFORM_DIR=%USERPROFILE%\projects\ct-platform-v2"
  if not exist "!PLATFORM_DIR!\frontend" (
    echo [错误] 找不到主平台仓库：!PLATFORM_DIR!（可用 CT_PLATFORM_DIR 指定）
    goto :fail
  )
  if not exist "!PLATFORM_DIR!\frontend\node_modules" (
    pushd "!PLATFORM_DIR!\frontend"
    call npm install --no-audit --no-fund
    popd
  )
  pushd "!PLATFORM_DIR!\frontend"
  start "ML-Platform" /min cmd /c "npm run dev -- --port 5173 --strictPort > %DEV%\platform.log 2>&1"
  popd
  echo       主平台前端: http://localhost:5173
)

echo.
echo 启动完成：
echo   自有项目   http://localhost:5174
if defined WITH_PLATFORM echo   主平台     http://localhost:5173  →「人工智能」
echo   后端接口   http://localhost:8080/api/health
echo   数据库     127.0.0.1:%DB_PORT%（.dev\mysql）
echo   日志目录   %DEV%
echo   关闭项目   scripts\stop-dev.bat [--mysql]
echo.
popd
exit /b 0

rem ---------- 子过程 ----------
:port_busy
netstat -ano | findstr /C:":%~1 " | findstr /C:LISTENING >nul 2>&1
exit /b %errorlevel%

:wait_mysql
for /l %%i in (1,1,60) do (
  "%MYSQL_BASE%\bin\mysqladmin.exe" -h127.0.0.1 -P%DB_PORT% -uroot ping >nul 2>&1
  if not errorlevel 1 exit /b 0
  timeout /t 1 >nul
)
echo [错误] MySQL 启动超时，请查看 .dev\mysql\mysqld.log
exit /b 1

:wait_http
for /l %%i in (1,1,%~2) do (
  curl.exe -s -m 2 -o nul http://127.0.0.1:%~1/ >nul 2>&1
  if not errorlevel 1 exit /b 0
  timeout /t 1 >nul
)
exit /b 1

:fail
rem 失败路径上的 pushd 均由调用方先 popd
popd 2>nul
exit /b 1
