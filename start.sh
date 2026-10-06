#!/bin/bash
# ===========================================================
# start.sh — يشغل بوت Java + Next.js معًا في نفس الحاوية
# ===========================================================

set -e

# تحديد المنفذ والعنوان لـ Next.js
export PORT=${PORT:-3000}
export HOSTNAME="0.0.0.0"

echo "[KushTicket] Starting deployment..."
echo "  JAVA_HOME: $(java -version 2>&1 | head -1)"
echo "  Node.js: $(node -v)"
echo "  Port: $PORT"

# إنشاء مجلدات البيانات والتسجيلات والتفريغ النصي
mkdir -p /app/kushticket-bot/data
mkdir -p /app/kushticket-bot/data/transcripts
mkdir -p /app/kushticket-bot/data/recordings
mkdir -p /app/public/transcripts
mkdir -p /app/public/recordings

# ===========================================================
# 1) بدء بوت Java في الخلفية
# ===========================================================
echo "[KushTicket] Starting Java bot..."
(cd /app/kushticket-bot && java $JAVA_OPTS -jar target/kushticket-2.0.0.jar) &
JAVA_PID=$!
echo "[KushTicket] Java bot started with PID $JAVA_PID"

# ===========================================================
# 2) بدء Next.js
# ===========================================================
echo "[KushTicket] Starting Next.js on port $PORT..."
node server.js &
NODE_PID=$!
echo "[KushTicket] Next.js started with PID $NODE_PID"

# ===========================================================
# 3) معالجة إشارات الإيقاف (Railway يرسل SIGTERM عند إعادة التشغيل)
# ===========================================================
cleanup() {
    echo "[KushTicket] Received shutdown signal. Stopping services..."
    kill -SIGTERM $JAVA_PID $NODE_PID 2>/dev/null || true
    wait $JAVA_PID 2>/dev/null || true
    wait $NODE_PID 2>/dev/null || true
    echo "[KushTicket] All services stopped."
    exit 0
}

trap cleanup SIGTERM SIGINT

# ===========================================================
# 4) انتظار أي عملية لتموت (لو انتهت واحدة، نوقف الأخرى)
# ===========================================================
wait -n $JAVA_PID $NODE_PID
EXIT_CODE=$?
echo "[KushTicket] One process exited with code $EXIT_CODE. Shutting down all."
cleanup
