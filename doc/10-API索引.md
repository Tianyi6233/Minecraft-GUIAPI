﻿﻿﻿# 10 - API 索引

> 本页是 `gui-api-paper 1.7.0` 与客户端模组 `1.6.0` **全部公开方法**的速查表。
> 只要方法出现在这里，就一定能被第三方插件/模组调用；没出现的都是内部实现。
> 用法与示例见对应章节，本页只解决「有没有这个方法、参数是什么」。

---

## 1. 服务端入口 `GuiApi`

| 签名 | 作用 | 详见 |
|---|---|---|
| `static GuiApi get()` | 获取单例；插件未启用时抛 `IllegalStateException` | 01 |
| `void openWindow(Player, GuiWindow)` | 推送并打开窗口，自动补发引用到的图片/音效 | 02 |
| `void closeWindow(Player, String windowId)` | 关闭指定窗口；`windowId` 传 `null` 关闭该玩家全部窗口 | 02 |
| `void updateWidget(Player, String windowId, String widgetId, Map<String,Object>)` | 局部刷新控件属性，不能增删控件 | 05 |
| `void registerImage(String, byte[])` | 注册 PNG 图片；同名重复注册会递增版本 | 06 |
| `void preloadImage(Player, String)` | 提前把图片下发给某个玩家 | 06 |
| `void sendImage(Player, String)` | 立即下发已注册图片 | 06 |
| `void registerSound(String, byte[])` | 注册 OGG 音效 | 06 |
| `void preloadSound(Player, String)` | 提前下发音效 | 06 |
| `void playSound(Player, String)` | 以默认音量/音高播放一次 | 06 |
| `void playSound(Player, String, float volume, float pitch)` | 指定音量、音高播放一次 | 06 |
| `void playLoopingSound(Player, String, float volume)` | 循环播放 | 06 |
| `void setLoopingSoundVolume(Player, String, float volume)` | 调整正在循环的音量 | 06 |
| `void stopSound(Player, String)` | 停止指定循环音效 | 06 |
| `void stopAllSounds(Player)` | 停止该玩家全部循环音效 | 06 |
| `void setClickSound(String soundId, float volume)` | 设置全局点击音；窗口可用 `clickSound` 覆盖 | 06 |
| `void runCommand(Player, String)` | 关闭所有窗口后让客户端执行一条命令 | 07 |
| `void addKeyHandler(String keyId, Consumer<Player>)` | 注册按键回调（服务端绑定键、客户端上报都走这里） | 07 |
| `void removeKeyHandler(String keyId)` | 移除单个按键回调 | 07 |
| `void clearKeyHandlers()` | 清空所有按键回调 | 07 |
| `void bindKey(Player, String keyId, int glfwKey)` | 向客户端下发一个**不显示**在按键设置里的快捷键 | 07 |
| `void unbindKey(Player, String keyId)` | 解除某个快捷键 | 07 |
| `void clearKeys(Player)` | 清空该玩家全部快捷键 | 07 |
| `boolean isClientSupported(Player)` | 客户端是否装了模组并完成握手 | 01 |
| `String clientVersion(Player)` | 客户端模组版本；未上报返回 `null` | 07 |
| `void onWindowClosed(String windowId, Consumer<GuiWindow>)` | 玩家在客户端关闭窗口时的回调 | 05 |
| `Map<String,GuiWindow> windowsOf(Player)` | 该玩家当前打开的窗口（只读） | 02 |

---

## 2. 窗口 `GuiWindow`

### 2.1 链式配置

| 方法 | 默认值 / 说明 |
|---|---|
| `new GuiWindow(String id)` | 窗口 id，按玩家维度唯一 |
| `title(String)` | 标题文字，默认空 |
| `position(int x, int y)` | 左上角，默认 `-1,-1` 居中 |
| `size(int w, int h)` | 默认 `260 x 160` |
| `draggable(boolean)` | 默认 `true` |
| `fullscreen(boolean)` | 默认 `false`；为 `true` 时忽略 size/position/draggable |
| `backgroundColor(String hex)` / `(int argb)` | 窗口底色 |
| `borderColor(String hex)` / `(int argb)` | 边框颜色 |
| `borderWidth(int)` | 默认 `1`，`0` = 无边框 |
| `titleBarColor(String hex)` / `(int argb)` | 标题栏底色 |
| `titleColor(String hex)` / `(int argb)` | 标题文字颜色 |
| `titleBar(boolean)` | 是否显示内置标题栏，默认 `true` |
| `titleHeight(int)` | 默认 `16` |
| `dim(int alpha)` | 窗口后方遮罩 0~255，默认 `0` |
| `alwaysOnTop(boolean)` | 始终压制其他窗口，默认 `false` |
| `closeOnEsc(boolean)` | 默认 `true`；`false` 时 Esc 改为发送 `esc` 事件 |
| `onEscape(Consumer<GuiActionEvent>)` | 接收 `esc` 事件 |
| `style(String)` | `default` / `minecraft` |
| `font(String)` | 字体 id，如 `guiapi:sysfont`；空串 = 原版像素字体 |
| `clickSound(String soundId, float volume)` | 本窗口点击音；传 `null` = 静音 |

