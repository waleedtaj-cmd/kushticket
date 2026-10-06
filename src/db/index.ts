import { drizzle } from "drizzle-orm/node-postgres";
import { Pool } from "pg";

// يقرأ الرابط من DATABASE_PUBLIC_URL أو DATABASE_URL، ويستخدم رابط Railway الخاص بك كقيمة احتياطية
const databaseUrl =
  process.env.DATABASE_PUBLIC_URL ||
  process.env.DATABASE_URL ||
  "postgresql://postgres:nvFkaxqhLWwHWovrcsFVOHWqnILxhPCh@interchange.proxy.rlwy.net:24661/railway";

const globalForDb = globalThis as typeof globalThis & {
  __arenaNextJsPostgresqlPool?: Pool;
};

export const pool =
  globalForDb.__arenaNextJsPostgresqlPool ??
  new Pool({
    connectionString: databaseUrl,
  });

if (process.env.NODE_ENV !== "production") {
  globalForDb.__arenaNextJsPostgresqlPool = pool;
}

export const db = drizzle(pool);
