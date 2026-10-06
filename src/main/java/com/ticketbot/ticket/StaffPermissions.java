package com.ticketbot.ticket;

import com.ticketbot.config.BotConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * التحقق من أن المستخدم إداري.
 *
 * يتم استدعاؤه داخل كل Button Interaction (Claim / Unclaim / Save / Close)
 * لأن أي شخص يرى الرسالة يستطيع الضغط على الزر، لذلك التحقق يجب أن يكون في السيرفر.
 */
public final class StaffPermissions {

    private StaffPermissions() {}

    /** إداري = لديه صلاحية Administrator أو لديه أحد رولات الإدارة المحددة في config.properties. */
    public static boolean isStaff(Member member, BotConfig config) {
        if (member == null) {
            return false;
        }
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }
        for (Role role : member.getRoles()) {
            if (config.staffRoleIds().contains(role.getIdLong())) {
                return true;
            }
        }
        return false;
    }

    /** مدير السيرفر (Administrator) يستطيع عمل Unclaim إجباري لشكوى استلمها إداري آخر. */
    public static boolean canForceUnclaim(Member member) {
        return member != null && member.hasPermission(Permission.ADMINISTRATOR);
    }

    public static final String NO_PERMISSION = "⛔ هذا الزر مخصص للإداريين فقط.";
}
