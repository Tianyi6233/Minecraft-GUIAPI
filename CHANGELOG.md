# 更新日志

## 客户端 1.3.16 / 服务端 1.6.5

### 新增

- **滚动条控件 `Scrollbar`**：`window.addScrollbar(id, x, y, w, h)`，上报 `0–100` 的数值。支持拖动滑块、点击轨道、在滚动条上滚轮；`thumb(百分比)` 控制滑块高度，`live(false)` 可改为只在松手时回调，也可自定义 `trackColor` / `thumbColor`。服务端在 `onChange` 里按 `offset = round(value / 100 * (总数 - 可视数))` 换算即可翻页
- **窗口级点击音 `GuiWindow.clickSound(soundId, volume)`**：点击音从全局改为「窗口优先、全局兜底」。传 `null` 可让单个窗口静音；没有调用过的窗口才回落到 `GuiApi.setClickSound(...)`

### 变更

- **下拉框支持自定义尺寸**：新增 `height(int)`（收起高度，默认 18）与 `rowHeight(int)`（展开行高，默认 16），字号调大时可以一起调高，默认值保持向后兼容
- **下拉框改为 MC 风格渲染**：展开列表使用深色面板 + 黑边 + 选中行高亮，文字位置按实际高度居中

### 兼容

- 协议版本仍为 `1`，新增属性全部为可选字段：旧客户端会忽略 `scrollbar` 与 `rowHeight`，不会报错
- `api.setClickSound(...)` 仍可用，行为不变；只是插件间互相影响的问题可以通过窗口级设置避开

## 客户端 1.3.15

### 变更

- 移除 `fabric.mod.json` 中的 `fabricloader` 版本下限（原为 `>=0.16.0`），客户端不再限制 Fabric Loader 版本
- 保留 `minecraft: ~26.1.2`、`java: >=25`、`fabric-api: *` 三项依赖不变
- 服务端 `gui-api-paper 1.6.4` 无需改动，协议未变

## 客户端 1.3.14 / 服务端 1.6.4

### 修复

- **窗口拖动拖尾**：控件位置改为与窗口同步平移，快速拖动、摇晃窗口时控件不再滞后于窗框，只有相对窗口的布局变化才会播放滑动动画
- **文字发虚**：窗口缩放或高 DPI 下，文字原点吸附到最近的设备像素；系统字体（`guiapi:sysfont`）的 oversample 由 2.0 提升到 4.0，放大缩小都保留更多采样余量
- **字体包自动重建**：oversample 计入字体缓存签名，旧字体包会在下次启动时自动重新生成，无需手动清理

### 兼容

- 网络协议未变，`gui-api-paper` 及所有依赖它的插件不需要改动
- 未升级客户端的玩家可以继续使用，不受影响

## 历史版本

更早的客户端 / 服务端 jar 统一放在 [历史版本存档](https://github.com/Tianyi6233/Minecraft-GUIAPI/releases/tag/legacy) 中，只用于回溯旧环境，不推荐在新服务器上使用。
