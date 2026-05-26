@echo on
set JAVA_HOME=C:\Program Files\Android\Android Studio\jre
cd /d C:\Users\shuva\AndroidStudioProjects\Re_fit
echo Starting build...
call gradlew.bat assembleDebug
echo Build completed with error code: %ERRORLEVEL%
