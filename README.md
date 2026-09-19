# Mouse Scroll Fix — Minecraft 1.20.1 / Forge 47.4.21

让鼠标滚轮「转一格 = 切一格」，不再受 KDE 的「滚动速度」设置影响。
换成原生 Wayland 后带来的鼠标指针问题（箭头变成系统默认样式）也由 mod 一并修好。

- 产物：`build/libs/mousescrollfix-1.1.0.jar`
- 纯客户端 mod，不会影响联机（`clientSideOnly=true`）
- 已在干净环境（无其他 mod 的开发客户端）实测通过，未装进任何整合包

---

## 一、问题到底是什么

不是 Minecraft 的 bug，也不是滚轮坏了，而是**两条链路叠加**的结果。

你在 KDE 里给每个鼠标设备设了一个「滚动速度」，它存在 `~/.config/kcminputrc`：

| 设备 | ScrollFactor |
|---|---|
| Rapoo Rapoo Gaming Device（你唯一在用的鼠标） | **1.5** |
| ITE Tech. Inc. ITE Device(8176) Keyboard | 0.75 |
| RDMCTMZT Wireless 2.4G Dongle Mouse（已坏） | 0.1 |

这个倍数的作用方式和一般人想的不一样。**实测**（用 `/dev/uinput` 造虚拟鼠标，
让 KWin 像处理真鼠标一样处理它，再数 Minecraft 真正收到几个滚轮事件）：

### 游戏跑在 X11 / XWayland 时（你现在的状态）

| 设备倍数 | 转 12 格，游戏实际收到 | 每个事件的值 |
|---|---|---|
| 1.0（默认） | 12 个事件 | 全部 `+1.0` |
| **1.5（你的 Rapoo）** | **18 个事件** | 全部 `+1.0` |
| 0.75 | **9 个事件** | 全部 `+1.0` |
| 0.1 | **1 个事件** | `+1.0` |

关键点：**事件的值永远是 ±1.0，KWin 改的是事件的「个数」。**
0.75 那组的到达节奏是 `200 200 401 200 200 400…`（毫秒）——
连发 3 格全部生效，第 4 格被整格吞掉。

而原版 Minecraft 是「一个事件 = 切一格」，所以：

- 倍数 1.5 → 每格切 1.5 格 → **有时连切两格，看起来像跳过物品**
- 倍数 0.75 → 每格切 0.75 格 → **滚一下不动、滚两下切一格、再滚跳过一格**
- 倍数 0.1 → 滚 10 格才切一格

**在 X11 这条路上，「你物理上转了几格」这个信息在到达游戏之前就已经被销毁了**，
游戏只看到一堆孤立的事件。所以任何客户端 mod 都无法在 X11 下还原它
（游戏内「离散鼠标滚动」开关、`mouseWheelSensitivity` 滑块也一样无济于事，
因为事件本来就是 ±1）。

### 游戏跑在原生 Wayland 时（本 mod 的目标状态）

| 设备倍数 | 转 12 格，游戏实际收到 | 每个事件的值 |
|---|---|---|
| **1.5（Rapoo）** | **12 个事件（一格一个）** | **`+1.5`** |

这时候倍数变成事件的**数值**，事件个数恢复成 1:1。
于是只要把数值归一化成 ±1，就是完美的「一格 = 一格」。

这就是本 mod 做的事 —— 所以它**必须配合原生 Wayland 使用**。

---

## 二、怎么用

### 1. 把 jar 放进 `mods` 文件夹

只放这一个 jar。**先在一个没有其他 mod 的 1.20.1 Forge 实例里试**（比如你 HMCL 里那个
`1.20.1-Forge`），确认没问题再考虑放进整合包。

### 2. 让 Minecraft 用原生 Wayland（必须）

MC 1.20.1 自己带的 GLFW 是 3.4.0，**它默认选 X11**；系统装的 GLFW 3.5.1 才会自动选 Wayland。
启动器不会自动帮你换，所以要手动指定。在 HMCL 里给这个实例加一条 JVM 参数：

```
-Dorg.lwjgl.glfw.libname=/usr/lib/libglfw.so.3
```

