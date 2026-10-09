package me.langyue.autotranslation.legacy;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds calls to {@link Hooks} to the text methods of optional mods. Every patch is "fail safe": if the target
 * method is not found (a different mod version) the class is left exactly as it was.
 *
 * <ul>
 *   <li>BetterQuesting {@code QuestTranslation.translate(String, Object...)}: its argument is replaced by the
 *       translated text before it runs (language keys are returned unchanged by the hook).</li>
 *   <li>FTB Quests {@code FTBQuestsClient.addI18nAndColors(String)}: the single place every quest title, subtitle
 *       and description line goes through before it is shown.</li>
 *   <li>Patchouli {@code BookTextRenderer(GuiBook, String, ...)}: page text is translated before it is laid out;
 *       {@code BookEntry/BookCategory.getName} results are translated on return and page titles right before
 *       {@code GuiBook.drawCenteredStringNoShadow} draws them.</li>
 * </ul>
 */
public class Transformer implements IClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("AutoTranslation/ASM");
    private static final String HOOKS = "me/langyue/autotranslation/legacy/Hooks";
    private static final String STRING = "(Ljava/lang/String;)Ljava/lang/String;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        try {
            if ("betterquesting.api2.utils.QuestTranslation".equals(transformedName)) {
                return patchHead(bytes, transformedName, "translate", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", 0, "betterQuesting");
            }
            if ("com.feed_the_beast.ftbquests.client.FTBQuestsClient".equals(transformedName)) {
                return patchHead(bytes, transformedName, "addI18nAndColors", STRING, 0, "ftbQuests");
            }
            if ("vazkii.patchouli.client.book.gui.BookTextRenderer".equals(transformedName)) {
                // the seven argument constructor: (GuiBook gui, String text, int x, int y, int width, int lineHeight, int baseColor)
                return patchHead(bytes, transformedName, "<init>", "(Lvazkii/patchouli/client/book/gui/GuiBook;Ljava/lang/String;IIIII)V", 2, "patchouliText");
            }
            if ("vazkii.patchouli.client.book.BookEntry".equals(transformedName)
                    || "vazkii.patchouli.client.book.BookCategory".equals(transformedName)) {
                return patchReturn(bytes, transformedName, "getName", "()Ljava/lang/String;", "patchouliName");
            }
            if ("vazkii.patchouli.client.book.gui.GuiBook".equals(transformedName)) {
                // page titles are drawn through this method: drawCenteredStringNoShadow(String s, int x, int y, int color)
                return patchHead(bytes, transformedName, "drawCenteredStringNoShadow", "(Ljava/lang/String;III)V", 1, "patchouliLabel");
            }
        } catch (Throwable t) {
            LOGGER.error("Could not patch {}; it is left unchanged", transformedName, t);
        }
        return bytes;
    }

    /** At the start of the method: {@code arg = Hooks.hook(arg)} for the String argument in the given local slot. */
    private static byte[] patchHead(byte[] bytes, String cls, String method, String desc, int slot, String hook) {
        ClassNode node = read(bytes);
        for (MethodNode m : node.methods) {
            if (m.name.equals(method) && m.desc.equals(desc)) {
                InsnList code = new InsnList();
                code.add(new VarInsnNode(Opcodes.ALOAD, slot));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, STRING, false));
                code.add(new VarInsnNode(Opcodes.ASTORE, slot));
                m.instructions.insert(code);
                LOGGER.info("Patched {}.{}{}", cls, method, desc);
                return write(node);
            }
        }
        LOGGER.warn("{}.{}{} not found; the integration is inactive for this version", cls, method, desc);
        return bytes;
    }

    /** Before every return: {@code result = Hooks.hook(result)} (the String on the stack). */
    private static byte[] patchReturn(byte[] bytes, String cls, String method, String desc, String hook) {
        ClassNode node = read(bytes);
        for (MethodNode m : node.methods) {
            if (m.name.equals(method) && m.desc.equals(desc)) {
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.ARETURN) {
                        m.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, STRING, false));
                    }
                }
                LOGGER.info("Patched {}.{}{}", cls, method, desc);
                return write(node);
            }
        }
        LOGGER.warn("{}.{}{} not found; the integration is inactive for this version", cls, method, desc);
        return bytes;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] write(ClassNode node) {
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
