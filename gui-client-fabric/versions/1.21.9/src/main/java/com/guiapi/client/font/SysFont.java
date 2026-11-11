package com.guiapi.client.font;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 把玩家自己的 Windows 系统字体打包成一个小资源包，注册出 {@code guiapi:sysfont} 字体 id，
 * 服务端窗口用 {@code GuiWindow.font("guiapi:sysfont")} 就能把界面文字换成系统字体。
 *
 * <p>Minecraft 只能从资源包加载字体，所以这里在客户端启动后把系统字体写成
 * {@code resourcepacks/guiapi_sysfont_<签名>.zip} 并加入已启用资源包列表，首次启动会重载一次资源。
 * 文件名带签名（字体名 + 字号），换字体时生成新文件、启用新包并删掉旧包，
 * 避免 Windows 上游戏正锁着旧 zip 时无法覆盖写入。找不到系统字体时直接跳过，界面继续使用原版字体。</p>
 *
 * <p>想换字体时，在 {@code config/guiapi-client.txt} 里写一行 {@code font=simhei.ttf}
 * （Windows 字体文件名，也可以写完整路径），再写一行 {@code font_size=10} 调整字号即可；
 * 下次启动会重新生成字体包。默认使用微软雅黑、字号 9。</p>
 */
public final class SysFont {

    /** 供服务端窗口使用的字体 id。 */
    public static final String ID = "guiapi:sysfont";

    private static final Logger LOG = LoggerFactory.getLogger("GuiApi");
    private static final String PACK_PREFIX = "guiapi_sysfont";
    private static final String PACK_SUFFIX = ".zip";
    /** 记录当前字体包用的是哪个字体、哪个字号，内容不符就重建。 */
    private static final String MARKER = "guiapi_font.txt";
    /** 优先微软雅黑，其次等线、黑体；都是 Windows 自带字体。 */
    private static final String[] CANDIDATES = {"msyh.ttc", "Deng.ttf", "simhei.ttf", "msyhbd.ttc"};
    private static final float DEFAULT_SIZE = 9.0f;
    /** 字形按显示尺寸的 4 倍光栅化，画布缩放/文字放大时有足够余量，不会糊。 */
    private static final float OVERSAMPLE = 4.0f;

    private SysFont() {}

    /** 客户端启动完成后调用：确保字体包存在并已启用。任何异常都不应影响正常游戏。 */
    public static void ensure(Minecraft client) {
        try {
            File dir = new File(client.gameDirectory, "resourcepacks");
            float size = configFloat(client, "font_size", DEFAULT_SIZE);
            File source = findSystemFont(configValue(client, "font"));
            if (source == null) {
                LOG.info("GuiApi: no Windows system font found, keeping the vanilla font");
                return;
            }

            // oversample 计入签名：参数变化时旧字体包会被判定为过期并重建
            String signature = source.getName() + "|" + size + "|o" + OVERSAMPLE;
            String packName = PACK_PREFIX + "_" + Integer.toHexString(signature.hashCode()) + PACK_SUFFIX;
            File zip = new File(dir, packName);
            String id = "file/" + packName;

            boolean changed = selectOnly(client, id);
            if (!signature.equals(readMarker(zip))) {
                writePack(zip, source, size, signature);
                LOG.info("GuiApi: system font pack built from {} (size {})", source.getName(), size);
            }
            if (!changed) return;
            // 1.21.8 的 reloadResourcePacks 不会重新读取 options.resourcePacks：
            // 新写出的包必须先让仓库发现并选中，否则首次启动时字体不生效
            PackRepository repository = client.getResourcePackRepository();
            repository.reload();
            repository.addPack(id);
            client.options.save();
            client.reloadResourcePacks().thenRun(() -> deleteStale(dir, zip));
        } catch (Throwable t) {
            LOG.warn("GuiApi: system font pack could not be installed", t);
        }
    }

    // ---------------------------------------------------------------- config

    private static String configValue(Minecraft client, String key) {
        for (String line : configLines(client)) {
            int eq = line.indexOf('=');
            String k = eq < 0 ? "" : line.substring(0, eq).trim();
            if (k.equalsIgnoreCase(key)) {
                String v = line.substring(eq + 1).trim();
                if (!v.isEmpty()) return v;
            }
        }
        return null;
    }