### 2.2 添加控件

| 方法 | 返回 |
|---|---|
| `addLabel(id, x, y, text)` | `Label` |
| `addImage(id, x, y, w, h, imageId)` | `ImageWidget` |
| `addItem(id, x, y, w, h, String material)` / `(…, org.bukkit.Material)` | `ItemWidget` |
| `addPanel(id, x, y, w, h)` | `Panel` |
| `addTextField(id, x, y, w, h)` | `TextField` |
| `addButton(id, x, y, w, h, text)` | `Button` |
| `addSlider(id, x, y, width)` | `Slider` |
| `addScrollbar(id, x, y, w, h)` | `Scrollbar` |
| `addVerticalViewport(group, y, height)` | 视口组对应的 `Scrollbar`；组内控件用 `verticalDragGroup(group)` 加入 |
| `addCheckbox(id, x, y, label, checked)` | `Checkbox` |
| `addRadio(id, x, y, label, group, checked)` | `Radio` |
| `addDropdown(id, x, y, w, List<String> options)` | `Dropdown` |
| `add(Widget)` | 添加自定义控件实例 |
| `widget(String id)` | 按 id 取控件，取不到返回 `null` |
| `widgets()` | 只读控件集合 |

### 2.3 读取当前值

`id()`、`title()`、`posX()`、`posY()`、`width()`、`height()`、`draggable()`、`fullscreen()`、`alwaysOnTop()`、`closeOnEsc()`、`escHandler()`、`style()`、`font()`、`clickSoundSet()`、`clickSound()`、`clickSoundVolume()`

---

## 3. 控件基类 `Widget`

所有控件都继承 `Widget`，下面方法全部可用：

| 方法 | 说明 |
|---|---|
| `position(int x, int y)` | 修改坐标 |
| `size(int w, int h)` | 修改尺寸 |
| `tooltip(String)` | 悬浮提示，支持 `\n`；行首 `#RRGGBB\|` 指定该行颜色 |
| `visible(boolean)` | 隐藏/显示；隐藏后不绘制、不响应点击 |
| `xPercent(double)` / `yPercent(double)` | 按窗口内容区百分比定位 |
| `widthPercent(double)` / `heightPercent(double)` | 按内容区百分比设尺寸 |
| `aspect(double wOverH)` | 按宽高比推导尺寸 |
| `dragGroup(String)` | 加入拖动组，组内任一控件拖动整组 |
| `verticalDragGroup(String)` | 加入纵向滚动组（配合 `addVerticalViewport`） |
| `glow(String hex)` | 外发光颜色 |
| `hoverLift(boolean)` | 悬停时轻微上浮 |
| `anim(int ms)` / `anim(int ms, String ease)` | 位置/尺寸变化补间 |
| `id()`、`x()`、`y()`、`width()`、`height()`、`tooltip()` | 读取当前值 |

> `position/size/tooltip/visible/...` 返回 `Widget`，链式调用时先写子类专属方法，再写这些基类方法。

---

## 4. 各控件专属方法

### 4.1 `Label`

`text()`、`color()`、`centered()`、`scale()`、`text(String)`、`color(String hex)`、`color(int argb)`、`centered(boolean)`、`scale(float)`

### 4.2 `Panel`

`backgroundColor(String/int)`、`borderColor(String/int)`、`borderWidth(int)`、`radius(int)`、`hoverColor(String/int)`、`gradient(String topHex, String bottomHex)`、`onClick(Consumer<GuiActionEvent>)`、`clickHandler()`

### 4.3 `Button`

`text()`、`text(String)`、`backgroundColor(String/int)`、`hoverColor(String/int)`、`textColor(String/int)`、`radius(int)`、`onClick(Consumer<GuiActionEvent>)`、`clickHandler()`

### 4.4 `ImageWidget`

`imageId()`、`image(String imageId)`、`fit(String)`（`stretch` / `cover` / `contain`）、`onClick(Consumer<GuiActionEvent>)`、`clickHandler()`

### 4.5 `TextField`

