package com.guiapi.demo;

import com.guiapi.paper.GuiApi;
import com.guiapi.paper.GuiWindow;
import com.guiapi.paper.net.Protocol;
import com.guiapi.paper.widget.TextField;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo plugin. Every shortcut is bound by this plugin at runtime (the client has no
 * built-in keys): N opens the demo list.
 */
public final class GuiDemoPlugin extends JavaPlugin implements Listener {
    /** GLFW key codes; the client resolves them, so no LWJGL dependency is needed here. */
    private static final int KEY_N = 78;

    private GuiApi api;

    @Override
    public void onEnable() {
        api = GuiApi.get();
        api.registerImage("demo_logo", generateDemoImage());
        api.registerImage("icon_book", generateBookIcon());
        api.registerImage("icon_star", generateStarIcon());
        api.addKeyHandler(Protocol.KEY_DEMO, this::openMainDemo);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("GuiDemo ready - press N for the demo list.");
    }

    /** Binds this plugin's own shortcuts; GuiApi replays them after every reconnect. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        api.bindKey(player, Protocol.KEY_DEMO, KEY_N);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!api.isClientSupported(player)) {
            player.sendMessage("§c你的客户端没有安装 GuiApi Fabric 模组，无法打开演示窗口。");
            return true;
        }
        openMainDemo(player);
        return true;
    }

    // ------------------------------------------------------------- main window

    private void openMainDemo(Player player) {
        GuiWindow window = new GuiWindow("demo")
                .title("GuiApi 演示窗口")
                .size(230, 340)
                .draggable(true);
        window.addLabel("header", 0, 6, "§l子 Demo 列表").color("#FFD700").centered(true);
        window.addLabel("hint", 0, 20, "点击按钮打开对应演示").color("#AAAAAA").centered(true);

        String[][] demos = {
                {"text", "文本演示"}, {"image", "图片演示"}, {"button", "按钮演示"},
                {"slider", "滑块演示"}, {"scrollbar", "滚动条演示"},
                {"choice", "复选/单选演示"}, {"dropdown", "下拉列表演示"},
                {"drag", "窗口拖动演示"}, {"item", "物品演示"},
                {"mcstyle", "MC风格演示"}, {"qrcode", "二维码生成"}
        };
        int y = 38;
        for (String[] demo : demos) {
            String key = demo[0];
            window.addButton("open_" + key, 25, y, 180, 20, demo[1])
                    .onClick(event -> openSubDemo(event.player(), key));
            y += 26;
        }
        api.openWindow(player, window);
    }

    private void openSubDemo(Player player, String key) {
        switch (key) {
            case "text" -> textDemo(player);
            case "image" -> imageDemo(player);
            case "button" -> buttonDemo(player);
            case "slider" -> sliderDemo(player);
            case "scrollbar" -> scrollbarDemo(player);
            case "choice" -> choiceDemo(player);
            case "dropdown" -> dropdownDemo(player);
            case "drag" -> dragDemo(player);
            case "item" -> itemDemo(player);
            case "mcstyle" -> mcStyleDemo(player);
            case "qrcode" -> qrcodeDemo(player);
        }
    }

    private void addCloseButton(GuiWindow window, int x, int y) {
        window.addButton("close", x, y, 90, 20, "关闭窗口")
                .onClick(event -> api.closeWindow(event.player(), window.id()));
    }

    // ------------------------------------------------------------- sub demos

    private void textDemo(Player player) {
        GuiWindow w = new GuiWindow("demo_text").title("文本演示").size(240, 150);
        w.addLabel("t1", 0, 8, "大号居中标题").color("#FFD700").centered(true).scale(2.0f);
        w.addLabel("t2", 15, 34, "普通白色文本 §a含§b颜色§d代码");
        w.addLabel("t3", 15, 50, "绿色文本").color("#55FF55");
        w.addLabel("t4", 15, 66, "红色文本").color("#FF5555");
        w.addLabel("t5", 15, 82, "半透明文本").color("#80FFFFFF");
        w.addLabel("t6", 15, 98, "缩放 1.5x").color("#55FFFF").scale(1.5f);
        addCloseButton(w, 75, 120);
        api.openWindow(player, w);
    }

    private void imageDemo(Player player) {
        GuiWindow w = new GuiWindow("demo_image").title("图片演示").size(200, 190);
        w.addLabel("caption", 0, 8, "此图片由服务器生成并发送").color("#AAAAAA").centered(true);
        w.addImage("img", 52, 26, 96, 96, "demo_logo");
        w.addLabel("caption2", 0, 128, "支持 PNG / JPEG").color("#55FFFF").centered(true);
        addCloseButton(w, 55, 152);
        api.openWindow(player, w);
    }

    private void buttonDemo(Player player) {
        AtomicInteger clicks = new AtomicInteger();
        GuiWindow w = new GuiWindow("demo_button").title("按钮演示").size(240, 160);
        w.addLabel("counter", 0, 10, "点击次数: 0").centered(true);
        w.addButton("b1", 20, 34, 200, 20, "按钮 A - 聊天提示").onClick(e -> {
            e.player().sendMessage("§a你点击了按钮 A!");
            int n = clicks.incrementAndGet();
            api.updateWidget(e.player(), "demo_button", "counter", Map.of("text", "点击次数: " + n));
        });
        w.addButton("b2", 20, 60, 200, 20, "按钮 B - 给你经验").onClick(e -> {
            e.player().giveExp(10);
            e.player().sendMessage("§b+10 经验");
        });
        w.addButton("b3", 20, 86, 200, 20, "按钮 C - 播放音效").onClick(e ->
                e.player().playSound(e.player().getLocation(), "entity.experience_orb.pickup", 1f, 1f));
        addCloseButton(w, 75, 122);
        api.openWindow(player, w);
    }

    private void sliderDemo(Player player) {
        GuiWindow w = new GuiWindow("demo_slider").title("滑块演示").size(260, 150);
        w.addLabel("value", 0, 10, "当前数值: 50").centered(true);
        w.addSlider("s1", 30, 34, 200).min(0).max(100).step(1).value(50).label("音量")
                .onChange(e -> api.updateWidget(e.player(), "demo_slider", "value",
                        Map.of("text", "当前数值: " + (int) e.valueAsDouble())));
        w.addSlider("s2", 30, 64, 200).min(0.0).max(1.0).step(0.05).value(0.5).label("精细调节")
                .onChange(e -> e.player().sendMessage("§7精细滑块: " + String.format("%.2f", e.valueAsDouble())));
        addCloseButton(w, 85, 110);
        api.openWindow(player, w);
    }

    /** 滚动条演示：30 条内容、可视 6 条，拖动右侧滚动条或滚轮翻页。 */
    private void scrollbarDemo(Player player) {
        final int visible = 6;
        GuiWindow w = new GuiWindow("demo_scroll").title("滚动条演示").size(240, 212);
        w.addLabel("hint", 0, 8, "拖动右侧滚动条，或在窗口内滚轮").color("#AAAAAA").centered(true);
        for (int i = 0; i < visible; i++) {
            w.addLabel("row" + i, 14, 30 + i * 20, SCROLL_ITEMS.get(i));
        }
        w.addLabel("pos", 14, 158, "1 - " + visible + " / " + SCROLL_ITEMS.size()).color("#55FFFF");
        w.addScrollbar("bar", 206, 28, 12, 120).thumb(100.0 * visible / SCROLL_ITEMS.size()).value(0)
                .onChange(e -> {
                    int offset = (int) Math.round(e.valueAsDouble() / 100.0 * (SCROLL_ITEMS.size() - visible));
                    for (int i = 0; i < visible; i++) {
                        api.updateWidget(e.player(), "demo_scroll", "row" + i,
                                Map.of("text", SCROLL_ITEMS.get(offset + i)));
                    }
                    api.updateWidget(e.player(), "demo_scroll", "pos",
                            Map.of("text", (offset + 1) + " - " + (offset + visible) + " / " + SCROLL_ITEMS.size()));
                });
        addCloseButton(w, 75, 180);
        api.openWindow(player, w);
    }

