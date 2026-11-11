package com.guiapi.client.gui;

import com.guiapi.client.model.WidgetModel;
import com.guiapi.client.model.WindowModel;
import com.guiapi.client.net.ClientNet;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The single screen that renders every server-driven window and dispatches input to widgets.
 * Windows later in {@link GuiManager#windows} render (and receive input) on top.
 */
public final class GuiWindowScreen extends Screen {
    /** Default title bar height, used when the window does not override it. */
    public static final int TITLE_H = 16;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("GuiApi");
    /** Image ids already reported as missing, so the log is not spammed every frame. */
    private static final java.util.Set<String> MISSING_IMAGES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final int COL_PANEL = 0xF018181C;
    private static final int COL_PANEL_BORDER = 0xFF000000;
    private static final int COL_TITLEBAR = 0xFF2D2F38;
    private static final int COL_TEXT = 0xFFFFFFFF;
    private static final int COL_TEXT_DIM = 0xFFAAAAAA;
    private static final int COL_ACCENT = 0xFF4C8DFF;
    private static final int COL_WIDGET = 0xFF3C3F47;
    private static final int COL_WIDGET_HOVER = 0xFF565B66;
    private static final int COL_WIDGET_BORDER = 0xFF101014;

    // ---- Minecraft-style palette ----
    private static final int MC_WINDOW_BG = 0xC0101010;
    private static final int MC_BORDER_OUTER = 0xFF000000;
    private static final int MC_BORDER_INNER = 0xFFFFFFFF;
    private static final int MC_TITLEBAR = 0xFF2D2F38;
    private static final int MC_BUTTON_BG = 0xFF6A6A6A;
    private static final int MC_BUTTON_HOVER = 0xFF8A8A8A;
    private static final int MC_BUTTON_BORDER = 0xFF000000;
    private static final int MC_BUTTON_HIGHLIGHT = 0x40FFFFFF;
    private static final int MC_BUTTON_SHADOW = 0x40000000;
    private static final int MC_PANEL_BG = 0xFFC6C6C6;
    private static final int MC_SLOT_BG = 0xFF8B8B8B;
    private static final int MC_SLOT_BORDER = 0xFF373737;
    private static final int MC_SLOT_LIGHT = 0xFFFFFFFF;
    private static final int MC_SLOT_DARK = 0xFF555555;

    /** Per-window runtime state (position, drags, expanded dropdowns). */
    public static final class WindowState {
        public WindowModel model;
        /** false until the window is placed for the first time (then the model position or centering is used). */
        public boolean placed;
        public int posX;
        public int posY;
        boolean dragging;
        int dragOffX;
        int dragOffY;
        WidgetModel activeSlider;
        /** 正在拖动的滚动条（null 表示没有）。 */
        WidgetModel activeScrollbar;
        /** 上一次实时上报滚动值的时间，用于节流。 */
        long lastScrollSendAt;
        WidgetModel focusedText;
        int textCursor;
        String expandedDropdown;
        int dropdownScroll;
        WidgetModel hovered;
        int hoveredX;
        int hoveredY;
        /** Horizontal scroll offset per drag group, kept across window re-opens. */
        final java.util.Map<String, Integer> groupOffsets = new java.util.HashMap<>();
        /** Group currently being dragged by the mouse (null when not dragging). */
        String dragGroup;
        /** Widget whose click is deferred until the mouse is released without dragging. */
        String pendingClick;
        int dragStartX;
        int dragStartOffset;
        boolean dragMoved;
        /** Smoothed render position per widget id, so layout changes glide instead of snapping. */
        final java.util.Map<String, PosAnim> animPos = new java.util.HashMap<>();
        /** 上一帧渲染时窗口的位置：窗口整体移动时把控件动画位置同步平移，避免控件拖尾。 */
        int renderWinX = Integer.MIN_VALUE;
        int renderWinY = Integer.MIN_VALUE;

        public WindowState(WindowModel model) {
            this.model = model;
        }
    }

    public GuiWindowScreen() {
        super(Component.literal("GuiApi"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- layout

    /**
     * Smallest logical screen the server UIs are designed for. Screens smaller than this
     * (small windows, high GUI scale, odd aspect ratios) are scaled down uniformly instead
     * of letting fixed pixel offsets overlap.
     */
    private static final int DESIGN_W = 640;
    private static final int DESIGN_H = 430;
    /** Below this the text would become unreadable, so the scale is clipped. */
    private static final float MIN_UI_SCALE = 0.3f;

    /** Uniform scale applied to every window, and the virtual canvas size it produces. */
    private float uiScale = 1f;
    private int virtW;
    private int virtH;

    /** Style carrying the font id of the window being rendered (empty = vanilla default font). */
    private Style textStyle = Style.EMPTY;
    /** 当前文字绘制坐标到设备像素的倍率（一般就是 uiScale），用于把文字原点吸附到整数像素。 */
    private float textSnapScale = 1f;

    /** Recomputes the virtual canvas for the current screen size. Cheap, called every frame. */
    private void updateViewport() {
        if (virtW > 0 && virtH > 0 && Math.abs(this.width / uiScale - virtW) < 0.51
                && Math.abs(this.height / uiScale - virtH) < 0.51) {
            return;
        }
        float s = Math.min(1f, Math.min((float) this.width / DESIGN_W, (float) this.height / DESIGN_H));
        if (s < MIN_UI_SCALE) s = MIN_UI_SCALE;
        uiScale = s;
        virtW = Math.max(1, Math.round(this.width / s));
        virtH = Math.max(1, Math.round(this.height / s));
    }

    /** Screen coordinates -> virtual layout coordinates. */
    private int toVirtual(double screen) {
        return (int) Math.round(screen / uiScale);
    }

    /** Virtual layout coordinates -> real screen pixels (used by the clipping rectangle). */
    private int toScreen(double virtual) {
        return (int) Math.round(virtual * uiScale);
    }

    private int winW(WindowState ws) {
        return ws.model.fullscreen ? virtW : ws.model.width;
    }

    private int winH(WindowState ws) {
        return ws.model.fullscreen ? virtH : ws.model.height;
    }

    /** Places the window once (server position, or centered) before its first use. */
    private void place(WindowState ws) {
        if (ws.placed) return;
        ws.posX = ws.model.x >= 0 ? ws.model.x : (virtW - winW(ws)) / 2;
        ws.posY = ws.model.y >= 0 ? ws.model.y : (virtH - winH(ws)) / 2;
        ws.placed = true;
    }

    private int winX(WindowState ws) {
        place(ws);
        return ws.posX;
    }

    private int winY(WindowState ws) {
        place(ws);
        return ws.posY;
    }

    /** Height of the built-in title bar (0 when hidden). */
    private int titleH(WindowState ws) {
        return ws.model.titleBar ? Math.max(0, ws.model.titleHeight) : 0;
    }

    /** Height of the window content area (window minus title bar). */
    private int contentH(WindowState ws) {
        return Math.max(0, winH(ws) - titleH(ws));
    }

    private int contentY(WindowState ws) {
        return winY(ws) + titleH(ws);
    }

    /**
     * Widget x relative to content origin.
     * Percentage (xPct) wins, then negative x means "center plus offset".
     */
    private int widgetX(WindowState ws, WidgetModel w) {
        int ox = winX(ws);
        int base;
        if (w.has("xPct")) {
            base = ox + (int) Math.round(winW(ws) * w.dbl("xPct") / 100.0) + w.x();
        } else {
            base = w.x() < 0 ? ox + winW(ws) / 2 + w.x() : ox + w.x();
        }
        return base + groupOffset(ws, w);
    }

    /** Current horizontal scroll of the widget's drag group (0 when it has none). */
    private static int groupOffset(WindowState ws, WidgetModel w) {
        String group = w.str("dragGroup");
        if (group.isEmpty()) return 0;
        return ws.groupOffsets.getOrDefault(group, 0);
    }

    /** Leftmost base position of every widget in the group, ignoring the current scroll. */
    private int groupLeft(WindowState ws, String group) {
        int offset = ws.groupOffsets.getOrDefault(group, 0);
        int min = Integer.MAX_VALUE;
        for (WidgetModel o : ws.model.widgets) {
            if (!group.equals(o.str("dragGroup"))) continue;
            min = Math.min(min, widgetX(ws, o) - offset);
        }
        return min == Integer.MAX_VALUE ? winX(ws) : min;
    }

    /** Rightmost base position of every widget in the group, ignoring the current scroll. */
    private int groupRight(WindowState ws, String group) {
        int offset = ws.groupOffsets.getOrDefault(group, 0);
        int max = Integer.MIN_VALUE;
        for (WidgetModel o : ws.model.widgets) {
            if (!group.equals(o.str("dragGroup"))) continue;
            max = Math.max(max, widgetX(ws, o) - offset + widgetW(ws, o));
        }
        return max == Integer.MIN_VALUE ? winX(ws) + winW(ws) : max;
    }

    private int widgetY(WindowState ws, WidgetModel w) {
        int oy = contentY(ws);
        if (w.has("yPct")) {
            return oy + (int) Math.round(contentH(ws) * w.dbl("yPct") / 100.0) + w.y();
        }
        return w.y() < 0 ? oy + contentH(ws) / 2 + w.y() : oy + w.y();
    }

    /** Widget width; negative w means "right edge minus margin". */
    private int widgetW(WindowState ws, WidgetModel w) {
        if (w.has("wPct")) {
            return (int) Math.round(winW(ws) * w.dbl("wPct") / 100.0) + w.w();
        }
        int ww = w.w();
        if (ww < 0) return Math.max(0, winW(ws) - (widgetX(ws, w) - winX(ws)) + ww);
        return ww;
    }

    private int widgetH(WindowState ws, WidgetModel w) {
        // fixed aspect ratio wins: height is derived from the resolved width
        if (w.has("aspect")) {
            double aspect = w.dbl("aspect");
            if (aspect > 0) return Math.max(1, (int) Math.round(widgetW(ws, w) / aspect));
        }
        if (w.has("hPct")) {
            return (int) Math.round(contentH(ws) * w.dbl("hPct") / 100.0) + w.h();
        }
        int hh = w.h();
        if (hh < 0) return Math.max(0, contentH(ws) - (widgetY(ws, w) - contentY(ws)) + hh);
        return hh;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateViewport();
        // everything below works in virtual layout coordinates
        int mx = toVirtual(mouseX);
        int my = toVirtual(mouseY);

        for (WindowState ws : GuiManager.windows) {
            ws.hovered = null;
        }
        // optional screen dim behind every window (0 = off), drawn in real pixels
        int dim = 0;
        for (WindowState ws : GuiManager.windows) dim = Math.max(dim, ws.model.dim);
        if (dim > 0) g.fill(0, 0, this.width, this.height, dim << 24);

        // only the topmost window under the cursor reacts to the mouse; windows underneath
        // must not highlight their buttons even though the click is already blocked there
        int hoverIndex = -1;
        for (int i = GuiManager.windows.size() - 1; i >= 0; i--) {
            WindowState ws = GuiManager.windows.get(i);
            if (inside(mx, my, winX(ws), winY(ws), winW(ws), winH(ws))) {
                hoverIndex = i;
                break;
            }
        }
        for (int i = 0; i < GuiManager.windows.size(); i++) {
            WindowState ws = GuiManager.windows.get(i);
            if (i == hoverIndex) renderWindow(g, ws, mx, my);
            else renderWindow(g, ws, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }

        // overlays share the same scale as the windows they belong to
        g.pose().pushMatrix();
        g.pose().scale(uiScale, uiScale);
        // expanded dropdowns overlay everything inside their window
        for (WindowState ws : GuiManager.windows) {
            if (ws.expandedDropdown != null) {
                WidgetModel w = ws.model.widget(ws.expandedDropdown);
                if (w != null) {
                    this.textStyle = fontStyle(ws.model.font);
                    renderDropdownOptions(g, ws, w, mx, my);
                }
            }
        }
        // tooltips last
        for (WindowState ws : GuiManager.windows) {
            if (ws.hovered == null) continue;
            // ignore hover on widgets that are clipped away (mouse outside the window)
            if (!inside(mx, my, winX(ws), winY(ws), winW(ws), winH(ws))) continue;
            String tip = ws.hovered.tooltip();
            if ("item".equals(ws.hovered.type())) {
                ItemStack stack = parseItemStack(ws.hovered);
                if (!stack.isEmpty()) tip = itemTooltip(ws.hovered, stack);
            }
            if (!tip.isEmpty()) {
                this.textStyle = fontStyle(ws.model.font);
                renderTooltip(g, tip, mx, my);
            }
        }
        g.pose().popMatrix();
    }

    private void renderWindow(GuiGraphics g, WindowState ws, int mouseX, int mouseY) {
        this.textStyle = fontStyle(ws.model.font);
        this.textSnapScale = uiScale;
        int px = winX(ws), py = winY(ws), w = winW(ws), h = winH(ws);
        int th = titleH(ws);
        boolean mc = "minecraft".equals(ws.model.style);

        // 窗口位置变了（拖动、重新布局等）时，把控件的平滑位置整体平移相同的距离。
        // 否则控件会以每帧插值的方式"追"窗口，快速拖动时明显拖尾、摇晃。
        if (ws.renderWinX != Integer.MIN_VALUE && (ws.renderWinX != px || ws.renderWinY != py)) {
            int ddx = px - ws.renderWinX, ddy = py - ws.renderWinY;
            for (PosAnim a : ws.animPos.values()) {
                a.x += ddx;
                a.y += ddy;
                a.fromX += ddx;
                a.fromY += ddy;
            }
        }
        ws.renderWinX = px;
        ws.renderWinY = py;

        // window chrome is drawn in virtual layout coordinates
        g.pose().pushMatrix();
        g.pose().scale(uiScale, uiScale);

        if (mc) {
            // Minecraft-style window: outer black border, inner white border, dark background
            g.fill(px - 2, py - 2, px + w + 2, py + h + 2, MC_BORDER_OUTER);
            g.fill(px - 1, py - 1, px + w + 1, py + h + 1, MC_BORDER_INNER);
            g.fill(px, py, px + w, py + h, MC_WINDOW_BG);
        } else {
            int bg = ws.model.bgColor;
            if ((bg >>> 24) != 0) g.fill(px, py, px + w, py + h, bg);
            if (ws.model.borderWidth > 0) {
                outline(g, px, py, w, h, ws.model.borderColor, ws.model.borderWidth);
            }
        }
        if (th > 0) {
            int tbColor = mc ? MC_TITLEBAR : ws.model.titleBarColor;
            g.fill(px, py, px + w, py + th, tbColor);
            int textY = py + Math.max(0, (th - 8) / 2);
            drawText(g, ws.model.title, px + 6, textY, ws.model.titleColor, false);
            // close button
            int cx = px + w - 14, cy = py + Math.max(0, (th - 12) / 2);
            boolean closeHover = mouseX >= cx && mouseX < cx + 12 && mouseY >= cy && mouseY < cy + 12;
            g.fill(cx, cy, cx + 12, cy + 12, closeHover ? 0xFFE06060 : 0xFF50525C);
            drawText(g, "x", cx + 4, cy + 2, COL_TEXT, false);
        }

        g.pose().popMatrix();

        // Clip widgets to the window area so anything sticking out is not drawn.
        // The scissor is set while the pose is unscaled, so it takes real pixels.
        g.enableScissor(toScreen(px), toScreen(py), toScreen(px + w), toScreen(py + h));
        g.pose().pushMatrix();
        g.pose().scale(uiScale, uiScale);
        for (WidgetModel widget : ws.model.widgets) {
            renderWidget(g, ws, widget, mouseX, mouseY);
        }
        g.pose().popMatrix();
        g.disableScissor();
    }

    private void renderWidget(GuiGraphics g, WindowState ws, WidgetModel w, int mouseX, int mouseY) {
        // hidden widgets are neither drawn nor hovered
        if (w.has("visible") && !w.bool("visible")) return;
        int targetX = widgetX(ws, w), targetY = widgetY(ws, w);
        int ww = widgetW(ws, w), hh = widgetH(ws, w);
        // 控件在布局变化时平滑滑到新位置（手牌重排、出牌等），避免生硬跳变
        int[] pos = animPos(ws, w, targetX, targetY);
        int x = pos[0], y = pos[1];
        // 悬停时轻轻抬起
        if (w.bool("hoverLift") && inside(mouseX, mouseY, targetX, targetY, ww, hh)) y -= 3;
        switch (w.type()) {
            case "panel" -> {
                boolean mc = "minecraft".equals(ws.model.style);
                boolean hovered = w.has("hoverColor") && inside(mouseX, mouseY, x, y, ww, hh);
                int bg = hovered ? w.num("hoverColor") : w.num("bgColor");
                // an unset/transparent colour falls back to the vanilla panel grey
                if ((bg >>> 24) == 0) bg = mc ? MC_PANEL_BG : 0;
                int bw = w.num("borderWidth");
                int radius = w.num("radius");
                if (radius > 0) {
                    if (bw > 0) {
                        fillRound(g, x, y, ww, hh, radius, w.num("borderColor"));
                        fillRound(g, x + bw, y + bw, ww - bw * 2, hh - bw * 2,
                                Math.max(0, radius - bw), bg);
                    } else {
                        fillRound(g, x, y, ww, hh, radius, bg);
                    }
                } else {
                    if (w.has("bgGradTop")) {
                        // 纵向渐变：逐行填充，营造柔和的背景层次
                        int top = w.num("bgGradTop"), bottom = w.num("bgGradBottom");
                        int rows = Math.max(1, hh);
                        for (int row = 0; row < rows; row++) {
                            float t = rows == 1 ? 0f : (float) row / (rows - 1);
                            g.fill(x, y + row, x + ww, y + row + 1, lerpColor(top, bottom, t));
                        }
                    } else if ((bg >>> 24) != 0) {
                        g.fill(x, y, x + ww, y + hh, bg);
                    }
                    if (mc && ww > 6 && hh > 6) {
                        // vanilla bevel: light top/left, dark bottom/right
                        g.fill(x, y, x + ww - 1, y + 1, MC_SLOT_LIGHT);
                        g.fill(x, y, x + 1, y + hh - 1, MC_SLOT_LIGHT);
                        g.fill(x + ww - 1, y, x + ww, y + hh, MC_SLOT_DARK);
                        g.fill(x, y + hh - 1, x + ww, y + hh, MC_SLOT_DARK);
                    }
                    if (bw > 0) outline(g, x, y, ww, hh, w.num("borderColor"), bw);
                }
                if (w.has("glowColor")) glow(g, x, y, ww, hh, w.num("glowColor"));
                // 面板也能挂 tooltip（卡牌点击层等）
                if (w.has("tooltip") && inside(mouseX, mouseY, x, y, ww, hh)) hover(ws, w, x, y);
            }
            case "label" -> {
                float scale = w.dbl("scale") > 0 ? (float) w.dbl("scale") : 1f;
                int color = w.num("color") != 0 ? w.num("color") : COL_TEXT;
                boolean centered = w.bool("center");
                // a positive width enables automatic wrapping; text is measured unscaled
                int maxWidth = ww > 0 ? Math.max(1, (int) (ww / scale)) : 0;
                List<String> lines = layoutLines(w.str("text"), maxWidth);
                int lineHeight = Math.max(9, Math.round(10 * scale));
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.isEmpty()) continue;
                    int ty = y + i * lineHeight;
                    // "center" means: centered inside the widget's own box when it has a width,
                    // otherwise centered across the whole window (legacy behaviour)
                    int tx;
                    if (centered) {
                        int lineWidth = (int) (textWidth(line) * scale);
                        tx = ww > 0
                                ? x + (ww - lineWidth) / 2
                                : winX(ws) + (winW(ws) - lineWidth) / 2;
                    } else {
                        tx = x;
                    }
                    if (scale != 1f) {
                        // 放大的文字同样把原点吸附到设备像素，配合高 oversample 字体减轻发虚
                        float stx = tx, sty = ty;
                        if (Math.abs(uiScale - 1f) > 1.0e-4f) {
                            stx = (float) (Math.round(tx * uiScale) / uiScale);
                            sty = (float) (Math.round(ty * uiScale) / uiScale);
                        }
                        g.pose().pushMatrix();
                        g.pose().translate(stx, sty);
                        g.pose().scale(scale, scale);
                        drawText(g, line, 0, 0, color, false);
                        g.pose().popMatrix();
                    } else {
                        drawText(g, line, tx, ty, color, false);
                    }
                }
            }
            case "image" -> {
                ResourceLocation tex = GuiManager.imageTexture(w.str("image"));
                if (tex != null) {
                    int[] size = GuiManager.imageSize(w.str("image"));
                    int tw = size != null ? size[0] : ww, th = size != null ? size[1] : hh;
                    float scaleX = (float) ww / tw;
                    float scaleY = (float) hh / th;
                    String fit = w.str("fit");
                    if ("cover".equals(fit) || "contain".equals(fit)) {
                        // cover = fill and crop, contain = whole image with letterboxing
                        float scale = "cover".equals(fit) ? Math.max(scaleX, scaleY) : Math.min(scaleX, scaleY);
                        float dw = tw * scale, dh = th * scale;
                        g.pose().pushMatrix();
                        g.pose().translate(x + (ww - dw) / 2f, y + (hh - dh) / 2f);
                        g.pose().scale(scale, scale);
                        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, tw, th, tw, th);
                        g.pose().popMatrix();
                    } else {
                        // stretch to the widget box
                        g.pose().pushMatrix();
                        g.pose().translate(x, y);
                        g.pose().scale(scaleX, scaleY);
                        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0f, 0f, tw, th, tw, th);
                        g.pose().popMatrix();
                    }
                    // clickable images (cards) get a light highlight so they read as interactive
                    if (w.bool("clickable") && inside(mouseX, mouseY, x, y, ww, hh)) {
                        g.fill(x, y, x + ww, y + hh, 0x30FFFFFF);
                    }
                    if (w.has("glowColor")) glow(g, x, y, ww, hh, w.num("glowColor"));
                    // 图片也能挂 tooltip（手牌说明等）
                    if (w.has("tooltip") && inside(mouseX, mouseY, x, y, ww, hh)) hover(ws, w, x, y);
                } else {
                    String imageId = w.str("image");
                    if (!imageId.isEmpty() && MISSING_IMAGES.add(imageId)) {
                        // once per image id, so a broken asset is visible in the client log
                        LOG.warn("GuiApi image '{}' is not available on this client (never received or failed to decode)", imageId);
                    }
                    g.fill(x, y, x + ww, y + hh, 0xFF202028);
                    outline(g, x, y, ww, hh, COL_WIDGET_BORDER);
                    drawText(g, "...", x + 4, y + hh / 2 - 4, COL_TEXT_DIM, false);
                }
            }
            case "item" -> {
                boolean mc = "minecraft".equals(ws.model.style);
                if (mc) {
                    // Minecraft-style item slot
                    g.fill(x, y, x + ww, y + hh, MC_SLOT_BG);
                    outline(g, x, y, ww, hh, MC_SLOT_BORDER);
                    // inner bevel: top+left light, bottom+right dark
                    g.fill(x + 1, y + 1, x + ww - 1, y + 2, MC_SLOT_LIGHT);
                    g.fill(x + 1, y + 1, x + 2, y + hh - 1, MC_SLOT_LIGHT);
                    g.fill(x + ww - 2, y + 1, x + ww - 1, y + hh - 1, MC_SLOT_DARK);
                    g.fill(x + 1, y + hh - 2, x + ww - 1, y + hh - 1, MC_SLOT_DARK);
                }
                boolean hovered = inside(mouseX, mouseY, x, y, ww, hh);
                ItemStack stack = parseItemStack(w);
                if (!stack.isEmpty()) {
                    // scale the 16x16 icon up so it fills big slots instead of floating in the middle
                    int scale = Math.max(1, Math.min(4, Math.min(ww, hh) / 18));
                    int iconSize = 16 * scale;
                    int ix = x + (ww - iconSize) / 2;
                    int iy = y + (hh - iconSize) / 2;
                    g.pose().pushMatrix();
                    g.pose().translate(ix, iy);
                    g.pose().scale(scale, scale);
                    g.renderItem(stack, 0, 0);
                    g.pose().popMatrix();
                    int amount = stack.getCount();
                    if (amount > 1) {
                        String label = String.valueOf(amount);
                        drawText(g, label, x + ww - textWidth(label) - 2, y + hh - 10, COL_TEXT, true);
                    }
                    if (hovered) {
                        g.fill(x + 1, y + 1, x + ww - 1, y + hh - 1, 0x50FFFFFF);
                        String tip = itemTooltip(w, stack);
                        if (!tip.isEmpty()) hover(ws, w, x, y);
                    }
                } else {
                    if (!mc) {
                        g.fill(x, y, x + ww, y + hh, 0xFF202028);
                        outline(g, x, y, ww, hh, COL_WIDGET_BORDER);
                    }
                    drawText(g, "?", x + ww / 2 - 3, y + hh / 2 - 4, COL_TEXT_DIM, false);
                }
            }
            case "button" -> {
                boolean mc = "minecraft".equals(ws.model.style);
                boolean hover = inside(mouseX, mouseY, x, y, ww, hh);
                int textColor = w.has("textColor") ? w.num("textColor") : COL_TEXT;
                int bg = hover
                        ? (w.has("hoverColor") ? w.num("hoverColor") : (mc ? MC_BUTTON_HOVER : COL_WIDGET_HOVER))
                        : (w.has("bgColor") ? w.num("bgColor") : (mc ? MC_BUTTON_BG : COL_WIDGET));
                int radius = w.num("radius");
                if (radius > 0) {
                    int border = mc ? MC_BUTTON_BORDER : COL_WIDGET_BORDER;
                    fillRound(g, x, y, ww, hh, radius, border);
                    fillRound(g, x + 1, y + 1, ww - 2, hh - 2, Math.max(0, radius - 1), bg);
                } else if (mc) {
                    // vanilla blue-grey buttons, unless the server supplied its own colours
                    g.fill(x, y, x + ww, y + hh, bg);
                    outline(g, x, y, ww, hh, MC_BUTTON_BORDER);
                    if (w.has("bgColor") || w.has("hoverColor")) {
                        g.fill(x + 1, y + 1, x + ww - 1, y + 2, 0x30FFFFFF);
                        g.fill(x + 1, y + hh - 2, x + ww - 1, y + hh - 1, 0x30000000);
                    } else {
                        g.fill(x + 1, y + 1, x + ww - 1, y + 2, MC_BUTTON_HIGHLIGHT);
                        g.fill(x + 1, y + hh - 2, x + ww - 1, y + hh - 1, MC_BUTTON_SHADOW);
                    }
                } else {
                    g.fill(x, y, x + ww, y + hh, bg);
                    outline(g, x, y, ww, hh, COL_WIDGET_BORDER);
                }
                String text = w.str("text");
                drawText(g, text, x + (ww - textWidth(text)) / 2, y + (hh - 8) / 2, textColor, false);
                if (hover) hover(ws, w, x, y);
            }
            case "text_field" -> {
                boolean mc = "minecraft".equals(ws.model.style);
                boolean focused = ws.focusedText == w;
                int bg = w.has("bgColor") ? w.num("bgColor") : (mc ? 0xFFFFFFFF : 0xFF1E1E1E);
                int border = w.has("borderColor") ? w.num("borderColor") : (mc ? 0xFF000000 : 0xFF4A4A4A);
                int color = w.has("textColor") ? w.num("textColor") : (mc ? 0xFF000000 : COL_TEXT);
                g.fill(x, y, x + ww, y + hh, bg);
                outline(g, x, y, ww, hh, border);
                int ty = y + (hh - 8) / 2;
                int maxW = Math.max(8, ww - 10);
                String full = w.str("text");
                int cursor = focused ? Math.min(ws.textCursor, full.length()) : full.length();
                int start = 0;
                while (start < cursor && textWidth(full.substring(start, cursor)) > maxW - 2) start++;
                String visible = full.substring(start);
                while (!visible.isEmpty() && textWidth(visible) > maxW) {
                    visible = visible.substring(0, visible.length() - 1);
                }
                if (full.isEmpty()) {
                    String hint = w.str("placeholder");
                    while (!hint.isEmpty() && textWidth(hint) > maxW) hint = hint.substring(0, hint.length() - 1);
                    drawText(g, hint, x + 5, ty, mc ? 0xFFAAAAAA : 0xFF7A7A7A, false);
                    if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                        g.fill(x + 5, ty - 1, x + 6, ty + 9, color);
                    }
                } else {
                    drawText(g, visible, x + 5, ty, color, false);
                    if (focused) {
                        int caretX = x + 5 + textWidth(full.substring(start, cursor));
                        if ((System.currentTimeMillis() / 500) % 2 == 0) {
                            g.fill(caretX, ty - 1, caretX + 1, ty + 9, color);
                        }
                    }
                }
                if (inside(mouseX, mouseY, x, y, ww, hh)) hover(ws, w, x, y);
            }
            case "slider" -> {
                double min = w.dbl("min"), max = w.dbl("max"), value = w.dbl("value");
                double ratio = max > min ? (value - min) / (max - min) : 0;
                ratio = Math.max(0, Math.min(1, ratio));
                String label = w.str("label");
                String valText = formatSliderValue(w, value);
                String caption = label.isEmpty() ? valText : label + ": " + valText;
                if (!caption.isEmpty()) {
                    drawText(g, caption, x, y, COL_TEXT_DIM, false);
                }
                int trackY = y + 12;
                g.fill(x, trackY, x + ww, trackY + 4, 0xFF4A4A4A);
                outline(g, x, trackY, ww, 4, 0xFF808080);
                int fillW = (int) (ww * ratio);
                if (fillW > 0) g.fill(x, trackY, x + fillW, trackY + 4, COL_ACCENT);
                int thumbX = x + fillW - 3;
                g.fill(thumbX, trackY - 3, thumbX + 6, trackY + 7, COL_TEXT);
                if (inside(mouseX, mouseY, x, trackY - 4, ww, 12)) hover(ws, w, x, y);
            }
            case "scrollbar" -> {
                int trackColor = w.has("trackColor") ? w.num("trackColor") : 0xFF2A2D36;
                int thumbColor = w.has("thumbColor") ? w.num("thumbColor") : 0xFF7C8395;
                g.fill(x, y, x + ww, y + hh, trackColor);
                outline(g, x, y, ww, hh, COL_WIDGET_BORDER);
                double ratio = Math.max(0, Math.min(100, w.dbl("value"))) / 100.0;
                double thumbPct = Math.max(6, Math.min(100, w.dbl("thumb")));
                int thumbH = Math.max(12, (int) Math.round(hh * thumbPct / 100.0));
                int travel = Math.max(0, hh - thumbH);
                int thumbY = y + (int) Math.round(travel * ratio);
                boolean hot = ws.activeScrollbar == w || inside(mouseX, mouseY, x, y, ww, hh);
                g.fill(x + 1, thumbY + 1, x + ww - 1, thumbY + thumbH - 1, hot ? 0xFFB9C0D4 : thumbColor);
                if (hot) outline(g, x, y, ww, hh, COL_TEXT_DIM);
            }
            case "checkbox" -> {
                g.fill(x, y, x + 12, y + 12, COL_WIDGET);
                outline(g, x, y, 12, 12, COL_WIDGET_BORDER);
                if (w.bool("checked")) {
                    g.fill(x + 2, y + 2, x + 10, y + 10, COL_ACCENT);
                    g.fill(x + 4, y + 4, x + 8, y + 8, COL_TEXT);
                }
                drawText(g, w.str("label"), x + 16, y + 2, COL_TEXT, false);
                if (inside(mouseX, mouseY, x, y, 16 + textWidth(w.str("label")), 12)) hover(ws, w, x, y);
            }
            case "radio" -> {
                // outer ring
                g.fill(x + 2, y, x + 10, y + 12, COL_WIDGET_BORDER);
                g.fill(x, y + 2, x + 12, y + 10, COL_WIDGET_BORDER);
                g.fill(x + 2, y + 2, x + 10, y + 10, COL_WIDGET);
                if (w.bool("checked")) {
                    g.fill(x + 4, y + 4, x + 8, y + 8, COL_ACCENT);
                }
                drawText(g, w.str("label"), x + 16, y + 2, COL_TEXT, false);
                if (inside(mouseX, mouseY, x, y, 16 + textWidth(w.str("label")), 12)) hover(ws, w, x, y);
            }
            case "dropdown" -> {
                boolean mc = "minecraft".equals(ws.model.style);
                boolean expanded = w.id().equals(ws.expandedDropdown);
                boolean hover = inside(mouseX, mouseY, x, y, ww, hh);
                int bg = hover || expanded
                        ? (mc ? MC_BUTTON_HOVER : COL_WIDGET_HOVER)
                        : (mc ? MC_BUTTON_BG : COL_WIDGET);
                g.fill(x, y, x + ww, y + hh, bg);
                outline(g, x, y, ww, hh, mc ? MC_BUTTON_BORDER : COL_WIDGET_BORDER);
                if (mc) {
                    g.fill(x + 1, y + 1, x + ww - 1, y + 2, MC_BUTTON_HIGHLIGHT);
                    g.fill(x + 1, y + hh - 2, x + ww - 1, y + hh - 1, MC_BUTTON_SHADOW);
                }
                List<String> options = w.stringList("options");
                int selected = w.num("selected");
                String current = selected >= 0 && selected < options.size() ? options.get(selected) : "";
                int textY = y + (hh - 8) / 2;
                drawText(g, current, x + 4, textY, mc ? 0xFFFFFFFF : COL_TEXT, false);
                drawText(g, expanded ? "^" : "v", x + ww - 10, textY, mc ? 0xFFFFFFFF : COL_TEXT_DIM, false);
                if (hover) hover(ws, w, x, y);
            }
            default -> { }
        }
    }

    private void renderDropdownOptions(GuiGraphics g, WindowState ws, WidgetModel w, int mouseX, int mouseY) {
        int x = widgetX(ws, w), y = widgetY(ws, w) + widgetH(ws, w);
        int ww = widgetW(ws, w);
        boolean mc = "minecraft".equals(ws.model.style);
        List<String> options = w.stringList("options");
        int rowH = Math.max(10, w.has("rowHeight") ? w.num("rowHeight") : 16);
        int visible = Math.min(6, options.size());
        int scroll = Math.max(0, Math.min(ws.dropdownScroll, options.size() - visible));
        g.fill(x, y, x + ww, y + visible * rowH, mc ? MC_PANEL_BG : COL_PANEL);
        outline(g, x, y, ww, visible * rowH, mc ? MC_BUTTON_BORDER : COL_PANEL_BORDER);
        for (int i = 0; i < visible; i++) {
            int index = i + scroll;
            int rowY = y + i * rowH;
            boolean rowHover = inside(mouseX, mouseY, x, rowY, ww, rowH);
            if (rowHover) g.fill(x + 1, rowY, x + ww - 1, rowY + rowH, mc ? 0xFF8B8B8B : COL_WIDGET_HOVER);
            int color = index == w.num("selected")
                    ? (mc ? 0xFF0000A0 : COL_ACCENT)
                    : (mc ? 0xFF3F3F3F : COL_TEXT);
            drawText(g, options.get(index), x + 4, rowY + (rowH - 8) / 2, color, false);
        }
    }

    private void renderTooltip(GuiGraphics g, String text, int mouseX, int mouseY) {
        String[] raw = text.split("\\n");
        String[] lines = new String[raw.length];
        int[] colors = new int[raw.length];
        int tw = 0;
        for (int i = 0; i < raw.length; i++) {
            String line = raw[i];
            int color = COL_TEXT;
            // optional per-line colour prefix: "#RRGGBB|"
            if (line.length() > 8 && line.charAt(0) == '#' && line.charAt(7) == '|') {
                try {
                    color = (int) (Long.parseLong(line.substring(1, 7), 16) | 0xFF000000L);
                    line = line.substring(8);
                } catch (NumberFormatException ignored) {
                    // not a colour prefix, keep the line untouched
                }
            }
            lines[i] = line;
            colors[i] = color;
            tw = Math.max(tw, textWidth(line));
        }
        int th = lines.length * 10 + 4;
        int x = Math.min(mouseX + 8, virtW - tw - 10);
        int y = Math.min(mouseY + 8, virtH - th - 4);
        g.fill(x, y, x + tw + 8, y + th, 0xF0101010);
        outline(g, x, y, tw + 8, th, 0xFF808080);
        for (int i = 0; i < lines.length; i++) {
            drawText(g, lines[i], x + 4, y + 4 + i * 10, colors[i], false);
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        updateViewport();
        int mx = toVirtual(mouseX);
        int my = toVirtual(mouseY);
        List<WindowState> windows = GuiManager.windows;

        // leaving a text field commits its content (same as pressing Enter)
        for (WindowState ws : windows) {
            if (ws.focusedText != null
                    && !inside(mx, my, widgetX(ws, ws.focusedText), widgetY(ws, ws.focusedText),
                    widgetW(ws, ws.focusedText), widgetH(ws, ws.focusedText))) {
                commitTextField(ws);
            }
        }

        for (int i = windows.size() - 1; i >= 0; i--) {
            WindowState ws = windows.get(i);
            int px = winX(ws), py = winY(ws), w = winW(ws), h = winH(ws);

            // expanded dropdown options first (they may extend past window bounds)
            if (ws.expandedDropdown != null) {
                WidgetModel dd = ws.model.widget(ws.expandedDropdown);
                if (dd != null && clickDropdownOption(ws, dd, mx, my)) return true;
            }

            if (!inside(mx, my, px, py, w, h)) continue;

            // bring to front (always-on-top windows stay above the rest)
            GuiManager.bringToFront(ws);

            // close button (only when a title bar is visible)
            int th = titleH(ws);
            if (th >= 14 && inside(mx, my, px + w - 14, py + (th - 12) / 2, 12, 12)) {
                if (ws.model.closeOnEsc) GuiManager.closeFromScreen(ws);
                else ClientNet.sendEscape(ws.model.id);
                return true;
            }
            // title bar drag
            if (th > 0 && ws.model.draggable && !ws.model.fullscreen && my < py + th) {
                ws.dragging = true;
                ws.placed = true;
                ws.posX = px;
                ws.posY = py;
                ws.dragOffX = mx - px;
                ws.dragOffY = my - py;
                return true;
            }
            // widgets
            for (WidgetModel widget : ws.model.widgets) {
                if (clickWidget(ws, widget, mx, my)) return true;
            }
            return true; // click consumed by window background
        }
        // clicked outside every window: collapse dropdowns
        for (WindowState ws : windows) ws.expandedDropdown = null;
        return super.mouseClicked(event, doubleClick);
    }

    private boolean clickWidget(WindowState ws, WidgetModel w, int mx, int my) {
        // hidden widgets do not receive clicks
        if (w.has("visible") && !w.bool("visible")) return false;
        int x = widgetX(ws, w), y = widgetY(ws, w);
        int ww = widgetW(ws, w), hh = widgetH(ws, w);
        String winId = ws.model.id;
        // widgets in a drag group start a potential drag; the click fires on release if the
        // pointer never moved, so dragging a wide card row does not toggle a selection
        String group = w.str("dragGroup");
        if (!group.isEmpty() && inside(mx, my, x, y, ww, hh)) {
            ws.dragGroup = group;
            ws.pendingClick = w.id();
            ws.dragStartX = mx;
            ws.dragStartOffset = ws.groupOffsets.getOrDefault(group, 0);
            ws.dragMoved = false;
            return true;
        }
        switch (w.type()) {
            case "panel" -> {
                // only panels the server marked as clickable consume clicks
                if (w.bool("clickable") && inside(mx, my, x, y, ww, hh)) {
                    ClientNet.sendClick(winId, w.id());
                    return true;
                }
            }
            case "button" -> {
                if (inside(mx, my, x, y, ww, hh)) {
                    ClientNet.sendClick(winId, w.id());
                    return true;
                }
            }
            case "slider" -> {
                if (inside(mx, my, x, y + 6, ww, 14)) {
                    ws.activeSlider = w;
                    updateSlider(ws, w, mx);
                    return true;
                }
            }
            case "scrollbar" -> {
                if (inside(mx, my, x, y, ww, hh)) {
                    ws.activeScrollbar = w;
                    updateScrollbar(ws, w, my);
                    return true;
                }
            }
            case "checkbox" -> {
                if (inside(mx, my, x, y, 16 + textWidth(w.str("label")), 12)) {
                    boolean now = !w.bool("checked");
                    w.merge(boolProp("checked", now));
                    ClientNet.sendToggle(winId, w.id(), now);
                    return true;
                }
            }
            case "radio" -> {
                if (inside(mx, my, x, y, 16 + textWidth(w.str("label")), 12)) {
                    for (WidgetModel other : ws.model.widgets) {
                        if ("radio".equals(other.type()) && other.str("group").equals(w.str("group"))) {
                            other.merge(boolProp("checked", false));
                        }
                    }
                    w.merge(boolProp("checked", true));
                    ClientNet.sendToggle(winId, w.id(), true);
                    return true;
                }
            }
            case "dropdown" -> {
                if (inside(mx, my, x, y, ww, hh)) {
                    ws.expandedDropdown = w.id().equals(ws.expandedDropdown) ? null : w.id();
                    ws.dropdownScroll = 0;
                    return true;
                }
            }
            case "text_field" -> {
                if (inside(mx, my, x, y, ww, hh)) {
                    ws.focusedText = w;
                    String text = w.str("text");
                    int rel = mx - (x + 5);
                    int idx = 0;
                    while (idx < text.length() && textWidth(text.substring(0, idx + 1)) <= rel) idx++;
                    ws.textCursor = idx;
                    return true;
                }
            }
            case "item" -> {
                if (inside(mx, my, x, y, ww, hh)) {
                    ClientNet.sendClick(winId, w.id());
                    return true;
                }
            }
            case "image" -> {
                // only images the server marked as clickable consume clicks (cards, buttons, ...)
                if (w.bool("clickable") && inside(mx, my, x, y, ww, hh)) {
                    ClientNet.sendClick(winId, w.id());
                    return true;
                }
            }
            default -> { }
        }
        return false;
    }

    private boolean clickDropdownOption(WindowState ws, WidgetModel dd, int mx, int my) {
        int x = widgetX(ws, dd), y = widgetY(ws, dd) + widgetH(ws, dd);
        int ww = widgetW(ws, dd);
        java.util.List<String> options = dd.stringList("options");
        int rowH = Math.max(10, dd.has("rowHeight") ? dd.num("rowHeight") : 16);
        int visible = Math.min(6, options.size());
        if (!inside(mx, my, x, y, ww, visible * rowH)) {
            ws.expandedDropdown = null;
            return false;
        }
        int scroll = Math.max(0, Math.min(ws.dropdownScroll, options.size() - visible));
        int index = (my - y) / rowH + scroll;
        if (index >= 0 && index < options.size()) {
            dd.merge(numProp("selected", index));
            ClientNet.sendSelect(ws.model.id, dd.id(), index, options.get(index));
        }
        ws.expandedDropdown = null;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        updateViewport();
        int mx = toVirtual(mouseX);
        int my = toVirtual(mouseY);
        for (WindowState ws : GuiManager.windows) {
            if (ws.dragGroup != null) {
                int shift = mx - ws.dragStartX;
                if (Math.abs(shift) > 3) ws.dragMoved = true;
                if (ws.dragMoved) {
                    // cannot scroll past the right edge, and never drag to the right of the start
                    int minOffset = Math.min(0, winX(ws) + winW(ws) - groupRight(ws, ws.dragGroup));
                    int wanted = ws.dragStartOffset + shift;
                    ws.groupOffsets.put(ws.dragGroup, Math.max(minOffset, Math.min(0, wanted)));
                }
                return true;
            }
            if (ws.dragging) {
                ws.posX = mx - ws.dragOffX;
                ws.posY = my - ws.dragOffY;
                return true;
            }
            if (ws.activeScrollbar != null) {
                updateScrollbar(ws, ws.activeScrollbar, my);
                return true;
            }
            if (ws.activeSlider != null) {
                updateSlider(ws, ws.activeSlider, mx);
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        for (WindowState ws : GuiManager.windows) {
            if (ws.dragGroup != null) {
                if (!ws.dragMoved && ws.pendingClick != null) {
                    ClientNet.sendClick(ws.model.id, ws.pendingClick);
                }
                ws.dragGroup = null;
                ws.pendingClick = null;
                ws.dragMoved = false;
            }
            if (ws.activeSlider != null) {
                ClientNet.sendChange(ws.model.id, ws.activeSlider.id(), ws.activeSlider.dbl("value"));
                ws.activeSlider = null;
            }
            if (ws.activeScrollbar != null) {
                ClientNet.sendChange(ws.model.id, ws.activeScrollbar.id(), ws.activeScrollbar.dbl("value"));
                ws.activeScrollbar = null;
            }
            ws.dragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        updateViewport();
        int vx = toVirtual(mouseX);
        int vy = toVirtual(mouseY);
        for (WindowState ws : GuiManager.windows) {
            if (ws.expandedDropdown != null) {
                WidgetModel dd = ws.model.widget(ws.expandedDropdown);
                if (dd != null) {
                    int x = widgetX(ws, dd), y = widgetY(ws, dd);
                    int visible = Math.min(6, dd.stringList("options").size());
                    if (inside(vx, vy, x, y, widgetW(ws, dd), 18 + visible * 16)) {
                        ws.dropdownScroll = Math.max(0, ws.dropdownScroll - (int) Math.signum(verticalAmount));
                        return true;
                    }
                }
            }
        }
        // 滚轮直接滚动鼠标下的滚动条
        for (WindowState ws : GuiManager.windows) {
            for (WidgetModel w : ws.model.widgets) {
                if (!"scrollbar".equals(w.type())) continue;
                if (w.has("visible") && !w.bool("visible")) continue;
                int bx = widgetX(ws, w), by = widgetY(ws, w);
                if (!inside(vx, vy, bx, by, widgetW(ws, w), widgetH(ws, w))) continue;
                double next = Math.max(0, Math.min(100,
                        w.dbl("value") - Math.signum(verticalAmount) * 5));
                if (next == w.dbl("value")) return true;
                w.merge(numProp("value", next));
                ClientNet.sendChange(ws.model.id, w.id(), next);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        for (WindowState ws : GuiManager.windows) {
            WidgetModel w = ws.focusedText;
            if (w == null) continue;
            String text = w.str("text");
            int cursor = Math.min(ws.textCursor, text.length());
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (cursor > 0) {
                        w.merge(stringProp("text", text.substring(0, cursor - 1) + text.substring(cursor)));
                        ws.textCursor = cursor - 1;
                        sendLiveText(ws, w);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (cursor < text.length()) {
                        w.merge(stringProp("text", text.substring(0, cursor) + text.substring(cursor + 1)));
                        sendLiveText(ws, w);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                    commitTextField(ws);
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    ws.textCursor = Math.max(0, cursor - 1);
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    ws.textCursor = Math.min(text.length(), cursor + 1);
                    return true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    ws.textCursor = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_END -> {
                    ws.textCursor = text.length();
                    return true;
                }
                default -> { }
            }
            return false;
        }
        // Esc: a window may refuse a direct close and let the server decide (e.g. confirm dialog)
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !GuiManager.windows.isEmpty()) {
            WindowState top = GuiManager.windows.get(GuiManager.windows.size() - 1);
            if (!top.model.closeOnEsc) {
                ClientNet.sendEscape(top.model.id);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char codePoint = (char) event.codepoint();
        for (WindowState ws : GuiManager.windows) {
            WidgetModel w = ws.focusedText;
            if (w == null) continue;
            if (codePoint < 32 || codePoint == 127) return true;
            String typed = String.valueOf(codePoint);
            if (typed.isEmpty()) return true;
            String text = w.str("text");
            int max = Math.max(1, w.num("maxLength"));
            int room = max - text.length();
            if (room <= 0) return true;
            if (typed.length() > room) typed = typed.substring(0, room);
            int cursor = Math.min(ws.textCursor, text.length());
            w.merge(stringProp("text", text.substring(0, cursor) + typed + text.substring(cursor)));
            ws.textCursor = cursor + typed.length();
            sendLiveText(ws, w);
            return true;
        }
        return super.charTyped(event);
    }

    /** Sends the field content to the server and drops focus. */
    private void commitTextField(WindowState ws) {
        WidgetModel w = ws.focusedText;
        if (w == null) return;
        ws.focusedText = null;
        ClientNet.sendText(ws.model.id, w.id(), w.str("text"));
    }

    /** Fields marked {@code live} report every keystroke so the server can filter in real time. */
    private void sendLiveText(WindowState ws, WidgetModel w) {
        if (w.bool("live")) ClientNet.sendText(ws.model.id, w.id(), w.str("text"));
    }

    private static com.google.gson.JsonObject stringProp(String key, String value) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty(key, value);
        return json;
    }

    @Override
    public void onClose() {
        GuiManager.closeAllFromScreen();
        super.onClose();
    }

    // ---------------------------------------------------------------- helpers

    private void updateSlider(WindowState ws, WidgetModel w, int mx) {
        int x = widgetX(ws, w);
        int ww = widgetW(ws, w);
        double min = w.dbl("min"), max = w.dbl("max"), step = w.dbl("step");
        double ratio = ww > 0 ? (double) (mx - x) / ww : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        double value = min + ratio * (max - min);
        if (step > 0) value = Math.round(value / step) * step;
        value = Math.max(min, Math.min(max, value));
        w.merge(numProp("value", value));
    }

    /** 根据鼠标 Y 计算滚动条数值；拖动中按需实时上报（60ms 节流），松手时由 mouseReleased 补发最终值。 */
    private void updateScrollbar(WindowState ws, WidgetModel w, int my) {
        int y = widgetY(ws, w);
        int hh = widgetH(ws, w);
        double thumbPct = Math.max(6, Math.min(100, w.dbl("thumb")));
        int thumbH = Math.max(12, (int) Math.round(hh * thumbPct / 100.0));
        int travel = Math.max(0, hh - thumbH);
        double ratio = travel > 0 ? (double) (my - y - thumbH / 2) / travel : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        double value = Math.round(ratio * 100);
        w.merge(numProp("value", value));
        long now = System.currentTimeMillis();
        if (w.bool("live") && now - ws.lastScrollSendAt >= 60) {
            ws.lastScrollSendAt = now;
            ClientNet.sendChange(ws.model.id, w.id(), value);
        }
    }

    private String formatSliderValue(WidgetModel w, double value) {
        double step = w.dbl("step");
        if (step >= 1 || value == Math.floor(value)) return String.valueOf((int) value);
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static com.google.gson.JsonObject boolProp(String key, boolean value) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty(key, value);
        return json;
    }

    private static com.google.gson.JsonObject numProp(String key, double value) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty(key, value);
        return json;
    }

    /** 每个控件向目标位置插值的速度（每帧），越大越跟手。 */
    private static final float ANIM_FACTOR = 0.45f;

    /** 控件位置动画状态：既支持"缓动跟随"，也支持服务端指定的定时补间（效果动画）。 */
    static final class PosAnim {
        float x, y;
        float fromX, fromY;
        int targetX, targetY;
        long start;
        int dur;
        String ease = "out";
    }

    /**
     * 渲染位置：默认平滑跟随布局；控件带 animMs 时改用定时补间，
     * 服务端只需把控件目标位置改一次，客户端就会自己飞过去（平底锅、交换牌等）。
     * 拖动整排手牌时不做插值，否则会拖泥带水。
     */
    private int[] animPos(WindowState ws, WidgetModel w, int tx, int ty) {
        PosAnim a = ws.animPos.get(w.id());
        if (a == null) {
            a = new PosAnim();
            a.x = tx;
            a.y = ty;
            a.targetX = tx;
            a.targetY = ty;
            ws.animPos.put(w.id(), a);
        }
        if (ws.dragGroup != null && !ws.dragGroup.isEmpty()) {
            a.x = tx;
            a.y = ty;
            a.start = 0;
            a.targetX = tx;
            a.targetY = ty;
            return new int[]{tx, ty};
        }
        int dur = w.num("animMs");
        if (dur > 0) {
            if (a.targetX != tx || a.targetY != ty) {
                a.fromX = a.x;
                a.fromY = a.y;
                a.targetX = tx;
                a.targetY = ty;
                a.start = System.currentTimeMillis();
                a.dur = dur;
                a.ease = w.str("animEase");
            }
            if (a.start != 0) {
                float t = (System.currentTimeMillis() - a.start) / (float) a.dur;
                if (t >= 1f) {
                    a.x = tx;
                    a.y = ty;
                    a.start = 0;
                } else {
                    float e = ease(t, a.ease);
                    a.x = a.fromX + (tx - a.fromX) * e;
                    a.y = a.fromY + (ty - a.fromY) * e;
                }
            }
            return new int[]{Math.round(a.x), Math.round(a.y)};
        }
        a.start = 0;
        a.targetX = tx;
        a.targetY = ty;
        float dx = tx - a.x, dy = ty - a.y;
        if (Math.abs(dx) < 0.6f && Math.abs(dy) < 0.6f) {
            a.x = tx;
            a.y = ty;
        } else {
            a.x += dx * ANIM_FACTOR;
            a.y += dy * ANIM_FACTOR;
        }
        return new int[]{Math.round(a.x), Math.round(a.y)};
    }

    /** 缓动：out 先快后慢（飞道具），inOut 两端慢，linear 匀速。 */
    private static float ease(float t, String mode) {
        t = Math.max(0f, Math.min(1f, t));
        if ("linear".equals(mode)) return t;
        if ("in".equals(mode)) return t * t;
        if ("inOut".equals(mode)) {
            return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2;
        }
        float inv = 1 - t;
        return 1 - inv * inv * inv;
    }

    /** 颜色线性插值，用于渐变背景。 */
    private static int lerpColor(int from, int to, float t) {
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int gr = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    /** 在控件外侧画三层逐渐变淡的描边，形成发光效果。 */
    private static void glow(GuiGraphics g, int x, int y, int w, int h, int color) {
        int base = (color >>> 24) == 0 ? 0x90 : (color >>> 24);
        for (int i = 1; i <= 3; i++) {
            int alpha = Math.min(255, base * (4 - i) / 5);
            int c = (alpha << 24) | (color & 0x00FFFFFF);
            outline(g, x - i, y - i, w + i * 2, h + i * 2, c, 1);
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        outline(g, x, y, w, h, color, 1);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color, int thickness) {
        for (int i = 0; i < thickness; i++) {
            g.fill(x + i, y + i, x + w - i, y + i + 1, color);
            g.fill(x + i, y + h - i - 1, x + w - i, y + h - i, color);
            g.fill(x + i, y + i, x + i + 1, y + h - i, color);
            g.fill(x + w - i - 1, y + i, x + w - i, y + h - i, color);
        }
    }

    /**
     * Filled rectangle with rounded corners. Each row's left/right boundary is computed as a
     * fractional position and the boundary pixels get a partial alpha, which removes the hard
     * staircase that naive per-row spans produce.
     */
    private static void fillRound(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int alpha = color >>> 24;
        int rgb = color & 0x00FFFFFF;
        double rr = (double) r * r;
        for (int row = 0; row < h; row++) {
            double ly = row + 0.5;
            double inset = 0;
            if (ly < r) {
                double dy = r - ly;
                inset = r - Math.sqrt(Math.max(0, rr - dy * dy));
            } else if (ly > h - r) {
                double dy = ly - (h - r);
                inset = r - Math.sqrt(Math.max(0, rr - dy * dy));
            }
            double left = inset;
            double right = w - inset;
            if (right <= left) continue;
            int ry = y + row;
            int i0 = Math.max(0, Math.min(w - 1, (int) Math.floor(left)));
            int i1 = Math.max(0, Math.min(w - 1, (int) Math.floor(right)));
            double covL = Math.min(1.0, i0 + 1 - left);
            double covR = Math.min(1.0, right - i1);
            if (i0 == i1) {
                double cov = Math.min(1.0, right - left);
                fillCovered(g, x + i0, ry, cov, alpha, rgb);
                continue;
            }
            if (i0 + 1 < i1) {
                g.fill(x + i0 + 1, ry, x + i1, ry + 1, color);
            }
            fillCovered(g, x + i0, ry, covL, alpha, rgb);
            fillCovered(g, x + i1, ry, covR, alpha, rgb);
        }
    }

    /** One-pixel column blended by {@code coverage} (0..1) so rounded edges look smooth. */
    private static void fillCovered(GuiGraphics g, int px, int py, double coverage, int alpha, int rgb) {
        if (coverage <= 0.02) return;
        int a = (int) Math.round(alpha * Math.min(1.0, coverage));
        if (a <= 0) return;
        g.fill(px, py, px + 1, py + 1, (a << 24) | rgb);
    }

    /** Style carrying a window's font id; empty when the window keeps the default font. */
    private static Style fontStyle(String fontId) {
        if (fontId == null || fontId.isBlank()) return Style.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(fontId);
        if (id == null) return Style.EMPTY;
        // 字体包没装成功时退回原版字体，避免整屏文字渲染不出来
        ResourceLocation json = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "font/" + id.getPath() + ".json");
        if (Minecraft.getInstance().getResourceManager().getResource(json).isEmpty()) return Style.EMPTY;
        return Style.EMPTY.withFont(new FontDescription.Resource(id));
    }

    /** Draws text with the current window's font (falls back to the vanilla font). */
    private void drawText(GuiGraphics g, String s, int x, int y, int color, boolean shadow) {
        float snap = textSnapScale;
        if (snap > 0f && Math.abs(snap - 1f) > 1.0e-4f) {
            // 画布处于分数倍率时，文字原点会落在设备像素之间，位图字形跨像素采样发虚。
            // 加一个微小位移把原点吸附到最近的整数像素，观感明显变锐利。
            float cx = (float) (Math.round(x * snap) / snap - x);
            float cy = (float) (Math.round(y * snap) / snap - y);
            if (cx != 0f || cy != 0f) {
                g.pose().pushMatrix();
                g.pose().translate(cx, cy);
                drawText0(g, s, x, y, color, shadow);
                g.pose().popMatrix();
                return;
            }
        }
        drawText0(g, s, x, y, color, shadow);
    }

    private void drawText0(GuiGraphics g, String s, int x, int y, int color, boolean shadow) {
        if (textStyle.isEmpty()) g.drawString(this.font, s, x, y, color, shadow);
        else g.drawString(this.font, Component.literal(s).withStyle(textStyle), x, y, color, shadow);
    }

    /** Text width measured with the current window's font, so layout stays aligned. */
    private int textWidth(String s) {
        if (textStyle.isEmpty()) return this.font.width(s);
        return this.font.width(Component.literal(s).withStyle(textStyle));
    }

    private void hover(WindowState ws, WidgetModel w, int x, int y) {
        ws.hovered = w;
        ws.hoveredX = x;
        ws.hoveredY = y;
    }

    /**
     * Splits text on explicit newlines and, when {@code maxWidth > 0}, wraps long lines.
     * CJK text has no spaces, so wrapping falls back to per-character breaks.
     */
    private List<String> layoutLines(String text, int maxWidth) {
        List<String> out = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (maxWidth <= 0 || textWidth(paragraph) <= maxWidth) {
                out.add(paragraph);
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < paragraph.length(); i++) {
                char c = paragraph.charAt(i);
                if (line.length() > 0 && textWidth(line.toString() + c) > maxWidth) {
                    int breakAt = line.lastIndexOf(" ");
                    if (breakAt > 0 && breakAt > line.length() / 2) {
                        out.add(line.substring(0, breakAt));
                        line = new StringBuilder(line.substring(breakAt + 1));
                    } else {
                        out.add(line.toString());
                        line = new StringBuilder();
                    }
                }
                line.append(c);
            }
            out.add(line.toString());
        }
        return out;
    }

    /** Parses the widget's material string into a vanilla ItemStack. Returns EMPTY on failure. */
    private static ItemStack parseItemStack(WidgetModel w) {
        String mat = w.str("material");
        if (mat.isEmpty()) return ItemStack.EMPTY;
        if (!mat.contains(":")) mat = "minecraft:" + mat.toLowerCase(java.util.Locale.ROOT);
        ResourceLocation id = ResourceLocation.tryParse(mat);
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, w.num("amount")));
    }

    /** Builds a multi-line tooltip string for an item widget. */
    private static String itemTooltip(WidgetModel w, ItemStack stack) {
        String name = w.str("displayName");
        if (name.isEmpty()) name = stack.getHoverName().getString();
        StringBuilder sb = new StringBuilder(name);
        for (String spec : w.stringList("enchants")) {
            String line = enchantLine(spec);
            if (!line.isEmpty()) sb.append('\n').append("#A8A8A8|").append(line);
        }
        for (String line : w.stringList("lore")) {
            if (!line.isEmpty()) sb.append('\n').append("#AAAAAA|").append(line);
        }
        return sb.toString();
    }

    /**
     * Turns {@code "enchantment.minecraft.sharpness:5"} into the player's own language,
     * e.g. "锋利 V" on a Chinese client and "Sharpness V" on an English one.
     */
    private static String enchantLine(String spec) {
        int split = spec.lastIndexOf(':');
        if (split <= 0) return "";
        String key = spec.substring(0, split);
        int level;
        try {
            level = Integer.parseInt(spec.substring(split + 1));
        } catch (NumberFormatException e) {
            return "";
        }
        String name;
        try {
            name = Component.translatable(key).getString();
        } catch (RuntimeException e) {
            name = key;
        }
        if (name.isEmpty() || name.equals(key)) {
            // unknown key: fall back to the readable path, e.g. "sweeping_edge" -> "sweeping edge"
            name = key.substring(key.lastIndexOf('.') + 1).replace('_', ' ');
        }
        return level > 1 ? name + " " + roman(level) : name;
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(value);
        };
    }
}
