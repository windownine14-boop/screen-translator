# Screen Translator (แอปแปลหน้าจอโทรศัพท์เป็นภาษาไทยแบบเรียลไทม์)

แอปพลิเคชันสำหรับแปลภาษาบนหน้าจอมือถือ Android เป็นภาษาไทยแบบเรียลไทม์ รองรับทั้งการเล่นเกม (Game Translation), อ่านการ์ตูน/มังงะ (Manga Translation), ดูคลิปวิดีโอ และแปลบทสนทนาในแอปต่างๆ โดยมี **ปุ่มลอย (Floating Bubble)** สามารถเปิด/ปิด และย้ายตำแหน่งได้อิสระ

---

## 🌟 คุณสมบัติเด่น (Key Features)

1. **Floating Bubble ควบคุมง่าย:**
   - แตะ 1 ครั้งเพื่อ **Snap & Translate** แปลข้อความบนหน้าจอนั้นทันที
   - ปุ่มลอยสามารถลากไปได้ทุกจุด และจะดูดติดขอบจอ (Edge Snapping) อัตโนมัติเมื่อปล่อยมือ
   - ปรับความโปร่งแสงอัตโนมัติเมื่อไม่ได้ใช้งาน ไม่บังสายตา
2. **2 โหมดการทำงาน:**
   - **โหมด Snap (แตะแปล):** สแกนและแปลเฉพาะตอนแตะ ประหยัดแบตเตอรี่สูงสุด เครื่องไม่ร้อน
   - **โหมด Live Auto (แปลสดต่อเนื่อง):** อัปเดตคำแปลอัตโนมัติต่อเนื่องทุกๆ 1.5 วินาที
3. **2 สไตล์การแสดงผลคำแปล:**
   - **In-Place Replacement:** วาดกล่องข้อความทับลงบนตำแหน่งเดิม ปรับขนาดฟอนต์ให้พอดีกรอบอัตโนมัติ (เหมาะกับมังงะ/เกม)
   - **Subtitle Bar:** แถบซับไตเติลสีดำด้านล่างจอ (เหมาะกับดูวิดีโอ)
4. **AI & OCR Model (ออฟไลน์ 100% ไม่เสียค่าเน็ต):**
   - ใช้ **Google ML Kit Text Recognition** (รองรับ อังกฤษ, ญี่ปุ่น, จีน, เกาหลี)
   - ใช้ **Google ML Kit On-Device Translation** แปลเป็นภาษาไทยได้โดยตรง ทำงานเร็วระดับเสี้ยววินาที (~50-100ms)
   - มีระบบรองรับ **Gemini 2.0 Flash API** สำหรับผู้ที่ต้องการแปลศัพท์แสลงหรือมังงะเนื้อเรื่องซับซ้อน
5. **เบาเครื่อง ไร้ไฟล์ขยะ (0 MB Cache):**
   - ประมวลผลบน RAM ชั่วคราว (In-Memory Buffer) และสั่ง `.close()` ทำลายทิ้งทันที
   - ไม่มีการบันทึกไฟล์ภาพหน้าจอลงเครื่องหรือแกลเลอรีรูปภาพแม้แต่ภาพเดียว

---

## 🚀 วิธีการสร้างไฟล์ `.apk` (Build APK)

คุณสามารถเลือกวิธีสร้างไฟล์ `.apk` ได้ตามความสะดวก 3 รูปแบบ:

### วิธีที่ 1: ใช้ GitHub Actions (แนะนำที่สุด - ง่ายสุด ไม่ต้องติดตั้งอะไรในคอม)
โปรเจกต์นี้มีไฟล์ [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) เตรียมไว้ให้แล้ว:
1. นำโฟลเดอร์โปรเจกต์นี้ขึ้น GitHub Repository ของคุณ (รองรับทั้ง Public และ Private)
2. เมื่อ Push โค้ดขึ้นไป ระบบ GitHub Actions จะทำการติดตั้ง Java 17 และ Android SDK บนคลาวด์ พร้อมคอมไพล์เป็น `.apk` ให้อัตโนมัติในเวลาประมาณ 2 นาที
3. เข้าไปที่แท็บ **Actions** บน GitHub แล้วดาวน์โหลดไฟล์ `ScreenTranslator-Debug-APK` ไปติดตั้งบนมือถือได้ทันที

