<p align="center"><img src="doc/logo.png" alt="GuiApi" width="200"></p>

<h1 align="center">GuiApi</h1>

<p align="center"><em>A Minecraft GUI framework that lets Paper plugins draw real windows inside the player's client: draggable glass windows, native widgets, live updates, images, sounds and runtime key bindings.</em></p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/static/v1?label=LICENSE&message=MIT&color=FF5C8A&labelColor=555555&style=for-the-badge" alt="License: MIT"></a>
  <img src="https://img.shields.io/static/v1?label=MINECRAFT&message=1.20.1%20~%2026.3&color=FF5C8A&labelColor=555555&style=for-the-badge" alt="Minecraft 1.20.1 ~ 26.3">
  <img src="https://img.shields.io/static/v1?label=SERVER&message=PAPER&color=FF5C8A&labelColor=555555&style=for-the-badge" alt="Server: Paper">
  <img src="https://img.shields.io/static/v1?label=CLIENT&message=FABRIC&color=FF5C8A&labelColor=555555&style=for-the-badge" alt="Client: Fabric">
  <img src="https://img.shields.io/static/v1?label=VERSION&message=1.3.16&color=FF5C8A&labelColor=555555&style=for-the-badge" alt="Client 1.3.16">
</p>

- - -

用 Bukkit API 写界面：Paper 插件下发一个窗口对象，玩家客户端里就出现一个可拖动的原生窗口，点击、滑块、勾选、下拉、输入全部回传到服务端主线程处理。

## 功能

- **窗口**：普通 / 全屏 / 多窗口叠放，背景、边框、标题栏配色全由服务端决定，支持拖动、遮罩、置顶、Esc 拦截
- **十一种控件**：文本、面板、按钮、复选框、单选框、滑块、滚动条、下拉框、输入框、图片、物品（含数量、附魔光效、tooltip）
- **服务端回调**：每个控件的回调都在主线程执行，可以直接调用 Bukkit API，不需要自己写线程调度
- **实时刷新**：窗口打开后可用 `updateWidget` 改文字、数值、选中项、选项列表、可见性
- **布局**：百分比布局、负边距、等比缩放、横向拖动组、位置补间动画，全屏界面适配不同分辨率
- **图片与音效**：服务端动态注册 PNG / OGG，客户端自动打包资源包，按 id 播放，支持循环 BGM 与音量调节
- **无内置快捷键**：按键由插件在运行时按玩家绑定，多个插件各占一键，断线重连自动恢复
- **字体**：可给窗口指定字体，`guiapi:sysfont` 使用玩家本机的 Windows 系统字体

> 细节全部在 [开发文档](开发文档.md)，分册版见 [doc/](doc/README.md)。

## 安装