    /** 滚动条演示的内容列表。 */
    private static final List<String> SCROLL_ITEMS = List.of(
            "01 · 苹果", "02 · 香蕉", "03 · 橙子", "04 · 葡萄", "05 · 西瓜",
            "06 · 草莓", "07 · 蓝莓", "08 · 樱桃", "09 · 芒果", "10 · 菠萝",
            "11 · 柠檬", "12 · 桃子", "13 · 梨", "14 · 椰子", "15 · 火龙果",
            "16 · 哈密瓜", "17 · 榴莲", "18 · 猕猴桃", "19 · 石榴", "20 · 木瓜",
            "21 · 橘子", "22 · 柚子", "23 · 杨梅", "24 · 枇杷", "25 · 李子",
            "26 · 杏", "27 · 无花果", "28 · 山楂", "29 · 柿子", "30 · 桑葚");

    private void choiceDemo(Player player) {
        GuiWindow w = new GuiWindow("demo_choice").title("复选框 / 单选框演示").size(240, 200);
        w.addLabel("state", 0, 8, "选择状态会显示在这里").color("#AAAAAA").centered(true);
        w.addCheckbox("c1", 20, 28, "启用功能一", true).onToggle(e ->
                api.updateWidget(e.player(), "demo_choice", "state", Map.of("text", "功能一: " + (e.valueAsBoolean() ? "开" : "关"))));
        w.addCheckbox("c2", 20, 46, "启用功能二", false).onToggle(e ->
                api.updateWidget(e.player(), "demo_choice", "state", Map.of("text", "功能二: " + (e.valueAsBoolean() ? "开" : "关"))));
        w.addLabel("g1label", 20, 70, "难度选择(单选):").color("#FFD700");
        w.addRadio("r1", 30, 86, "简单", "difficulty", true).onToggle(e ->
                api.updateWidget(e.player(), "demo_choice", "state", Map.of("text", "难度: 简单")));
        w.addRadio("r2", 30, 104, "普通", "difficulty", false).onToggle(e ->
                api.updateWidget(e.player(), "demo_choice", "state", Map.of("text", "难度: 普通")));
        w.addRadio("r3", 30, 122, "困难", "difficulty", false).onToggle(e ->
                api.updateWidget(e.player(), "demo_choice", "state", Map.of("text", "难度: 困难")));
        addCloseButton(w, 75, 158);
        api.openWindow(player, w);
    }

