package com.guiapi.paper.widget;

import com.google.gson.JsonObject;
import com.guiapi.paper.GuiActionEvent;

import java.util.function.Consumer;

/**
 * An image widget. The referenced image must be registered through GuiApi.registerImage(...).
 * Images can be made clickable, which is what card / board-game UIs need.
 */
public class ImageWidget extends Widget {
    private String imageId;
    private String fit;
    private Consumer<GuiActionEvent> clickHandler;

    public ImageWidget(String id, int x, int y, int width, int height, String imageId) {
        super(id, x, y, width, height);
        this.imageId = imageId;
    }

    public String imageId() { return imageId; }
    public ImageWidget image(String imageId) { this.imageId = imageId; return this; }

    /**
     * How the image is mapped onto the widget box:
     * {@code "stretch"} (default), {@code "cover"} (fills the box, cropping the overflow) or
     * {@code "contain"} (keeps the whole image visible, letterboxed). Use "cover" for
     * fullscreen backgrounds so art is never distorted.
     */
    public ImageWidget fit(String fit) { this.fit = fit; return this; }

    /** Makes the whole image clickable. The event action is {@code "click"}. */
    public ImageWidget onClick(Consumer<GuiActionEvent> handler) {
        this.clickHandler = handler;
        return this;
    }

    public Consumer<GuiActionEvent> clickHandler() { return clickHandler; }

    @Override protected String type() { return "image"; }

    @Override protected void writeProperties(JsonObject json) {
        json.addProperty("image", imageId);
        if (fit != null && !fit.isBlank()) json.addProperty("fit", fit);
        if (clickHandler != null) json.addProperty("clickable", true);
    }
}
