package com.guiapi.paper.net;

import com.google.gson.JsonObject;

/** Protocol constants shared with the client mod. */
public final class Protocol {
    private Protocol() {}

    public static final String CHANNEL = "guiapi:main";
    public static final int VERSION = 1;

    // server -> client
    public static final String TYPE_OPEN_WINDOW = "open_window";
    public static final String TYPE_CLOSE_WINDOW = "close_window";
    public static final String TYPE_UPDATE_WIDGET = "update_widget";
    public static final String TYPE_IMAGE = "image";
    /** Delivers one OGG sound file: {soundId, data(base64)}. */
    public static final String TYPE_SOUND = "sound";
    /** Plays a delivered sound: {soundId, volume, pitch, loop}. */
    public static final String TYPE_PLAY_SOUND = "play_sound";
    /** Stops a looping sound (or every looping sound when soundId is absent): {soundId}. */
    public static final String TYPE_STOP_SOUND = "stop_sound";
    /** Changes the volume of a looping sound without restarting it: {soundId, volume}. */
    public static final String TYPE_SET_SOUND_VOLUME = "set_sound_volume";
    /** Binds a keyboard shortcut on the client: {key, code}. */
    public static final String TYPE_BIND_KEY = "bind_key";
    /** Removes one shortcut: {key}. */
    public static final String TYPE_UNBIND_KEY = "unbind_key";
    /** Removes every shortcut the server bound. */
    public static final String TYPE_CLEAR_KEYS = "clear_keys";
    /** Closes every window on the client and then runs a chat command there: {command}. */
    public static final String TYPE_RUN_COMMAND = "run_command";

    // client -> server
    public static final String TYPE_READY = "ready";
    public static final String TYPE_EVENT = "event";
    public static final String TYPE_KEY = "key";

    /** Key id used by the bundled demo plugin. It is no longer hardcoded on the client. */
    public static final String KEY_DEMO = "demo";

    public static JsonObject baseMessage(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("v", VERSION);
        json.addProperty("type", type);
        return json;
    }
}
