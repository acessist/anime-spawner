@echo off
setlocal
cd /d "%~dp0"
if not exist "work\classes" mkdir "work\classes"
if not exist "work\jars" mkdir "work\jars"
javac -encoding UTF-8 -d "work\classes" src\animespawner\*.java
if errorlevel 1 exit /b 1
jar --create --file "work\jars\anime-spawner.jar" --main-class animespawner.AnimeSpawner -C "work\classes" .
if errorlevel 1 exit /b 1
if /I "%~1"=="test" (
    if not exist "work\test-classes" mkdir "work\test-classes"
    javac -encoding UTF-8 -cp "work\classes" -d "work\test-classes" tests\animespawner\*.java
    if errorlevel 1 exit /b 1
    java -cp "work\classes;work\test-classes" animespawner.Tests "work\tests" --ui
    if errorlevel 1 exit /b 1
)
if /I "%~1"=="run" javaw -Xmx1024m -jar "work\jars\anime-spawner.jar" --data-dir "data"
echo Built work\jars\anime-spawner.jar
