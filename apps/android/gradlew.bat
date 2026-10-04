@echo off
setlocal
if defined JAVA_HOME (set "TASK_JAVA=%JAVA_HOME%\bin\java.exe") else (set "TASK_JAVA=java.exe")
"%TASK_JAVA%" -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %errorlevel%
