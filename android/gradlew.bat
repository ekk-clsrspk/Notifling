@rem Gradle wrapper batch script for Windows.
@rem Uses JAVA_HOME if set (JDK 17 required: Gradle 8.7 cannot run on Java 25).
@IF "%DEBUG%"=="" @ECHO OFF
SET DIRNAME=%~dp0
SET APP_HOME=%DIRNAME%
SET CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
IF EXIST "%JAVA_HOME%\bin\java.exe" (
    SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) ELSE (
    SET JAVA_EXE=java
)
"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