    private void dropdownDemo(Player player) {
        GuiWindow w = new GuiWindow("demo_dropdown").title("下拉列表演示").size(240, 160);
        w.addLabel("sel", 0, 10, "当前选择: 苹果").centered(true);
        w.addLabel("l1", 20, 32, "选择一种水果:").color("#FFD700");
        w.addDropdown("d1", 20, 46, 200, List.of("苹果", "香蕉", "橙子", "西瓜", "葡萄", "草莓", "芒果", "樱桃"))
                .selected(0)
                .onSelect(e -> api.updateWidget(e.player(), "demo_dropdown", "sel",
                        Map.of("text", "当前选择: " + e.valueAsString())));
        addCloseButton(w, 75, 118);
        api.openWindow(player, w);
    }

    private void dragDemo(Player player) {
        GuiWindow w = new GuiWindow("demo_drag").title("按住标题栏拖我!").size(220, 120).position(60, 60);
        w.addLabel("l1", 0, 10, "这个窗口可以用鼠标拖动").centered(true);
        w.addLabel("l2", 0, 26, "试着把它拖到屏幕任意位置").color("#AAAAAA").centered(true);
        addCloseButton(w, 65, 80);
        api.openWindow(player, w);
    }

    private void itemDemo(Player player) {
        GuiWindow w = new GuiWindow("demo_item").title("物品演示").size(280, 210);
        w.addLabel("hint", 0, 8, "悬停查看 tooltip，点击物品触发事件").color("#AAAAAA").centered(true);

        // 基础物品
        w.addItem("item1", 30, 30, 32, 32, "diamond_sword")
                .displayName("§b钻石剑")
                .lore(List.of("§7锋利无比", "§7点击有惊喜"));
        w.addItem("item2", 80, 30, 32, 32, "golden_apple")
                .amount(3)
                .displayName("§6金苹果");
        w.addItem("item3", 130, 30, 32, 32, "enchanted_book")
                .enchanted(true)
                .displayName("§d附魔书")
                .lore(List.of("§7 protection IV"));
        w.addItem("item4", 180, 30, 32, 32, "nether_star")
                .displayName("§e下界之星")
                .onClick(e -> e.player().sendMessage("§e你点击了下界之星！"));

        // 更多物品展示
        w.addItem("item5", 30, 80, 32, 32, "diamond").amount(16);
        w.addItem("item6", 80, 80, 32, 32, "iron_ingot").amount(16);
        w.addItem("item7", 130, 80, 32, 32, "emerald").amount(64);
        w.addItem("item8", 180, 80, 32, 32, "dragon_egg").enchanted(true);

        // 点击事件演示
        w.addItem("buy_btn", 230, 30, 32, 32, "emerald")
                .displayName("§a购买")
                .lore(List.of("§7花费: 64 绿宝石"))
                .onClick(e -> e.player().sendMessage("§a购买成功！"));

        w.addLabel("label1", 30, 64, "钻石剑");
        w.addLabel("label2", 80, 64, "金苹果 x3");
        w.addLabel("label3", 130, 64, "附魔书");
        w.addLabel("label4", 180, 64, "下界之星");
        w.addLabel("label5", 230, 64, "购买");

        w.addLabel("label6", 30, 114, "钻石 x16");
        w.addLabel("label7", 80, 114, "铁锭 x16");
        w.addLabel("label8", 130, 114, "绿宝石 x64");
        w.addLabel("label9", 180, 114, "龙蛋");

        addCloseButton(w, 95, 160);
        api.openWindow(player, w);
    }

