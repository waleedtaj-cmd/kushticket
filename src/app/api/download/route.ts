import { BOT_DIR_NAME, listBotFiles } from "@/lib/botFiles";
import { createZip } from "@/lib/zip";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function GET() {
  const files = listBotFiles();
  const zip = createZip(
    files.map((f) => ({ name: `${BOT_DIR_NAME}/${f.path}`, data: Buffer.from(f.content, "utf8") })),
  );
  return new Response(new Uint8Array(zip), {
    headers: {
      "Content-Type": "application/zip",
      "Content-Disposition": `attachment; filename="${BOT_DIR_NAME}.zip"`,
      "Cache-Control": "no-store",
    },
  });
}
