# 🚀 نشر KushTicket على Railway — دليل كامل

هذا الدليل يشرح كيف تنشر مشروع **KushTicket** (بوت Java + موقع Next.js) على [Railway.com](https://railway.com).

## 📋 المتطلبات قبل البدء

1. **حساب Railway** — سجّل من [railway.com](https://railway.com) (خطة مجانية بـ $5 رصيد شهريًا).
2. **حساب GitHub** — لرفع المشروع.
3. **Discord Developer Portal** — لتفعيل صلاحيات البوت.
4. **Git** — مثبت على جهازك (لرفع المشروع).

---

## 🏗️ هيكل المشروع

```
KushTicket/
├── Dockerfile              # يبني Java + Next.js معًا
├── start.sh                # يشغل البوت والموقع
├── railway.toml            # إعدادات Railway
├── .dockerignore
├── .gitignore
├── .env.example
├── package.json            # Next.js dependencies
├── next.config.ts          # Next.js config (output: 'standalone')
├── src/                    # Next.js source
└── kushticket-bot/         # Java bot
    ├── pom.xml
    ├── src/
    └── config.properties.example
```

عند النشر، Dockerfile سينشئ حاوية واحدة تشغل:
- **بوت Java** (يستمع على أي منفذ داخلي)
- **موقع Next.js** (على `PORT` من Railway — عادة 3000)

---

## 📝 الخطوة 1: إعداد Discord Developer Portal

1. افتح [Discord Developer Portal](https://discord.com/developers/applications)
2. اضغط **New Application** → سمِّه `KushTicket`
3. من القائمة اليسرى: **Bot** → **Reset Token** → انسخ التوكن (ستحتاجه لاحقًا)
4. تحت **Privileged Gateway Intents**، فعّل:
   - ✅ **Server Members Intent**
   - ✅ **Message Content Intent**
   - ✅ **Presence Intent**
5. من **OAuth2 → URL Generator**:
   - Scopes: `bot`, `applications.commands`
   - Bot Permissions: `Manage Channels`, `Manage Roles`, `View Channels`, `Send Messages`, `Embed Links`, `Attach Files`, `Read Message History`, `Connect`, `Speak`, `Use Voice Activity`
6. انسخ الرابط الناتج وافتحه في المتصفح لإضافة البوت لسيرفرك

### احفظ هذه IDs:
- **Bot Token** (من صفحة Bot)
- **Guild ID** (كليك يمين على اسم السيرفر مع Developer Mode → Copy ID)
- **Staff Role ID** (من Server Settings → Roles)
- **Transcript Channel ID** (كليك يمين على القناة)
- **Voice Complaint Role ID** (اختياري — رول خاص لصاحب الشكوى الصوتية)

---

## 📦 الخطوة 2: رفع المشروع على GitHub

```bash
# في مجلد المشروع
git init
git add .
git commit -m "Initial commit: KushTicket (Java bot + Next.js)"
git branch -M main
git remote add origin https://github.com/USERNAME/kushticket.git
git push -u origin main
```

> ⚠️ **تأكد أن `config.properties` و `.env` في `.gitignore`** حتى لا يُرفع التوكن.

---

## 🌐 الخطوة 3: النشر على Railway

### أ) إنشاء مشروع جديد
1. افتح [railway.com](https://railway.com) وسجّل الدخول بـ GitHub
2. اضغط **New Project**
3. اختر **Deploy from GitHub Repo**
4. اختر `kushticket` من قائمة repos

### ب) إضافة قاعدة بيانات PostgreSQL
1. داخل المشروع، اضغط **+ New** → **Database** → **Add PostgreSQL**
2. انتظر حتى يتم إنشاءها
3. من صفحة PostgreSQL → **Connect** → انسخ `DATABASE_URL` (استخدم **Internal Connection** للأداء الأفضل)

### ج) إضافة Volume لحفظ البيانات
**مهم جدًا**: Railway filesystem مؤقت — أي ملف في `/app` يُحذف عند إعادة التشغيل. نحتاج Volume لحفظ التذاكر والـ Transcripts.

1. اضغط على خدمة الـ Web
2. تبويب **Volumes** → **New Volume**
3. إعدادات الـ Volume:
   - **Mount Path**: `/app/kushticket-bot/data`
   - **Initial Size**: `1 GB` (يكفي لآلاف التذاكر)

### د) ضبط Environment Variables
1. اضغط على خدمة الـ Web → تبويب **Variables**
2. اضغط **+ New Variable** وأضف المتغيرات التالية:

| المتغير | القيمة | ملاحظات |
|---|---|---|
| `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` | يُستخدم تلقائيًا من Railway |
| `KUSHTICKET_TOKEN` | `التوكن_هنا` | من Discord Developer Portal |
| `KUSHTICKET_GUILD_ID` | `123456789012345678` | ID السيرفر |
| `KUSHTICKET_STAFF_ROLE_IDS` | `role1,role2` | مفصولة بفاصلة |
| `KUSHTICKET_TICKET_CATEGORY_ID` | `0` | أو ID كاتيجوري التذاكر |
| `KUSHTICKET_TRANSCRIPT_CHANNEL_ID` | `0` | أو ID قناة الأرشيف |
| `KUSHTICKET_VOICE_COMPLAINT_ROLE_ID` | `0` | أو ID رول الشكوى الصوتية |
| `KUSHTICKET_TIMEZONE` | `Asia/Riyadh` | أو منطقتك الزمنية |
| `KUSHTICKET_CLOSE_DELETE_AFTER_SECONDS` | `10` | `-1` لعدم حذف القناة |
| `KUSHTICKET_VOICE_MAX_MINUTES` | `30` | أقصى مدة للتسجيل |
| `NODE_ENV` | `production` | |

> 💡 **نصيحة**: استخدم **Raw Editor** (أيقونة القلم) لإضافة كل المتغيرات دفعة واحدة بدل إضافة كل متغير منفردًا.

### هـ) إعداد النطاق العام (Public Domain)
1. من تبويب **Settings** → **Networking** → **Public Networking**
2. اضغط **Generate Domain** → Railway سيُنشئ رابطًا مثل `kushticket-production.up.railway.app`
3. يتم ضبط `RAILWAY_PUBLIC_DOMAIN` تلقائيًا ليُستخدم في روابط الـ Transcript

---

## 🎬 الخطوة 4: أول نشر

1. بعد حفظ كل المتغيرات، Railway سيبدأ البناء تلقائيًا (يأخذ 3-5 دقائق لأول مرة)
2. في تبويب **Deployments** → **View Logs** لرؤية:
   - `[KushTicket] Starting deployment...`
   - `[KushTicket] Starting Java bot...`
   - `[KushTicket] Starting Next.js on port 3000...`
   - `KushTicket is ready as KushTicket`
3. افتح الرابط العام (`https://kushticket-production.up.railway.app`) للتأكد أن الموقع يعمل
4. في Discord: اكتب `/ticket-setup` في القناة التي تريد فيها لوحة التذاكر

---

## 🔄 الخطوة 5: التحديث المستمر

من الآن فصاعدًا، أي `git push` للـ branch `main` سيُطلق إعادة نشر تلقائية:

```bash
# بعد التعديل على الكود
git add .
git commit -m "fix: something"
git push
```

Railway سيُعيد بناء Docker image ويُعيد تشغيل الخدمة خلال 1-2 دقيقة.

---

## 🗂️ هيكل البيانات على Volume

```
/app/kushticket-bot/data/          ← Volume محفوظ على Railway
├── kushticket.lock                ← قفل منع النسخ المكررة
├── tickets.json                   ← حالة كل التذاكر
├── transcripts/
│   └── complaint-admin-0001-<channelId>/
│       ├── v001/
│       │   ├── transcript.txt
│       │   ├── transcript.html
│       │   ├── complaint.json
│       │   └── attachments/
│       └── v002/
│           └── ...
└── recordings/
    └── complaint-admin-0004/
        ├── recording-20260101-120000.wav
        └── recording-20260101-143000.wav
```

---

## 📊 مراقبة الأداء والتكاليف

- **Railway Free Tier**: $5 شهريًا ≈ 500 ساعة تشغيل
- **الاستخدام المتوقع لبوت واحد**: ~$1-3 شهريًا (يعتمد على الاستخدام)
- **Volume**: $0.25/GB شهريًا (1GB = $0.25/شهر)
- **PostgreSQL**: مضمن في الخطة المجانية

من **Dashboard** → **Usage** يمكنك مراقبة الاستخدام.

---

## 🐛 حل المشاكل الشائعة

### ❌ البوت لا يعمل بعد النشر
**السبب المحتمل**: Environment Variables غير صحيحة

```bash
# في Railway Logs، ابحث عن:
java.lang.IllegalStateException: Bot token is missing
```

**الحل**: تأكد من أن `KUSHTICKET_TOKEN` مضبوط بشكل صحيح.

### ❌ خطأ DAVE Protocol / Loop صوتي
**السبب المحتمل**: مكتبة DAVE native غير محملة

```bash
# في السجلات، ابحث عن:
java.lang.UnsatisfiedLinkError
```

**الحل**: Dockerfile الحالي يتضمن DAVE natives. إذا ظهر الخطأ، تواصل معي.

### ❌ الملفات تُحذف عند إعادة التشغيل
**السبب المحتمل**: لم تُضف Volume

**الحل**: راجع الخطوة 3ج — تأكد من أن Volume على `/app/kushticket-bot/data`.

### ❌ Next.js يعطي 502 Bad Gateway
**السبب المحتمل**: البناء فشل

**الحل**: راجع السجلات في Railway → Deployments → View Logs. عادة بسبب:
- فشل `npm ci` (تحقق من `package-lock.json`)
- خطأ TypeScript في `npm run build`

### ❌ زر "عرض الشكوى" لا يعمل
**السبب المحتمل**: لم تُفعّل Public Domain

**الحل**: راجع الخطوة 3ه — تأكد من تفعيل Public Networking.

---

## 🛠️ التطوير المحلي قبل النشر

للتجربة قبل النشر على Railway:

### أ) Next.js فقط (الأسرع)
```bash
npm install
npm run dev
# افتح http://localhost:3000
```

### ب) بوت Java فقط
```bash
cd kushticket-bot
cp config.properties.example config.properties
# عدّل config.properties (التوكن، IDs)
mvn clean package
java -jar target/kushticket-2.0.0.jar
```

### ج) كلاهما مع Docker (محاكاة Railway)
```bash
# بناء الـ image
docker build -t kushticket .

# تشغيله
docker run -p 3000:3000 \
  -v ./local-data:/app/kushticket-bot/data \
  -e KUSHTICKET_TOKEN=your_token \
  -e KUSHTICKET_GUILD_ID=your_guild \
  -e KUSHTICKET_STAFF_ROLE_IDS=your_roles \
  kushticket
```

---

## 📚 روابط مفيدة

- [Railway Documentation](https://docs.railway.app)
- [Discord Developer Portal](https://discord.com/developers/applications)
- [JDA Documentation](https://jda.wiki)
- [Next.js Deployment](https://nextjs.org/docs/app/building-your-application/deploying)

---

## ✨ ملاحظات أخيرة

- **النسخ الاحتياطي**: Volume على Railway لا يُنسخ احتياطيًا تلقائيًا. حمل `tickets.json` ومجلد `transcripts/` دوريًا.
- **السجلات**: Railway يحتفظ بالسجلات لـ 7 أيام. استخدم خدمة مثل [Better Stack](https://betterstack.com) للاحتفاظ بها أطول.
- **Scaling**: هذه الإعدادات لنسخة واحدة. إذا أردت Scaling، ستحتاج لتعديل البنية (Redis لمزامنة الحالة بين النسخ).
