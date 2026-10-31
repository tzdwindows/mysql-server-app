@echo off
setlocal enabledelayedexpansion

echo [1/7] Initializing build environment...
set SDK=D:\android-dev\sdk
set BUILD_TOOLS=%SDK%\build-tools\34.0.0
set PLATFORM=%SDK%\platforms\android-34\android.jar
set JDK=C:\Program Files\Java\jdk-21.0.12.1
set APP_DIR=D:\cppp\mysql-server-app\app
set BUILD_DIR=%APP_DIR%\build

if exist "%BUILD_DIR%\classes" rd /s /q "%BUILD_DIR%\classes"
if exist "%BUILD_DIR%\dex" rd /s /q "%BUILD_DIR%\dex"
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if not exist "%BUILD_DIR%\gen" mkdir "%BUILD_DIR%\gen"
if not exist "%BUILD_DIR%\classes" mkdir "%BUILD_DIR%\classes"
if not exist "%BUILD_DIR%\dex" mkdir "%BUILD_DIR%\dex"

echo [2/7] Compiling Android resources with aapt2...
"%BUILD_TOOLS%\aapt2.exe" compile --dir "%APP_DIR%\res" -o "%BUILD_DIR%\res.zip"
if %ERRORLEVEL% neq 0 (
    echo aapt2 compile failed!
    exit /b 1
)

echo [3/7] Linking resources and generating R.java...
"%BUILD_TOOLS%\aapt2.exe" link -I "%PLATFORM%" --manifest "%APP_DIR%\AndroidManifest.xml" --java "%BUILD_DIR%\gen" -o "%BUILD_DIR%\resources.apk" "%BUILD_DIR%\res.zip" --auto-add-overlay
if %ERRORLEVEL% neq 0 (
    echo aapt2 link failed!
    exit /b 1
)

echo [4/7] Compiling Java sources with javac...
dir /b /s "%APP_DIR%\src\com\mysqllab\*.java" "%BUILD_DIR%\gen\com\mysqllab\*.java" > "%BUILD_DIR%\sources.txt"
"%JDK%\bin\javac.exe" --release 8 -encoding UTF-8 -cp "%PLATFORM%" -d "%BUILD_DIR%\classes" @"%BUILD_DIR%\sources.txt"
if %ERRORLEVEL% neq 0 (
    echo javac compilation failed!
    exit /b 1
)

echo [5/7] Converting bytecode to DEX with d8...
"%JDK%\bin\java.exe" "%APP_DIR%\StripClassAttrs.java" "%BUILD_DIR%\classes"
"%JDK%\bin\jar.exe" -cf "%BUILD_DIR%\classes.jar" -C "%BUILD_DIR%\classes" .
call "%BUILD_TOOLS%\d8.bat" --min-api 29 --lib "%PLATFORM%" --output "%BUILD_DIR%\dex" "%BUILD_DIR%\classes.jar"
if %ERRORLEVEL% neq 0 (
    echo d8 conversion failed!
    exit /b 1
)

echo [6/7] Packaging APK archive...
copy /Y "%BUILD_DIR%\resources.apk" "%BUILD_DIR%\unaligned.apk" > nul
cd /D "%APP_DIR%"
"%JDK%\bin\jar.exe" -uf "%BUILD_DIR%\unaligned.apk" -C "%BUILD_DIR%\dex" classes.dex
"%JDK%\bin\jar.exe" -uf "%BUILD_DIR%\unaligned.apk" lib
"%JDK%\bin\jar.exe" -uf "%BUILD_DIR%\unaligned.apk" assets

"%BUILD_TOOLS%\zipalign.exe" -p -f 4 "%BUILD_DIR%\unaligned.apk" "%BUILD_DIR%\aligned.apk"
if %ERRORLEVEL% neq 0 (
    echo zipalign failed!
    exit /b 1
)

echo [7/7] Signing APK with apksigner...
if not exist "%APP_DIR%\debug.keystore" (
    echo Generating debug signing keystore...
    "%JDK%\bin\keytool.exe" -genkeypair -v -keystore "%APP_DIR%\debug.keystore" -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US" > nul
)

call "%BUILD_TOOLS%\apksigner.bat" sign --ks "%APP_DIR%\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "%APP_DIR%\mysql-server-app.apk" "%BUILD_DIR%\aligned.apk"
if %ERRORLEVEL% neq 0 (
    echo apksigner failed!
    exit /b 1
)

echo Verifying signed APK...
call "%BUILD_TOOLS%\apksigner.bat" verify "%APP_DIR%\mysql-server-app.apk"
if %ERRORLEVEL% neq 0 (
    echo APK signature verification failed!
    exit /b 1
)

echo.
echo ========================================================
echo BUILD SUCCESSFUL: %APP_DIR%\mysql-server-app.apk
echo ========================================================
