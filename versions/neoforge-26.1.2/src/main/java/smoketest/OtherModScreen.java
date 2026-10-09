package smoketest;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Stands in for the screen of "some other mod" in the start-up check: it is on no list of ours. */
public class OtherModScreen extends Screen {
    public OtherModScreen() {
        super(Component.literal("other mod"));
    }
}
