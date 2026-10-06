# ===========================================================
# Dockerfile متعدد المراحل لنشر KushTicket على Railway
# يبني ويشغل Java 21 Bot + Next.js 16 في نفس الحاوية
# ===========================================================

# ===== Stage 1: بناء بوت Java =====
FROM eclipse-temurin:21-jdk-alpine AS java-builder

RUN apk add --no-cache maven

WORKDIR /build

# نسخ ملفات البوت فقط (لتسريع Docker layer cache)
COPY kushticket-bot/pom.xml ./pom.xml
COPY kushticket-bot/src ./src

# بناء البوت (ينتج target/kushticket-2.0.0.jar)
RUN mvn -B -q clean package -DskipTests

# ===== Stage 2: بناء تطبيق Next.js =====
FROM node:20-alpine AS web-builder

WORKDIR /build

# نسخ ملفات الـ Next.js فقط
COPY package.json package-lock.json ./
COPY next.config.ts tsconfig.json ./
COPY postcss.config.mjs eslint.config.mjs ./
COPY src ./src
COPY public ./public 2>/dev/null || true

# نسخ ملفات مشروع البوت التي يحتاجها الموقع (ZIP download + عرض الكود)
# هذه تُقرأ من القرص في runtime من /app/kushticket-bot
COPY kushticket-bot/pom.xml ./kushticket-bot/pom.xml
COPY kushticket-bot/src ./kushticket-bot/src
COPY kushticket-bot/src ./kushticket-bot/src
COPY kushticket-bot/config.properties.example ./kushticket-bot/config.properties.example
COPY kushticket-bot/.gitignore ./kushticket-bot/.gitignore
COPY kushticket-bot/README.md ./kushticket-bot/README.md

# نسخ jar المبني من Stage 1
COPY --from=java-builder /build/target ./kushticket-bot/target

RUN npm ci
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ===== Stage 3: حاوية التشغيل النهائية =====
FROM eclipse-temurin:21-jre-alpine AS runtime

# تثبيت Node.js 20 + bash + curl + tini (init system)
RUN apk add --no-cache \
    bash \
    curl \
    nodejs \
    npm \
    tini

WORKDIR /app

# نسخ بناء Next.js (standalone output — لا يحتاج node_modules كامل)
COPY --from=web-builder /build/.next/standalone ./
COPY --from=web-builder /build/.next/static ./.next/static
COPY --from=web-builder /build/public ./public

# نسخ مشروع البوت (يشمل: pom.xml + src + jar المبني + README)
# حتى يعمل /api/download ويظهر الكود في صفحة الويب
COPY --from=web-builder /build/kushticket-bot ./kushticket-bot

# إنشاء مجلدات البيانات
RUN mkdir -p /app/kushticket-bot/data \
             /app/kushticket-bot/data/transcripts \
             /app/kushticket-bot/data/recordings

# نسخ script التشغيل
COPY start.sh ./start.sh
RUN chmod +x ./start.sh

# ملاحظة: Railway لا يدعم تعليمة VOLUME داخل Dockerfile،
# بل يتم إضافة الـ Volume من واجهة Railway (Volumes -> Add Volume على المسار /app/kushticket-bot/data)

# متغيرات افتراضية
ENV NODE_ENV=production \
    HOSTNAME=0.0.0.0 \
    PORT=3000 \
    JAVA_OPTS="-Xmx512m"

EXPOSE 3000

# Healthcheck — Railway يتحقق من /api/health
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:3000/api/health >/dev/null || exit 1

# tini يعالج SIGTERM بشكل صحيح عند إعادة تشغيل Railway
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["/app/start.sh"]
