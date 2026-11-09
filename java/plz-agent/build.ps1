param(
    [string]$PzJar = $env:PZ_JAR,
    [string]$JdkHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-25" }),
    [string]$ShadowDist = (Join-Path $PSScriptRoot "../java-patch/dist"),
    [switch]$SkipGate,
    [switch]$SkipNative,
    [switch]$AcceptDrift,
    [string]$NativeDll,
    [string[]]$ExtraSrc = @()
)
$ErrorActionPreference = "Stop"

function Invoke-Native([string]$exe, [string[]]$argv) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $exe @argv 2>&1 | ForEach-Object { "$_" }
    } finally {
        $ErrorActionPreference = $prev
    }
}

if (-not $PzJar) { $PzJar = "D:\Games\Steam\steamapps\common\ProjectZomboid\projectzomboid.jar" }
if (-not (Test-Path $PzJar)) { throw "game jar not found: $PzJar" }
if (-not (Test-Path $ShadowDist)) { throw "java-patch dist not found: $ShadowDist (run java-patch\build.ps1 first)" }

$onWindows = [System.IO.Path]::DirectorySeparatorChar -eq [char]92
$exeSuffix = $(if ($onWindows) { ".exe" } else { "" })
$sep = [string][System.IO.Path]::PathSeparator
$javac = Join-Path (Join-Path $JdkHome "bin") "javac$exeSuffix"
$java = Join-Path (Join-Path $JdkHome "bin") "java$exeSuffix"
$jarExe = Join-Path (Join-Path $JdkHome "bin") "jar$exeSuffix"
$libDir = Join-Path $PSScriptRoot "lib"
$outDir = Join-Path $PSScriptRoot "dist"
$classes = Join-Path $outDir "classes"

$locked = Get-Content (Join-Path $PSScriptRoot "libs.lock") | Where-Object { $_ -and -not $_.StartsWith("#") }
$libs = @()
foreach ($line in $locked) {
    $parts = $line -split "\s+"
    $sha = $parts[0]; $file = $parts[1]; $url = $parts[2]
    $path = Join-Path $libDir $file
    if (-not (Test-Path $path)) {
        New-Item -ItemType Directory -Force $libDir | Out-Null
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $path
    }
    $actual = (Get-FileHash -Algorithm SHA256 $path).Hash.ToLower()
    if ($actual -ne $sha) { throw "$file sha256 $actual does not match libs.lock $sha" }
    $libs += $path
}

if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force $classes | Out-Null

$srcRoots = @((Join-Path $PSScriptRoot "src")) + @($ExtraSrc | Where-Object { $_ } | ForEach-Object { (Resolve-Path $_).Path })
$sources = @($srcRoots | ForEach-Object { Get-ChildItem -Recurse -Path $_ -Filter "*.java" } | ForEach-Object { $_.FullName })
$cp = (@($PzJar, (Resolve-Path $ShadowDist).Path) + $libs) -join $sep
Invoke-Native $javac (@("--release", "25", "-nowarn", "-encoding", "UTF-8", "-cp", $cp, "-d", $classes) + $sources)
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }

Copy-Item -Recurse (Join-Path (Join-Path $PSScriptRoot "resources") "*") $classes

$mixinNames = @(foreach ($root in $srcRoots) {
        $mixinRoot = Join-Path $root "plz/mixin"
        if (Test-Path $mixinRoot) {
            Get-ChildItem -Recurse -Path $mixinRoot -Filter "*Mixin.java" | ForEach-Object {
                $_.FullName.Substring($mixinRoot.Length + 1) -replace '[\\/]', '.' -replace '\.java$', ''
            }
        }
    })
$dupes = @($mixinNames | Group-Object | Where-Object Count -gt 1 | ForEach-Object Name)
if ($dupes.Count -gt 0) { throw "mixin defined in more than one source root: $($dupes -join ', ')" }
$mixinNames = @($mixinNames | Sort-Object)
if ($mixinNames.Count -eq 0) { throw "no mixins under $($srcRoots -join ', ')" }
$configPath = Join-Path $classes "plz.mixins.json"
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$config.mixins = $mixinNames
[System.IO.File]::WriteAllText($configPath, ($config | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding($false)))
"mixins   : $($mixinNames.Count) registered from $($srcRoots.Count) source root(s)"

