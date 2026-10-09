import { pgTable, serial, text, timestamp, boolean, varchar } from 'drizzle-orm/pg-core';

// 1. جدول التذاكر (Tickets)
export const tickets = pgTable('tickets', {
  id: serial('id').primaryKey(),
  ticketId: text('ticket_id').notNull().unique(), // ID التذكرة أو القناة
  guildId: text('guild_id').notNull(),            // ID السيرفر
  userId: text('user_id').notNull(),              // ID صاحب التذكرة
  channelId: text('channel_id').notNull(),        // ID قناة التذكرة
  category: text('category').default('general'), // قسم التذكرة
  status: varchar('status', { length: 20 }).default('open').notNull(), // open, closed
  closedBy: text('closed_by'),                   // ID المشرف الذي أغلق التذكرة
  createdAt: timestamp('created_at').defaultNow().notNull(),
  closedAt: timestamp('closed_at'),
});

// 2. جدول المحادثات والسجلات (Transcripts / Messages)
export const transcripts = pgTable('transcripts', {
  id: serial('id').primaryKey(),
  ticketId: text('ticket_id').notNull(),          // مرجع التذكرة
  authorId: text('author_id').notNull(),          // ID المرسل
  authorName: text('author_name').notNull(),      // اسم المرسل
  content: text('content'),                       // نص الرسالة
  attachments: text('attachments'),               // روابط المرفقات أو الملفات الصوتية
  isVoiceNote: boolean('is_voice_note').default(false),
  createdAt: timestamp('created_at').defaultNow().notNull(),
});

// 3. جدول الشكاوى والبلاغات (Complaints)
export const complaints = pgTable('complaints', {
  id: serial('id').primaryKey(),
  ticketId: text('ticket_id').notNull(),
  reporterId: text('reporter_id').notNull(),       // المُشتكي
  targetId: text('target_id'),                     // المُشتكى عليه
  reason: text('reason').notNull(),               // سبب الشكوى
  resolution: text('resolution'),                 // الإجراء المتخذ
  resolvedBy: text('resolved_by'),                 // المشرف المسؤول
  createdAt: timestamp('created_at').defaultNow().notNull(),
});
