import type { ReactNode } from "react";
import CopyButton from "@/components/CopyButton";
import { listBotFiles, type FileStatus } from "@/lib/botFiles";

export const dynamic = "force-dynamic";

const TOC = [
  ["notice", "قبل أن تبدأ"],
  ["problem", "1. المشاكل التي وجدتها"],
  ["voice-why", "2. لماذا كان Loop الصوت يحدث"],
  ["voice-fix", "3. كيف أصلحت الصوت"],
  ["claim", "4. Claim و Unclaim"],
  ["save", "5. Save Complaint"],
  ["transcript", "6. كيف يُنشأ الـ Transcript"],
  ["pagination", "7. جلب كل رسائل Discord"],
  ["placement", "8. أين أضع كل Class"],
  ["main", "9. تعديلات Main"],
  ["pom", "10. تعديلات pom.xml"],
  ["eclipse", "11. التشغيل في Eclipse"],
  ["merge", "12. الدمج مع كودك الحالي"],
  ["files", "13. الملفات كاملة"],
] as const;

const STATUS_STYLE: Record<FileStatus, { label: string; cls: string }> = {
  new: { label: "ملف جديد", cls: "bg-emerald-500/15 text-emerald-300 border-emerald-500/30" },
  replace: { label: "استبدال كامل", cls: "bg-amber-500/15 text-amber-300 border-amber-500/30" },
  config: { label: "إعدادات", cls: "bg-sky-500/15 text-sky-300 border-sky-500/30" },
};

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} className="scroll-mt-6 rounded-2xl border border-white/5 bg-[#2b2d31] p-6 md:p-8">
      <h2 className="mb-4 text-xl font-bold text-white md:text-2xl">{title}</h2>
      <div className="prose-ar text-[15px] text-slate-300">{children}</div>
    </section>
  );
}

function Callout({ tone = "info", title, children }: { tone?: "info" | "warn" | "ok"; title: string; children: ReactNode }) {
  const styles = {
    info: "border-indigo-400/40 bg-indigo-500/10",
    warn: "border-amber-400/40 bg-amber-500/10",
    ok: "border-emerald-400/40 bg-emerald-500/10",
  }[tone];
  return (
    <div className={`my-4 rounded-xl border-s-4 ${styles} p-4`}>
      <p className="!mt-0 font-bold text-white">{title}</p>
      <div>{children}</div>
    </div>
  );
}

function Pre({ children, copy = true }: { children: string; copy?: boolean }) {
  return (
    <div className="relative my-3">
      {copy && (
        <div className="absolute left-2 top-2">
          <CopyButton text={children} />
        </div>
      )}
      <pre className="code overflow-x-auto rounded-xl border border-white/5 bg-[#1a1b1e] p-4 pt-10 text-slate-200">{children}</pre>
    </div>
  );
}

const TREE = `KushTicket/                         ← مجلد مشروعك في Eclipse
├── pom.xml                         ← استبدال كامل
├── config.properties               ← أنشئه من config.properties.example (فيه التوكن)
├── config.properties.example
├── .gitignore
├── data/                           ← يُنشأ تلقائيًا عند التشغيل
│   ├── tickets.json
│   ├── transcripts/complaint-admin-0001-<channelId>/v001/
│   │   ├── transcript.txt
│   │   ├── transcript.html
│   │   ├── complaint.json
│   │   └── attachments/...
│   └── recordings/voice-complaint-0004/recording-....wav
└── src/main/
    ├── resources/
    │   └── logback.xml
    └── java/com/ticketbot/
        ├── Main.java
        ├── config/
        │   └── BotConfig.java
        ├── ticket/
        │   ├── TicketSystem.java
        │   ├── ClaimSystem.java
        │   ├── TicketPanel.java
        │   ├── Ticket.java
        │   ├── TicketStore.java
        │   ├── TicketType.java
        │   ├── TicketIds.java
        │   └── StaffPermissions.java
        ├── complaint/
        │   └── ComplaintSaveSystem.java
        ├── transcript/
        │   ├── TranscriptCollector.java
        │   ├── TranscriptWriter.java
        │   └── TranscriptModels.java
        └── voice/
            ├── VoiceComplaintSystem.java
            ├── VoiceRecordingSession.java
            └── WavRecorder.java`;

