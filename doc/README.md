# GuiApi 使用文档

GuiApi 是一套「服务端画界面、客户端来渲染」的 Minecraft GUI 框架。你在 Paper 插件里用普通 Java 代码拼装窗口与控件，GuiApi 把界面描述发给玩家客户端，由 **GuiApi 客户端模组**在游戏内绘制成可点击的原生覆盖层。

- 不需要资源包，不需要改客户端代码，不需要额外端口。
- 界面是覆盖在游戏画面上的独立窗口，不是箱子/物品栏界面。
- 只有装了 GuiApi 客户端模组的玩家能看到界面，服务端用 `GuiApi.get().isClientSupported(player)` 判断。
- 位置与尺寸用**客户端虚拟像素**（设计基准 640×430）。虚拟画布 = 屏幕逻辑尺寸 ÷ 统一缩放，屏幕够大时等于屏幕逻辑尺寸（例如 1280×720），所以**固定像素坐标在不同分辨率下物理大小不同**；要跨分辨率一致，请用百分比 + `aspect`。

本文档面向只拿到 `gui-api-paper` 插件 jar 与 `guiapi-client` 模组、没有 GuiApi 源码的第三方插件开发者。文中方法名、参数顺序均与 `com.guiapi:gui-api-paper:1.6.4` 的实际公开 API 一致，示例可直接编译。

---

## 文档索引

| 章节 | 内容 | 适合什么时候读 |
| --- | --- | --- |
| [01-快速开始](./01-快速开始.md) | 从零到第一个可点击窗口：工程、依赖、plugin.yml、主类、部署、排错 | 第一次接入 GuiApi |
| [02-窗口与坐标](./02-窗口与坐标.md) | GuiWindow 全部属性、虚拟画布、内容区、居中、像素与百分比混用、等高等比换算 | 开始设计布局之前 |
| [03-基础控件](./03-基础控件.md) | Widget 基类（坐标、百分比、等比、拖动组、高亮、动画）+ Panel、Label、Button、ImageWidget | 搭静态界面 |
| [04-表单控件](./04-表单控件.md) | TextField、Slider、Scrollbar、Checkbox、Radio、Dropdown、ItemWidget | 做可输入、可选择的界面 |
| [05-事件与交互](./05-事件与交互.md) | 事件类型、GuiActionEvent、局部刷新、关闭回调、多窗口协作 | 让界面真正能用 |
| [06-图片与音效](./06-图片与音效.md) | registerImage / preloadImage / registerSound / playSound / 循环音效 / 窗口级点击音 | 做卡牌、图标、背景图和点击音 |
| [07-按键与客户端能力](./07-按键与客户端能力.md) | 快捷键绑定、按键回调、客户端版本判断、runCommand | 做快捷键呼出的界面 |
| [08-布局技巧与常见问题](./08-布局技巧与常见问题.md) | 响应式套路、控件尺寸速查、踩坑清单 | 布局开始变复杂时 |
| [09-实战案例](./09-实战案例.md) | 从零写一个完整功能界面（列表 + 表单 + 刷新） | 写第一个正式界面 |

---

## 能力总览

| 能力 | 说明 | 主要 API |
| --- | --- | --- |
| 窗口 | 标题栏、拖动、关闭按钮、全屏、置顶、遮罩、样式、字体 | `GuiWindow`、`GuiApi.openWindow` |
| 基础控件 | 文本、按钮、面板（背景/卡片/可点击区域）、图片、原版物品 | `addLabel`、`addButton`、`addPanel`、`addImage`、`addItem` |
| 表单控件 | 输入框、滑块、滚动条、复选框、单选框、下拉列表 | `addTextField`、`addSlider`、`addScrollbar`、`addCheckbox`、`addRadio`、`addDropdown` |
| 布局 | 绝对像素、百分比、宽高比锁定、拖动分组、悬停抬起、发光、位移动画 | `position`、`size`、`xPercent`、`aspect`、`dragGroup`、`glow` |
| 事件 | 点击、数值改变、勾选、选择、文本提交、Esc、窗口关闭 | `onClick`、`onChange`、`onToggle`、`onSelect`、`onEscape` |
| 局部刷新 | 只改文字/数值/可见性，不重建窗口 | `GuiApi.updateWidget` |
| 图片 | 服务端注册 PNG，按需下发，控件按 id 引用 | `registerImage`、`preloadImage`、`sendImage` |
| 音效 | 服务端注册 OGG，客户端播放，支持循环与音量 | `registerSound`、`playSound`、`playLoopingSound`、`stopSound` |
| 快捷键 | 服务端绑定 GLFW 键码与回调，重连后自动重发 | `addKeyHandler`、`bindKey` |
| 客户端能力 | 是否装了模组、模组版本、让客户端执行命令 | `isClientSupported`、`clientVersion`、`runCommand` |

---

## 安装与依赖

### 服务端