### วิธีที่ 2: เปิดด้วย Android Studio (สำหรับนักพัฒนา)
1. เปิดโปรแกรม **Android Studio**
2. เลือกเมนู **File > Open...** แล้วเลือกโฟลเดอร์ `e:\.Ai Project\App realtime translate`
3. รอให้ Gradle โหลด dependencies เสร็จเรียบร้อย
4. ไปที่เมนูด้านบนเลือก **Build > Build Bundle(s) / APK(s) > Build APK(s)**
5. ไฟล์จะอยู่ที่: `app/build/outputs/apk/debug/app-debug.apk`

### วิธีที่ 3: คอมไพล์ในเครื่องผ่าน PowerShell
หากในเครื่องมี Java (JDK 17) และ Android SDK อยู่แล้ว สามารถเปิด PowerShell ในโฟลเดอร์นี้แล้วรัน:
```powershell
.\build_apk.ps1
```
หรือ
```bash
./gradlew assembleDebug
```

---

## 📱 วิธีติดตั้งและเปิดใช้งานบนมือถือ Android

1. ส่งไฟล์ `app-debug.apk` เข้ามือถือ (ผ่านสาย USB, Google Drive, หรือดาวน์โหลดจาก GitHub)
2. แตะไฟล์ `.apk` เพื่อติดตั้ง (กดยอมรับ "ติดตั้งจากแหล่งที่ไม่รู้จัก" หรือ Unknown Sources)
3. เปิดแอป **Screen Translator**:
   - กดปุ่ม **"ตั้งค่า"** เพื่อเปิดสิทธิ์ **"แสดงทับแอปอื่น (Display over other apps)"**
   - กดปุ่ม **"อนุญาต"** สำหรับสิทธิ์ **"บันทึกหน้าจอ (MediaProjection)"**
4. เลื่อนสวิตช์เปิด **"ระบบกำลังทำงาน"**
5. ปุ่มลอยวงกลมจะปรากฏขึ้นบนหน้าจอ คุณสามารถกดปุ่ม Home เพื่อกลับไปหน้าจอหลัก แล้วเปิดเกมหรือแอปที่ต้องการแปล แตะที่ปุ่มลอยเพื่อเริ่มการแปลได้ทันที!

---

## 📂 โครงสร้างซอร์สโค้ด (Project Structure)

```
app/src/main/
├── AndroidManifest.xml                  # ประกาศสิทธิ์ Overlay, MediaProjection, Foreground Service
├── java/com/screentranslate/app/
│   ├── MainActivity.kt                  # หน้าจอตั้งค่า และตัวจัดการ Flow สิทธิ์
│   ├── model/
│   │   └── Models.kt                    # Data Classes (RecognizedTextItem, Mode, Language)
│   ├── engine/
│   │   ├── OCRManager.kt                # ตัวอ่านข้อความ ML Kit Text Recognition
│   │   ├── TranslationEngine.kt         # Interface สำหรับโมเดลแปล
│   │   ├── MLKitTranslator.kt           # โมเดลแปลภาษาออฟไลน์ (ML Kit On-Device)
│   │   └── GeminiTranslator.kt          # โมเดลแปลภาษาออนไลน์ (Google Gemini Flash API)
│   ├── service/
│   │   ├── ScreenCaptureService.kt      # Foreground Service จับภาพหน้าจอและคืน RAM
│   │   ├── FloatingBubbleService.kt     # จัดการปุ่มลอยและการลากดูดขอบจอ
│   │   └── TranslationOverlayView.kt    # วาดกล่องคำแปลทับตำแหน่งเดิม (In-Place) และ Subtitle
│   └── ui/
│       ├── MainScreen.kt                # UI หน้าตั้งค่าหลักด้วย Jetpack Compose
│       └── theme/                       # ธีมสี Dark Mode ทันสมัย
└── res/                                 # Drawables, Icons, Strings, Colors
```