export default function HomePage() {
  const files = listBotFiles();
  const totalLines = files.reduce((n, f) => n + f.lines, 0);

  return (
    <main className="mx-auto max-w-6xl px-4 py-8 md:py-12">
      {/* ===== Hero ===== */}
      <header className="mb-8 overflow-hidden rounded-3xl bg-gradient-to-br from-[#5865f2] to-[#3b44c4] p-8 text-white shadow-2xl md:p-12">
        <p className="text-sm font-semibold uppercase tracking-widest text-indigo-100">Java 21 · JDA 6.7.0 · Maven · Eclipse</p>
        <h1 className="mt-3 text-3xl font-extrabold leading-tight md:text-5xl">🎫 KushTicket — دليل التعديلات الكامل</h1>
        <p className="mt-4 max-w-3xl text-indigo-100">
          نظام Claim / Unclaim، زر حفظ الشكوى مع Transcript كامل لكل الرسائل (TXT + HTML + JSON)، وإصلاح Loop دخول/خروج
          البوت من الروم الصوتي — مع شرح خطوة بخطوة للمبتدئين.
        </p>
        <div className="mt-6 flex flex-wrap gap-3">
          <a href="/api/download" className="rounded-xl bg-white px-5 py-3 font-bold text-[#3b44c4] shadow transition hover:scale-[1.02]">
            ⬇️ تحميل المشروع (ZIP)
          </a>
          <a href="#files" className="rounded-xl border border-white/30 px-5 py-3 font-bold text-white transition hover:bg-white/10">
            📄 تصفح الملفات ({files.length} ملف · {totalLines} سطر)
          </a>
        </div>
      </header>

      <div className="grid gap-6 lg:grid-cols-[240px_1fr]">
        {/* ===== TOC ===== */}
        <nav className="h-fit rounded-2xl border border-white/5 bg-[#2b2d31] p-4 lg:sticky lg:top-6">
          <p className="mb-2 text-xs font-bold uppercase tracking-wider text-slate-400">المحتويات</p>
          <ul className="space-y-1 text-sm">
            {TOC.map(([id, label]) => (
              <li key={id}>
                <a href={`#${id}`} className="block rounded-lg px-2 py-1 text-slate-300 hover:bg-white/5 hover:text-white">
                  {label}
                </a>
              </li>
            ))}
          </ul>
        </nav>

        <div className="space-y-6">
          {/* ===== Notice ===== */}
          <Section id="notice" title="قبل أن تبدأ — ملاحظة مهمة وصريحة">
            <Callout tone="warn" title="كود مشروعك الحالي لم يصلني">
              <p>
                طلبت مني أن أقرأ الكود الذي سترسله أولاً، لكن لم يصلني أي ملف Java مع الرسالة. لذلك <b>لم أفترض أسماء Classes أو Methods
                عندك</b>، بل كتبت الأنظمة الجديدة كـ Classes مستقلة تمامًا داخل الـ package <code>com.ticketbot</code>، كل نظام في
                Class خاص به (<code>TicketSystem</code>، <code>ClaimSystem</code>، <code>ComplaintSaveSystem</code>،
                <code>VoiceComplaintSystem</code>)، ويمكن تشغيلها كما هي أو دمجها مع كودك.
              </p>
              <p>
                القسم <a className="text-indigo-300 underline" href="#merge">12. الدمج مع كودك الحالي</a> يشرح بالضبط ماذا تفعل
                بكودك القديم. وإذا أرسلت لي ملفاتك الحالية (خصوصًا <code>Main</code> وكلاس التذاكر وكلاس الصوت) أستطيع ربطها سطرًا بسطر.
              </p>
            </Callout>
            <Callout tone="ok" title="ما الذي تم التحقق منه فعلاً">
              <ul>
                <li>المشروع تمت ترجمته (compile) ببيئة <b>Java 21</b> و <b>Maven</b> حقيقيين بدون أي خطأ أو تحذير Deprecated مع <b>JDA 6.7.0</b> (أحدث إصدار).</li>
                <li>ملف الـ jar تم بناؤه، ومكتبة DAVE الأصلية (native) تم تحميلها بنجاح عند التشغيل.</li>
                <li>كاتب الـ Transcript تم اختباره بنص عربي (UTF-8 سليم + حماية HTML من الأكواد الخبيثة).</li>
                <li>مسجل WAV تم اختباره: التوقيت صحيح (يضيف صمتًا عند الانقطاع)، و<code>finish()</code> آمن عند استدعائه أكثر من مرة.</li>
              </ul>
            </Callout>
          </Section>

          {/* ===== 1 ===== */}
          <Section id="problem" title="1. المشاكل التي وجدتها">
            <ol>
              <li>
                <b>Discord أصبح يفرض تشفير الصوت DAVE (E2EE) على كل الاتصالات الصوتية منذ 1 مارس 2026.</b> أي بوت لا يدعم DAVE يُرفض
                اتصاله بكود <code>4017</code>. JDA 5.x لا يدعمه إطلاقًا، والدعم بدأ من <code>JDA 6.3.0</code> ويحتاج مكتبة إضافية.
              </li>
              <li>
                <b>لا يوجد Logger في المشروع</b> (مثل logback). JDA يستخدم SLF4J، وبدون مكتبة Logging كل رسائل الأخطاء تُرمى بصمت. لهذا
                لم ترَ أي خطأ في Console.
              </li>
              <li>
                <b>الـ Transcript يحتاج Intent اسمه <code>MESSAGE_CONTENT</code></b>. بدونه البوت يرى الرسائل لكن محتواها فارغ.
              </li>
              <li>
                <b>روابط مرفقات Discord تنتهي صلاحيتها</b> بعد فترة، وتُحذف عند حذف القناة. لو حفظنا الروابط فقط ستضيع الأدلة؛ لذلك
                نحمّل نسخة محلية من كل مرفق.
              </li>
              <li>
                <b>حالة Claim يجب أن تُحفظ في السيرفر</b> وليس في نص الرسالة أو الزر، مع حماية من ضغط إداريين بنفس اللحظة (Race Condition).
              </li>
            </ol>
          </Section>

          {/* ===== 2 ===== */}
          <Section id="voice-why" title="2. لماذا كان البوت يدخل ويخرج من الروم في Loop؟">
            <p>الأعراض التي وصفتها (يدخل → يبقى ~3 ثوانٍ → يخرج → يدخل مرة أخرى… بدون أخطاء واضحة) تطابق تمامًا هذا التسلسل:</p>
            <Pre copy={false}>{`openAudioConnection(channel)
   │
   ▼
البوت يدخل الروم (Gateway) ← تراه داخل الروم
   │
   ▼
JDA يفتح Voice WebSocket ويحاول الـ Handshake  (~2-3 ثوانٍ)
   │
   ▼
Discord: "هذا العميل لا يدعم DAVE" → يغلق الاتصال بكود 4017
   │
   ▼
JDA يعتبرها خطأ اتصال قابل لإعادة المحاولة (shouldReconnect = true)
   │
   ▼
JDA يعيد الاتصال تلقائيًا ← يدخل مرة أخرى ← 4017 ← ... ∞
   (ولا شيء يظهر في Console لأنه لا يوجد Logger)`}</Pre>
            <p>
              <b>لماذا تغير الوقت من أقل من ثانية إلى ~3 ثوانٍ بعد تعديلاتك؟</b> لأن الرفض يحدث في مرحلة الـ Handshake الصوتي. أي تأخير
              أضفته (مثل sleep أو إعادة ترتيب الكود) غيّر توقيت المحاولة فقط، لكن السبب الحقيقي (عدم دعم DAVE) بقي كما هو.
            </p>
            <p>
              <b>لماذا يستمر أحيانًا حتى بعد «إنهاء التسجيل»؟</b> هناك أسباب شائعة جدًا تجعل الـ Loop يستمر، وكلها تمت معالجتها في الكود الجديد:
            </p>
            <ol>
              <li>
                <b>تشغيل نسختين من البوت في Eclipse.</b> الضغط على Run مرة ثانية بدون إيقاف الأولى يشغّل نسختين بنفس التوكن. كل نسخة تطرد
                الأخرى من الصوت، وإيقاف التسجيل في نسخة لا يوقف الأخرى. ← <b>الحل:</b> قفل ملف <code>data/kushticket.lock</code> في
                <code>Main</code> يمنع تشغيل نسخة ثانية.
              </li>
              <li>
                <b>Listener يعيد الدخول.</b> كود مثل «إذا خرج البوت من الروم في <code>onGuildVoiceUpdate</code> ادخل مرة أخرى» يحوّل كل
                رفض إلى محاولة جديدة. ← <b>الحل:</b> في <code>VoiceComplaintSystem</code> الـ Events <b>توقف</b> التسجيل فقط، ولا تستدعي
                <code>openAudioConnection</code> أبدًا.
              </li>
              <li>
                <b>Scheduler أو Thread لم يُلغَ.</b> مثل <code>scheduleAtFixedRate</code> «للتأكد أن البوت متصل». ← <b>الحل:</b> المؤقتات الوحيدة
                (مهلة الاتصال + الحد الأقصى) تُلغى في أول سطر من <code>stop()</code>.
              </li>
              <li>
                <b>الإيقاف بدون <code>closeAudioConnection()</code></b> (مثلاً فقط <code>setReceivingHandler(null)</code> أو تغيير flag). JDA يبقى
                يحاول إعادة الاتصال من طابوره الداخلي. ← <b>الحل:</b> <code>closeAudioConnection()</code> يُستدعى دائمًا مرة واحدة، وهو يلغي أيضًا
                أي محاولة اتصال معلقة.
              </li>
              <li>
                <b>Race Condition:</b> ضغطتان على «بدء التسجيل»، أو بدء جديد قبل اكتمال الإيقاف السابق → اتصالان يتداخلان. ← <b>الحل:</b>
                <code>sessions.putIfAbsent()</code> (عملية ذرية) + State Machine لكل جلسة.
              </li>
            </ol>
          </Section>

          {/* ===== 3 ===== */}
          <Section id="voice-fix" title="3. كيف أصلحت نظام الصوت">
            <ol>
              <li><b>pom.xml:</b> الترقية إلى <code>JDA 6.7.0</code> + إضافة <code>libdave-jvm 0.1.4</code> (يعمل على Java 21؛ البديل JDAVE يحتاج Java 25).</li>
              <li><b>Main:</b> <code>setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(...))</code> و <code>NativeDaveFactory.ensureAvailable()</code> عند التشغيل.</li>
              <li><b>logback:</b> الآن كل تغيير في حالة الاتصال يظهر في Console مثل <code>[Voice] voice-complaint-0004 -&gt; CONNECTED</code>.</li>
              <li><b>VoiceRecordingSession:</b> State Machine بهذه الحالات فقط وبترتيب واحد:</li>
            </ol>
            <Pre copy={false}>{`CONNECTING ──(CONNECTED)──► RECORDING ──(stop)──► STOPPING ──► STOPPED
     └──────────(خطأ / مهلة 20 ثانية / 3 فشل اتصال)──┘

start():  setReceivingHandler → setConnectionListener → openAudioConnection   (مرة واحدة)
stop():   إلغاء المؤقتات → فصل Listener و Handler → closeAudioConnection      (مرة واحدة)
          → إنهاء ملف WAV على Virtual Thread → رفع التسجيل للتذكرة → إزالة الجلسة`}</Pre>
            <ul>
              <li><b>حد أقصى 3 أخطاء اتصال:</b> بدل إعادة المحاولة للأبد، الجلسة تتوقف وتكتب السبب في التذكرة.</li>
              <li><b><code>stop()</code> آمن (Idempotent):</b> يمكن استدعاؤه من زر «إنهاء التسجيل» ومن Event خروج البوت بنفس الوقت؛ أول استدعاء فقط ينفذ، والرفع يتم مرة واحدة (<code>pipelineOnce</code>).</li>
              <li><b>WavRecorder:</b> لا يكتب على القرص داخل Thread الصوت الخاص بـ JDA (كان ذلك قد يسبب تقطيع)، بل يضع البيانات في Queue ويكتبها Virtual Thread منفصل.</li>
              <li><b>إذا غادر صاحب الشكوى الروم</b> أو طُرد البوت أو حُذفت القناة → إيقاف وحفظ تلقائي، <b>بدون</b> إعادة دخول.</li>
              <li>ملف التسجيل يُرسل داخل التذكرة كدليل، فيظهر تلقائيًا في الـ Transcript عند الحفظ.</li>
            </ul>
          </Section>

          {/* ===== 4 ===== */}
          <Section id="claim" title="4. كيف يعمل Claim و Unclaim">
            <p>رسالة التحكم الرئيسية تُرسل <b>مرة واحدة</b> عند إنشاء التذكرة، ويتم حفظ الـ ID الخاص بها في <code>Ticket.controlMessageId</code>.</p>
            <Pre copy={false}>{`🎫 KushTicket — 🛡️ شكوى على إداري
نوع الشكوى: شكوى على إداري     صاحب الشكوى: @User
الحالة: 🟢 مفتوحة               Claimed by: لا يوجد
[ ✋ Claim ] [ 💾 حفظ الشكوى ] [ ✅ تم حل المشكلة ]

        ── الإداري يضغط Claim ── (Edit لنفس الرسالة) ──►

الحالة: 🔵 مفتوحة (قيد المعالجة)   Claimed by: @Admin (منذ دقيقة)
[ ↩️ Unclaim ] [ 💾 حفظ الشكوى ] [ ✅ تم حل المشكلة ]`}</Pre>
            <ol>
              <li><code>ClaimSystem.onButtonInteraction</code> يستقبل فقط <code>ticket_claim</code> و <code>ticket_unclaim</code> ويتجاهل غيرها.</li>
              <li>يبحث عن التذكرة من <code>event.getChannel().getIdLong()</code> في <code>TicketStore</code>.</li>
              <li><b>التحقق من الصلاحية داخل الـ Interaction:</b> <code>StaffPermissions.isStaff(member, config)</code>. صاحب الشكوى يرى رسالة خاصة «هذا الزر للإداريين فقط».</li>
              <li><code>ticket.tryClaim(adminId)</code> دالة <code>synchronized</code>: لو ضغط إداريان بنفس اللحظة، واحد فقط ينجح، والثاني يرى «مستلمة بالفعل بواسطة @X».</li>
              <li><code>event.editMessageEmbeds(TicketPanel.embed(ticket)).setComponents(TicketPanel.rows(ticket))</code> — هذا يرد على الـ Interaction ويعدل نفس الرسالة في طلب واحد. <b>لا رسالة جديدة.</b></li>
              <li><b>Unclaim:</b> فقط الإداري الذي عمل Claim يستطيع إلغاءه. (استثناء: من لديه صلاحية Administrator يستطيع إلغاء Claim إجباري إذا غاب الإداري.)</li>
              <li>الحالة تُحفظ في <code>data/tickets.json</code>، فلو أُعيد تشغيل البوت تبقى معروفة.</li>
            </ol>
            <Callout title="لماذا Custom IDs بسيطة بدون User ID؟">
              <p>
                الأزرار موجودة داخل قناة التذكرة نفسها، فنعرف التذكرة من القناة. ومعلومة «من عمل Claim» محفوظة في السيرفر، لا في الزر.
                لو وضعنا User ID داخل الـ Custom ID سيصبح قديمًا بعد كل تغيير، ولا نستطيع الوثوق به أصلاً. لأزرار فتح التذاكر فقط نستخدم
                <code>ticket_open:ADMIN_COMPLAINT</code>، ونتحقق من النوع بـ <code>TicketType.parse()</code> التي ترجع <code>null</code> لأي قيمة مزيفة.
              </p>
            </Callout>
          </Section>

          {/* ===== 5 ===== */}
          <Section id="save" title="5. كيف يعمل Save Complaint">
            <ol>
              <li>التحقق أن الضاغط إداري.</li>
              <li><code>event.deferReply(true)</code>: الحفظ قد يأخذ أكثر من 3 ثوانٍ (حد Discord)، والرد يكون Ephemeral (للإداري فقط) فلا تمتلئ التذكرة برسائل.</li>
              <li>جلب <b>كل</b> الرسائل (القسم 7) وترتيبها من الأقدم للأحدث.</li>
              <li><b>منع النسخ المكررة:</b> نقارن ID آخر رسالة + الحالة + من عمل Claim بآخر حفظ. إذا لم يتغير شيء → «لا توجد تغييرات منذ آخر حفظ (v002)» بدون إنشاء نسخة.</li>
              <li><b>منع الحفظ المتوازي:</b> <code>inFlight</code> Map: إذا ضغط إداري آخر أثناء الحفظ يرى «جارٍ الحفظ الآن». زر الإغلاق ينتظر انتهاء الحفظ الجاري ثم يحفظ.</li>
              <li>إنشاء نسخة مرقمة في مجلد منظم: <code>data/transcripts/complaint-admin-0001-&lt;channelId&gt;/v001/</code>.</li>
              <li>تحميل كل المرفقات (صور، ملفات، تسجيلات) إلى <code>attachments/</code> داخل نفس المجلد.</li>
              <li>كتابة <code>transcript.txt</code> و <code>transcript.html</code> و <code>complaint.json</code>.</li>
              <li>تحديث <code>Ticket</code> (رقم النسخة، آخر رسالة، من حفظ، متى) + <b>Edit</b> لرسالة التحكم لتظهر «💾 آخر حفظ: v001 بواسطة @Admin».</li>
              <li>إرسال ملفات TXT و HTML للإداري في الرد الخاص، ونسخة لقناة الأرشيف إذا حددت <code>transcript.channel.id</code>.</li>
            </ol>
            <Callout tone="ok" title="تم حل المشكلة (ticket_close)">
              <p>
                تأكيد خاص للإداري ← إيقاف أي تسجيل صوتي ورفعه ← <b>حفظ نهائي تلقائي</b> بالحالة «تم الحل» ← قفل الكتابة على صاحب الشكوى ← Edit
                رسالة التحكم (الأزرار تتعطل) ← حذف القناة بعد <code>close.delete.after.seconds</code> ثانية (ضع <code>-1</code> لعدم الحذف).
                <b> إذا فشل الحفظ لا تُحذف القناة</b> وترجع التذكرة مفتوحة، حتى لا تضيع المحادثة.
              </p>
            </Callout>
          </Section>

          {/* ===== 6 ===== */}
          <Section id="transcript" title="6. كيف يتم إنشاء الـ Transcript">
            <p>كل رسالة تتحول إلى <code>TranscriptMessage</code> (record) يحتوي على:</p>
            <ul>
              <li>اسم المرسل (Username + الاسم الظاهر في السيرفر) و User ID، وهل هو بوت.</li>
              <li>وقت الإرسال ووقت التعديل (إن عُدلت).</li>
              <li>المحتوى المقروء (<code>@Admin</code>) والمحتوى الخام (<code>&lt;@123&gt;</code>).</li>
              <li>المرفقات: الاسم، الحجم، النوع، الرابط الأصلي، ومسار النسخة المحلية.</li>
              <li>الـ Embeds (مثل رسالة التحكم)، الملصقات، الردود (Reply)، ورسائل النظام.</li>
              <li>روابط الأدلة المستخرجة من النص، وتجمع في قسم «Evidence Links» في النهاية.</li>
            </ul>
            <p>ثم <code>TranscriptWriter</code> يكتب 3 صيغ:</p>
            <Pre copy={false}>{`========================================
KushTicket Complaint Transcript
========================================

Ticket: complaint-admin-0001
Version: v001
User: @user (ID: 123456789012345678)
Complaint Type: Complaint Against Admin / شكوى على إداري
Claimed By: @admin (ID: 987654321098765432)
Status: Open / مفتوحة
Created At: 2026-10-05 01:30:12
Saved At: 2026-10-05 02:10:45
Messages: 6
Attachments: 1

----------------------------------------
CHAT
----------------------------------------

[2026-10-05 01:31:02] @user [ID: 123456789012345678]:
السلام عليكم، عندي شكوى

[2026-10-05 01:32:40] @admin [ID: 987654321098765432]:
وعليكم السلام، اتفضل اشرح المشكلة.

[2026-10-05 01:35:10] @user [ID: 123456789012345678]:
Attachment: proof.png (245.3 KB)
  https://cdn.discordapp.com/attachments/...
  Saved copy: attachments/1290-0-proof.png

========================================
END OF TRANSCRIPT
========================================`}</Pre>
            <ul>
              <li><b>transcript.html</b>: تصميم يشبه Discord (صور المستخدمين، الصور داخل الصفحة، مشغل للفيديو والصوت). افتحه بالمتصفح. كل النصوص يتم عمل escape لها لمنع أي كود HTML خبيث من أعضاء السيرفر.</li>
              <li><b>complaint.json</b>: كل البيانات بشكل منظم لو أردت لاحقًا لوحة تحكم أو بحث.</li>
            </ul>
          </Section>

          {/* ===== 7 ===== */}
          <Section id="pagination" title="7. كيف يتم جلب جميع رسائل Discord (Pagination)">
            <p>Discord يعطي <b>100 رسالة كحد أقصى</b> في كل طلب. لجلب كل المحادثة نطلب صفحات متتالية:</p>
            <Pre copy={false}>{`الطلب 1:  GET /channels/{id}/messages?limit=100              → أحدث 100
الطلب 2:  GET /channels/{id}/messages?limit=100&before={أقدم ID} → الـ 100 التي قبلها
...
الطلب N:  يرجع قائمة فارغة  → وصلنا لأول رسالة في القناة ✔`}</Pre>
            <p>في JDA لا نحتاج كتابة هذا يدويًا. <code>getIterableHistory()</code> يرجع <code>MessagePaginationAction</code> و <code>forEachAsync</code> يطلب الصفحات تلقائيًا ويحترم Rate Limits:</p>
            <Pre>{`channel.getIterableHistory()
        .cache(false)
        .forEachAsync(message -> {
            newestFirst.add(message);
            return newestFirst.size() < MAX_MESSAGES; // false = توقف
        })
        .thenApply(ignored -> newestFirst.reversed()); // Java 21 → الأقدم أولاً`}</Pre>
            <p>
              <code>forEachAsync</code> لا يجمّد البوت (Async)، ويرجع <code>CompletableFuture</code> ينتهي بعد آخر صفحة. حد الأمان 50,000 رسالة
              لكل تذكرة. و <code>reversed()</code> من ميزات Java 21 (SequencedCollection).
            </p>
          </Section>

          {/* ===== 8 ===== */}
          <Section id="placement" title="8. أين أضع كل Class">
            <p>
              في Eclipse: كليك يمين على <code>src/main/java</code> ← <b>New ← Package</b> واكتب الاسم (مثلاً <code>com.ticketbot.ticket</code>)، ثم
              كليك يمين على الـ package ← <b>New ← Class</b> واكتب اسم الكلاس <b>بنفس الاسم تمامًا</b> (حساس لحروف Capital)، ثم الصق المحتوى كاملاً.
            </p>
            <Pre copy={false}>{TREE}</Pre>
            <p>
              أسهل طريقة: اضغط <a href="/api/download" className="text-indigo-300 underline">تحميل المشروع (ZIP)</a> وانسخ مجلد <code>src</code>
              و <code>pom.xml</code> فوق مشروعك (بعد عمل نسخة احتياطية).
            </p>
          </Section>

          {/* ===== 9 ===== */}
          <Section id="main" title="9. التعديلات في Main">
            <Pre>{`JDA jda = JDABuilder.createDefault(config.token(), EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT,      // جديد: محتوى الرسائل للـ Transcript
                GatewayIntent.GUILD_VOICE_STATES))  // لمعرفة روم العضو
        .setAudioModuleConfig(new AudioModuleConfig()             // جديد: DAVE
                .withDaveSessionFactory(new LDJDADaveSessionFactory(new NativeDaveFactory())))
        .addEventListeners(ticketSystem, claimSystem, saveSystem, voiceSystem)
        .build();`}</Pre>
            <ul>
              <li><b>MESSAGE_CONTENT</b> هو Privileged Intent: افتح <a className="text-indigo-300 underline" href="https://discord.com/developers/applications" target="_blank" rel="noreferrer">Developer Portal</a> ← تطبيقك ← <b>Bot</b> ← فعّل <b>Message Content Intent</b> ← Save.</li>
              <li><b>NativeDaveFactory.ensureAvailable()</b> يفحص مكتبة DAVE عند التشغيل، فلو كانت ناقصة ترى الخطأ فورًا.</li>
              <li><b>acquireSingleInstanceLock</b> يمنع تشغيل نسختين (رسالة واضحة في Console إذا كانت هناك نسخة تعمل).</li>
              <li><b>Shutdown hook</b>: عند إيقاف البوت ينهي التسجيلات ويحفظ الملفات و <code>tickets.json</code>.</li>
              <li>يسجل أمر <code>/ticket-setup</code> في السيرفر المحدد في <code>guild.id</code> (فوري) أو Global.</li>
            </ul>
          </Section>

          {/* ===== 10 ===== */}
          <Section id="pom" title="10. التعديلات في pom.xml">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-white/10 text-right text-slate-400">
                    <th className="py-2">المكتبة</th>
                    <th className="py-2">الإصدار</th>
                    <th className="py-2">لماذا</th>
                  </tr>
                </thead>
                <tbody className="[&_td]:py-2 [&_tr]:border-b [&_tr]:border-white/5">
                  <tr><td><code>net.dv8tion:JDA</code></td><td>6.7.0</td><td>أحدث إصدار، ودعم DAVE بدأ من 6.3.0. واجهة الأزرار الجديدة <code>net.dv8tion.jda.api.components.*</code></td></tr>
                  <tr><td><code>moe.kyokobot.libdave:adapter-jda</code></td><td>0.1.4</td><td>يربط DAVE مع JDA</td></tr>
                  <tr><td><code>moe.kyokobot.libdave:impl-jni</code></td><td>0.1.4</td><td>تنفيذ DAVE (يعمل على Java 21)</td></tr>
                  <tr><td><code>natives-win-x86-64 / linux / darwin</code></td><td>0.1.4</td><td>المكتبة الأصلية لـ Windows (جهازك) و Linux (VPS) و macOS</td></tr>
                  <tr><td><code>ch.qos.logback:logback-classic</code></td><td>1.5.18</td><td>لظهور أخطاء JDA في Console</td></tr>
                  <tr><td><code>maven-shade-plugin</code></td><td>3.6.0</td><td>يبني <code>target/kushticket-2.0.0.jar</code> فيه كل شيء</td></tr>
                </tbody>
              </table>
            </div>
            <Callout tone="warn" title="إذا كان مشروعك على JDA 5">
              <p>
                الترقية لـ JDA 6 <b>إجبارية</b> للصوت. أهم تغيير ستلاحظه في كودك القديم: مسارات الـ import للأزرار تغيرت من
                <code>net.dv8tion.jda.api.interactions.components.buttons.Button</code> إلى <code>net.dv8tion.jda.api.components.buttons.Button</code>،
                و <code>ActionRow</code> إلى <code>net.dv8tion.jda.api.components.actionrow.ActionRow</code>، و <code>Modal</code> إلى
                <code>net.dv8tion.jda.api.modals.Modal</code> مع <code>Label.of(...)</code> لحقول الإدخال، و <code>asDisabled()</code> أصبح <code>withDisabled(true)</code>.
                Eclipse سيضع علامة حمراء على كل import قديم: اضغط <b>Ctrl+Shift+O</b> لإصلاحها تلقائيًا.
              </p>
            </Callout>
          </Section>

          {/* ===== 11 ===== */}
          <Section id="eclipse" title="11. التشغيل في Eclipse خطوة بخطوة">
            <ol>
              <li>استبدل <code>pom.xml</code> ثم: كليك يمين على المشروع ← <b>Maven ← Update Project…</b> ← فعّل <b>Force Update of Snapshots/Releases</b> ← OK.</li>
              <li>تأكد أن المشروع يستخدم Java 21: كليك يمين ← <b>Properties ← Java Compiler</b> ← 21.</li>
              <li>انسخ <code>config.properties.example</code> باسم <code>config.properties</code> في جذر المشروع (بجانب <code>pom.xml</code>) واكتب التوكن و IDs رولات الإدارة.</li>
              <li>فعّل <b>Message Content Intent</b> من Developer Portal.</li>
              <li>افتح <code>Main.java</code> ← كليك يمين ← <b>Run As ← Java Application</b>.</li>
              <li>لعرض العربية في Console: <b>Run ← Run Configurations ← Common ← Encoding ← UTF-8</b>.</li>
              <li>في Discord اكتب <code>/ticket-setup</code> في القناة التي تريد فيها لوحة التذاكر.</li>
              <li><b>قبل كل تشغيل جديد</b> أوقف القديم من زر المربع الأحمر في Console (والبوت الآن يرفض تشغيل نسخة ثانية).</li>
            </ol>
            <p>صلاحيات البوت المطلوبة في السيرفر: Manage Channels, Manage Roles (للصلاحيات داخل القناة), View Channel, Send Messages, Embed Links, Attach Files, Read Message History, Connect.</p>
            <p>لبناء jar للتشغيل على VPS: كليك يمين ← <b>Run As ← Maven build…</b> ← Goals: <code>clean package</code> ← ثم <code>java -jar target/kushticket-2.0.0.jar</code>.</p>
          </Section>

          {/* ===== 12 ===== */}
          <Section id="merge" title="12. الدمج مع كودك الحالي (ما الذي يُحذف ولماذا)">
            <p>بما أن كودك لم يصلني، هذه القواعد تحدد ماذا تفعل بكل جزء قديم:</p>
            <ul>
              <li><b>كود الصوت القديم (AudioManager / AudioReceiveHandler / Listeners الصوت):</b> يجب حذفه أو إيقاف تسجيله في <code>addEventListeners</code>. السبب: أي Listener قديم يستدعي <code>openAudioConnection()</code> سيعيد الـ Loop حتى مع الكود الجديد. ابحث في المشروع (<b>Ctrl+H</b> ← File Search) عن <code>openAudioConnection</code> — يجب أن يظهر <b>مكان واحد فقط</b>: <code>VoiceRecordingSession.start()</code>.</li>
              <li><b>نظام إنشاء التذاكر القديم:</b> إذا أردت إبقاءه، بعد إنشاء القناة أنشئ <code>Ticket</code> واستدعِ <code>store.add(ticket)</code> ثم أرسل رسالة التحكم بـ <code>TicketPanel.embed(ticket)</code> و <code>TicketPanel.rows(ticket)</code> واحفظ <code>ticket.setControlMessageId(...)</code> — انظر نهاية <code>TicketSystem.onModalInteraction</code> كمثال جاهز.</li>
              <li><b>أزرار الإغلاق القديمة:</b> إذا كان عندك Listener قديم يتعامل مع زر بنفس ID <code>ticket_close</code> احذفه أو غيّر الـ ID، حتى لا يرد على الزر Listener-ين (سيظهر خطأ «Interaction already acknowledged»).</li>
              <li><b>Main القديم:</b> انقل منه أي Listeners أخرى لا علاقة لها بالتذاكر/الصوت إلى سطر <code>addEventListeners(...)</code> في <code>Main</code> الجديد.</li>
            </ul>
            <p>أرسل لي ملفاتك الحالية وسأعطيك نسخة مدمجة دقيقة بدل هذه القواعد العامة.</p>
          </Section>

          {/* ===== 13 ===== */}
          <section id="files" className="scroll-mt-6">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-xl font-bold text-white md:text-2xl">13. الملفات كاملة</h2>
              <a href="/api/download" className="rounded-xl bg-[#5865f2] px-4 py-2 text-sm font-bold text-white hover:bg-[#4752c4]">
                ⬇️ تحميل الكل ZIP
              </a>
            </div>
            <div className="space-y-3">
              {files.map((f) => {
                const s = STATUS_STYLE[f.status];
                return (
                  <details key={f.path} className="group overflow-hidden rounded-2xl border border-white/5 bg-[#2b2d31]">
                    <summary className="flex cursor-pointer flex-wrap items-center gap-3 p-4 hover:bg-white/[0.03]">
                      <span className="text-slate-500 transition group-open:rotate-90">◀</span>
                      <code dir="ltr" className="font-mono text-sm font-semibold text-indigo-200">{f.path}</code>
                      <span className={`rounded-full border px-2 py-0.5 text-[11px] font-bold ${s.cls}`}>{s.label}</span>
                      <span className="text-xs text-slate-500">{f.lines} سطر</span>
                      <span className="basis-full text-sm text-slate-400">{f.description}</span>
                    </summary>
                    <div className="relative border-t border-white/5">
                      <div className="absolute left-3 top-3 z-10">
                        <CopyButton text={f.content} label="نسخ الملف كاملاً" />
                      </div>
                      <pre className="code max-h-[70vh] overflow-auto bg-[#1a1b1e] p-4 pt-12 text-slate-200">{f.content}</pre>
                    </div>
                  </details>
                );
              })}
            </div>
          </section>

          <footer className="py-8 text-center text-xs text-slate-500">
            KushTicket · Java 21 · JDA 6.7.0 · libdave-jvm 0.1.4 · تمت الترجمة والاختبار بـ Maven
          </footer>
        </div>
      </div>
    </main>
  );
}
