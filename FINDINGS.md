# 实测记录：Minecraft 1.20.1 滚轮切换物品问题的真实机制

全部数据来自本机实测（CachyOS + KDE Plasma 6.7.5 / Wayland / KWin 6.7.5），
测量手段：`/dev/uinput` 造一个虚拟鼠标，让 KWin 像处理真实鼠标一样处理它，
再用 GLFW 写的最小探针程序记录游戏实际收到的滚轮事件。

## 1. 游戏跑在哪个窗口后端上

游戏内自报（`logs/mousescrollfix-debug.log`）：

```
GLFW 3.4.0 Wayland X11 GLX Null EGL OSMesa monotonic shared | window backend: x11 / xwayland
```

- MC 1.20.1 用的 LWJGL 3.3.1，其 `lwjgl-glfw-3.3.1-natives-linux.jar` 里打包的
  `libglfw.so` **是 GLFW 3.4.0，本身带 Wayland 后端**（sha1 `e4e724fc…`，与 HMCL 解出来的一致）。
- 但 **GLFW 3.4.0 默认选 X11**，必须由程序显式设置 `GLFW_PLATFORM` 提示才会用 Wayland。
  MC 1.20.1 用的是 LWJGL 3.3.1，没有这个绑定，所以**永远走 X11 / XWayland**。
- 对照实验：把系统的 GLFW 3.5.1 用 `-Dorg.lwjgl.glfw.libname=/usr/lib/libglfw.so.3` 换进来，
  探针立刻变成 `libwayland-client mapped: true` —— **GLFW 3.5.1 才会自动选原生 Wayland**。

## 2. 一格滚轮到底产生什么

在 X11 路径下，用虚拟鼠标发 N 格，数 GLFW 收到几个事件、值是多少：

| 设备（对应 kcminputrc 里的 ScrollFactor） | 发出的格数 | GLFW 收到的事件数 | 事件值 |
|---|---|---|---|
| 通用设备（默认 ScrollFactor = 1.0） | 12 | **12** | 全部 `y=+1.000000` |
| `ITE ... Device(8176) Keyboard`（0.75） | 12 | **9** | 全部 `y=+1.000000` |
| `Rapoo Rapoo Gaming Device`（1.5） | 12 | **18** | 全部 `y=+1.000000` |
| `RDMCTMZT Wireless 2.4G Dongle Mouse`（0.1） | 12 | **1** | `y=+1.000000` |

**关键结论：X11 路径下，事件值永远是 ±1.0，没有小数。**
KWin 的「滚动速度」不能改事件的大小，只能改**事件的个数**。

0.75 那一组的到达时间戳（每 200 ms 发一格）：

```
事件:  t=0   200  400  [600 缺失]  800  1000  1200  [1400 缺失]  1600  1800  2000
间隔:      200  200      401        200  200        400          200   200
```

也就是：**连发 3 格 → 全部生效；第 4 格被整格吞掉。**
这正是用户描述的「滚一下不切、滚两下切一格、再滚又跳过一格」。

## 3. 原版 Minecraft 怎么处理这些事件

```java
double d0 = (discreteMouseScroll ? Math.signum(yOffset) : yOffset) * mouseWheelSensitivity;
accumulatedScroll += d0;
int i = (int) accumulatedScroll;
if (i == 0) return;
accumulatedScroll -= (double) i;
...
player.getInventory().swapPaint((double) i);
```

```java
public void swapPaint(double d) {
    int i = (int) Math.signum(d);   // ← 只取符号，永远只走一格
    for (this.selected -= i; this.selected < 0; this.selected += 9) {}
    while (this.selected >= 9) { this.selected -= 9; }
}
```

因为事件恒为 ±1.0，`accumulatedScroll` 每次恰好被消耗干净，`i = ±1`，
`swapPaint` 又只取符号 —— **原版是「一个事件 = 一格」，而且这个逻辑本身是正确的**。

问题完全在于**事件的个数**先被 KWin 改掉了。

## 4. 因此