位置：实例设置 → 高级设置 → JVM 参数。HMCL 的「使用系统 GLFW」开关**也能达到同样效果**
（2026-09-19 实测：`1.20.1-Forge` 实例就是靠这个开关跑进 Wayland 的，JVM 参数一栏是空的），
两者选一个即可，都写上也不冲突。

### 3. 确认生效

启动游戏后看 `logs/latest.log`，应该出现：

```
[mousescrollfix] GLFW 3.5.1 Wayland X11 GLX Null EGL OSMesa monotonic shared | window backend: wayland (libX11 also present)
[mousescrollfix] desktop cursor theme 'breeze_cursors' from /home/bunnyh/.config/kdedefaults/kcminputrc: /usr/share/icons/breeze_cursors/cursors/default, 32px image, nominal size 24, hotspot 4,4
```

第一行是后端，写 `x11 / xwayland` 说明第 2 步的 JVM 参数没生效。
第二行说明指针已换成你桌面的主题（主题名、来源文件、尺寸都会打出来；换了主题这里就跟着变）。

### 4. 想看到证据的话

改 `config/mousescrollfix-client.toml`：

```toml
debug_log = true          # 每个滚轮事件写进 logs/mousescrollfix-debug.log
self_test_on_join = true  # 进世界 4 秒后自动跑一次自检
```

或者进世界后输入 `/mousescrollfix test`。日志长这样：

```
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=7
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=6
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=5
```

`raw` 是 GLFW 交出来的原值，`norm` 是归一化后交给原版的值，`slotBefore` 是这次事件
发生前的物品栏位置。上面这段就是「转 9 格 → 物品栏 7,6,5,4,3,2,1,0,8」，正好一格一格。

---

## 三、实测结果

同一台机器、同一个鼠标（Rapoo，倍数 1.5）。下表都是实测值：

| 环境 | 转 12 格，游戏收到几个滚轮事件 | 每个事件的值 | 结果 |
|---|---|---|---|
| 原版 + X11 | **18 个** | `+1.0` | 每格切 1.5 格，错乱 |
| 原版 + 原生 Wayland | 12 个 | `+1.5` | 每格还是切 1.5 格（原版把 1.5 当数量） |
| **本 mod + 原生 Wayland** | 12 个 | `+1.0`（mod 转换后） | **正好一格一格** |

换成「转 9 格」在真实世界里验证（进世界后用虚拟鼠标发 9 格）：

```
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=7
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=6
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=5
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=4
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=3
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=2
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=1
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=0
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=8
```

9 格 → 物品栏 7,6,5,4,3,2,1,0,8 —— 正好 9 格，一格一格（含 9 格回绕）。

进世界后自动自检的输出（`yOffset` 是「一格」被模拟成的原始值，格子里的数字是这一格
让物品栏走了几格）：

```
yOffset   notch1 notch2 notch3 notch4
+0.10         -1     -1     -1     -1
+0.75         -1     -1     -1     -1
+1.00         -1     -1     -1     -1
+1.50         -1     -1     -1     -1
+2.00         -1     -1     -1     -1
RESULT: PASS - every simulated notch moved exactly 1 slot
```

从 0.1 到 2.0 全部恰好一格 —— 也就是说，**你以后换成别的鼠标、或者改 KDE 的滚动速度，
这个 mod 依然是对的**。

---

## 四、配置项

`config/mousescrollfix-client.toml`（首次启动自动生成）

| 项 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关 |
| `affect_screens` | `true` | 背包/JEI/创造物品栏等 GUI 也一并归一化；设 `false` 则只修快捷栏 |
| `dedupe_window_ms` | `0` | 同一格滚轮被报成两个事件的抑制窗口。实测 GLFW 一格只发一个事件，所以保持 0 |
| `debug_log` | `false` | 把每个滚轮事件写进 `logs/mousescrollfix-debug.log` |
| `self_test_on_join` | `false` | 进世界后自动跑一次自检 |
| `fix_cursor_theme` | `true` | 自动把鼠标指针换成桌面的游标主题（仅原生 Wayland 生效）。设 `false` 恢复 GLFW 的默认箭头（会变成 Adwaita 那套通用箭头）；配置改动若 10 秒内没生效，说明 Forge 没自动重载配置，重启游戏即可 |
| `cursor_theme` | 空 | 留空＝自动读桌面设置。只有当 mod 认不出你的桌面环境时，才需要手填主题名（`/usr/share/icons` 下的目录名） |
| `cursor_size` | `0` | 游标尺寸（逻辑像素），`0`＝用桌面配置的尺寸（读不到时 24） |

