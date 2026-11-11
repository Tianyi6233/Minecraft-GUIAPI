package com.guiapi.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Sounds sent by the server, same idea as {@link GuiManager}'s dynamic images.
 *
 * <p>Minecraft can only play OGG files that live in a resource pack, so the received bytes are
 * written into {@code resourcepacks/guiapi_sounds_<签名>.zip} (one entry in {@code sounds.json}
 * plus the file itself), the pack is enabled and the resources are reloaded once. Afterwards
 * {@code play_sound} messages are played by id. The pack name carries a content hash, so a later
 * version of the sounds produces a new file instead of trying to overwrite a locked zip.</p>
 */
public final class GuiSound {
    private static final Logger LOG = LoggerFactory.getLogger("GuiApi");

    private static final String NAMESPACE = "guiapi";
    private static final String PACK_PREFIX = "guiapi_sounds";
    private static final String PACK_SUFFIX = ".zip";
    /** 记录当前音效包的内容签名，和现有文件比对决定是否需要重写。 */
    private static final String MARKER = "guiapi_sounds.txt";
    /** 一局里正常只会排十几条，大量积压说明音效包没生效，超过就丢弃。 */
    private static final int MAX_QUEUE = 48;

    private static final Map<String, byte[]> sounds = new LinkedHashMap<>();
    private static final Map<String, LoopInstance> loops = new HashMap<>();
    /** 所有正在播放的实例（含一次性音效），stop 时能把长音一起掐掉。 */
    private static final Map<String, List<SimpleSoundInstance>> active = new HashMap<>();
    private static final List<Runnable> deferred = new ArrayList<>();
    private static final java.util.Set<String> logged = new java.util.HashSet<>();
    private static boolean reloading;
    private static boolean installing;
    private static int failedInstalls;
    private static Timer timer;
    private static TimerTask pendingTask;

    private GuiSound() {}

    /** Called for every {@code sound} message: remembers the bytes and installs them lazily. */
    public static void register(String soundId, byte[] ogg) {
        if (soundId.isEmpty() || ogg.length == 0) return;
        if (Arrays.equals(sounds.get(soundId), ogg)) return;
        sounds.put(soundId, ogg);
        LOG.info("GuiApi sound '{}' received ({} bytes, {} total)", soundId, ogg.length, sounds.size());
        installSoon();
    }

    /**
     * Plays a received sound once. Requests that arrive before the pack finished loading are
     * replayed after the reload instead of being dropped.
     */
    public static void play(String soundId, float volume, float pitch, boolean loop) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation sound = identifier(soundId);
        if (mc.getSoundManager().getSoundEvent(sound) == null) {
            if (!sounds.containsKey(soundId)) {
                LOG.warn("GuiApi sound '{}' was never delivered", soundId);
                return;
            }
            if (deferred.size() < MAX_QUEUE) deferred.add(() -> play(soundId, volume, pitch, loop));
            installSoon();
            return;
        }

