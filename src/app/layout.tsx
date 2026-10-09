import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "KushTicket — سجلات البلاغات",
  description: "بوابة KushTicket لمراجعة سجلات البلاغات والشكاوى وفتح نسخ المحادثات المحفوظة.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ar" dir="rtl">
      <body className="bg-[#17181d] text-slate-100 antialiased">{children}</body>
    </html>
  );
}
