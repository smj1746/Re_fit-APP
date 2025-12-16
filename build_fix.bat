@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jre
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "C:\Users\shuva\AndroidStudioProjects\Re_fit"
echo Starting Gradle build...
echo Current directory: %CD%
dir gradlew.bat
"C:\Users\shuva\AndroidStudioProjects\Re_fit\gradlew.bat" clean assembleDebug --stacktrace
echo Build completed with exit code: %ERRORLEVEL%
