# KushTicket 2.0 — Java 21 + JDA 6.7.0

## التشغيل السريع (Eclipse)
1. استبدل `pom.xml` ← كليك يمين على المشروع ← Maven ← Update Project (Force Update).
2. انسخ `config.properties.example` باسم `config.properties` بجانب `pom.xml` وضع التوكن و IDs رولات الإدارة.
3. Developer Portal ← Bot ← فعّل **Message Content Intent**.
4. شغّل `com.ticketbot.Main` ← Run As ← Java Application.
5. في Discord: `/ticket-setup`.

## الأنظمة
| Class | الوظيفة |
|---|---|
| `ticket/TicketSystem` | لوحة فتح التذاكر، Modal، إنشاء القناة، تم حل المشكلة |
| `ticket/ClaimSystem` | `ticket_claim` / `ticket_unclaim` (Edit لنفس الرسالة) |
| `ticket/TicketPanel` | بناء رسالة التحكم الرئيسية |
| `complaint/ComplaintSaveSystem` | `ticket_save`: Transcript كامل بنسخ v001, v002 بدون تكرار |
| `transcript/*` | جلب كل الرسائل (Pagination) + TXT/HTML/JSON + تحميل المرفقات |
| `voice/*` | تسجيل صوتي: اتصال واحد، إيقاف واحد، Cleanup كامل |

## سبب Loop الصوت
Discord يفرض بروتوكول DAVE (E2EE) على الصوت منذ 1 مارس 2026. JDA 5 لا يدعمه ⇒ رفض بكود 4017 ⇒ JDA يعيد الاتصال تلقائيًا ⇒ Loop.
بدون logback لم تكن الأخطاء تظهر. الحل: JDA 6.7.0 + libdave-jvm + `setAudioModuleConfig(...)` في Main.

## مكان الحفظ
```
data/tickets.json
data/transcripts/<channel>-<channelId>/v001/{transcript.txt, transcript.html, complaint.json, attachments/}
data/recordings/<ticket>/recording-*.wav
```
