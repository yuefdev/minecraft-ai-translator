package me.langyue.autotranslation.mc.generic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Decides where hard-coded text may be translated: only while a screen is open (never the in-game chat or the HUD,
 * which show other players' words), and not on screens that edit text or that have a dedicated integration.
 */
public final class GenericGate {
    private static final String[] BLOCKED = {
            "net.minecraft.client.gui.screens.ChatScreen",
            "net.minecraft.client.gui.screens.TitleScreen",
            "net.minecraft.client.gui.screens.inventory.BookEditScreen",
            "net.minecraft.client.gui.screens.inventory.SignEditScreen",
            "net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen",
            "net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen",
            "net.minecraft.client.gui.screens.inventory.MinecartCommandBlockEditScreen",
            "net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen",
            "net.minecraft.client.gui.screens.inventory.AnvilScreen",
            "net.minecraft.client.gui.screens.worldselection.",
            "net.minecraft.client.gui.screens.multiplayer.",
            "net.minecraft.client.gui.screens.social.",
            "com.mojang.realmsclient.",
            "dev.ftb.mods.ftblibrary.",
            "dev.ftb.mods.ftbquests.",
            "vazkii.patchouli.client.book.gui.",
            "me.shedaniel.clothconfig2.",
            "me.langyue.autotranslation."
    };

    private static Screen lastScreen;
    private static boolean lastAllowed;

    private GenericGate() {
    }

    public static boolean screenAllowed() {
        Screen screen = Minecraft.getInstance().screen;
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
