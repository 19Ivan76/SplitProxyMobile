@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle not found. Open the project in Android Studio and select Build APK.
  pause
  exit /b 1
)
gradle --no-daemon assembleDebug
if errorlevel 1 exit /b 1
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
