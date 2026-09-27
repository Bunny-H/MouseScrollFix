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
- 但 **LWJGL 3.3.1 打包的这份 GLFW，在 Wayland 会话里仍然选 X11**（实测）。
- 对照实验（同一份进程环境，只换加载的库，2026-09-19 复测）：

  | 加载的 GLFW | 运行时版本字符串 | 结果 |
  |---|---|---|
  | LWJGL 3.3.1 自带的 `libglfw.so` | `3.4.0 Wayland X11 …` | `libwayland-client=false` → **X11** |
  | `/usr/lib/libglfw.so.3`（发行版） | `3.5.1 Wayland X11 …` | `libwayland-client=true` → **Wayland** |

- 原因**不是版本号**：上游 GLFW 3.4 与 3.5 的 `_glfwSelectPlatform` 逻辑完全一样
  （`XDG_SESSION_TYPE=wayland` + `WAYLAND_DISPLAY` 存在就选 Wayland，见 `src/platform.c`），
  而 LWJGL 打包的那份 `libglfw.so` 里**连 `XDG_SESSION_TYPE` 这个字符串都没有**
  （`strings` 实测；发行版那份有），平台表顺序也还是 X11 在前 —— 也就是说它是
  "XDG_SESSION_TYPE 选择逻辑加入之前"的 3.4 快照。
- 结论：**要的不是"最新版"，而是一份"在 Wayland 会话里会选 Wayland"的 GLFW**
  （GLFW ≥ 3.4 且带上这套选择逻辑；发行版自带的一般都满足。GLFW 3.3.x 根本没有 Wayland 后端，
  所以 Ubuntu/Debian 这类老 LTS 上做不到，除非自行编译更新的 GLFW）。

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
| Mixin 归一化 `yOffset`（v1.2.0 的做法） | ✗ 在 X11 下是空操作（实测值本来就是 ±1.0） |
| **Mixin 合并同一瞬间的重复事件（v1.3.0）** | **✓ 倍率 > 1 时有效，见第 8 节（实测 130 个复制对全部合上）** |
| 还原倍率 < 1 时被吞掉的格 | ✗ 不可能：信息在到达游戏之前就没了 |
| **把该设备的 KWin 滚动速度设为 1.0** | ✓ 实测 12 格 → 12 事件 → 12 格，与 Windows 完全一致 |
| 让游戏跑原生 Wayland + 归一化 Mixin | ✓ 已实测可行，见第 5 节 |

**结论（2026-09-24 修订）**：倍率 > 1 时，多出来的事件是同一格的复制品（跟原事件相隔
不到 1 毫秒），可以认出来并只算一格；倍率 < 1 时被吞掉的格没有任何办法还原。

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
| `ScrollProbe.java` + `run_probe.sh` | 用游戏同款 LWJGL/GLFW 开一个窗口并打印每个滚轮事件的值 **和相邻事件的间隔（`dtMicros`）**；同时从 `/proc/self/maps` 判断实际选中的是 Wayland 还是 X11。`run_probe.sh` 负责编译与运行（默认 X11，加 `--wayland` 换系统 GLFW） |
| `summary.py` | 把 `logs/mousescrollfix-debug.log` 汇总成「收到多少事件 / 合并多少重复 / 实际切了几格」，用于核对上面的表格 |
| `aim_and_wheel.py` | 用 xdotool 把指针移到指定窗口上（会自检是否真的移上去了）再发滚轮；`--delay` 控制每格间隔 |
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

> **v1.4.0 起这一节的功能默认关闭**（原因见第 9 节）：默认用 GLFW 自己的光标，要桌面主题光标
> 得在 Mods 列表里打开本 mod 的设置界面拨开关。滚轮修复不受影响。

## 8. X11 / XWayland 路径（v1.3.0）：合并「被复制的同一个格」

全部为 2026-09-24 本机实测（CachyOS / KDE Plasma 6.7.5 / KWin 6.7.5 / XWayland，
游戏内是 LWJGL 3.3.1 自带的 GLFW 3.4.0）。

### 8.1 复制事件长什么样（探针 + 虚拟鼠标，每次「转 12 格」）

| 冒充的设备（ScrollFactor） | 转格节奏 | 收到事件 | 相邻事件间隔 |
|---|---|---|---|
| 无（默认 1.0） | 200 ms | 12 | 200 ms |
| Rapoo（1.5） | 200 ms | **18** | 200 ms 与 **780–947 µs** 交替 |
| Rapoo（1.5） | 20 ms（50 格/秒） | **18** | 20 ms 与 **189–471 µs** 交替 |
| Rapoo（1.5） | 5 ms（200 格/秒） | **18** | 5 ms 与 **148–531 µs** 交替 |
| ITE 键盘（0.75） | 200 ms | **9** | `200 200 400` 循环 |

