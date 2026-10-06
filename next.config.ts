import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // output: 'standalone' مطلوب عند النشر بـ Docker / Railway / Vercel
  // يُنتج مجلد .next/standalone صغير الحجم بدون node_modules كامل
  output: "standalone",
};

export default nextConfig;
