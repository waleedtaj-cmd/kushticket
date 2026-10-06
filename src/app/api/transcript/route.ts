import { BOT_DIR_NAME } from "@/lib/botFiles";
import fs from "node:fs";
import path from "node:path";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

/**
 * GET /api/transcript?ticket=<ticketName>&v=<version>
 *
 * يعرض ملف transcript.html المحفوظ بواسطة البوت.
 * مثال: /api/transcript?ticket=complaint-admin-0001-123456789012345678&v=1
 */
export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const ticket = searchParams.get("ticket");
  const versionStr = searchParams.get("v");

  if (!ticket || !versionStr) {
    return Response.json({ error: "Missing ticket or v parameter" }, { status: 400 });
  }

  const version = parseInt(versionStr, 10);
  if (isNaN(version) || version < 1) {
    return Response.json({ error: "Invalid version" }, { status: 400 });
  }

  const versionFolder = path.join(process.cwd(), BOT_DIR_NAME, "data", "transcripts", ticket, `v${String(version).padStart(3, "0")}`);
  const htmlFile = path.join(versionFolder, "transcript.html");

  if (!fs.existsSync(htmlFile)) {
    return new Response("Transcript not found", { status: 404, headers: { "Content-Type": "text/plain; charset=utf-8" } });
  }

  try {
    const html = fs.readFileSync(htmlFile, "utf8");
    return new Response(html, {
      headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "public, max-age=3600",
      },
    });
  } catch {
    return new Response("Failed to read transcript", { status: 500, headers: { "Content-Type": "text/plain; charset=utf-8" } });
  }
}
