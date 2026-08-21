package net.ledok.economy_ld.client.screen;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/**
 * Locks one of the mod's owo-ui screens to a fixed, <em>integer</em> GUI scale while it is open.
 *
 * <p>The screens are authored at GUI Scale 2. Rather than matrix-scaling the rendered UI by a
 * fractional factor (which makes Minecraft's bitmap font blurry and shaves edge pixels), this
 * forces the window's GUI scale to an integer for the lifetime of the screen so the font renders
 * natively and crisply. The largest integer scale whose layout fits the window is used (capped at
 * {@link #MAX_SCALE}), so the screen fills as much of the window as it can while staying sharp —
 * Scale 2 on a 1080p window, more on higher-resolution displays, stepping down to Scale 1 only on
 * small windows.</p>
 *
 * <p>Because the global GUI scale is changed, mouse input and the GUI projection stay consistent
 * automatically — no coordinate transforms are needed. The original scale is restored on close.</p>
 *
 * <p>Usage from a screen:</p>
 * <ul>
 *   <li>in {@code init()}, call {@link #apply(Minecraft)} <em>before</em> {@code super.init()},
 *       then refresh {@code this.width}/{@code this.height} from the window;</li>
 *   <li>in {@code removed()}, call {@link #restore(Minecraft)}.</li>
 * </ul>
 */
public final class ForcedGuiScale {

    /** Breathing room (design units) required around the shell for a scale to be considered a fit. */
    private static final int MARGIN_X = 12;
    private static final int MARGIN_Y = 8;
    /** Upper bound on the forced scale, to keep the UI sane on very high-resolution displays. */
    private static final int MAX_SCALE = 4;

    private final int designW;
    private final int designH;
    private Double savedScale;

    public ForcedGuiScale(int designW, int designH) {
        this.designW = designW;
        this.designH = designH;
    }

    /** Force the chosen integer GUI scale. Safe to call repeatedly (e.g. on resize). */
    public void apply(Minecraft mc) {
        Window window = mc.getWindow();
        if (this.savedScale == null) {
            this.savedScale = window.getGuiScale();
        }
        double target = pickScale(window);
        if (window.getGuiScale() != target) {
            window.setGuiScale(target);
        }
    }

    /** Restore the GUI scale that was active before this screen forced its own. */
    public void restore(Minecraft mc) {
        if (this.savedScale != null) {
            mc.getWindow().setGuiScale(this.savedScale);
            this.savedScale = null;
        }
    }

    /** The largest integer GUI scale whose design fits the physical window, in [1, {@link #MAX_SCALE}]. */
    private double pickScale(Window window) {
        int byWidth = window.getWidth() / (designW + MARGIN_X);
        int byHeight = window.getHeight() / (designH + MARGIN_Y);
        int scale = Math.min(byWidth, byHeight);
        return Math.max(1, Math.min(scale, MAX_SCALE));
    }
}
