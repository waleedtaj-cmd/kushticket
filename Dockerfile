# ===========================================================
# Dockerfile متعدد المراحل لنشر KushTicket على Railway
# يبني ويشغل Java 21 Bot + Next.js 16 في نفس الحاوية
# ===========================================================

# ===== Stage 1: بناء بوت Java =====
FROM maven:3.9.8-eclipse-temurin-21 AS java-builder

WORKDIR /build

# نسخ ملفات البوت من الجذر مباشرة
COPY pom.xml ./pom.xml
COPY src ./src

# بناء البوت (ينتج target/kushticket-2.0.0.jar)
RUN mvn -B -q clean package -DskipTests

# ===== Stage 2: بناء تطبيق Next.js =====
FROM node:20-slim AS web-builder

WORKDIR /build

# نسخ ملفات الـ Next.js
COPY package.json ./
COPY package-lock.jso[n] ./
COPY next.config.ts tsconfig.json ./
COPY postcss.config.mjs eslint.config.mjs ./
COPY drizzle.config.json* ./
COPY src ./src
COPY public* ./public/

# نسخ ملفات مشروع البوت لتكون مجهزة لموقع الويب
COPY pom.xml ./kushticket-bot/pom.xml
COPY src ./kushticket-bot/src
COPY README.md* ./kushticket-bot/
COPY config.properties* ./kushticket-bot/

# نسخ jar المبني من Stage 1
COPY --from=java-builder /build/target ./kushticket-bot/target

# التثبيت عبر npm install لضمان العمل حتى بدون package-lock.json
RUN npm install
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ===== Stage 3: حاوية التشغيل النهائية =====
FROM eclipse-temurin:21-jre-noble AS runtime

# تثبيت الحزم الأساسية ومكتبات الصوت
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    gnupg \
    bash \
    tini \
    libopus0 \
    libopus-dev \
    build-essential \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# تثبيت Node.js 20
RUN mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" | tee /etc/apt/sources.list.d/nodesource.list \
    && apt-get update \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# نسخ بناء Next.js Standalone بشكل كامل
COPY --from=web-builder /build/.next/standalone ./
COPY --from=web-builder /build/.next/static ./.next/static
COPY --from=web-builder /build/public ./public

# نسخ مشروع البوت
COPY --from=web-builder /build/kushticket-bot ./kushticket-bot

# نسخ config.properties إلى الجذر ومجلد البوت
COPY config.properties* ./config.properties
COPY config.properties* ./kushticket-bot/config.properties

# إنشاء مجلدات البيانات
RUN mkdir -p /app/kushticket-bot/data \
             /app/kushticket-bot/data/transcripts \
             /app/kushticket-bot/data/recordings \
             /app/public/transcripts \
             /app/public/recordings \
             /app/data

# نسخ script التشغيل
COPY start.sh ./start.sh
RUN chmod +x ./start.sh

# متغيرات بيئة التشغيل
ENV NODE_ENV=production \
    HOSTNAME=0.0.0.0 \
    PORT=3000 \
    JAVA_OPTS="-Xmx512m"

EXPOSE 3000

# فحص صحة الخدمة
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:3000/api/health >/dev/null || exit 1

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["/app/start.sh"]