游戏内命令：`/mousescrollfix test`（跑自检）、`/mousescrollfix status`（看当前状态）、
`/mousescrollfix cursor`（重新应用并打印指针用的是哪个主题、哪个文件）。

---

## 五、这个 mod 做了什么

| 类 | 作用 |
|---|---|
| `MouseHandlerMixin` | **核心**：把每个滚轮事件的 `yOffset` 换成正负 1，再交给原版后续逻辑。一行原版代码都没改写，所以旁观者调速、创造模式飞行、`ForgeHooksClient.onMouseScroll`（其他 mod 的滚轮功能）全部照旧 |
| `GlxWaylandCompatMixin` | 让 MC 1.20.1 能在 Wayland 上启动：原版会把 GLFW 的 `0x1000C`（"Wayland 不提供窗口位置"）当成致命错误直接崩溃 |
| `WindowWaylandCompatMixin` | 同上，处理启动后的另一条：`0x1000C`（"Wayland 不支持设置窗口图标"）会让原版弹「请更新显卡驱动」对话框卡死。两个 Mixin 都只放过 `0x1000C` 这一个错误码并记日志，其他 GLFW 错误照常按原版处理 |
| `CursorThemeFix` | **v1.1.0 新增**：Wayland 下窗口上的指针由 GLFW 决定，而 GLFW 只认 `XCURSOR_THEME` 环境变量（桌面环境不导出它），于是显示通用箭头。这个类自己去读桌面配置（KDE `kcminputrc`／GTK `settings.ini`／环境变量），在开界面时把主题里的箭头交给 GLFW。GLFW 在抓取/释放鼠标时会忘记窗口游标，所以每 10 秒和每次开界面都会重新应用一次 |
| `XCursorFile` | **v1.1.0 新增**：解析 Xcursor 文件格式（主题里 `.cursor`/`cursors/*` 的格式），挑出与请求尺寸最接近的那一档箭头，把预乘 ARGB 转成 GLFW 要的直通 RGBA |

---

## 六、已知限制与风险（请先看完再用）

1. **原生 Wayland 在这台机器上只验证了「干净环境」**。你的 DAdv 整合包有 251 个 mod，
   换图形后端可能和其中某些 mod（尤其是渲染类、截图类、输入类、录屏类）冲突。
   **所以先在空实例里试，不要直接放进整合包。**
2. 换到原生 Wayland 会改变整个窗口/输入/缩放后端，副作用不止滚轮：
   - 窗口图标没设置（你会在日志里看到那条被忽略的警告）
   - Wayland 不提供窗口位置，游戏可能记不住窗口坐标
   - 分数缩放（你这里是 1.25x）下的画面、光标行为可能和以前不同

