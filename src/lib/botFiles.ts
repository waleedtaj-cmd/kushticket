import fs from "node:fs";
import path from "node:path";

export const BOT_DIR_NAME = "kushticket-bot";

export function botRoot(): string {
  return path.join(process.cwd(), BOT_DIR_NAME);
}

export type FileStatus = "new" | "replace" | "config";

export type BotFile = {
  path: string;
  content: string;
  lang: string;
  status: FileStatus;
  description: string;
  lines: number;
};

/** وصف كل ملف + ترتيب العرض. */
export const FILE_META: { path: string; status: FileStatus; description: string }[] = [
  { path: "pom.xml", status: "replace", description: "تحديث JDA إلى 6.7.0 + مكتبة DAVE للصوت + logback لإظهار الأخطاء + maven-shade لبناء jar واحد." },
  { path: "src/main/java/com/ticketbot/Main.java", status: "replace", description: "تشغيل البوت: Intents الصحيحة، إعداد DAVE، تسجيل الأنظمة، قفل النسخة الواحدة، Shutdown hook." },
  { path: "src/main/java/com/ticketbot/config/BotConfig.java", status: "new", description: "قراءة الإعدادات من config.properties (التوكن، رولات الإدارة، قناة الأرشيف...)." },
  { path: "src/main/java/com/ticketbot/ticket/TicketType.java", status: "new", description: "أنواع التذاكر: شكوى على إداري، على عضو، مشكلة عامة، شكوى صوتية." },
  { path: "src/main/java/com/ticketbot/ticket/TicketIds.java", status: "new", description: "كل Custom IDs: ticket_claim, ticket_unclaim, ticket_save, ticket_close ..." },
  { path: "src/main/java/com/ticketbot/ticket/Ticket.java", status: "new", description: "بيانات التذكرة + منطق Claim/Unclaim الآمن (synchronized) + معلومات آخر حفظ." },
  { path: "src/main/java/com/ticketbot/ticket/TicketStore.java", status: "new", description: "حفظ التذاكر في data/tickets.json حتى لا تضيع بعد إعادة التشغيل." },
  { path: "src/main/java/com/ticketbot/ticket/StaffPermissions.java", status: "new", description: "التحقق من أن الضاغط إداري (Administrator أو رول إدارة)." },
  { path: "src/main/java/com/ticketbot/ticket/TicketPanel.java", status: "new", description: "يبني رسالة التحكم الرئيسية (Embed + أزرار) ويعدّلها Edit بدل إرسال رسائل جديدة." },
  { path: "src/main/java/com/ticketbot/ticket/TicketSystem.java", status: "new", description: "لوحة فتح التذاكر، الـ Modal، إنشاء القناة، والإغلاق (تم حل المشكلة) مع حفظ تلقائي." },
  { path: "src/main/java/com/ticketbot/ticket/ClaimSystem.java", status: "new", description: "أزرار Claim / Unclaim مع التحقق من الصلاحية داخل الـ Interaction." },
  { path: "src/main/java/com/ticketbot/complaint/ComplaintSaveSystem.java", status: "new", description: "زر حفظ الشكوى: جلب كل الرسائل، منع النسخ المكررة، إنشاء نسخ v001, v002..." },
  { path: "src/main/java/com/ticketbot/transcript/TranscriptModels.java", status: "new", description: "Records لبيانات الـ Transcript (رسالة، مرفق، Embed، الوثيقة كاملة)." },
  { path: "src/main/java/com/ticketbot/transcript/TranscriptCollector.java", status: "new", description: "جلب كل رسائل القناة مع Pagination + تحميل المرفقات محليًا." },
  { path: "src/main/java/com/ticketbot/transcript/TranscriptWriter.java", status: "new", description: "كتابة transcript.txt + transcript.html (تصميم Discord) + complaint.json." },
  { path: "src/main/java/com/ticketbot/voice/VoiceComplaintSystem.java", status: "new", description: "أزرار بدء/إنهاء التسجيل، جلسة واحدة لكل سيرفر، لا إعادة اتصال تلقائية أبدًا." },
  { path: "src/main/java/com/ticketbot/voice/VoiceRecordingSession.java", status: "new", description: "دورة حياة الاتصال الصوتي (State Machine): open مرة واحدة، close مرة واحدة، Cleanup." },
  { path: "src/main/java/com/ticketbot/voice/WavRecorder.java", status: "new", description: "AudioReceiveHandler يكتب الصوت في WAV عبر Virtual Thread منفصل." },
  { path: "src/main/resources/logback.xml", status: "new", description: "إعداد الـ Logging حتى تظهر أخطاء JDA في Console." },
  { path: "config.properties.example", status: "config", description: "مثال للإعدادات. انسخه باسم config.properties وضع التوكن." },
  { path: ".gitignore", status: "config", description: "يمنع رفع التوكن والبيانات على GitHub." },
  { path: "README.md", status: "config", description: "ملخص سريع للتشغيل والأنظمة." },
];

function langFor(file: string): string {
  if (file.endsWith(".java")) return "java";
  if (file.endsWith(".xml")) return "xml";
  if (file.endsWith(".properties") || file.endsWith(".example")) return "properties";
  return "text";
}

export function listBotFiles(): BotFile[] {
  const root = botRoot();
  const result: BotFile[] = [];
  for (const meta of FILE_META) {
    const full = path.join(root, meta.path);
    if (!fs.existsSync(full)) continue;
    const content = fs.readFileSync(full, "utf8");
    result.push({
      ...meta,
      content,
      lang: langFor(meta.path),
      lines: content.split("\n").length,
    });
  }
  return result;
}