    /**
     * Minecraft-style UI showcase: vanilla-like window, buttons, item slots, panels.
     */
    private void mcStyleDemo(Player player) {
        GuiWindow w = new GuiWindow("demo_mcstyle")
                .title("MC 风格演示")
                .size(320, 260)
                .style("minecraft")
                .draggable(true);

        w.addLabel("hint", 0, 6, "我的世界风格界面").color("#FFFFFF").centered(true);

        // 物品格子行
        w.addItem("slot1", 30, 30, 24, 24, "diamond_sword").displayName("§b钻石剑");
        w.addItem("slot2", 60, 30, 24, 24, "golden_apple").amount(3);
        w.addItem("slot3", 90, 30, 24, 24, "enchanted_book").enchanted(true);
        w.addItem("slot4", 120, 30, 24, 24, "nether_star");
        w.addItem("slot5", 150, 30, 24, 24, "emerald").amount(16);
        w.addItem("slot6", 180, 30, 24, 24, "dragon_egg");

        // 按钮行
        w.addButton("mc_btn1", 30, 70, 80, 20, "按钮 A")
                .onClick(e -> e.player().sendMessage("§a点击了按钮 A"));
        w.addButton("mc_btn2", 120, 70, 80, 20, "按钮 B")
                .onClick(e -> e.player().sendMessage("§b点击了按钮 B"));
        w.addButton("mc_btn3", 210, 70, 80, 20, "按钮 C")
                .onClick(e -> e.player().sendMessage("§c点击了按钮 C"));

        // 面板 + 文本
        w.addPanel("panel1", 30, 100, 260, 60);
        w.addLabel("panel_label", 40, 108, "这是一个 MC 风格面板").color("#000000");
        w.addLabel("panel_label2", 40, 122, "背景为浅灰色，类似 MC 界面").color("#333333");

        // 输入框
        w.addTextField("mc_input", 30, 140, 200, 20)
                .placeholder("MC 风格输入框...")
                .onChange(e -> e.player().sendMessage("§7输入: " + e.valueAsString()));

        addCloseButton(w, 115, 180);
        api.openWindow(player, w);
    }