3. **鼠标指针会自动换成你桌面的主题（v1.1.0 起 mod 自己搞定，不用你配任何东西）。**
   背景是这样：Wayland 下窗口上的箭头完全由 GLFW 给出（Minecraft 本体从不调用 `glfwSetCursor`，
   整个客户端里只有 `glfwSetCursorPos` 和几个回调），而 GLFW 的 Wayland 后端既没有
   compositor 侧的游标形状协议（在 `/usr/lib/libglfw.so.3` 里搜不到任何 `wp_cursor_shape`），
   主题名又**只认 `XCURSOR_THEME` / `XCURSOR_SIZE` 环境变量**——KDE 在 X11 侧把主题写进 X 资源库
   （`xrdb -query` 里能看到 `Xcursor.theme: breeze_cursors`），Wayland 侧却不导出这两个变量，
   于是 GLFW 退回名为 `default` 的主题，而 `/usr/share/icons/default/index.theme` 只有一行
   `Inherits=Adwaita` → 你会看到一套通用的 Adwaita 箭头。

   想靠环境变量修是**行不通的**：Forge 的早期启动窗口（`Loading ImmediateWindowProvider
   fmlearlywindow`）在 mod 加载之前就初始化了 GLFW，而 GLFW 的游标主题只在那一刻读一次环境变量。
   所以 mod 的做法是自己读桌面配置（KDE 的 `kcminputrc` / `kcminputrc` 的 `kdedefaults` 默认文件 /
   GTK 的 `settings.ini`，都没有才看环境变量），把主题里的箭头图像解析出来直接交给 GLFW。
   你换主题它就跟着换；X11 下这个功能自动跳过（那边本来就正常）。

   实测（本机，外接屏 1.25x 缩放）：把它和两套主题的原始箭头做逐像素模板匹配，
   Breeze 平均色差 33.6、Adwaita 130.2（主菜单）；进世界后按 ESC（鼠标抓取→释放的完整周期）
   复测 38.4 / 142.4；把功能关掉则反过来变成 Adwaita 45.2 / Breeze 113.9 —— 即修复前你看到的就是 Adwaita。
   `/mousescrollfix cursor` 可以随时查看它当前用的是哪个主题、哪个文件。

4. **在 X11 上这个 mod 对滚轮不起作用**（因为那条路上没有小数，无事可做）。
   它只会让 Wayland 能启动那两条生效。
5. 如果原生 Wayland 在你的整合包里问题太多，**最省事的替代方案**是把 Rapoo 鼠标的
   KDE 滚动速度改回默认（1.0）—— 实测那样 X11 下就是 12 格 = 12 事件 = 12 格，
   和 Windows 完全一致，不需要任何 mod。代价是这个鼠标在桌面所有程序里都会滚得快一些。
6. 自检是直接调用原版的 `MouseHandler.onScroll`（绕过物理滚轮），
   走的是**完全相同**的代码路径，只是不需要真的去转鼠标。

## 七、窗口特别大怎么办（与本 mod 无关，但顺手说明）

启动器把窗口尺寸当参数传给游戏。你的 HMCL 里存的是 **2560×1440**（日志里能看到
`--width 2560 --height 1440`），而外接屏 DP-2 是 2560×1440 **物理**、缩放 1.25 →
**逻辑只有 2048×1152**。Wayland 下窗口尺寸按逻辑像素算，于是这个窗口等于 3200×1800 物理像素，
比屏幕本身还大 25%，怎么摆都装不下：

| 屏幕 | 物理 | 缩放 | 逻辑（能用的最大窗口） |
|---|---|---|---|
| DP-2 外接 | 2560×1440 | 1.25 | **2048×1152** |
| eDP-1 笔记本内屏 | 2560×1600 | 1.4 | **1829×1143** |

改法：HMCL → 实例设置/全局游戏设置 → 把「窗口宽度/高度」改成不超过上表逻辑尺寸的值。
推荐 `1920×1080`（物理 2400×1350，留出边距）或 `1600×900` 更小巧。改完重启游戏生效。
想铺满屏幕就在游戏里按 F11 用全屏，全屏不受这个问题影响。

## 八、卸载

删掉 `mods/mousescrollfix-1.1.0.jar`，并去掉那条 `-Dorg.lwjgl.glfw.libname`
JVM 参数（去掉后就回到 X11，一切恢复原样，系统设置从未被修改过）。

## 九、给开发者

```bash
# 编译
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew build

# 普通开发客户端（X11）
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew runClient

# 原生 Wayland 开发客户端（等价于上面第 2 步的 JVM 参数）
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew runClient -Pwayland

# 直接进指定世界（跳过 GUI）
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew runClient -Pwayland -Pquickplay=<世界名>
```

`tools/` 里有验证用的脚本：`spectacle -b -p` 截图带指针、`keys.py` 用 uinput 发按键
（XTest 的按键到不了 Wayland 原生客户端）、`kwin_query.js` 查窗口几何。

实测数据与复现方法见 [`FINDINGS.md`](FINDINGS.md)。
