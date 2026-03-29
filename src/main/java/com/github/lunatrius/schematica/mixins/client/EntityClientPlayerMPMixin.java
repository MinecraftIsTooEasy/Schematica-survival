// MITE port (c) 2025 hahahha. Licensed under the MIT License.
package com.github.lunatrius.schematica.mixins.client;

import com.github.lunatrius.schematica.client.gui.GuiSchematicaControl;
import java.util.Locale;
import net.minecraft.EntityClientPlayerMP;
import net.minecraft.Minecraft;
import net.xiaoyu233.fml.reload.event.HandleChatCommandEvent;
import net.xiaoyu233.fml.reload.event.MITEEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityClientPlayerMP.class)
public abstract class EntityClientPlayerMPMixin {
    @Inject(method = "sendChatMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void schematica$handleClientCommand(String command, boolean permissionOverride, CallbackInfo ci) {
        if (schematica$handleLocalSchematicaCommand(command)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean schematica$handleLocalSchematicaCommand(String command) {
        if (command == null) {
            return false;
        }

        String trimmed = command.trim();
        if (!trimmed.startsWith("/")) {
            return false;
        }
        trimmed = trimmed.substring(1).trim();

        if (trimmed.isEmpty()) {
            return false;
        }
        trimmed = schematica$normalizeCommandAlias(trimmed);

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!(lower.equals("schematica list")
                || lower.equals("schematica load")
                || lower.startsWith("schematica load ")
                || lower.equals("schematica save")
                || lower.startsWith("schematica save ")
                || lower.equals("schematica create")
                || lower.startsWith("schematica create ")
                || lower.equals("schematica sel status")
                || lower.equals("schematica sel clear")
                || lower.equals("schematica unload")
                || lower.equals("schematica move")
                || lower.startsWith("schematica move ")
                || lower.equals("schematica nudge")
                || lower.startsWith("schematica nudge ")
                || lower.equals("schematica rotate")
                || lower.startsWith("schematica rotate ")
                || lower.equals("schematica mirror")
                || lower.startsWith("schematica mirror ")
                || lower.equals("schematica origin here")
                || lower.equals("schematica status")
                || lower.equals("schematica help")
                || lower.equals("schematica paste")
                || lower.startsWith("schematica paste ")
                || lower.equals("schematica menu")
                || lower.equals("schematica undo")
                || lower.equals("schematica printer")
                || lower.startsWith("schematica printer "))) {
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }

        if ("schematica menu".equals(lower)) {
            mc.displayGuiScreen(new GuiSchematicaControl());
            return true;
        }

        HandleChatCommandEvent event = new HandleChatCommandEvent(mc.thePlayer, trimmed, mc.thePlayer, mc.theWorld);
        MITEEvents.MITE_EVENT_BUS.post(event);
        return event.isExecuteSuccess();
    }

    @Unique
    private static String schematica$normalizeCommandAlias(String command) {
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) {
            return trimmed;
        }
        if (!"schematica".equalsIgnoreCase(parts[0])) {
            return trimmed;
        }
        if (parts.length == 1) {
            return "schematica help";
        }

        String sub = parts[1].toLowerCase(Locale.ROOT);
        if ("help".equals(sub)) {
            return "schematica help";
        }
        if ("list".equals(sub)) {
            return "schematica list";
        }
        if ("load".equals(sub)) {
            return schematica$composeAliasedCommand("schematica load", parts, 2);
        }
        if ("unload".equals(sub)) {
            return "schematica unload";
        }
        if ("status".equals(sub)) {
            return "schematica status";
        }
        if ("origin".equals(sub) && parts.length >= 3 && "here".equalsIgnoreCase(parts[2])) {
            return "schematica origin here";
        }
        if ("move".equals(sub)) {
            return schematica$composeAliasedCommand("schematica move", parts, 2);
        }
        if ("nudge".equals(sub)) {
            return schematica$composeAliasedCommand("schematica nudge", parts, 2);
        }
        if ("rotate".equals(sub)) {
            return schematica$composeAliasedCommand("schematica rotate", parts, 2);
        }
        if ("mirror".equals(sub)) {
            return schematica$composeAliasedCommand("schematica mirror", parts, 2);
        }
        if ("paste".equals(sub)) {
            return schematica$composeAliasedCommand("schematica paste", parts, 2);
        }
        if ("undo".equals(sub)) {
            return "schematica undo";
        }
        if ("printer".equals(sub)) {
            return schematica$composeAliasedCommand("schematica printer", parts, 2);
        }
        if ("save".equals(sub)) {
            return schematica$composeAliasedCommand("schematica save", parts, 2);
        }
        if ("create".equals(sub)) {
            return schematica$composeAliasedCommand("schematica create", parts, 2);
        }
        if ("sel".equals(sub) && parts.length >= 3) {
            if ("status".equalsIgnoreCase(parts[2])) {
                return "schematica sel status";
            }
            if ("clear".equalsIgnoreCase(parts[2])) {
                return "schematica sel clear";
            }
        }
        if ("menu".equals(sub)) {
            return "schematica menu";
        }
        return trimmed;
    }

    @Unique
    private static String schematica$composeAliasedCommand(String prefix, String[] parts, int argStartIndex) {
        if (parts.length <= argStartIndex) {
            return prefix;
        }
        StringBuilder builder = new StringBuilder(prefix);
        for (int i = argStartIndex; i < parts.length; ++i) {
            builder.append(' ').append(parts[i]);
        }
        return builder.toString();
    }
}
