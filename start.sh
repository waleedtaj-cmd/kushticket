#!/bin/bash
# ===========================================================
# start.sh — يشغل بوت Java + Next.js معًا في نفس الحاوية
# ===========================================================

set -e

echo "[KushTicket] Starting deployment..."
echo "  JAVA_HOME: $(java -version 2>&1 | head -1)"
echo "  Node.js: $(node -v)"
echo "  Port: $PORT (default 3000)"

# إنشاء مجلدات البيانات (إذا لم تكن موجودة)
mkdir -p /app/kushticket-bot/data
mkdir -p /app/kushticket-bot/data/transcripts
mkdir -p /app/kushticket-bot/data/recordings

# ===========================================================
# 1) بدء بوت Java في الخلفية
# ===========================================================
echo "[KushTicket] Starting Java bot..."
java $JAVA_OPTS -jar /app/kushticket-bot/target/kushticket-2.0.0.jar &
JAVA_PID=$!
echo "[KushTicket] Java bot started with PID $JAVA_PID"

# ===========================================================
# 2) بدء Next.js في الواجهة (foreground)
# ===========================================================
echo "[KushTicket] Starting Next.js on port ${PORT:-3000}..."
exec node server.js &
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