$manifest = Join-Path $outDir "MANIFEST.MF"
$classPath = ($libs | ForEach-Object { "lib/" + (Split-Path $_ -Leaf) }) -join " "
$lines = @("Premain-Class: plz.agent.PLZAgent", "Class-Path: $classPath")
$wrapped = foreach ($l in $lines) {
    $first = $true
    while ($l.Length -gt 0) {
        $take = [Math]::Min($l.Length, $(if ($first) { 70 } else { 69 }))
        $(if ($first) { "" } else { " " }) + $l.Substring(0, $take)
        $l = $l.Substring($take); $first = $false
    }
}
[System.IO.File]::WriteAllText($manifest, (($wrapped -join "`r`n") + "`r`n"), (New-Object System.Text.UTF8Encoding($false)))

$agentJar = Join-Path $outDir "plz-agent.jar"
Invoke-Native $jarExe @("--create", "--file", $agentJar, "--manifest", $manifest, "-C", $classes, ".")
if ($LASTEXITCODE -ne 0) { throw "jar failed ($LASTEXITCODE)" }
New-Item -ItemType Directory -Force (Join-Path $outDir "lib") | Out-Null
$libs | ForEach-Object { Copy-Item $_ (Join-Path $outDir "lib") }
"agent    : $agentJar"

if ($NativeDll) {
    Copy-Item -LiteralPath $NativeDll -Destination (Join-Path $outDir "plz-native.dll")
    "native   : $NativeDll (prebuilt)"
} elseif (-not $SkipNative) {
    if (-not $onWindows) { throw "plz-native.dll builds with MSVC only; pass -NativeDll or -SkipNative" }
    Invoke-Native "cmd.exe" @("/c", (Join-Path $PSScriptRoot "native/build.cmd"))
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path (Join-Path $outDir "plz-native.dll"))) { throw "native build failed ($LASTEXITCODE)" }
    "native   : $(Join-Path $outDir 'plz-native.dll')"
}
"game jar : $PzJar  sha256 $((Get-FileHash -Algorithm SHA256 $PzJar).Hash)"

if ($SkipGate) { return }

$gateOut = Join-Path $outDir "gate"
New-Item -ItemType Directory -Force $gateOut | Out-Null
Invoke-Native $javac @("--release", "25", "-nowarn", "-cp", ($libs -join $sep), "-d", $gateOut,
    (Join-Path $PSScriptRoot "test/PLZMixinGate.java"), (Join-Path $PSScriptRoot "test/PLZBytecodeCheck.java"),
    (Join-Path $PSScriptRoot "test/PLZDriftCheck.java"))
if ($LASTEXITCODE -ne 0) { throw "gate javac failed ($LASTEXITCODE)" }
$runCp = @((Resolve-Path $ShadowDist).Path, $PzJar, $gateOut) -join $sep
$checkCp = (@((Resolve-Path $ShadowDist).Path, $PzJar, $gateOut, $classes) + $libs) -join $sep
foreach ($side in @("client", "server")) {
    # Mixin empties .mixin.out under its working directory at startup, so each side exports apart.
    $sideDir = Join-Path $outDir "gate-$side"
    New-Item -ItemType Directory -Force $sideDir | Out-Null
    Push-Location $sideDir
    try {
        Invoke-Native $java @("-javaagent:$agentJar", "-Dplz.side=$side", "-Dmixin.debug.export=true", "-cp", $runCp, "PLZMixinGate", $agentJar) |
            ForEach-Object { "[$side] $_" }
        $gate = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($gate -ne 0) { throw "MIXIN GATE FAILED on the $side side (exit $gate): a mixin did not apply to $PzJar" }
    Invoke-Native $java @("-cp", $checkCp, "PLZBytecodeCheck", (Join-Path $sideDir ".mixin.out/class")) | ForEach-Object { "[$side] $_" }
    if ($LASTEXITCODE -ne 0) { throw "BYTECODE CHECK FAILED on the $side side: a transformed class does not verify" }
}

$probeOut = Invoke-Native $java @("-javaagent:$agentJar", "-Dplz.side=server", "-cp", $runCp, "zombie.network.ServerMap")
if ($LASTEXITCODE -eq 86) {
    $probeOut | Select-Object -First 5
    throw "MAIN-CLASS PROBE FAILED: a mixin target loaded as the JVM's first class is refused (the dedicated server's main class is one)"
}
"probe    : a mixin target as the JVM's first class transforms cleanly"

$driftArgs = @("-cp", ((@($gateOut) + $libs) -join $sep), "PLZDriftCheck", $agentJar, $PzJar, (Join-Path $PSScriptRoot "drift-baseline.json"))
if ($AcceptDrift) { $driftArgs += "accept" }
Invoke-Native $java $driftArgs
if ($LASTEXITCODE -ne 0) { throw "DRIFT CHECK FAILED: vanilla code under a PLZ mixin changed; review, then rebuild with -AcceptDrift" }
