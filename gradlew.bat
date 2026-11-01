@rem Minimal Gradle wrapper launcher (Windows).
@rem Needs gradle\wrapper\gradle-wrapper.jar -> run setup.bat once to download it.
@if "%DEBUG%"=="" @echo off
setlocal
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
  echo gradle-wrapper.jar is missing. Run setup.bat first. 1>&2
  exit /b 1
)

if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)

"%JAVA_EXE%" -Xmx64m -Xms64m "-Dorg.gradle.appname=gradlew" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
