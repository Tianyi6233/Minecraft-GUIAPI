package com.guiapi.paper.widget;

import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;

import java.util.function.Consumer;

/**
 * 垂直滚动条。滑块位置对应 0–100 的百分比值，拖动或滚轮改变数值，
 * 服务端据此滚动内容（例如重排一组 Label 的文本或位置）。
 *
 * <p>典型用法：内容有 30 条、可视区 6 条时，用 {@code value} 换算偏移量：
 * {@code offset = round(value / 100 * (total - visible))}。
 */
public class Scrollbar extends Widget {
    private double value = 0;          // 0–100
    private double thumb = 20;         // 滑块高度占轨道高度的百分比
    private boolean live = true;       // 拖动过程中是否实时回传（false 只在松手时回传）
    private Integer trackColor;        // 轨道颜色，默认由客户端决定
    private Integer thumbColor;        // 滑块颜色，默认由客户端决定
    private Consumer<GuiActionEvent> changeHandler;

    public Scrollbar(String id, int x, int y, int width, int height) {
        super(id, x, y, width, height);
    }

    public double value() { return value; }
    public double thumb() { return thumb; }
    public boolean live() { return live; }

    /** 0–100 的当前位置。 */
    public Scrollbar value(double value) { this.value = value; return this; }

    /** 滑块高度占轨道高度的百分比，用来表示"可视内容 / 全部内容"的比例。 */
    public Scrollbar thumb(double percent) { this.thumb = percent; return this; }

    /** 拖动过程中是否连续回传，默认 true；数据量大时可以关掉，只在松手时触发一次。 */
    public Scrollbar live(boolean live) { this.live = live; return this; }

    /** 轨道颜色，支持 "#RRGGBB" / "#AARRGGBB"。 */
    public Scrollbar trackColor(String hex) { this.trackColor = Panel.parseColor(hex); return this; }

    public Scrollbar trackColor(int argb) { this.trackColor = argb; return this; }

    /** 滑块颜色，支持 "#RRGGBB" / "#AARRGGBB"。 */
    public Scrollbar thumbColor(String hex) { this.thumbColor = Panel.parseColor(hex); return this; }

    public Scrollbar thumbColor(int argb) { this.thumbColor = argb; return this; }

    /** 数值变化时在主线程回调（{@link GuiActionEvent#valueAsDouble()}）。 */
    public Scrollbar onChange(Consumer<GuiActionEvent> handler) { this.changeHandler = handler; return this; }
    public Consumer<GuiActionEvent> changeHandler() { return changeHandler; }

    @Override protected String type() { return "scrollbar"; }

    @Override protected void writeProperties(JsonObject json) {
        json.addProperty("value", value);
        json.addProperty("thumb", thumb);
        json.addProperty("live", live);
        if (trackColor != null) json.addProperty("trackColor", trackColor);
        if (thumbColor != null) json.addProperty("thumbColor", thumbColor);
    }
}
