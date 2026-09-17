param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin\javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { 'java' }
$dest = Join-Path $repo 'build\recognition-tests'
New-Item -ItemType Directory -Path $dest -Force | Out-Null
$sources = @('FoodDetailDetector', 'DetailVisitTracker', 'MealNutrition', 'MealNutritionAnalyzer') |
    ForEach-Object { Join-Path $repo "app\src\main\java\com\nutriflow\app\analysis\$_.java" }
$sources += Join-Path $PSScriptRoot 'RecognitionRegression.java'
& $javac -encoding UTF-8 -d $dest @sources
if ($LASTEXITCODE -ne 0) { throw 'Recognition test compilation failed.' }
& $java -cp $dest RecognitionRegression
if ($LASTEXITCODE -ne 0) { throw 'Recognition tests failed.' }