`text()`、`text(String)`、`placeholder(String)`、`maxLength(int)`、`backgroundColor(String/int)`、`borderColor(String/int)`、`textColor(String/int)`、`live(boolean)`、`live()`、`onChange(Consumer<GuiActionEvent>)`、`changeHandler()`

### 4.6 `Slider`

`min()`、`max()`、`step()`、`value()`、`label()`、`min(double)`、`max(double)`、`step(double)`、`value(double)`、`label(String)`、`onChange(Consumer<GuiActionEvent>)`、`changeHandler()`

### 4.7 `Scrollbar`

`value()`、`thumb()`、`live()`、`value(double)`、`thumb(double)`、`live(boolean)`、`trackColor(String/int)`、`thumbColor(String/int)`、`onChange(Consumer<GuiActionEvent>)`、`changeHandler()`

### 4.8 `Checkbox`

`label()`、`checked()`、`label(String)`、`checked(boolean)`、`onToggle(Consumer<GuiActionEvent>)`、`toggleHandler()`

### 4.9 `Radio`

`label()`、`group()`、`checked()`、`label(String)`、`group(String)`、`checked(boolean)`、`onToggle(Consumer<GuiActionEvent>)`、`toggleHandler()`

### 4.10 `Dropdown`

`options()`、`selected()`、`rowHeight()`、`height(int)`、`rowHeight(int)`、`options(List<String>)`、`selected(int)`、`onSelect(Consumer<GuiActionEvent>)`、`selectHandler()`

### 4.11 `ItemWidget`

`material()`、`material(String)`、`material(org.bukkit.Material)`、`amount()`、`amount(int)`、`displayName()`、`displayName(String)`、`nameKey()`、`nameKey(String)`、`lore()`、`lore(List<String>)`、`enchants()`、`enchants(List<String>)`、`enchanted()`、`enchanted(boolean)`、`customModelData()`、`customModelData(Integer)`、`itemModel()`、`itemModel(String)`、`from(org.bukkit.inventory.ItemStack)`、`onClick(Consumer<GuiActionEvent>)`、`clickHandler()`

---

## 5. 事件对象 `GuiActionEvent`

| 方法 | 说明 |
|---|---|
| `player()` | 触发事件的玩家 |
| `window()` | 事件所属窗口 |
| `widget()` | 触发事件的控件 |
| `action()` | `click` / `change` / `toggle` / `select` / `text` / `esc` / `close` / `key` |
| `rawValue()` | 原始 `JsonElement`，可能为 `null` |
| `valueAsDouble()` / `valueAsInt()` | 数值读取，`null` 返回 `0` |
| `valueAsBoolean()` | 布尔读取，`null` 返回 `false` |
| `valueAsString()` | 字符串读取，`null` 返回 `""` |

---

## 6. 客户端 API（第三方客户端模组）

包名：`com.guiapi.client.api`

| 方法 | 作用 |
|---|---|
| `GuiApiKeybinds.register(String id, String label, Integer defaultKey)` | 注册一个出现在原版「按键设置」的快捷键，自己轮询 `KeyMapping.consumeClick()` |
| `GuiApiKeybinds.register(String id, String label, Integer defaultKey, Runnable action)` | 同上，并让 GuiApi 在按键按下时自动执行 `action`（仅在无界面打开时） |
| `GuiApiKeybinds.get(String id)` | 按 id 取已注册的 `KeyMapping` |
| `GuiApiKeybinds.tick(Minecraft)` | 内部使用，GuiApi 已在 `END_CLIENT_TICK` 中调用，第三方不要重复调用 |

> `defaultKey` 传 `null` = 默认不绑定；传 `GLFW.GLFW_KEY_*` = 默认键。必须在 `onInitializeClient()` 期间注册，否则本次启动不会出现在按键设置界面。
> 详细说明见 [更新方法日志/快捷键绑定新方法.md](./更新方法日志/快捷键绑定新方法.md)。

---

## 7. 版本与兼容速查

| 项目 | 当前版本 |
|---|---|
| 服务端 `gui-api-paper` | 1.7.0 |
| 客户端 `guiapi-client` | 1.6.0 |
| 交易所示例 `gui-auction-paper` | 1.2.2 |
| 演示插件 `gui-demo-paper` | 1.0.0 |

- 新增方法只增不减，旧方法签名保持不变；第三方按本文档调用的代码不会因小版本升级而失效。
- 服务端 API 与客户端模组版本解耦：`bindKey` 等旧能力对旧客户端仍可用，`GuiApiKeybinds` 只存在于 1.6.0+ 客户端。