        SimpleSoundInstance instance = loop
                ? new LoopInstance(sound, volume, pitch)
                : new SimpleSoundInstance(
                        sound,
                        // 统一走主音量：MUSIC 分类常被玩家拉到 0，BGM 会完全没声
                        SoundSource.MASTER,
                        Math.max(0.0f, volume),
                        Math.max(0.1f, pitch),
                        RandomSource.create(),
                        false,
                        0,
                        SoundInstance.Attenuation.NONE,
                        0.0, 0.0, 0.0,
                        true);
        List<SimpleSoundInstance> playing = active.computeIfAbsent(soundId, k -> new ArrayList<>());
        if (playing.size() >= 8) playing.remove(0);
        playing.add(instance);
        if (instance instanceof LoopInstance loopInstance) {
            LoopInstance old = loops.put(soundId, loopInstance);
            if (old != null) mc.getSoundManager().stop(old);
        }
        mc.getSoundManager().play(instance);
        // 每个 id 只记一次，方便确认声音到底有没有被播放
        if (logged.add(soundId)) {
            LOG.info("GuiApi playing '{}' (loop={}, volume={})", soundId, loop, volume);
        }
    }

    /**
     * Changes the volume of a looping sound without restarting it. The loop instance is tickable,
     * so the sound engine re-applies the gain every tick — no restart, no click.
     */
    public static void setVolume(String soundId, float volume) {
        LoopInstance instance = loops.get(soundId);
        if (instance != null) instance.setVolume(Math.max(0.0f, volume));
    }

    /** Stops one sound by id — loopers and already-started one-shot sounds alike. */
    public static void stop(String soundId) {
        Minecraft mc = Minecraft.getInstance();
        SimpleSoundInstance instance = loops.remove(soundId);
        if (instance != null) mc.getSoundManager().stop(instance);
        List<SimpleSoundInstance> playing = active.remove(soundId);
        if (playing != null) {
            for (SimpleSoundInstance sound : playing) mc.getSoundManager().stop(sound);
        }
    }

    public static void stopAll() {
        Minecraft mc = Minecraft.getInstance();
        for (SimpleSoundInstance instance : loops.values()) mc.getSoundManager().stop(instance);
        loops.clear();
        for (List<SimpleSoundInstance> playing : active.values()) {
            for (SimpleSoundInstance sound : playing) mc.getSoundManager().stop(sound);
        }
        active.clear();
    }

    // ---------------------------------------------------------------- pack install

    /** 等音效包这一批消息收完再重载一次资源，避免每收到一个文件就卡一次。 */
    private static void installSoon() {
        synchronized (GuiSound.class) {
            if (reloading) return;
            if (timer == null) timer = new Timer("guiapi-sounds", true);
            if (pendingTask != null) pendingTask.cancel();
            pendingTask = new TimerTask() {
                @Override
                public void run() {
                    Minecraft.getInstance().execute(GuiSound::install);
                }
            };
            timer.schedule(pendingTask, 1200L);
        }
    }

    private static void install() {
        if (reloading) {
            // 上一次重载还没结束：等它完成后由 afterReload 再调用
            installing = true;
            return;
        }
        installing = false;
        Minecraft mc = Minecraft.getInstance();
        File dir = new File(mc.gameDirectory, "resourcepacks");
        String signature = signature();
        String packName = PACK_PREFIX + "_" + Integer.toHexString(signature.hashCode()) + PACK_SUFFIX;
        File zip = new File(dir, packName);
        String packId = "file/" + packName;

        try {
            boolean current = zip.isFile() && signature.equals(readMarker(zip));
            if (!current) writePack(zip, signature);
            boolean changed = select(mc, packId);
            if (current && !changed) {
                // 上一局已经装好并启用，声音在启动时就读进来了
                drain();
                return;
            }
            // 重载只会按「已选中」的 id 重新扫描：新写出的包必须先让仓库发现并选中，
            // 否则这次重载的包列表里根本没有它，声音永远找不到（字体包是启动前就存在的，才没踩到）
            PackRepository repository = mc.getResourcePackRepository();
            repository.reload();
            repository.addPack(packId);
            mc.options.save();
            reloading = true;
            mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> {
                reloading = false;
                // 重载会清掉动态贴图，界面要立刻能继续显示
                GuiManager.reRegisterImages();
                deleteStale(dir, zip);
                if (installing) install();
                else drain();
            }));
        } catch (Throwable t) {
            reloading = false;
            LOG.warn("GuiApi: sound pack could not be installed", t);
        }
    }

    private static void drain() {
        if (deferred.isEmpty()) {
            failedInstalls = 0;
            return;
        }
        List<Runnable> queued = new ArrayList<>(deferred);
        deferred.clear();
        for (Runnable runnable : queued) runnable.run();

        if (!deferred.isEmpty()) {
            // 仍然找不到声音：多半是包没被接受，重试几次后放弃，避免无限重载
            failedInstalls++;
            if (failedInstalls > 2) {
                LOG.warn("GuiApi: {} sound request(s) could not be resolved, giving up", deferred.size());
                deferred.clear();
                failedInstalls = 0;
            } else {
                installSoon();
            }
        }
    }

    /** 启用 {@code wantedId}，把旧的 GuiApi 音效包从启用列表移除。返回列表是否发生变化。 */
    private static boolean select(Minecraft mc, String wantedId) {
        List<String> packs = mc.options.resourcePacks;
        boolean changed = false;
        for (String entry : new ArrayList<>(packs)) {
            if (entry.startsWith("file/" + PACK_PREFIX) && !entry.equals(wantedId)) {
                packs.remove(entry);
                changed = true;
            }
        }
        if (!packs.contains(wantedId)) {
            packs.add(wantedId);
            changed = true;
        }
        return changed;
    }

    /** 删除旧音效包；此刻游戏可能还锁着它们，失败就留到下次启动。 */
    private static void deleteStale(File dir, File keep) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (!name.startsWith(PACK_PREFIX) || !name.endsWith(PACK_SUFFIX) || f.equals(keep)) continue;
            if (f.delete()) LOG.info("GuiApi: removed stale sound pack {}", name);
        }
    }

    private static String signature() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, byte[]> entry : sounds.entrySet()) {
            byte[] data = entry.getValue();
            sb.append(entry.getKey()).append(':').append(data.length).append(':').append(Arrays.hashCode(data)).append(';');
        }
        return sb.toString();
    }

    private static String readMarker(File zip) {
        if (!zip.isFile()) return "";
        try (ZipFile zf = new ZipFile(zip)) {
            ZipEntry entry = zf.getEntry(MARKER);
            if (entry == null) return "";
            try (InputStreamReader in = new InputStreamReader(zf.getInputStream(entry), StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[4096];
                int read;
                while ((read = in.read(buf)) >= 0) sb.append(buf, 0, read);
                return sb.toString();
            }
        } catch (IOException e) {
            return "";
        }
    }

    private static void writePack(File zip, String signature) throws IOException {
        // 1.20.1 的 pack.mcmeta 只认 pack_format
        int format = SharedConstants.RESOURCE_PACK_FORMAT;
        String mcmeta = "{\"pack\":{\"pack_format\":" + format
                + ",\"description\":\"GuiApi sounds\"}}";

        JsonObject registry = new JsonObject();
        for (Map.Entry<String, byte[]> entry : sounds.entrySet()) {
            String soundName = safe(entry.getKey());
            JsonObject sound = new JsonObject();
            sound.addProperty("name", NAMESPACE + ":" + soundName);
            // 长音频（BGM）用流式播放，避免整段解码进内存；BGM 需要循环，普通音效一律整段载入
            if (entry.getValue().length > 2_000_000) sound.addProperty("stream", true);
            JsonArray list = new JsonArray();
            list.add(sound);
            JsonObject definition = new JsonObject();
            definition.add("sounds", list);
            registry.add(soundName, definition);
        }

        File parent = zip.getParentFile();
        if (parent != null) parent.mkdirs();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            put(out, "pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));
            put(out, "assets/" + NAMESPACE + "/sounds.json", registry.toString().getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> entry : sounds.entrySet()) {
                put(out, "assets/" + NAMESPACE + "/sounds/" + safe(entry.getKey()) + ".ogg", entry.getValue());
            }
            put(out, MARKER, signature.getBytes(StandardCharsets.UTF_8));
        }
        LOG.info("GuiApi: sound pack written ({} sounds)", sounds.size());
    }

    private static void put(ZipOutputStream out, String path, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(path));
        out.write(data);
        out.closeEntry();
    }

    private static ResourceLocation identifier(String soundId) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, safe(soundId));
    }

    private static String safe(String soundId) {
        return soundId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
    }

    /** 调试用：确认某个音效已经随资源包载入。 */
    public static boolean loaded(String soundId) {
        return Minecraft.getInstance().getSoundManager().getSoundEvent(identifier(soundId)) != null;
    }

    /**
     * 循环播放专用实例：实现 TickableSoundInstance，声音引擎每 tick 都会按 {@code getVolume()}
     * 重新设置通道增益，因此改音量不会让音乐从头重播。
     */
    private static final class LoopInstance extends SimpleSoundInstance implements TickableSoundInstance {
        LoopInstance(ResourceLocation sound, float volume, float pitch) {
            super(sound, SoundSource.MASTER, Math.max(0.0f, volume), Math.max(0.1f, pitch),
                    RandomSource.create(), true, 0, SoundInstance.Attenuation.NONE,
                    0.0, 0.0, 0.0, true);
        }

        void setVolume(float volume) {
            this.volume = volume;
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public void tick() {
        }
    }
}
