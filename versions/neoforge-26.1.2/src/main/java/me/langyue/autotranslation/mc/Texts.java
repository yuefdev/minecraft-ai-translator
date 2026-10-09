package me.langyue.autotranslation.mc;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/** The few text component operations whose API differs between Minecraft versions. */
public final class Texts {
    private Texts() {
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    /** A plain text or a translatable component (no children, no nested arguments to preserve). */
    public static boolean isSimple(Component component) {
        return component.getContents() instanceof PlainTextContents || component.getContents() instanceof TranslatableContents;
    }
}