| 方案 | 能否做到「一格 = 一格」 |
|---|---|
| 在游戏里改 `discrete_mouse_scroll` | ✗ 无效：事件本来就是 ±1 |
| 在游戏里改「滚轮灵敏度」 | ✗ 只能改变每个事件的步数，改不了事件个数 |
| 统计意义上的 UI 滚动库（JEI 等） | ✗ 同理 |
| 写 Mixin 归一化 `yOffset` | ✗ 在 X11 下是空操作（实测值本来就是 ±1.0） |
| **把该设备的 KWin 滚动速度设为 1.0** | ✓ 实测 12 格 → 12 事件 → 12 格，与 Windows 完全一致 |
| 让游戏跑原生 Wayland + 归一化 Mixin | ✓ 已实测可行，见第 5 节 |

**结论：在 X11 路径上，物理滚了多少格这个信息在到达游戏之前就被销毁了，
任何客户端 mod 都无法把它还原。**

## 5. 原生 Wayland 路径（最终采用的方案）

### 5.1 怎么让 1.20.1 跑上原生 Wayland

| 事实 | 证据 |
|---|---|
| 游戏自带的 GLFW 3.4.0 编译进了 Wayland 后端，但**默认仍选 X11** | 探针实测 `libwayland-client mapped: false` |
| 系统 GLFW 3.5.1 **会自动选 Wayland** | 加 `-Dorg.lwjgl.glfw.libname=/usr/lib/libglfw.so.3` 后 `libwayland-client mapped: true` |
| 换到 Wayland 后原版**立刻崩溃** | `IllegalStateException: GLFW error before init: [0x1000C]Wayland: The platform does not provide the window position`，抛自 `GLX._initGlfw` → `Window.checkGlfwError` |
| 放过那一个错误码后仍会卡住 | 弹窗 `GLFW error 65548: Wayland: The platform does not support setting the window icon`，来自 `Window.bootCrash` |

`0x1000C` = `GLFW_FEATURE_UNAVAILABLE`。Wayland 天生不提供窗口位置、不支持设窗口图标，
GLFW 把这两件事报成 error，而原版 Minecraft **对任何 GLFW error 都当作致命**。
mod 用两个 Mixin 只放过这一个错误码（`GlxWaylandCompatMixin`、`WindowWaylandCompatMixin`），
游戏随即正常启动到主菜单（`Sound engine started`）。

### 5.2 Wayland 下的滚轮实测

同一个虚拟鼠标、同一个 Rapoo 标识（ScrollFactor = 1.5），转 12 格：

```
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=7
raw=+1.5000 norm=+1.0000 gapMs=-1 ctx=world  slotBefore=6
...
```

- **12 格 → 12 个事件**（X11 下是 18 个），一格一个，个数问题消失
- 每个事件的 `yOffset` 正好是 ScrollFactor 的数值 `1.5`（X11 下恒为 1.0）
- 归一化后交给原版的是 `1.0` → 原版 `swapPaint` 每事件走一格

真实滚轮 9 格进世界的物品栏变化：**7,6,5,4,3,2,1,0,8** —— 一格一格，含 9 格回绕。

自检（0.1 / 0.75 / 1.0 / 1.5 / 2.0 各四次，直接调用原版 `MouseHandler.onScroll`）：

```
yOffset   notch1 notch2 notch3 notch4
+0.10         -1     -1     -1     -1
+0.75         -1     -1     -1     -1
+1.00         -1     -1     -1     -1
+1.50         -1     -1     -1     -1
+2.00         -1     -1     -1     -1
RESULT: PASS - every simulated notch moved exactly 1 slot
```

## 6. 复现方法（可重复验证）

验证工具都在 [`tools/`](tools/)：

