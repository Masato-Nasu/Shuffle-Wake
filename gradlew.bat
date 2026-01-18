@ECHO OFF
SETLOCAL
SET DIR=%~dp0
SET JAVA_CMD=java
"%JAVA_CMD%" -classpath "%DIR%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
ENDLOCAL
