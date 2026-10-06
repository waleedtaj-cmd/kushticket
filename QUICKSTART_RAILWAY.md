# 🚀 نشر سريع على Railway — KushTicket

**دليل مختصر** — للتفاصيل الكاملة اقرأ [DEPLOYMENT.md](./DEPLOYMENT.md)

## ✅ الخطوات (5 دقائق)

### 1️⃣ ارفع المشروع على GitHub
```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/USERNAME/kushticket.git
git push -u origin main
```

### 2️⃣ أنشئ مشروع Railway
- افتح [railway.com](https://railway.com) → **New Project** → **Deploy from GitHub** → اختر `kushticket`

### 3️⃣ أضف PostgreSQL
- **New** → **Database** → **Add PostgreSQL**
- انسخ `DATABASE_URL` من **Connect → Internal**

### 4️⃣ أضف Volume
- اضغط على الخدمة → **Volumes** → **New Volume**
- **Mount Path**: `/app/kushticket-bot/data`
- **Size**: `1 GB`

### 5️⃣ أضف Environment Variables
اضغط **Variables** → **Raw Editor** والصق:

```env
DATABASE_URL=${{Postgres.DATABASE_URL}}
KUSHTICKET_TOKEN=توكن_البوت_هنا
KUSHTICKET_GUILD_ID=ID_السيرفر
KUSHTICKET_STAFF_ROLE_IDS=ID_رول_الإدارة
KUSHTICKET_TICKET_CATEGORY_ID=0
KUSHTICKET_TRANSCRIPT_CHANNEL_ID=0
KUSHTICKET_VOICE_COMPLAINT_ROLE_ID=0
KUSHTICKET_TIMEZONE=Asia/Riyadh
KUSHTICKET_CLOSE_DELETE_AFTER_SECONDS=10
KUSHTICKET_VOICE_MAX_MINUTES=30
NODE_ENV=production
```

### 6️⃣ فعّل Public Domain
- **Settings** → **Networking** → **Generate Domain**
- سيتم ضبط `RAILWAY_PUBLIC_DOMAIN` تلقائيًا

### 7️⃣ انتظر البناء (3-5 دقائق)
- **Deployments** → **View Logs** للتأكد من:
  ```
  [KushTicket] Starting Java bot...
  [KushTicket] Starting Next.js on port 3000...
  KushTicket is ready as KushTicket
  ```

### 8️⃣ اختبر
- افتح الرابط العام (`https://kushticket-xxx.up.railway.app`)
- في Discord: `/ticket-setup`

---

## 🔑 Environment Variables المطلوبة

| المتغير | مثال | ملاحظات |
|---|---|---|
| `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` | من Railway |
| `KUSHTICKET_TOKEN` | `MTIz...` | من Discord Developer Portal |
| `KUSHTICKET_GUILD_ID` | `123456789012345678` | ID السيرفر |
| `KUSHTICKET_STAFF_ROLE_IDS` | `111,222` | مفصولة بفاصلة |
| `KUSHTICKET_TICKET_CATEGORY_ID` | `0` | ID كاتيجوري التذاكر |
| `KUSHTICKET_TRANSCRIPT_CHANNEL_ID` | `0` | ID قناة الأرشيف |
| `KUSHTICKET_VOICE_COMPLAINT_ROLE_ID` | `0` | ID رول الشكوى الصوتية |
| `KUSHTICKET_TIMEZONE` | `Asia/Riyadh` | المنطقة الزمنية |

---

## 💰 التكلفة المتوقعة

- **Railway Free**: $5/شهر ≈ 500 ساعة
- **بوت واحد**: ~$1-3/شهر
- **Volume 1GB**: $0.25/شهر
- **PostgreSQL**: مجاني

---

## 🐛 مشاكل شائعة

| المشكلة | الحل |
|---|---|
| `Bot token is missing` | أضف `KUSHTICKET_TOKEN` |
| الملفات تُحذف | أضف Volume على `/app/kushticket-bot/data` |
| زر "عرض الشكوى" لا يعمل | فعّل Public Domain |
| Loop صوتي | تأكد من DAVE protocol في السجلات |

---

## 📚 للمزيد

- [DEPLOYMENT.md](./DEPLOYMENT.md) — دليل مفصل
- [Railway Docs](https://docs.railway.app)
- [Discord Developer Portal](https://discord.com/developers/applications)
