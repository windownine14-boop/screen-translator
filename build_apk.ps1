<#
.SYNOPSIS
    Script ช่วยสร้างไฟล์ .apk สำหรับแอป Screen Translator
#>

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   Screen Translator - APK Build Helper   " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. ตรวจสอบ Java (JDK 17)
$javaInstalled = $false
try {
    $javaVer = java -version 2>&1
    if ($LASTEXITCODE -eq 0) {
        $javaInstalled = $true
        Write-Host "[✓] ตรวจพบ Java บนเครื่อง" -ForegroundColor Green
    }
} catch {
    $javaInstalled = $false
}

if (-not $javaInstalled) {
    Write-Host "[!] ยังไม่พบ Java (JDK 17) ในเครื่อง" -ForegroundColor Yellow
    Write-Host "    คำแนะนำสำหรับการสร้างไฟล์ .apk:" -ForegroundColor White
    Write-Host "    วิธีที่ 1: นำโฟลเดอร์นี้เปิดด้วยโปรแกรม 'Android Studio' แล้วกดเมนู Build > Build APK" -ForegroundColor Cyan
    Write-Host "    วิธีที่ 2: อัปโหลดโฟลเดอร์นี้ขึ้น GitHub ระบบ GitHub Actions จะคอมไพล์เป็น .apk ให้ดาวน์โหลดฟรีอัตโนมัติ" -ForegroundColor Cyan
    Write-Host "    วิธีที่ 3: ติดตั้ง OpenJDK 17 ผ่านคำสั่ง: winget install EclipseAdoptium.Temurin.17.JDK" -ForegroundColor Cyan
    Write-Host ""
    exit
}

# 2. คอมไพล์ด้วย Gradle Wrapper
Write-Host "[*] กำลังเริ่มกระบวนการคอมไพล์ APK ผ่าน Gradle..." -ForegroundColor Cyan
.\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "[✓] สร้างไฟล์ APK สำเร็จเรียบร้อยแล้ว!" -ForegroundColor Green
    Write-Host "    ตำแหน่งไฟล์: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "[X] เกิดข้อผิดพลาดในการคอมไพล์ กรุณาตรวจสอบ Android SDK ในเครื่อง" -ForegroundColor Red
}
