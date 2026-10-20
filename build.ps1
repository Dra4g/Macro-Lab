param(
    [string]$Jdk = "$env:USERPROFILE\.jdks\jdk-26.0.2.1+1",
    [switch]$Test
)
$ErrorActionPreference = 'Stop'
$compiler = Join-Path $Jdk 'bin\javac.exe'
$java = Join-Path $Jdk 'bin\java.exe'
$jar = Join-Path $Jdk 'bin\jar.exe'
if (-not (Test-Path -LiteralPath $compiler)) { throw 'Indica il JDK 26 con -Jdk <directory>.' }
$build = Join-Path $PSScriptRoot ('out\build-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
$classes = Join-Path $build 'classes'
$tests = Join-Path $build 'tests'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'macrolab') -Filter '*.java' | ForEach-Object FullName)
& $compiler -encoding UTF-8 --release 26 -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilazione fallita.' }
if ($Test) {
    New-Item -ItemType Directory -Path $tests -Force | Out-Null
    $testSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\macrolab') -Filter '*.java' | ForEach-Object FullName)
    & $compiler -encoding UTF-8 --release 26 -cp $classes -d $tests @testSources
    if ($LASTEXITCODE -ne 0) { throw 'Compilazione dei test fallita.' }
    & $java '-Djava.awt.headless=true' -cp "$classes;$tests" macrolab.PatternStudyTest
    if ($LASTEXITCODE -ne 0) { throw 'Test falliti.' }
}
$artifact = Join-Path $build 'MacroLab.jar'
& $jar --create --file $artifact --main-class macrolab.MacroLab -C $classes macrolab
if ($LASTEXITCODE -ne 0) { throw 'Creazione JAR fallita.' }
Write-Output $artifact
