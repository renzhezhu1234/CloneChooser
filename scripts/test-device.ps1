param(
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$Sdk,
    [string]$Adb,
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
function Checked([scriptblock]$Command) { & $Command; if ($LASTEXITCODE -ne 0) { throw "Test command failed: $LASTEXITCODE" } }
$root = Split-Path $PSScriptRoot -Parent
$Jdk = (Resolve-Path $Jdk).Path
$Sdk = (Resolve-Path $Sdk).Path
if (!$Adb) { $Adb = Join-Path $Sdk 'platform-tools/adb.exe' }
$Adb = (Resolve-Path $Adb).Path
$adbArgs = @()
if ($Serial) { $adbArgs = @('-s', $Serial) }
$bt = (Get-ChildItem "$Sdk/build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$platform = (Get-ChildItem "$Sdk/platforms" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$android = "$platform/android.jar"
$testDir = Join-Path $root 'build-local/device-tests'
New-Item -ItemType Directory -Force "$testDir/classes", "$testDir/dex" | Out-Null
$sources = @(Get-ChildItem "$root/tests" -Filter '*.java' | Select-Object -ExpandProperty FullName)
foreach ($name in @('Config', 'RouteIntents', 'HookContract', 'ReceiverTransport', 'ProfilePolicy')) {
    $sources += "$root/app/src/main/java/io/github/clonechooser/$name.java"
}
Checked { & "$Jdk/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $android -d "$testDir/classes" $sources }
Checked { & "$Jdk/bin/jar.exe" cf "$testDir/test-classes.jar" -C "$testDir/classes" . }
Checked { & "$Jdk/bin/java.exe" -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --min-api 30 --lib $android --output "$testDir/dex" "$testDir/test-classes.jar" }
Checked { & "$Jdk/bin/jar.exe" cf "$testDir/tests.jar" -C "$testDir/dex" classes.dex }
Checked { & $Adb @adbArgs push "$testDir/tests.jar" /data/local/tmp/clonechooser-tests.jar }
try {
    foreach ($test in @('PayloadTest', 'HookContractTest', 'ReceiverTransportTest', 'ProfilePolicyTest')) {
        Checked { & $Adb @adbArgs shell "CLASSPATH=/data/local/tmp/clonechooser-tests.jar app_process /system/bin io.github.clonechooser.$test" }
    }
} finally {
    & $Adb @adbArgs shell rm -f /data/local/tmp/clonechooser-tests.jar
}
