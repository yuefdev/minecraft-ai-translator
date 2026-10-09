package me.langyue.autotranslation.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Decides where hard-coded text may be translated: only while a screen is open (never the in-game chat or the HUD,
 * which show other players' words), and not on screens that edit text or that have a dedicated integration.
 */
final class GenericGate {
    private static final String[] BLOCKED = {
            "net.minecraft.client.gui.GuiChat",
            "net.minecraft.client.gui.GuiScreenBook",
            "net.minecraft.client.gui.inventory.GuiEditSign",
            "net.minecraft.client.gui.inventory.GuiCommandBlock",
            "net.minecraft.client.gui.inventory.GuiEditStructure",
            "net.minecraft.client.gui.GuiRepair",
            "net.minecraft.client.gui.inventory.GuiRepair",
            "net.minecraft.client.gui.GuiCreateWorld",
            "net.minecraft.client.gui.GuiWorldSelection",
            "net.minecraft.client.gui.GuiMultiplayer",
            "net.minecraft.client.gui.GuiScreenAddServer",
            "net.minecraft.client.gui.GuiScreenServerList",
            "net.minecraft.client.gui.GuiWorldEdit",
            "com.feed_the_beast.ftblib.lib.gui.",
            "com.feed_the_beast.ftbquests.gui.",
            "betterquesting.",
            "vazkii.patchouli.client.book.gui.",
            "me.langyue.autotranslation."
    };

    private static GuiScreen lastScreen;
    private static boolean lastAllowed;

    private GenericGate() {
    }

    static boolean screenAllowed() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen == null) return false;
        if (screen == lastScreen) return lastAllowed;
        String name = screen.getClass().getName();
        boolean allowed = true;
        for (String blocked : BLOCKED) {
            if (name.startsWith(blocked)) {
                allowed = false;
                break;
            }
        }
        lastScreen = screen;
        lastAllowed = allowed;
        return allowed;
    }
}
