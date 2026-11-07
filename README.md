# GPS Map V1 — Ultra Clean Android

แอปแผนที่ Android โทน Dark แบบมินิมอล ใช้ Kotlin, Jetpack Compose และ MapLibre Native

## ฟีเจอร์ใน V1
- แผนที่ MapLibre แบบเต็มจอ
- ขอสิทธิ์ GPS และแสดงตำแหน่งปัจจุบัน
- เปลี่ยนสีจุดตำแหน่ง (Cyan / Violet / Amber)
- แตะแผนที่เพื่อกำหนดปลายทาง
- ตั้งต้นทางจากตำแหน่งปัจจุบัน
- วาดเส้นตรงระหว่างต้นทางกับปลายทาง
- แสดงเปอร์เซ็นต์ความคืบหน้าโดยประมาณจากตำแหน่ง GPS เทียบกับแนวเส้นตรง (ไม่ใช่การนำทางตามถนน)
- ปุ่มกลับไปตำแหน่งปัจจุบัน และสวิตช์ติดตามกล้อง
- Dark UI และแผงควบคุมแบบเรียบง่าย

## ข้อจำกัดที่ควรรู้
V1 นี้วาดเส้นทางแบบเส้นตรง (great-circle distance ไม่ใช่เส้นทางถนน) เปอร์เซ็นต์ที่แสดงเป็นการประมาณตำแหน่งบนแนวเส้นตรงระหว่างต้นทางกับปลายทาง ไม่ใช่ความคืบหน้าตามเส้นทางถนนจริง ต้องเพิ่ม routing + map matching ในรุ่นถัดไป ส่วนระบบคืนกล้องหลัง 5 วินาทีเป็นโครงเริ่มต้นที่ควรทดสอบกับ gesture จริงบนอุปกรณ์ก่อนใช้งานประจำ

แผนที่ใช้ public demo style ของ MapLibre สำหรับต้นแบบ ไม่ควรใช้เป็น tile service production ระยะยาว ควรเปลี่ยนเป็นผู้ให้บริการ tiles ที่มีเงื่อนไขการใช้งานเหมาะสมก่อนเผยแพร่

## วิธีเปิดโปรเจกต์
1. ติดตั้ง Android Studio รุ่นที่รองรับ Android Gradle Plugin 8.9.2 และ JDK 17
2. เปิดโฟลเดอร์ `GPSMapV1`
3. รอ Gradle Sync
4. ต่อมือถือ Android หรือเปิด Emulator แล้วกด Run

## อัปขึ้น GitHub
```bash
git init
git add .
git commit -m "Initial GPS Map V1"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/gps-map-v1.git
git push -u origin main
```

## Roadmap ถัดไป
- คำนวณเส้นทางถนนและเปอร์เซ็นต์จริงด้วย OSRM / GraphHopper
- เปลี่ยนไอคอนเป็นชุด SVG/PNG ที่เลือกได้
- ปรับ camera follow: heading, bearing lock และคืนมุมกล้องหลัง idle 5 วินาที
- เพิ่มการจัดการ lifecycle ของ GPS และ permission แบบ production
- เพิ่ม offline map / cache ตามผู้ให้บริการ tiles
