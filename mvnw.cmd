@ECHO OFF
@REM Maven Wrapper for Windows — falls back to system mvn if wrapper jar unavailable
SETLOCAL

SET "BASE_DIR=%~dp0"
SET "WRAPPER_JAR=%BASE_DIR%.mvn\wrapper\maven-wrapper.jar"
SET "WRAPPER_PROPERTIES=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties"

@REM Check if JAVA_HOME is set
IF NOT "%JAVA_HOME%"=="" (
    SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
    SET "JAVA_EXE=java"
)

@REM Read distribution URL from properties
FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%WRAPPER_PROPERTIES%") DO (
    IF "%%A"=="distributionUrl" SET "DISTRIBUTION_URL=%%B"
)

@REM If wrapper jar exists and is a real jar (>10KB), use it
FOR %%F IN ("%WRAPPER_JAR%") DO SET JAR_SIZE=%%~zF
IF EXIST "%WRAPPER_JAR%" IF %JAR_SIZE% GTR 10000 (
    "%JAVA_EXE%" -classpath "%WRAPPER_JAR%" ^
        "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" ^
        org.apache.maven.wrapper.MavenWrapperMain %*
    GOTO :EOF
)

@REM Fallback: use system mvn
WHERE mvn >NUL 2>&1
IF %ERRORLEVEL% EQU 0 (
    ECHO [mvnw] Wrapper JAR not found, using system Maven...
    mvn %*
    GOTO :EOF
)

ECHO.
ECHO ERROR: Neither Maven Wrapper JAR nor system Maven found.
ECHO.
ECHO Please install Maven:
ECHO   1. Download from https://maven.apache.org/download.cgi
ECHO   2. Extract to C:\Program Files\Apache\maven
ECHO   3. Add C:\Program Files\Apache\maven\bin to your PATH
ECHO   4. Restart PowerShell and run: mvn spring-boot:run
ECHO.
ECHO Or install via Scoop:  scoop install maven
ECHO Or install via Winget: winget install Apache.Maven
EXIT /B 1