所有事件的值都是 `+1.0`。**复制出来的那个事件紧跟原事件不到 1 毫秒**，而真实格与格之间
即使狠转也有几毫秒（5 ms 那行已经是 200 格/秒，人手做不到）。1.5 倍时 12 格产生 18 个事件
（第 2、4、6…格各多一个），0.75 倍时 12 格只剩 9 个（第 4、8、12 格被整格吞掉）。

### 8.2 游戏内实测（开发客户端跑 XWayland，虚拟鼠标冒充 Rapoo 1.5）

| 配置 | 转 12 格 | 收到事件 | 判为复制 | 物品栏实际切格 |
|---|---|---|---|---|
| `x11_fix = "off"`（等于没有这个修复） | 12 | 18 | 0 | **18** ← 跳格 |
| `x11_fix = "auto"`（默认） | 12 | 18 | 6 | **12** ← 一格一格 |
| 默认设备（倍率 1.0） | 12 | 12 | 0 | 12（不受影响） |
| Rapoo，每 20 ms 一格（50 格/秒） | 12 | 18 | 6 | **12**（快滚没被吃掉） |

默认设置那行的物品栏路径：`8,7,6,5,4,3,2,1,0,8,7,6,5` —— 12 格正好 12 步（含 9 格回绕）。
被合并的复制事件实测间隔 138–197 µs，判定窗口是 2 ms，差 10 倍以上。

**漏合并**（复制事件恰好落在两帧之间、被当成新的一格）实测：主菜单连续 200 格
（每 50 ms 一格）→ 收到 300 个事件 = 200 × 1.5、100 对全部合上、最终正好 200 步；
再加上先前 30 对与世界内 12 对，共 142 对，**一个都没漏**。

游戏内自检（`self_test_on_join = true`）也覆盖这条路径：

```
duplicate merging (X11 / XWayland): true | this desktop duplicates wheel events (ScrollFactor 1.50 in .../kcminputrc (Libinput][9390][5138][Rapoo Rapoo Gaming Device))
two events in the same instant : -1 slot(s)  (want -1)
two events 40 ms apart         : -2 slot(s)  (want -2)
RESULT: PASS - duplicates merged, real notches left alone
```

### 8.3 为什么默认只在「倍率 > 1」时合并

`x11_fix = auto`（默认）只在 KDE 的 `kcminputrc` 里读到 **ScrollFactor > 1** 时才启用合并：
倍率 ≤ 1 的机器上根本不存在复制事件，mod 于是完全不介入，也就不会把「转得飞快时两格落在
同一帧」误判成复制而吃掉真实输入。合并窗口（`x11_merge_ms = 2`）比复制事件的间隔大 2–10 倍，
比任何真实的两格间隔小 2 个数量级。

已知边界：自由滚轮（无段落感的飞轮）与触控板在 X11 下能报出每秒上百次点击，其中同帧的部分
会被这条规则合掉；有段落感的鼠标物理上做不到（需要 ≥ 125 格/秒）。

### 8.4 为什么不能相信 `user.home`（v1.3.0 实测踩到的坑）

用户在自己的 HMCL 纯净实例（`versions/1.20.1-Forge`）里装 1.3.0 后**完全没有效果**，
日志里是：

```
window backend: x11 / xwayland
scroll fix in this session: nothing (no scaler detected on this backend, or merging turned off)
X11 detection: no KDE wheel ScrollFactor configured, nothing to merge
```

同一个用户、同一份 `~/.config/kcminputrc`（603 字节、纯 ASCII、`ScrollFactor=1.5` 就在里面），
开发客户端里能读到、HMCL 里读不到。原因在 HMCL 记录的启动命令里：

```
"-Duser.home=/home/bunnyh/.config/hmcl"
```

**HMCL 把游戏的 `user.home` 指向了自己的数据目录**，于是按 `user.home/.config/kcminputrc`
去找就变成了 `~/.config/hmcl/.config/kcminputrc` —— 不存在。而 `HOME` 环境变量在游戏的进程里
仍然是 `/home/bunnyh`（HMCL 的子进程照常继承）。v1.1.0 的游标主题功能用的是同一套路径逻辑，
所以在 HMCL 实例里（原生 Wayland 下）它其实也一直静默跳过。

修法（`DesktopFiles`）：`HOME` 优先，`user.home` 只作兜底；并且把**所有**可能的位置都查一遍
（`$XDG_CONFIG_HOME`、`$HOME/.config`、`$(user.home)/.config`、以及 `user.home` 上溯两级的
`.config`——最后这条让「连 HOME 都没传」的环境也能找到）；找不到时日志会把查过的文件列出来，
下次再出现「没效果」可以直接从日志判断是路径问题还是真的没有倍率。

