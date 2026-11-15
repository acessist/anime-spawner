param([string]$Destination = (Join-Path $PSScriptRoot 'work/packages'))
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & "$PSScriptRoot/build.cmd"
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    New-Item -ItemType Directory -Force work/tools | Out-Null
    javac -encoding UTF-8 -cp work/classes -d work/tools tools/animespawner/BuildAssets.java
    if ($LASTEXITCODE -ne 0) { throw 'Icon compiler failed' }
    java -cp 'work/classes;work/tools' animespawner.BuildAssets work/anime-spawner.ico
    if ($LASTEXITCODE -ne 0) { throw 'Icon generation failed' }
    New-Item -ItemType Directory -Force $Destination | Out-Null
    jpackage --type app-image --name 'Anime Spawner' --input work/jars --main-jar anime-spawner.jar --main-class animespawner.AnimeSpawner --dest $Destination --app-version 1.1.0 --vendor 'Anime Spawner' --description 'Animated desktop companions' --icon work/anime-spawner.ico --add-modules java.desktop,java.logging,java.xml --java-options '-Xmx1024m'
    if ($LASTEXITCODE -ne 0) { throw 'Packaging failed. Use a fresh destination folder if it already exists.' }
} finally { Pop-Location }
