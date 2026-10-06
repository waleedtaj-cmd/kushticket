import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "KushTicket — دليل التعديلات (Claim · Save Complaint · Voice Fix)",
  description: "شرح وكود كامل لبوت KushTicket باستخدام Java 21 + JDA 6 + Maven.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ar" dir="rtl">
      <body className="bg-[#1e1f22] text-slate-200 antialiased">{children}</body>
    </html>
  );
}