验证（同一份 jar，四种环境，走的就是游戏里那套代码）：

| 环境 | 结果 |
|---|---|
| 开发客户端（有 `XDG_CONFIG_HOME`，无 `-Duser.home`） | 读到 `ScrollFactor 1.50` ✓ |
| `-Duser.home=~/.config/hmcl`，无 `XDG_CONFIG_HOME`（= HMCL） | 读到 `ScrollFactor 1.50` ✓ |
| 同上、连 `HOME` 也没有 | 读到 `ScrollFactor 1.50` ✓（靠上溯） |
| 故意给错 `user.home`、无 `HOME` | 正确报告「找不到」并列出查过的文件 ✓ |

游戏内验证：以 HMCL 同样条件启动的客户端里，12 格 → 12 格（合并 6 个复制事件），
用户随后在自己的 HMCL 实例里实测：「效果非常好」。

## 9. 整合包内崩溃调查（v1.4.0）：`glfwSetCursor` 撞上野指针

**现象**：2026-09-27，用户在自己的 DAdv 整合包（HMCL 启动，Forge 47.4.21，日志里 246 个 mod）里
玩了一小时后**原生崩溃**（`hs_err_pid103604.log` + 3.8 GB core dump，都在 `err/`）。崩溃帧：

```
C  [libwayland-client.so.0+0x7f1a]  wl_proxy_marshal_flags+0xca
J  org.lwjgl.glfw.GLFW.glfwSetCursor
J  com.bunnyh.mousescrollfix.CursorThemeFix.apply()   ← 本 mod 的游标主题功能（整合包里是旧 jar 1.1.0）
J  ScreenEvent.Init.Post（刚要打开界面）
```

**调查步骤**（每步都可复现）：

| # | 做法 | 得到的事实 |
|---|---|---|
| 1 | 反汇编 `libwayland-client.so.0.26.0` 崩溃指令前后 | 崩在 `mov 0x8(%rax),%rdi`（读 `interface->methods[opcode]`），`si_addr = 0x8` ⇒ `methods == NULL`：那个"Wayland 对象"根本不是对象 |
| 2 | gdb 读 core：GLFW 递出去的指针指向的内存 | 里面是别的字符串数据（`"MDOExtra"`…），不是 `wl_proxy` ⇒ 对象早被销毁、内存已被复用 |
| 3 | 反汇编 `libglfw.so.3.5` 里返回地址所在的函数（0x25100） | 它是 `unlockPointer()`：`zwp_relative_pointer_v1_destroy` + `zwp_locked_pointer_v1_destroy`——正是"游戏放开鼠标"时销毁相对指针/锁定指针那段 |
| 4 | 对照 GLFW 3.5.1 源码（`wl_window.c`） | 每个 destroy 之后紧接着 `= NULL`；**单线程执行不可能留下"字段非空、对象已释放"** ⇒ 必然有第二个执行者同时改同一份状态 |
| 5 | 读 core 里同一窗口的那两个字段 | 都已经是 0（"销毁+置空"在别处已经跑完）——与竞态吻合 |
| 6 | 查环境 | 整合包带 **Ixeris 4.5.2**（mods.toml: "Buffered raw input and threaded event polling"；`config/ixeris.toml`: `flexibleThreading=true`、`fullyBlockingMode=false`），且**故意不带 LWJGL 自带的 GLFW**，实际加载系统 `/usr/lib/libglfw.so.3.5`（`glfw 1:3.5.1-1.1`）——GLFW 的 Wayland 后端不是线程安全的 |

**结论**：被弄坏的状态（相对指针、锁定指针）是**原版**在抓取/释放鼠标时创建销毁的；Ixeris 把 GLFW
事件轮询放到另一个线程，两个线程会同时进同一段销毁/创建逻辑 ⇒ 一边已经销毁并置空，另一边还拿着旧
地址调 libwayland。本 mod 是**整个客户端里唯一调用 `glfwSetCursor` 的代码**（原版只用
`glfwSetCursorPos` 和光标回调），所以它是"第一个撞上"的调用；每次开界面加每 10 秒一次的重复应用
也确实增加了这段状态的翻动次数。**根因不在本 mod 能修的范围**（Ixeris 的线程模型 × GLFW 的
非线程安全 Wayland 后端）；要给上游的话，方向是 `github.com/decce6/Ixeris/issues`。

**处置（v1.4.0）**：

- `fix_cursor_theme` 默认改为 `false`＝默认光标（不再调用任何 GLFW 游标函数）。
- 新增设置界面（Mods 列表 → 本 mod → Config）：一个开关，鼠标悬停时说明「换成桌面主题光标有概率
  与其他 mod 不兼容」；改动即时生效并写回 toml。
- 滚轮修复与此无关，任何情况下都不受影响。