    private static float configFloat(Minecraft client, String key, float fallback) {
        String raw = configValue(client, key);
        if (raw == null) return fallback;
        try {
            float v = Float.parseFloat(raw);
            return v >= 4 && v <= 32 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Iterable<String> configLines(Minecraft client) {
        File file = new File(client.gameDirectory, "config/guiapi-client.txt");
        if (!file.isFile()) return List.of();
        try {
            List<String> out = new ArrayList<>();
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
            }
            return out;
        } catch (IOException e) {
            LOG.warn("GuiApi: could not read {}", file.getName(), e);
            return List.of();
        }
    }

    // ---------------------------------------------------------------- font file

    private static File findSystemFont(String wanted) {
        String winDir = System.getenv("WINDIR");
        if (winDir == null || winDir.isBlank()) winDir = "C:\\Windows";
        File fonts = new File(winDir, "Fonts");

        if (wanted != null) {
            File direct = new File(wanted);
            if (direct.isAbsolute()) {
                if (direct.isFile()) return direct;
            } else {
                File named = new File(fonts, wanted);
                if (named.isFile()) return named;
            }
            LOG.warn("GuiApi: configured font '{}' was not found, falling back to the default", wanted);
        }

        for (String name : CANDIDATES) {
            File f = new File(fonts, name);
            if (f.isFile() && f.length() > 0) return f;
        }
        return null;
    }

    // ---------------------------------------------------------------- pack

    /** 启用 {@code wantedId}，同时把旧的 GuiApi 字体包从启用列表移除。返回列表是否发生变化。 */
    private static boolean selectOnly(Minecraft client, String wantedId) {
        List<String> packs = client.options.resourcePacks;
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

    /** 删除除当前字体包以外的旧包（旧包此刻可能仍被游戏占用，失败就留到下次启动）。 */
    private static void deleteStale(File dir, File keep) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (!name.startsWith(PACK_PREFIX) || !name.endsWith(PACK_SUFFIX) || f.equals(keep)) continue;
            if (f.delete()) LOG.info("GuiApi: removed stale font pack {}", name);
        }
    }

    private static String readMarker(File zip) {
        if (!zip.isFile()) return "";
        try (ZipFile zf = new ZipFile(zip)) {
            ZipEntry entry = zf.getEntry(MARKER);
            if (entry == null) return "";
            try (InputStreamReader in = new InputStreamReader(zf.getInputStream(entry), StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = in.read()) >= 0 && sb.length() < 128) sb.append((char) c);
                return sb.toString().trim();
            }
        } catch (IOException e) {
            return "";
        }
    }

    private static void writePack(File zip, File ttf, float size, String signature) throws IOException {
        // ttf 的 file 是 assets/<ns>/font/ 下的相对路径（含扩展名），MC 会自动补上 font/ 前缀
        String fontJson = "{\"providers\":[{\"type\":\"ttf\",\"file\":\"guiapi:sysfont.ttf\","
                + "\"size\":" + size + ",\"oversample\":" + OVERSAMPLE + ",\"shift\":[0,1],\"skip\":\"\"}]}";
        // 1.21.8 的资源包格式号已经到 64：除 pack_format 外还写 min_format/max_format，兼容按新格式校验的版本
        int format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major();
        String mcmeta = "{\"pack\":{\"pack_format\":" + format
                + ",\"min_format\":" + format
                + ",\"max_format\":" + format
                + ",\"description\":\"GuiApi system font\"}}";
        File parent = zip.getParentFile();
        if (parent != null) parent.mkdirs();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            put(out, "pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));
            put(out, "assets/guiapi/font/sysfont.json", fontJson.getBytes(StandardCharsets.UTF_8));
            put(out, "assets/guiapi/font/sysfont.ttf", Files.readAllBytes(ttf.toPath()));
            put(out, MARKER, signature.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void put(ZipOutputStream out, String path, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(path));
        out.write(data);
        out.closeEntry();
    }

    /** 调试用：确认字体 id 是否已经随资源包载入。 */
    public static boolean loaded(Minecraft client) {
        ResourceLocation json = ResourceLocation.fromNamespaceAndPath("guiapi", "font/sysfont.json");
        return client.getResourceManager().getResource(json).isPresent();
    }
}
