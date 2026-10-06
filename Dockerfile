# ===========================================================
# Dockerfile متعدد المراحل لنشر KushTicket على Railway
# يبني ويشغل Java 21 Bot + Next.js 16 في نفس الحاوية
# ===========================================================

# ===== Stage 1: بناء بوت Java =====
FROM eclipse-temurin:21-jdk-alpine AS java-builder

RUN apk add --no-cache maven

WORKDIR /build

# نسخ ملفات البوت من المجلد الرئيسي مباشرة
COPY pom.xml ./pom.xml
COPY src ./src

# بناء البوت (ينتج target/kushticket-2.0.0.jar)
RUN mvn -B -q clean package -DskipTests

# ===== Stage 2: بناء تطبيق Next.js =====
FROM node:20-alpine AS web-builder

WORKDIR /build

# نسخ ملفات الـ Next.js
COPY package.json package-lock.json ./
COPY next.config.ts tsconfig.json ./
COPY postcss.config.mjs eslint.config.mjs ./
COPY src ./src
COPY public ./public 2>/dev/null || true

# نسخ ملفات مشروع البوت التي يحتاجها الموقع (ZIP download + عرض الكود)
COPY pom.xml ./kushticket-bot/pom.xml
COPY src ./kushticket-bot/src
COPY config.properties.example ./kushticket-bot/config.properties.example 2>/dev/null || true
COPY .gitignore ./kushticket-bot/.gitignore 2>/dev/null || true
COPY README.md ./kushticket-bot/README.md 2>/dev/null || true

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

# نسخ بناء Next.js
COPY --from=web-builder /build/.next/standalone ./
COPY --from=web-builder /build/.next/static ./.next/static
COPY --from=web-builder /build/public ./public

# نسخ مشروع البوت
COPY --from=web-builder /build/kushticket-bot ./kushticket-bot

# إنشاء مجلدات البيانات
RUN mkdir -p /app/kushticket-bot/data \
             /app/kushticket-bot/data/transcripts \
             /app/kushticket-bot/data/recordings

# نسخ script التشغيل
COPY start.sh ./start.sh
RUN chmod +x ./start.sh

# متغيرات افتراضية
ENV NODE_ENV=production \
    HOSTNAME=0.0.0.0 \
    PORT=3000 \
    JAVA_OPTS="-Xmx512m"

EXPOSE 3000

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:3000/api/health >/dev/null || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["/app/start.sh"]
