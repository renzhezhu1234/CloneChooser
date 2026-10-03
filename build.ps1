param(
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$Sdk,
    [Parameter(Mandatory=$true)][string]$XposedJar,
    [string]$BuildDir = "$PSScriptRoot/build-local"
)
$ErrorActionPreference = 'Stop'
function Checked([scriptblock]$Command) { & $Command; if ($LASTEXITCODE -ne 0) { throw "Build command failed: $LASTEXITCODE" } }
$Jdk = (Resolve-Path $Jdk).Path
$Sdk = (Resolve-Path $Sdk).Path
$XposedJar = (Resolve-Path $XposedJar).Path
$bt = (Get-ChildItem "$Sdk/build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$platform = (Get-ChildItem "$Sdk/platforms" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$android = "$platform/android.jar"
$main = "$PSScriptRoot/app/src/main"
New-Item -ItemType Directory -Force "$BuildDir/classes", "$BuildDir/dex", "$BuildDir/generated" | Out-Null
$BuildDir = (Resolve-Path $BuildDir).Path
Checked { & "$bt/aapt2.exe" compile --dir "$main/res" -o "$BuildDir/resources.zip" }
[xml]$standaloneManifest = Get-Content "$main/AndroidManifest.xml" -Raw
$standaloneManifest.manifest.SetAttribute('package', 'io.github.clonechooser')
$standaloneManifest.Save("$BuildDir/AndroidManifest.xml")
Checked { & "$bt/aapt2.exe" link -I $android --manifest "$BuildDir/AndroidManifest.xml" -A "$main/assets" --java "$BuildDir/generated" --version-code 5 --version-name '0.1.4' -o "$BuildDir/base.apk" "$BuildDir/resources.zip" }
$sources = Get-ChildItem "$main/java" -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
Checked { & "$Jdk/bin/javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath "$android;$XposedJar" -d "$BuildDir/classes" $sources }
Checked { & "$Jdk/bin/jar.exe" cf "$BuildDir/classes.jar" -C "$BuildDir/classes" . }
Checked { & "$Jdk/bin/java.exe" -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --min-api 30 --lib $android --classpath $XposedJar --output "$BuildDir/dex" "$BuildDir/classes.jar" }
Copy-Item "$BuildDir/base.apk" "$BuildDir/unsigned.apk" -Force
Checked { & "$Jdk/bin/jar.exe" uf "$BuildDir/unsigned.apk" -C "$BuildDir/dex" classes.dex }
Checked { & "$bt/zipalign.exe" -f 4 "$BuildDir/unsigned.apk" "$BuildDir/aligned.apk" }
if (!(Test-Path "$BuildDir/development.keystore")) {
    Checked { & "$Jdk/bin/keytool.exe" -genkeypair -keystore "$BuildDir/development.keystore" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=CloneChooser Development' }
}
Checked { & "$Jdk/bin/java.exe" -jar "$bt/lib/apksigner.jar" sign --ks "$BuildDir/development.keystore" --ks-pass pass:android --key-pass pass:android --out "$BuildDir/CloneChooser-0.1.4.apk" "$BuildDir/aligned.apk" }
Checked { & "$Jdk/bin/java.exe" -jar "$bt/lib/apksigner.jar" verify --verbose "$BuildDir/CloneChooser-0.1.4.apk" }
Write-Output "$BuildDir/CloneChooser-0.1.4.apk"