| 文件 | 作用 |
|---|---|
| `wheel.py` | 通过 `/dev/uinput` 造一个虚拟鼠标并发 N 格滚轮。`--name/--vendor/--product` 可以让 KWin 把它当成你某只真鼠标，从而套用 `kcminputrc` 里那台设备的 ScrollFactor |
| `click.py` | 通过 uinput 发真实左键点击（原生 Wayland 客户端收不到 XTest 事件，`xdotool click` 没用） |
| `ScrollProbe.java` | 用游戏同款 LWJGL/GLFW 开一个窗口并打印每个滚轮事件的值；同时从 `/proc/self/maps` 判断实际选中的是 Wayland 还是 X11 |
| `aim_and_wheel.py` | 用 xdotool 把指针移到指定窗口上（会自检是否真的移上去了）再发滚轮 |
| `kwin_helper.js` | KWin 脚本：把 Minecraft 窗口置顶并打印其几何位置（Wayland 下应用自己拿不到窗口坐标） |

```bash
# 虚拟鼠标：12 格，冒充 ITE 键盘设备（ScrollFactor 0.75）→ 预期只收到 9 个事件
python3 tools/wheel.py 12 --delay 200 \
    --name "ITE Tech. Inc. ITE Device(8176) Keyboard" --vendor 0x048D --product 0xC992

# 冒充 Rapoo（1.5）→ 预期 18 个事件（X11）或 12 个 raw=1.5 的事件（Wayland）
python3 tools/wheel.py 12 --delay 200 \
    --name "Rapoo Rapoo Gaming Device" --vendor 0x24AE --product 0x1412
```

游戏内的验证不需要这些工具：把 `self_test_on_join = true` 打开，进世界 4 秒后自检
会自己跑并把结果写进 `logs/mousescrollfix-debug.log` 和聊天栏。

## 7. 游标主题（v1.1.0）

换成原生 Wayland 后窗口上的指针变成通用箭头，机制与实测：

| 事实 | 证据 |
|---|---|
| Minecraft 本体从不设置游标 | 扫整个客户端 jar，与游标相关的只有 `glfwSetCursorPos` 和两个回调，`glfwSetCursor`/`glfwCreateStandardCursor` 一次都没有 |
| Wayland 下箭头由 GLFW 决定，且它只认环境变量 | `/usr/lib/libglfw.so.3` 里有 `XCURSOR_THEME`/`XCURSOR_SIZE` 字符串，没有任何 `wp_cursor_shape` 协议 |
| 这两个变量在本机处处为空 | shell、`systemctl --user show-environment`、游戏进程 `/proc/<pid>/environ` 三处都没有；KDE 只在 X11 侧写 X 资源库（`xrdb -query` → `Xcursor.theme: breeze_cursors`） |
| 因此 GLFW 退回名为 `default` 的主题 | libwayland-cursor 上游源码 `if (!name) name = "default";`；而 `/usr/share/icons/default/index.theme` 只有 `Inherits=Adwaita` |
| 桌面主题的真实来源 | `~/.config/kdedefaults/kcminputrc` → `[Mouse] cursorTheme=breeze_cursors`（用户自己的 `kcminputrc` 里没有这个键，KDE 的 `kreadconfig6` 就是从 kdedefaults 回退出来的） |
| 环境变量改不动 | Forge 的早期窗口（`fmlearlywindow`，jar 里有 `glfwInit`×3）在 mod 加载之前就初始化了 GLFW，而 GLFW 只在那一刻读一次主题 |

验证方式：`spectacle -b -p` 截带指针的全屏图（游标热点坐标 = 逻辑坐标 × 1.25），
用 Python 把主题的 `cursors/left_ptr` 解出来当模板，在 ±14px 内滑窗做逐像素比色：

| 场景 | Breeze 色差 | Adwaita 色差 | 判定 |
|---|---|---|---|
| mod 游标功能**关闭** | 113.9 | **45.2** | Adwaita（= 修复前用户看到的） |
| mod 游标功能开启，主菜单 | **33.6** | 130.2 | Breeze |
| mod 游标功能开启，进世界后按 ESC（抓取→释放鼠标） | **38.4** | 142.4 | Breeze |
| 最终版本（重构后） | **33.9** | 130.6 | Breeze |

三次开启状态下的最佳匹配缩放都是 1.25，与外接屏的分数缩放一致，说明拿到的就是主题里那一档图。