1. 一台 Paper 服务端，**1.20.1 到最新版都行**（`api-version` 为 `1.20`）。
2. 把 `gui-api-paper-1.6.5.jar` 放进 `plugins/`。
3. 重启服务端，控制台出现 `GuiApi ready on channel guiapi:main` 即表示就绪。

### 客户端

每个要看到界面的玩家都需要：

1. 安装 Fabric API，Fabric Loader 版本不限。
2. 把与游戏版本对应的 `guiapi-client` 模组 jar 放进 `mods/`（模组 id 为 `guiapi`）。支持 1.20.1 / 1.21 / 1.21.1 / 1.21.4 / 1.21.5 / 1.21.8 / 1.21.9 / 1.21.10 / 1.21.11 / 26.1 / 26.1.1 / 26.1.2 / 26.2 / 26.3，分别构建于 `gui-client-fabric/versions/<版本>/` 与 `gui-client-fabric/build/libs/`。
3. 进入服务器后客户端会自动握手，服务端据此把该玩家标记为「支持 GuiApi」。

### 开发者（你自己的插件）

```xml
<dependency>
    <groupId>com.guiapi</groupId>
    <artifactId>gui-api-paper</artifactId>
    <version>1.6.4</version>
    <scope>provided</scope>
</dependency>
```

- `scope` 必须是 `provided`：GuiApi 的类由服务端的 GuiApi 插件提供，**不要**打进你自己的 jar，否则会与插件里的类冲突。
- 依赖不在你的公共仓库里时，先把 jar 装进本地仓库：
  `mvn install:install-file -Dfile=gui-api-paper-1.6.4.jar -DgroupId=com.guiapi -DartifactId=gui-api-paper -Dversion=1.6.4 -Dpackaging=jar`
- `plugin.yml` 必须声明依赖，否则 `GuiApi.get()` 可能在 GuiApi 启用前被调用：

```yaml
depend: [GuiApi]
```

完整工程文件（`pom.xml`、`plugin.yml`、主类、构建与部署）见 [01-快速开始](./01-快速开始.md)。

---

## API 速查

入口是单例 `GuiApi.get()`。GuiApi 插件未启用时它会抛 `IllegalStateException("GuiApi plugin is not enabled yet")`，所以只在 `onEnable()` 及之后调用，不要写在静态初始化块里。

| 方法 | 作用 | 注意 |
| --- | --- | --- |
| `GuiApi.get()` | 取得 API 实例 | 需 GuiApi 插件已启用；`plugin.yml` 写 `depend: [GuiApi]` |
| `openWindow(Player, GuiWindow)` | 打开窗口，或替换同 id 窗口 | 同一玩家同 id 只保留一个；内容整体重置；玩家拖动过的位置会保留 |
| `closeWindow(Player, String windowId)` | 关闭一个窗口 | 传 `null` 关闭该玩家全部窗口 |
| `updateWidget(Player, String windowId, String widgetId, Map<String,Object>)` | 更新已显示控件的属性 | 不能增删控件；窗口未打开时更新被丢弃；结构变化请重新 `openWindow` |
| `windowsOf(Player)` | 该玩家当前打开的窗口 | 只读用途，不要修改返回的 Map |
| `onWindowClosed(String windowId, Consumer<GuiWindow>)` | 玩家在客户端关掉窗口时的回调 | 按 windowId 注册，对所有玩家生效 |
| `isClientSupported(Player)` | 客户端是否装了模组并完成握手 | 开界面前必须检查 |
| `clientVersion(Player)` | 客户端模组版本，如 `"1.3.12"` | 旧客户端返回 `null` |
| `registerImage(String, byte[])` | 注册图片（PNG/JPEG 字节） | 同名重复注册会递增版本；下次 `openWindow` 时才会补发新图 |
| `preloadImage(Player, String)` | 预下发图片 | 适合稍后通过 `updateWidget` 才出现的图片 |
| `registerSound(String, byte[])` | 注册音效（OGG 字节） | 客户端会把它放进自动生成的资源包 |
| `playSound(Player, String)` / `playSound(Player, String, float, float)` | 播放一次音效 | 后两个参数是音量、音高 |
| `playLoopingSound(Player, String, float)` / `stopSound` / `stopAllSounds` | 循环播放与停止 | 只影响循环音效 |
| `setClickSound(String, float)` | 设置**全局默认**点击音（所有插件共享） | 推荐改用 `GuiWindow.clickSound(...)` 做窗口级隔离 |
| `addKeyHandler(String, Consumer<Player>)` / `bindKey(Player, String, int)` | 注册快捷键回调并绑定 GLFW 键码 | 键码 `<= 0` 表示解绑；重连后自动重发 |
| `runCommand(Player, String)` | 关掉全部 GuiApi 窗口，再让客户端像玩家手打一样执行命令 | 命令前的 `/` 可省略 |

---

## 最小可运行示例

把下面的类和 `pom.xml`、`plugin.yml` 一起打包，装好后执行 `/gui` 即可看到第一个可点击窗口。工程细节见 [01-快速开始](./01-快速开始.md)。