| 组件 | 位置 | 说明 |
|---|---|---|
| `guiapi-client-1.3.16.jar` | `.minecraft/mods` | Fabric 客户端，另需 [Fabric API](https://modrinth.com/mod/fabric-api) |
| `gui-api-paper-1.6.5.jar` | 服务端 `plugins/` | 核心插件，其他 GUI 插件 `depend: [GuiApi]` |

**客户端按 Minecraft 版本各出一个 jar**（文件名相同，按版本目录区分）：

| Minecraft | 客户端 jar | Java |
|---|---|---|
| 1.20.1 | `versions/1.20.1/build/libs/guiapi-client-1.3.16.jar` | 17+ |
| 1.21 / 1.21.1 | `versions/1.21/`、`versions/1.21.1/` | 21+ |
| 1.21.4 | `versions/1.21.4/` | 21+ |
| 1.21.5 | `versions/1.21.5/` | 21+ |
| 1.21.8 | `versions/1.21.8/` | 21+ |
| 1.21.9 / 1.21.10 / 1.21.11 | `versions/1.21.9/`、`versions/1.21.10/`、`versions/1.21.11/` | 21+ |
| 26.1 / 26.1.1 | `versions/26.1/`、`versions/26.1.1/` | 25+ |
| 26.1.2 | `build/libs/guiapi-client-1.3.16.jar` | 25+ |
| 26.2 | `versions/26.2/` | 25+ |
| 26.3 | `versions/26.3/` | 25+ |

**服务端只有一个 jar**：编译目标是 Paper 1.20.1 的 API、Java 17 字节码，`api-version: '1.20'`，所以同一个 `gui-api-paper-1.6.5.jar` 可以直接跑在 **Paper 1.20.1 到最新版** 上（Paper 官方 1.20.1+ 都接受这个 api-version）。Fabric Loader 不限版本。玩家没装客户端模组时，服务端会通过握手检测到，可以提示或拒绝。

## 快速开始

```java
public final class ShopPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        GuiApi api = GuiApi.get();
        api.addKeyHandler("shop", player -> {
            GuiWindow window = new GuiWindow("shop")
                    .title("商店")
                    .size(220, 168);

            window.addLabel("balance", 12, 10, "余额：100")
                    .color("#FFD700");
            window.addButton("buy", 12, 44, 196, 26, "买一颗钻石")
                    .onClick(e -> {
                        e.player().getInventory().addItem(new ItemStack(Material.DIAMOND));
                    });

            api.openWindow(player, window);
        });
    }
}
```

绑定按键 `K`（GLFW 键码 75）：

```java
api.bindKey(player, "shop", 75);
```

仓库内的 `gui-demo-paper`、`gui-kittens-paper`、`gui-auction-paper`、`gui-menu-paper`、`gui-poster-paper`、`gui-reward-paper`、`gui-admin-paper` 都是可直接跑的示例插件。想快速体验，可以直接下载 Release 里的 `gui-demo-paper-1.0.0.jar` 丢进 `plugins/`，进服按 `N` 打开演示列表（含滚动条、下拉、物品、MC 风格等 11 个子演示）。

## 构建

需要 JDK 25（服务端与客户端都是）。

```bat
:: 服务端插件（含依赖）
mvn -pl gui-api-paper -am package

:: 客户端模组
cd gui-client-fabric
gradlew build
```

产物：`gui-api-paper/target/gui-api-paper-1.6.5.jar`（一个 jar 兼容 Paper 1.20.1+）、`gui-client-fabric/build/libs/guiapi-client-1.3.16.jar`（26.1.2 客户端）。

其他 Minecraft 版本的客户端各自独立构建：

```bash
cd gui-client-fabric
gradlew -p versions/1.20.1 build     # 1.20.1
gradlew -p versions/1.21 build       # 1.21
gradlew -p versions/1.21.1 build     # 1.21.1
gradlew -p versions/1.21.4 build     # 1.21.4
gradlew -p versions/1.21.5 build     # 1.21.5
gradlew -p versions/1.21.8 build     # 1.21.8
gradlew -p versions/1.21.9 build     # 1.21.9
gradlew -p versions/1.21.10 build    # 1.21.10
gradlew -p versions/1.21.11 build    # 1.21.11
gradlew -p versions/26.1 build       # 26.1
gradlew -p versions/26.1.1 build     # 26.1.1
gradlew -p versions/26.2 build       # 26.2
gradlew -p versions/26.3 build       # 26.3
```

<details>
<summary>协议与兼容说明</summary>

- 传输走 `guiapi:main` 插件消息通道，超过 16 KiB 的消息自动分片重组，单条上限 4 MiB
- 客户端连接后先发 `ready` 握手（携带模组版本），服务端据此判断是否支持、并自动补发按键绑定与图片缓存
- 协议版本固定为 `1`，新增能力全部以可选字段下发：旧版客户端会忽略不认识的属性，不会报错
- 需要客户端版本差异时用 `api.clientVersion(player)` 判断，未上报版本的旧客户端返回 `null`

</details>

## 说明

使用前请确认客户端模组与服务端的 Minecraft 版本匹配（客户端按版本分包，服务端单包通吃 1.20.1+）；协议版本固定为 `1`，所有版本共用同一套 JSON 字段。26.1.2 为无混淆版本，服务端与客户端都使用官方 `net.minecraft.*` 类名。

第三方：Paper API、Fabric Loader / Fabric API、Gson。

许可证：[MIT](LICENSE)
