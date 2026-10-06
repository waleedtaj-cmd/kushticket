package com.ticketbot.ticket;

import com.ticketbot.config.BotConfig;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * نظام Claim / Unclaim.
 *
 * عند الضغط على Claim:
 *   1) نتحقق أن الضاغط إداري.
 *   2) نحاول Claim بطريقة Thread-safe (ticket.tryClaim).
 *   3) نعدل نفس الرسالة (event.editMessageEmbeds) => الزر يصبح Unclaim ويظهر "Claimed by: @Admin".
 *
 * event.editMessageEmbeds(...) يعمل شيئين في طلب واحد:
 *   - يرد على الـ Interaction (حتى لا يظهر "This interaction failed").
 *   - يعدل الرسالة التي تحتوي على الزر.
 */
public final class ClaimSystem extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimSystem.class);

    private final BotConfig config;
    private final TicketStore store;

    public ClaimSystem(BotConfig config, TicketStore store) {
        this.config = config;
        this.store = store;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.equals(TicketIds.CLAIM) && !id.equals(TicketIds.UNCLAIM)) {
            return; // ليس زرنا، نتركه لباقي الأنظمة
        }

        Optional<Ticket> found = store.byChannel(event.getChannel().getIdLong());
        if (found.isEmpty()) {
            event.reply("❌ هذه القناة ليست تذكرة مسجلة.").setEphemeral(true).queue();
            return;
        }
        Ticket ticket = found.get();
        Member member = event.getMember();

        // ===== التحقق من الصلاحية داخل الـ Interaction نفسه =====
        if (!StaffPermissions.isStaff(member, config)) {
            event.reply(StaffPermissions.NO_PERMISSION).setEphemeral(true).queue();
            return;
        }

        // الأمان: نتأكد أن الزر من رسالة التحكم الحقيقية وليس نسخة قديمة
        if (ticket.controlMessageId() != 0 && event.getMessageIdLong() != ticket.controlMessageId()) {
            event.reply("⚠️ هذه رسالة تحكم قديمة، استخدم رسالة التحكم الرئيسية.").setEphemeral(true).queue();
            return;
        }

        if (id.equals(TicketIds.CLAIM)) {
            handleClaim(event, ticket, member);
        } else {
            handleUnclaim(event, ticket, member);
        }
    }

    private void handleClaim(ButtonInteractionEvent event, Ticket ticket, Member member) {
        Ticket.ClaimResult result = ticket.tryClaim(member.getIdLong());
        switch (result) {
            case CLAIMED -> {
                store.save();
                LOG.info("{} claimed by {}", ticket.displayName(), member.getUser().getName());
                // Edit Message: نفس الرسالة، الزر يصبح Unclaim
                event.editMessageEmbeds(TicketPanel.embed(ticket))
                        .setComponents(TicketPanel.rows(ticket))
                        .queue();
            }
            case ALREADY_YOURS -> refreshWithNotice(event, ticket, "ℹ️ أنت مستلم هذه الشكوى بالفعل.");
            case CLAIMED_BY_OTHER -> refreshWithNotice(event, ticket,
                    "⚠️ هذه الشكوى مستلمة بالفعل بواسطة <@" + ticket.claimedById() + ">.");
            case NOT_OPEN -> event.reply("❌ هذه التذكرة مغلقة.").setEphemeral(true).queue();
        }
    }

    private void handleUnclaim(ButtonInteractionEvent event, Ticket ticket, Member member) {
        boolean force = StaffPermissions.canForceUnclaim(member);
        Ticket.UnclaimResult result = ticket.tryUnclaim(member.getIdLong(), force);
        switch (result) {
            case UNCLAIMED -> {
                store.save();
                LOG.info("{} unclaimed by {}", ticket.displayName(), member.getUser().getName());
                // Edit Message: يرجع الزر Claim و "Claimed by: لا يوجد"
                event.editMessageEmbeds(TicketPanel.embed(ticket))
                        .setComponents(TicketPanel.rows(ticket))
                        .queue();
            }
            case NOT_CLAIMED -> refreshWithNotice(event, ticket, "ℹ️ هذه الشكوى غير مستلمة حاليًا.");
            case NOT_YOUR_CLAIM -> event.reply("⛔ فقط <@" + ticket.claimedById()
                    + "> (الذي استلم الشكوى) يستطيع عمل Unclaim.").setEphemeral(true).queue();
            case NOT_OPEN -> event.reply("❌ هذه التذكرة مغلقة.").setEphemeral(true).queue();
        }
    }

    /**
     * عندما تكون الرسالة التي يراها الإداري قديمة (مثلاً ضغط Claim بعد أن استلمها غيره بلحظة):
     * نرد عليه برسالة خاصة (Ephemeral) ونصحح رسالة التحكم لتظهر الحالة الصحيحة.
     */
    private void refreshWithNotice(ButtonInteractionEvent event, Ticket ticket, String notice) {
        event.reply(notice).setEphemeral(true).queue();
        event.getMessage().editMessageEmbeds(TicketPanel.embed(ticket))
                .setComponents(TicketPanel.rows(ticket))
                .queue(null, err -> LOG.debug("Refresh failed: {}", err.getMessage()));
    }
}
