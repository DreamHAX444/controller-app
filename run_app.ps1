$ErrorActionPreference = 'Stop'
Write-Host 'Building and installing...'
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew installDebug

Write-Host 'Launching app...'
& $env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe shell am start -n com.aistudio.missioncontrol.pxytwe/.MainActivity

Write-Host '==================================================='
Write-Host 'SUCCESS! The app is running.'
Write-Host 'Open the "Logcat" tab at the bottom of Android Studio'
Write-Host 'to see your logs!'
Write-Host '==================================================='
