"use client";

import { FormEvent, useState } from "react";

export default function HomePage() {
  const [ticket, setTicket] = useState("");
  const [version, setVersion] = useState("1");
  const [openedTicket, setOpenedTicket] = useState("");
  const [openedVersion, setOpenedVersion] = useState("1");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const cleanTicket = ticket.trim();
    const cleanVersion = version.trim();

    if (!cleanTicket) {
      setError("اكتب معرّف البلاغ أو اسم مجلد التذكرة أولاً.");
      return;
    }
    if (!/^\d+$/.test(cleanVersion) || Number(cleanVersion) < 1) {
      setError("رقم النسخة لازم يكون رقم صحيح أكبر من صفر.");
      return;
    }

    setLoading(true);
    try {
      const url = `/api/transcript?ticket=${encodeURIComponent(cleanTicket)}&v=${encodeURIComponent(cleanVersion)}`;
      const response = await fetch(url, { method: "GET", cache: "no-store" });
      if (!response.ok) {
        if (response.status === 404) {
          setError("ما لقينا سجل بالبيانات دي. اتأكد من معرّف البلاغ ورقم النسخة، وتأكد إن ملفات التسجيل محفوظة في المسار المتصل بالموقع.");
        } else {
          setError("حصل خطأ أثناء جلب السجل. جرّب تاني أو راجع Logs في Railway.");
        }
        return;
      }
      setOpenedTicket(cleanTicket);
      setOpenedVersion(cleanVersion);
    } catch {
      setError("ما قدرنا نتصل بالخادم حالياً. حدّث الصفحة وجرب مرة تانية.");
    } finally {
      setLoading(false);
    }
  }

  const transcriptUrl = openedTicket
    ? `/api/transcript?ticket=${encodeURIComponent(openedTicket)}&v=${encodeURIComponent(openedVersion)}`
    : "";

  return (
    <main className="min-h-screen px-4 py-8 text-slate-100 md:px-8 md:py-12">
      <div className="mx-auto max-w-6xl">
        <header className="relative overflow-hidden rounded-3xl border border-white/10 bg-gradient-to-br from-[#252b58] via-[#20233b] to-[#171923] p-7 shadow-2xl md:p-11">
          <div className="pointer-events-none absolute -left-16 -top-20 h-64 w-64 rounded-full bg-indigo-500/20 blur-3xl" />
          <div className="relative">
            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-indigo-300/20 bg-indigo-300/10 px-3 py-1.5 text-sm text-indigo-100">
              <span className="h-2 w-2 rounded-full bg-emerald-400" />
              KushTicket Support Portal
            </div>
            <h1 className="text-3xl font-black leading-tight md:text-5xl">مرحباً بكم في KushTicket</h1>
            <p className="mt-4 max-w-3xl text-base leading-8 text-slate-300 md:text-lg">
              دي الصفحة المخصصة لمراجعة سجلات البلاغات والشكاوى المحفوظة من نظام التذاكر. تقدر تبحث عن سجل بلاغ معيّن وتفتح نسخة الـ Transcript لمراجعة تفاصيل المحادثة.
            </p>
            <div className="mt-7 flex flex-wrap gap-3 text-sm">
              <span className="rounded-xl border border-white/10 bg-white/5 px-4 py-2.5">سجلات البلاغات</span>
              <span className="rounded-xl border border-white/10 bg-white/5 px-4 py-2.5">مراجعة المحادثات</span>
              <span className="rounded-xl border border-white/10 bg-white/5 px-4 py-2.5">أرشيف التذاكر</span>
            </div>
          </div>
        </header>

        <section className="mt-7 grid gap-5 md:grid-cols-3">
          <article className="rounded-2xl border border-white/10 bg-[#22242b] p-5">
            <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-500/15 text-indigo-300">
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" strokeLinecap="round"/></svg>
            </div>
            <h2 className="font-bold">سجلات البلاغات</h2>
            <p className="mt-2 text-sm leading-7 text-slate-400">ابحث عن بلاغ محفوظ باستخدام اسم مجلد التذكرة أو المعرّف المسجل عند البوت.</p>
          </article>
          <article className="rounded-2xl border border-white/10 bg-[#22242b] p-5">
            <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-sky-500/15 text-sky-300">
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21z"/><path d="M4 5.5v13A2.5 2.5 0 0 1 6.5 16H20"/></svg>
            </div>
            <h2 className="font-bold">Transcript كامل</h2>
            <p className="mt-2 text-sm leading-7 text-slate-400">افتح نسخة المحادثة المحفوظة لمراجعة الرسائل والتفاصيل المتاحة في الأرشيف.</p>
          </article>
          <article className="rounded-2xl border border-white/10 bg-[#22242b] p-5">
            <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-500/15 text-emerald-300">
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M12 3 20 6v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6z"/><path d="m8.5 12 2.2 2.2 4.8-5" strokeLinecap="round" strokeLinejoin="round"/></svg>
            </div>
            <h2 className="font-bold">مراجعة منظمة</h2>
            <p className="mt-2 text-sm leading-7 text-slate-400">واجهة واحدة للوصول للسجلات المتوفرة، بدون الحاجة للبحث داخل ملفات المشروع يدوياً.</p>
          </article>
        </section>

        <section className="mt-7 rounded-2xl border border-white/10 bg-[#22242b] p-6 md:p-8">
          <div className="mb-5">
            <p className="text-sm font-semibold text-indigo-300">الأرشيف</p>
            <h2 className="mt-1 text-2xl font-extrabold">البحث في سجلات البلاغات</h2>
            <p className="mt-2 text-sm leading-7 text-slate-400">أدخل اسم مجلد التذكرة كما هو محفوظ في الأرشيف، وحدد رقم نسخة الـ Transcript.</p>
          </div>
          <form onSubmit={handleSearch} className="grid gap-4 md:grid-cols-[minmax(0,1fr)_150px_auto] md:items-end">
            <label className="block">
              <span className="mb-2 block text-sm font-semibold text-slate-300">معرّف البلاغ / اسم مجلد التذكرة</span>
              <input value={ticket} onChange={(e) => setTicket(e.target.value)} placeholder="مثال: complaint-admin-0001-123456789012345678" className="w-full rounded-xl border border-white/10 bg-[#17181d] px-4 py-3 text-left text-sm text-white outline-none transition placeholder:text-slate-600 focus:border-indigo-400" dir="ltr" />
            </label>
            <label className="block">
              <span className="mb-2 block text-sm font-semibold text-slate-300">رقم النسخة</span>
              <input value={version} onChange={(e) => setVersion(e.target.value)} inputMode="numeric" placeholder="1" className="w-full rounded-xl border border-white/10 bg-[#17181d] px-4 py-3 text-left text-white outline-none transition placeholder:text-slate-600 focus:border-indigo-400" dir="ltr" />
            </label>
            <button type="submit" disabled={loading} className="rounded-xl bg-indigo-500 px-6 py-3 font-bold text-white transition hover:bg-indigo-400 disabled:cursor-wait disabled:opacity-60">
              {loading ? "جاري البحث..." : "عرض السجل"}
            </button>
          </form>
          {error && <div role="alert" className="mt-4 rounded-xl border border-amber-400/20 bg-amber-400/10 p-4 text-sm leading-7 text-amber-100">{error}</div>}
          {!openedTicket && !error && <p className="mt-4 text-xs leading-6 text-slate-500">ملاحظة: السجلات ما حتظهر تلقائياً إلا إذا كانت ملفات الـ Transcript محفوظة في المسار الذي يقرأ منه الموقع.</p>}
        </section>

        {openedTicket && transcriptUrl && (
          <section className="mt-7 overflow-hidden rounded-2xl border border-white/10 bg-[#22242b]">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-white/10 p-5">
              <div>
                <h2 className="font-bold">سجل البلاغ</h2>
                <p className="mt-1 break-all text-xs text-slate-400" dir="ltr">{openedTicket} · v{openedVersion}</p>
              </div>
              <a href={transcriptUrl} target="_blank" rel="noreferrer" className="rounded-lg border border-white/15 px-4 py-2 text-sm font-semibold transition hover:bg-white/5">فتح في صفحة مستقلة</a>
            </div>
            <iframe key={transcriptUrl} title="Transcript البلاغ" src={transcriptUrl} className="h-[75vh] min-h-[520px] w-full bg-white" />
          </section>
        )}

        <footer className="mt-8 flex flex-col gap-2 border-t border-white/10 py-6 text-center text-xs leading-6 text-slate-500">
          <p className="font-semibold text-slate-400">KushTicket — بوابة سجلات البلاغات</p>
          <p>للاستخدام الإداري ومراجعة البلاغات المحفوظة في النظام.</p>
        </footer>
      </div>
    </main>
  );
}