```java
package com.example.mygui;

import com.guiapi.paper.GuiApi;
import com.guiapi.paper.GuiWindow;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class MyGuiPlugin extends JavaPlugin {
    private GuiApi api;

    @Override
    public void onEnable() {
        api = GuiApi.get();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!api.isClientSupported(player)) {
            player.sendMessage("§c请安装 GuiApi 客户端模组");
            return true;
        }

        GuiWindow win = new GuiWindow("demo_home").title("Hello GuiApi").size(260, 160);
        win.addLabel("msg", 16, 12, "点击下面的按钮").color("#FFD166");
        win.addButton("ok", 16, 36, 228, 22, "点我").onClick(event -> {
            event.player().sendMessage("§a你好，" + event.player().getName());
            api.updateWidget(event.player(), "demo_home", "msg", Map.of("text", "按钮已点击"));
        });
        win.addButton("close", 16, 108, 228, 22, "关闭")
                .onClick(event -> api.closeWindow(event.player(), "demo_home"));

        api.openWindow(player, win);
        return true;
    }
}
```

对应的 `plugin.yml` 至少要有：

```yaml
name: MyGuiPlugin
version: 1.0.0
main: com.example.mygui.MyGuiPlugin
api-version: '1.20'
depend: [GuiApi]
commands:
  gui:
    description: 打开 GuiApi 示例窗口
```

---

## 学习路线

1. **跑通第一个窗口**：按 [01-快速开始](./01-快速开始.md) 建工程、写 `plugin.yml`、装插件与模组，确认 `/gui` 能弹出窗口、按钮能点。
2. **学会摆位置**：读 [02-窗口与坐标](./02-窗口与坐标.md)，弄清「窗口 → 内容区 → 控件」三层坐标和百分比规则，再做静态界面（[03-基础控件](./03-基础控件.md)）。
3. **加上输入与事件**：用 [04-表单控件](./04-表单控件.md) 与 [05-事件与交互](./05-事件与交互.md) 做可交互界面，并区分「整体重建」与「`updateWidget` 局部刷新」。
4. **补齐表现力**：[06-图片与音效](./06-图片与音效.md)、[07-按键与客户端能力](./07-按键与客户端能力.md)。
5. **照着案例写**：先扫 [08-布局技巧与常见问题](./08-布局技巧与常见问题.md)，再按 [09-实战案例](./09-实战案例.md) 写完整界面。

---

## 公共约定

| 约定 | 说明 |
| --- | --- |
| 坐标单位 | 虚拟像素。控件坐标相对**窗口内容区**（标题栏下方）左上角 |
| 窗口默认尺寸 | 260×160；内容区高度 = 窗口高 - 标题栏高（默认 16px） |
| `x = -1, y = -1` | 窗口居中；任何负数都按居中处理，只有 `>= 0` 才是绝对坐标 |
| 颜色 | `"#RRGGBB"`（自动补不透明）、`"#AARRGGBB"`，或 int ARGB，如 `0xF018181C` |
| id | 窗口 id 在每个玩家范围内唯一；控件 id 在窗口内唯一，同 id 会覆盖旧控件 |
| 刷新策略 | 增删控件/换布局 → 重新 `openWindow`；只改文字/数值/可见性 → `updateWidget` |
| 事件线程 | 所有监听器都在服务端主线程执行，可直接操作 Bukkit API |
| 生命周期 | 窗口是会话级的：玩家退出或关闭后不会自动恢复，需要自己在 `PlayerJoinEvent` 里重新打开 |

---

## 版本与兼容

| 项目 | 要求 |
| --- | --- |
| GuiApi 服务端插件 | `com.guiapi:gui-api-paper:1.6.5`（`plugin.yml` 中 `api-version: '1.20'`） |
| 服务端 | Paper 1.20.1 ~ 最新版，Java 17+ |
| 客户端模组 | 模组 id `guiapi`，按游戏版本选择对应构建（1.20.1 / 1.21 / 1.21.1 / 1.21.4 / 1.21.5 / 1.21.8 / 1.21.9 / 1.21.10 / 1.21.11 / 26.1 / 26.1.1 / 26.1.2 / 26.2 / 26.3），不限 Fabric Loader 版本，需 Fabric API |
| 最低客户端 | `isClientSupported` 只判断「装没装」；要限制版本用 `clientVersion` 自己比较（见 07 章） |

服务端一个 jar 通吃 Paper 1.20.1+，不需要按版本分包；客户端模组必须与游戏版本对应，装错版本会在 Fabric 加载阶段被拒绝。

---

## 常见问题入口

- 窗口打不开、控制台报 `ClassNotFoundException`：见 [01-快速开始](./01-快速开始.md) 排错表。
- 控件看不见、位置不对、百分比算不明白：见 [02-窗口与坐标](./02-窗口与坐标.md) 的「常见坑」。
- 点击没反应、刷新无效：见 [05-事件与交互](./05-事件与交互.md)。
- 布局复杂后的综合问题：见 [08-布局技巧与常见问题](./08-布局技巧与常见问题.md)。
