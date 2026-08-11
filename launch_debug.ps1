$ErrorActionPreference = 'Stop'
Write-Host 'Building and installing...'
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew installDebug

Write-Host 'Launching app in debug wait mode...'
& $env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe shell am start -D -n com.aistudio.missioncontrol.pxytwe/.MainActivity

Start-Sleep -Seconds 2

Write-Host 'Finding Process ID...'
$pidOutput = & $env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe shell "pidof com.aistudio.missioncontrol.pxytwe"
$pidNum = $pidOutput.Trim()
if ([string]::IsNullOrWhiteSpace($pidNum)) {
    Write-Host 'Failed to find PID! Magisk might be hiding it. Attempting with root...'
    $pidOutput = & $env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe shell "su -c 'pidof com.aistudio.missioncontrol.pxytwe'"
    $pidNum = $pidOutput.Trim()
}

if (-not [string]::IsNullOrWhiteSpace($pidNum)) {
    Write-Host "Found PID: $pidNum"
    Write-Host 'Clearing old forwards...'
    & $env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe forward --remove-all
    Write-Host 'Forwarding JDWP port 5005...'
    & $env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe forward tcp:5005 jdwp:$pidNum
    Write-Host '==================================================='
    Write-Host 'SUCCESS! The app is waiting.'
    Write-Host 'Go to Android Studio, select "Remote Debug" from the dropdown,'
    Write-Host 'and click the green PLAY (Run) button to attach!'
    Write-Host '==================================================='
} else {
    Write-Host 'Could not resolve PID even with root.'
}
