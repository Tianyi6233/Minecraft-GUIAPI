# 更新日志

## 客户端 1.6.0 / 服务端 1.7.0

### 新增

- **可改键的快捷键 API（客户端）**：`GuiApiKeybinds.register(id, label, defaultKey[, action])`。注册后快捷键出现在原版「选项 → 控制 → 按键绑定 → GuiApi」分类，玩家可自行改键、原版会提示冲突，设置写入 `options.txt`。`defaultKey` 传 `GLFW.GLFW_KEY_*` 为默认键，传 `null` 为默认不绑定；同一 `id` 重复注册返回已有绑定
- **通用纵向滚动（服务端）**：`Widget.verticalDragGroup(group)` 把控件加入滚动组，`GuiWindow.addVerticalViewport(group, y, height)` 声明可视区域并返回对应的 `Scrollbar`，不新增协议字段

### 兼容

- 原有的 `bindKey` 隐形快捷键完全保留，行为不变；两套快捷键可同时使用
- 服务端 `GuiApi` 公开方法签名无变化，旧插件无需重新编译

## 客户端 1.5.1

### 修复

- **输入法冲突模组下无法输入中文**：输入框改为通过 `Minecraft.onTextInputFocusChange` 注册文本输入焦点，并实现 `preeditUpdated` 与 `IMEPreeditOverlay`，拼音预编辑串正常显示
- **Ctrl+A/C/V/X 无效**：改用原版的 `isSelectAll/isCopy/isPaste/isCut` 判定，并在 `charTyped` 里兜底控制字符，避免部分输入法吞掉组合键

## 客户端 1.5.0

### 新增

- 输入框快捷键：`Ctrl+C` 复制、`Ctrl+V` 粘贴、`Ctrl+X` 剪切、`Ctrl+A` 全选、`Del` 删除所选、`Shift+←/→` 选中；macOS 使用 `Command` 键
- 输入框支持选区高亮与替换输入，粘贴时自动过滤控制字符

## 客户端 1.4.3

### 修复

- **跨服务器状态残留**：切换服务器或重连时统一执行 `resetSession()`，清理窗口、动态图片纹理、音效、分片缓存与快捷键，避免正式服商品较多时沿用上一个服务器的布局导致溢出

## 客户端 1.4.2

### 修复

- 纵向滚动区域的鼠标滚轮方向反了，改为与拖动方向一致

## 客户端 1.4.1 / 交易所 1.2.1

### 修复

- **滚动卡顿**：商品查找改为哈希索引，滚动时不再每帧遍历全部控件
- **翻页误触购买**：裁剪区外的控件不再响应点击，上下页按钮不会穿透到商品行

## 客户端 1.4.0 / 交易所 1.2.0

### 新增

- 通用纵向滚动：`verticalDragGroup` + `addVerticalViewport`，支持拖动、滚轮、140ms 缓动与 scissor 裁剪

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