    /**
     * QR code generator demo: input text, call api.qrtool.cn, display image.
     */
    private void qrcodeDemo(Player player) {
        GuiWindow w = new GuiWindow("demo_qrcode")
                .title("二维码生成器")
                .size(280, 320);

        w.addLabel("label", 20, 20, "输入文本生成二维码:").color("#FFFFFF");
        com.guiapi.paper.widget.TextField input = w.addTextField("qr_input", 20, 40, 180, 24)
                .placeholder("输入任意文本...")
                .maxLength(128);

        w.addButton("generate", 210, 40, 50, 24, "生成")
                .onClick(e -> {
                    String text = input.text();
                    if (text.isEmpty()) {
                        e.player().sendMessage("§c请输入文本！");
                        return;
                    }
                    e.player().sendMessage("§7正在生成二维码...");
                    // 异步下载二维码
                    new Thread(() -> {
                        try {
                            String url = "https://api.qrtool.cn/?text=" + java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
                            byte[] imageData = downloadImage(url);
                            String imageId = "qrcode_" + System.currentTimeMillis();
                            api.registerImage(imageId, imageData);
                            api.sendImage(player, imageId); // 必须发送图片数据到客户端
                            // 更新图片控件
                            api.updateWidget(player, "demo_qrcode", "qr_image", java.util.Map.of("image", imageId));
                            e.player().sendMessage("§a二维码生成成功！");
                        } catch (Exception ex) {
                            e.player().sendMessage("§c生成失败: " + ex.getMessage());
                        }
                    }).start();
                });

        w.addImage("qr_image", 40, 80, 200, 200, "");
        addCloseButton(w, 95, 290);
        api.openWindow(player, w);
    }

    private static byte[] downloadImage(String url) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .build();
        java.net.http.HttpResponse<byte[]> response = java.net.http.HttpClient.newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        return response.body();
    }

    // ------------------------------------------------------------- demo image

    /** Generates a 96x96 demo PNG in memory (gradient + circle + text). */
    private byte[] generateDemoImage() {
        try {
            BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(0x2E86DE), 96, 96, new Color(0x6C5CE7)));
            g.fillRect(0, 0, 96, 96);
            g.setColor(new Color(255, 215, 0, 230));
            g.fillOval(18, 14, 60, 60);
            g.setColor(new Color(0, 0, 0, 90));
            g.setStroke(new BasicStroke(3f));
            g.drawOval(18, 14, 60, 60);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            g.drawString("GUI", 30, 50);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g.drawString("GuiApi Demo", 16, 88);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            getLogger().warning("Failed to generate demo image: " + e.getMessage());
            return new byte[0];
        }
    }

    /** 32x32 enchanted-book card icon. */
    private byte[] generateBookIcon() {
        try {
            BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x7A3E9E));
            g.fillRoundRect(3, 2, 26, 28, 4, 4);
            g.setColor(new Color(0x5A2A78));
            g.fillRect(7, 2, 4, 28);
            g.setColor(new Color(0xF2E8D5));
            g.fillRect(12, 5, 15, 22);
            g.setColor(new Color(0xBFAE8A));
            g.drawRect(12, 5, 15, 22);
            g.setColor(new Color(0xD9B44A));
            g.fillRect(24, 14, 4, 6);
            g.setColor(new Color(0xE6D6FF));
            g.fillOval(15, 8, 4, 4);
            g.fillOval(20, 13, 3, 3);
            g.fillOval(17, 19, 3, 3);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            getLogger().warning("Failed to generate book icon: " + e.getMessage());
            return new byte[0];
        }
    }

    /** 16x16 gold star for the header. */
    private byte[] generateStarIcon() {
        try {
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int[] xs = {8, 10, 15, 10, 8, 6, 1, 6};
            int[] ys = {1, 6, 8, 10, 15, 10, 8, 6};
            Polygon star = new Polygon(xs, ys, 8);
            g.setColor(new Color(0xB8860B));
            g.fillPolygon(star);
            g.setColor(new Color(0xFFD700));
            g.setStroke(new BasicStroke(1f));
            g.drawPolygon(star);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            getLogger().warning("Failed to generate star icon: " + e.getMessage());
            return new byte[0];
        }
    }
}
