# FixMinecraftMouseWheel — 项目约定

Minecraft 1.20.1 Forge 模组（目标：修复鼠标滚轮相关行为，具体需求以用户为准）。

## 版本固定

| 项 | 值 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.21 |
| Mappings | `official` / `1.20.1` |
| Java 语言级别 | 17 |
| ForgeGradle | `[6.0,6.2)`（解析为 6.0.54） |
| Gradle | 8.8（工程自带 wrapper） |

## 构建环境（2026-09-19 实测确认，非推测）

### 1. 必须显式指定 JDK 17，禁止用系统默认 Java

本机系统默认 Java 是 **26**（`/usr/lib/jvm/java-26-openjdk`），而 Gradle 8.8 只能运行在 Java 8–22 上。
不指定 JDK 17 会直接构建失败。

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew build
```

JDK 17 位置：`/usr/lib/jvm/java-17-openjdk`（jdk17-openjdk 17.0.19，完整 JDK，含 javac 与 jmods）。
**每条 `./gradlew` 命令都要带这个前缀**，不要依赖 shell 里的 JAVA_HOME（它是空的）。

### 2. 只用工程自带的 wrapper，不要用系统 Gradle

本机没装全局 gradle，也不需要装。始终用工程里的 `./gradlew`（固定 Gradle 8.8）。
不要改用 `~/.gradle/wrapper/dists` 里已有的 Gradle 9.4.0 —— ForgeGradle 6 与 Gradle 9 不兼容。

### 3. 网络

无代理、无镜像，全部直连可达：`maven.minecraftforge.net`、`libraries.minecraft.net`、
`piston-meta.mojang.com`、`piston-data.mojang.com`、`repo1.maven.org`、`plugins.gradle.org`、
`services.gradle.org`。不要添加 aliyun 之类的镜像，会破坏 Forge 私服构件的解析。

## 常用命令

```bash
# 编译并产出 jar（产物在 build/libs/）
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew build

# 快速语法检查
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew compileJava

# 启动开发客户端进游戏实测
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew runClient

# 数据生成
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew runData
```

`runClient` 会一直占用终端直到游戏关闭。**启动成功的标志**是日志出现：

```
[Render thread/INFO] [minecraft/SoundEngine]: Sound engine started
```

用 `kill <pid>` 关游戏会让 Gradle 报 `Process ... finished with non-zero exit value 143`，
这是 SIGTERM 的正常表现，不是缺陷。

## 实测基准（用于判断"这次是不是卡住了/坏了"）

| 项目 | 实测值 |
|---|---|
| 首次 `build`（含下载 Gradle 8.8 + 反编译 Minecraft） | 4 分 11 秒 |
| 首次 `runClient`（含下载 628 MB 资源文件） | 2 分 53 秒 |
| `~/.gradle` 缓存总量 | 1.8 GB（其中资源 628 MB、wrapper 147 MB） |
| 空 MDK 工程的构建产物 | 约 11 KB 的 jar |
| 硬件 | i7-14650HX / 24 线程 / 31 GB 内存 / 空闲 176 GB |

## 游戏内测试的两条路

1. `./gradlew runClient` —— 独立开发环境，最干净，推荐。
2. 你已有装好 Forge 1.20.1-47.4.21 的 HMCL 实例：把 `build/libs/*.jar` 放进
   `~/.config/hmcl/.minecraft/mods/`。注意该 mods 目录被这个 `.minecraft` 根目录下所有版本共用
   （1.7.10、26.2 等实例也会扫到），所以优先用 `runClient`。

## 已知无害日志（不要当 bug 去修）

- `Reflective setAccessible(true) disabled` —— netty 常规提示
- `Narrator ... Failed to load library flite` —— 旁白朗读缺库，不影响游戏
- `Missing sound for event: minecraft:item.goat_horn.play` —— 原版遗留
- `Unable to load model: examplemod:example_block` —— MDK 模板自带的缺失材质
- `ResourceLocation(String) 已过时` —— MDK 示例 `Config.java` 的写法，仅警告

## 工作方式约定

- **用户不写 Java，也不读 Java。** 所有代码改动由 agent 完成，用户只看结果。
- 因此每次改动后必须真跑 `build`；涉及游戏内行为的必须跑 `runClient` 实测，并用日志或截图
  说明效果。不要交付"应该能行"的代码。
- 用户用中文交流。
- 参考工程：`~/forge-mdk-selftest/` 是 2026-09-19 用于验证环境的原始 MDK 工程，
  可作为"纯净模板"对照；不再需要时可删。
