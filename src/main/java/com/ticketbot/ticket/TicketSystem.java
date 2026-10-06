package com.ticketbot.ticket;

import com.ticketbot.complaint.ComplaintSaveSystem;
import com.ticketbot.config.BotConfig;
import com.ticketbot.voice.VoiceComplaintSystem;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * النظام الأساسي للتذاكر:
 *   - /ticket-setup  => يرسل لوحة فتح التذاكر (أزرار لكل نوع).
 *   - ticket_open:TYPE => يفتح Modal (سبب الشكوى، الأدلة، المشكو عليه).
 *   - ticket_modal:TYPE => ينشئ قناة التذكرة + يرسل رسالة التحكم الرئيسية (مرة واحدة).
 *   - ticket_close => تأكيد Ephemeral => حفظ تلقائي => تعديل رسالة التحكم => حذف القناة.
 */
public final class TicketSystem extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(TicketSystem.class);

    private static final EnumSet<Permission> OWNER_ALLOW = EnumSet.of(
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS);
    private static final EnumSet<Permission> STAFF_ALLOW = EnumSet.of(
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_MANAGE);
    private static final EnumSet<Permission> BOT_ALLOW = EnumSet.of(
            Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS, Permission.MANAGE_CHANNEL,
            Permission.MANAGE_PERMISSIONS);

    private final BotConfig config;
    private final TicketStore store;
    private final ComplaintSaveSystem saveSystem;
    private final VoiceComplaintSystem voiceSystem;

    public TicketSystem(BotConfig config, TicketStore store,
                        ComplaintSaveSystem saveSystem, VoiceComplaintSystem voiceSystem) {
        this.config = config;
        this.store = store;
        this.saveSystem = saveSystem;
        this.voiceSystem = voiceSystem;
    }

    /** تعريف أمر /ticket-setup (يتم تسجيله في Main). */
    public static SlashCommandData setupCommand() {
        return Commands.slash(TicketIds.SETUP_COMMAND, "إرسال لوحة فتح التذاكر في هذه القناة")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    // =====================================================================
    // 1) /ticket-setup
    // =====================================================================

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(TicketIds.SETUP_COMMAND)) return;
        if (!StaffPermissions.isStaff(event.getMember(), config)) {
            event.reply(StaffPermissions.NO_PERMISSION).setEphemeral(true).queue();
            return;
        }
        var embed = new EmbedBuilder()
                .setTitle("🎫 KushTicket — نظام الشكاوى")
                .setDescription("""
                        اختر نوع التذكرة من الأزرار بالأسفل.
                        سيتم إنشاء قناة خاصة بك مع الإدارة.

                        🛡️ **شكوى على إداري** — تدعم التسجيل الصوتي داخل روم صوتي.
                        ❓ **شكوى عامة** — تذكرة نصية عادية.""")
                .setColor(new Color(0x5865F2))
                .build();

        // زرين فقط: شكوى على إداري (صوتية) + شكوى عامة
        List<Button> buttons = List.of(
                Button.secondary(TicketIds.OPEN_PREFIX + TicketType.ADMIN_COMPLAINT.name(),
                                TicketType.ADMIN_COMPLAINT.arabicLabel())
                        .withEmoji(Emoji.fromUnicode(TicketType.ADMIN_COMPLAINT.emoji())),
                Button.secondary(TicketIds.OPEN_PREFIX + TicketType.GENERAL_ISSUE.name(),
                                TicketType.GENERAL_ISSUE.arabicLabel())
                        .withEmoji(Emoji.fromUnicode(TicketType.GENERAL_ISSUE.emoji())));
        event.getChannel().sendMessageEmbeds(embed).setComponents(ActionRow.of(buttons)).queue();
        event.reply("✅ تم إرسال لوحة التذاكر.").setEphemeral(true).queue();
    }

    // =====================================================================
    // 2) Buttons: open / close / close confirm
    // =====================================================================

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id.startsWith(TicketIds.OPEN_PREFIX)) {
            handleOpenButton(event, id.substring(TicketIds.OPEN_PREFIX.length()));
        } else if (id.equals(TicketIds.CLOSE)) {
            handleCloseButton(event);
        } else if (id.equals(TicketIds.CLOSE_CONFIRM)) {
            handleCloseConfirm(event);
        } else if (id.equals(TicketIds.CLOSE_CANCEL)) {
            event.editMessage("تم إلغاء الإغلاق.").setComponents().queue();
        }
    }

    private void handleOpenButton(ButtonInteractionEvent event, String typeName) {
        TicketType type = TicketType.parse(typeName);
        if (type == null || event.getGuild() == null) {
            event.reply("❌ نوع غير معروف.").setEphemeral(true).queue();
            return;
        }
        Optional<Ticket> open = store.findOpen(event.getGuild().getIdLong(), event.getUser().getIdLong(), type);
        if (open.isPresent()) {
            event.reply("⚠️ لديك تذكرة مفتوحة من نفس النوع: <#" + open.get().channelId() + ">")
                    .setEphemeral(true).queue();
            return;
        }

        List<Label> fields = new ArrayList<>();
        if (type.needsTarget()) {
            fields.add(Label.of("المشكو عليه (الاسم أو ID)",
                    TextInput.create(TicketIds.FIELD_TARGET, TextInputStyle.SHORT)
                            .setRequired(true).setMaxLength(200).build()));
        }
        fields.add(Label.of("سبب الشكوى / وصف المشكلة",
                TextInput.create(TicketIds.FIELD_REASON, TextInputStyle.PARAGRAPH)
                        .setRequired(true).setMinLength(5).setMaxLength(1000).build()));
        fields.add(Label.of("الأدلة (روابط أو وصف) — اختياري",
                TextInput.create(TicketIds.FIELD_EVIDENCE, TextInputStyle.PARAGRAPH)
                        .setRequired(false).setMaxLength(1000)
                        .setPlaceholder("يمكنك أيضًا إرسال الصور والملفات داخل التذكرة بعد فتحها").build()));

        Modal modal = Modal.create(TicketIds.MODAL_PREFIX + type.name(), type.arabicLabel())
                .addComponents(fields)
                .build();
        event.replyModal(modal).queue();
    }

    // =====================================================================
    // 3) Modal submit => إنشاء القناة + رسالة التحكم
    // =====================================================================

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith(TicketIds.MODAL_PREFIX)) return;
        TicketType type = TicketType.parse(event.getModalId().substring(TicketIds.MODAL_PREFIX.length()));
        Guild guild = event.getGuild();
        Member owner = event.getMember();
        if (type == null || guild == null || owner == null) {
            event.reply("❌ حدث خطأ.").setEphemeral(true).queue();
            return;
        }
        if (store.findOpen(guild.getIdLong(), owner.getIdLong(), type).isPresent()) {
            event.reply("⚠️ لديك تذكرة مفتوحة من نفس النوع.").setEphemeral(true).queue();
            return;
        }

        String target = value(event.getValue(TicketIds.FIELD_TARGET));
        String reason = value(event.getValue(TicketIds.FIELD_REASON));
        String evidence = value(event.getValue(TicketIds.FIELD_EVIDENCE));

        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        int number = store.nextNumber();
        String channelName = "%s-%04d".formatted(type.channelPrefix(), number);
        Category category = config.ticketCategoryId() == 0 ? null : guild.getCategoryById(config.ticketCategoryId());

        ChannelAction<TextChannel> action = (category != null
                ? category.createTextChannel(channelName)
                : guild.createTextChannel(channelName))
                .setTopic("KushTicket #%04d | %s | owner: %s".formatted(number, type.englishLabel(), owner.getId()))
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addMemberPermissionOverride(guild.getSelfMember().getIdLong(), BOT_ALLOW, null)
                .addMemberPermissionOverride(owner.getIdLong(), OWNER_ALLOW, null);
        for (long roleId : config.staffRoleIds()) {
            Role role = guild.getRoleById(roleId);
            if (role != null) action = action.addRolePermissionOverride(roleId, STAFF_ALLOW, null);
        }

        action.queue(channel -> {
            Ticket ticket = new Ticket(number, guild.getIdLong(), channel.getIdLong(), channel.getName(),
                    owner.getIdLong(), type, target, reason, evidence, Instant.now());
            store.add(ticket);

            // ===== إسناد رول الشكوى الصوتية عند فتح تذكرة من نوع شكوى على إداري =====
            if (type.isVoice() && config.voiceComplaintRoleId() != 0) {
                Role voiceRole = guild.getRoleById(config.voiceComplaintRoleId());
                if (voiceRole != null && guild.getSelfMember().canInteract(voiceRole)) {
                    guild.addRoleToMember(owner, voiceRole)
                            .reason("KushTicket — فتح شكوى صوتية على إداري")
                            .queue(null, err -> LOG.warn("Could not assign voice complaint role: {}", err.getMessage()));
                } else if (voiceRole == null) {
                    LOG.warn("voice.complaint.role.id={} not found in guild", config.voiceComplaintRoleId());
                } else {
                    LOG.warn("Bot hierarchy does not allow assigning role {}", voiceRole.getName());
                }
            }

            // منشن صاحب الشكوى + رولات الإدارة (تنبيه) — رسالة واحدة فقط هي رسالة التحكم
            StringBuilder mentions = new StringBuilder(owner.getAsMention());
            for (long roleId : config.staffRoleIds()) mentions.append(" <@&").append(roleId).append('>');

            channel.sendMessage(mentions.toString())
                    .setEmbeds(TicketPanel.embed(ticket))
                    .setComponents(TicketPanel.rows(ticket))
                    .mentionUsers(owner.getIdLong())
                    .mentionRoles(config.staffRoleIds().stream().map(String::valueOf).toList())
                    .queue(message -> {
                        ticket.setControlMessageId(message.getIdLong());
                        store.save();
                        hook.editOriginal("✅ تم فتح تذكرتك: " + channel.getAsMention()).queue();
                        LOG.info("Created {} for {}", ticket.displayName(), owner.getUser().getName());
                    }, err -> hook.editOriginal("❌ تم إنشاء القناة لكن فشل إرسال رسالة التحكم.").queue());
        }, err -> {
            LOG.error("Failed to create ticket channel", err);
            hook.editOriginal("❌ فشل إنشاء التذكرة: " + err.getMessage()).queue();
        });
    }

    // =====================================================================
    // 4) Close / Solved
    // =====================================================================

    private void handleCloseButton(ButtonInteractionEvent event) {
        Ticket ticket = store.byChannel(event.getChannel().getIdLong()).orElse(null);
        if (ticket == null) {
            event.reply("❌ هذه القناة ليست تذكرة مسجلة.").setEphemeral(true).queue();
            return;
        }
        if (!StaffPermissions.isStaff(event.getMember(), config)) {
            event.reply(StaffPermissions.NO_PERMISSION).setEphemeral(true).queue();
            return;
        }
        if (ticket.status() != Ticket.Status.OPEN) {
            event.reply("❌ هذه التذكرة مغلقة بالفعل.").setEphemeral(true).queue();
            return;
        }
        // تأكيد يظهر للإداري فقط (Ephemeral) => لا يضيف رسائل للتذكرة
        event.reply("هل أنت متأكد أن المشكلة تم حلها؟ سيتم حفظ الشكوى تلقائيًا ثم إغلاق التذكرة.")
                .setEphemeral(true)
                .setComponents(ActionRow.of(
                        Button.danger(TicketIds.CLOSE_CONFIRM, "نعم، تم الحل").withEmoji(Emoji.fromUnicode("✅")),
                        Button.secondary(TicketIds.CLOSE_CANCEL, "إلغاء")))
                .queue();
    }

    private void handleCloseConfirm(ButtonInteractionEvent event) {
        Ticket ticket = store.byChannel(event.getChannel().getIdLong()).orElse(null);
        Member member = event.getMember();
        if (ticket == null || !(event.getChannel() instanceof TextChannel channel)) {
            event.editMessage("❌ هذه القناة ليست تذكرة مسجلة.").setComponents().queue();
            return;
        }
        if (!StaffPermissions.isStaff(member, config)) {
            event.editMessage(StaffPermissions.NO_PERMISSION).setComponents().queue();
            return;
        }
        if (!ticket.tryMarkSolved(member.getIdLong())) {
            event.editMessage("❌ هذه التذكرة مغلقة بالفعل.").setComponents().queue();
            return;
        }
        event.editMessage("⏳ جارٍ إيقاف أي تسجيل وحفظ الشكوى ثم الإغلاق...").setComponents().queue();
        InteractionHook hook = event.getHook();
        Guild guild = channel.getGuild();

        // 1) إيقاف التسجيل الصوتي (إن وجد) — حتى يُرفع الملف قبل الحفظ
        voiceSystem.stopForTicket(ticket, "تم إغلاق التذكرة")
                // 2) حفظ Transcript نهائي (بالحالة: تم الحل)
                .thenCompose(v -> saveSystem.saveTicket(ticket, channel, member.getUser()))
                .whenComplete((result, error) -> {
                    if (error != null) {
                        // لا نحذف القناة بدون Transcript!
                        LOG.error("Final save failed for {}", ticket.displayName(), error);
                        ticket.revertSolved();
                        store.save();
                        TicketPanel.refresh(guild, ticket);
                        hook.editOriginal("❌ فشل الحفظ النهائي، لم يتم إغلاق التذكرة: " + error.getMessage()).queue();
                        return;
                    }

                    int delay = config.deleteChannelAfterCloseSeconds();
                    if (delay >= 0) ticket.setDeleteAt(Instant.now().plusSeconds(delay));
                    store.save();

                    // 3) قفل الكتابة على صاحب الشكوى
                    channel.getManager()
                            .putMemberPermissionOverride(ticket.ownerId(),
                                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                                    EnumSet.of(Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES))
                            .queue(null, err -> LOG.warn("Could not lock channel: {}", err.getMessage()));

                    // 4) Edit رسالة التحكم: الحالة "تم الحل" + تعطيل الأزرار
                    TicketPanel.refresh(guild, ticket);

                    hook.editOriginal("✅ تم حل الشكوى وحفظها (`v%03d`).".formatted(result.version())
                            + (delay >= 0 ? " سيتم حذف القناة خلال " + delay + " ثانية." : "")).queue();

                    // 5) حذف القناة حسب الإعداد (close.delete.after.seconds = -1 لإبقائها)
                    if (delay >= 0) {
                        channel.delete().reason("KushTicket solved by " + member.getUser().getName())
                                .queueAfter(delay, TimeUnit.SECONDS, null,
                                        err -> LOG.warn("Could not delete channel: {}", err.getMessage()));
                    }
                });
    }

    private static String value(ModalMapping mapping) {
        return mapping == null ? "" : mapping.getAsString().trim();
    }
}
