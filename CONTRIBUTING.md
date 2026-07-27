<a id="english"></a>

# Contributing to Couchy Launcher

Thanks for helping out! Couchy is GPLv3 — by contributing you agree your changes ship under the same license.

[**English**](#english) · [中文](#chinese)

## Build

Requirements: **JDK 17**, the **Android SDK** (API 34), and either Android Studio (Ladybug+) or the command line. There's no `local.properties` in the repo — point Gradle at your SDK:

```
export ANDROID_HOME=~/Android/Sdk      # or set sdk.dir in local.properties
./gradlew assembleDebug                # app/build/outputs/apk/debug/
```

Install and run on a TV over the network:

```
adb connect <tv-ip>:5555
./gradlew installDebug
adb shell monkey -p com.conreo.couchytv -c android.intent.category.LAUNCHER 1
```

**Always test performance on a release build** — debug Compose runs several times slower. A locally-installable, debug-signed release:

```
./gradlew installRelease -PlocalSign
```

(Without `-PlocalSign` the release is **unsigned**, which is what F-Droid builds and signs itself.)

## Project layout

Everything is Kotlin + Jetpack Compose for TV (`androidx.tv:tv-material`), min SDK 21.

```
app/src/main/java/com/conreo/couchytv/
  MainActivity.kt          entry point, HOME intent-filter, package-change rescan
  Actions.kt               launch / app-info / uninstall / force-stop / settings intents
  data/Config.kt           LauncherConfig (@Serializable) + DataStore persistence
  data/AppRepository.kt     app scan, auto-categorization, section grouping, icon cache
  data/BuiltinAerials.kt    built-in aerial manifest loading
  data/Status.kt            network + VPN status flow
  ui/LauncherScreen.kt      the launcher: layouts, wallpaper (ExoPlayer), move mode
  ui/SettingsSheet.kt       D-pad settings panel (animated sub-screens)
  ui/SetupWizard.kt         first-run wizard (default-home + VPN steps)
  ui/AppCard.kt, Menus.kt, StatusBar.kt, Theme.kt, Icons.kt
app/src/main/res/          strings (values + 17 locales), drawables, themes
```

## Conventions

- **Remote-first:** every control must be reachable with the D-pad alone. Text input only ever happens inside a dedicated dialog (see `RenameDialog`).
- **No hardcoded user-facing text** — put it in `res/values/strings.xml` and reference it with `stringResource(...)`. Default category and wallpaper names are localized too.
- **Match the surrounding style** — the codebase favors small, commented, self-explanatory composables. Comments explain *why*, not *what*.
- **State** lives in `LauncherConfig` and persists via `ConfigStore`. New config fields need a sensible default (deserialization ignores unknown keys, so old installs stay compatible).
- Keep dependencies minimal and FOSS (this ships on F-Droid — no Google Play Services, no trackers).

## Translations

To add or fix a language, copy `app/src/main/res/values/strings.xml` to `values-<code>/strings.xml` and translate the *values* (keep the `name="..."` keys). Android selects the right file automatically; English is the fallback.

Currently shipped: `ar de es fr hi in it ja ko nl pl pt ru th tr vi zh` (+ English).

Universal symbols (`0.5×`, `◄` / `►`) don't need translating.

## Pull requests

1. Keep changes focused; describe *what* and *why*.
2. Make sure `./gradlew assembleDebug` passes and the app runs on a real (or emulated) Android TV.
3. For UI changes, attach a screenshot.
4. New user-facing strings must be added to `values/strings.xml` (and ideally `values-fr`, `values-zh`).

## Releases & F-Droid

Releases are tagged `vX.Y` on the default branch. The F-Droid build recipe lives at [`fdroid/com.conreo.couchytv.yml`](fdroid/com.conreo.couchytv.yml). See that file's header for the submission steps.

<br>

---

<a id="chinese"></a>

# 参与 Couchy Launcher 贡献

感谢你的帮助！Couchy 采用 GPLv3——提交贡献即表示你同意以相同许可证发布你的改动。

[English](#english) · [**中文**](#chinese)

## 构建

环境要求：**JDK 17**、**Android SDK**（API 34），以及 Android Studio（Ladybug 及以上）或命令行。仓库中没有 `local.properties`——需让 Gradle 指向你的 SDK：

```
export ANDROID_HOME=~/Android/Sdk      # 或在 local.properties 中设置 sdk.dir
./gradlew assembleDebug                # 产物在 app/build/outputs/apk/debug/
```

通过网络在电视上安装运行：

```
adb connect <电视IP>:5555
./gradlew installDebug
adb shell monkey -p com.conreo.couchytv -c android.intent.category.LAUNCHER 1
```

**性能务必在 release 构建上测试**——debug 版 Compose 会慢好几倍。生成本地可安装、用 debug 密钥签名的 release：

```
./gradlew installRelease -PlocalSign
```

（不加 `-PlocalSign` 时 release 是**未签名**的，这正是 F-Droid 自行构建并签名的方式。）

## 项目结构

全部为 Kotlin + Jetpack Compose for TV（`androidx.tv:tv-material`），最低 SDK 21。

```
app/src/main/java/com/conreo/couchytv/
  MainActivity.kt          入口、HOME intent-filter、包变化后重新扫描
  Actions.kt               启动 / 应用信息 / 卸载 / 强制停止 / 设置 意图
  data/Config.kt           LauncherConfig（@Serializable）+ DataStore 持久化
  data/AppRepository.kt      应用扫描、自动归类、分区分组、图标缓存
  data/BuiltinAerials.kt     内置航拍清单加载
  data/Status.kt            网络 + VPN 状态流
  ui/LauncherScreen.kt      启动器主体：布局、壁纸（ExoPlayer）、移动模式
  ui/SettingsSheet.kt       方向键设置面板（子页面带动画）
  ui/SetupWizard.kt         首次运行向导（默认主页 + VPN 步骤）
  ui/AppCard.kt、Menus.kt、StatusBar.kt、Theme.kt、Icons.kt
app/src/main/res/          字符串（values + 17 种语言）、图形、主题
```

## 约定

- **遥控优先：** 每个控件都必须仅用方向键即可到达。文字输入只在专门的对话框中进行（见 `RenameDialog`）。
- **不硬编码面向用户的文本**——放入 `res/values/strings.xml` 并用 `stringResource(...)` 引用。默认分区名与壁纸名也要本地化。
- **与周边风格一致**——代码偏好小而带注释、自解释的 composable。注释解释*为什么*，而非*是什么*。
- **状态**存于 `LauncherConfig`，通过 `ConfigStore` 持久化。新增配置字段需给出合理默认值（反序列化会忽略未知键，因此旧安装仍兼容）。
- 依赖保持精简且开源（本项目上架 F-Droid——不含 Google Play 服务，不含追踪器）。

## 翻译

添加或修正某语言，把 `app/src/main/res/values/strings.xml` 复制为 `values-<代码>/strings.xml`，并翻译其中的*值*（保留 `name="..."` 键）。Android 会自动选择对应文件；英文为回退。

现有语言：`ar de es fr hi in it ja ko nl pl pt ru th tr vi zh`（外加英文）。

通用符号（`0.5×`、`◄` / `►`）无需翻译。

## Pull Request

1. 改动保持聚焦；说明*做了什么*以及*为什么*。
2. 确保 `./gradlew assembleDebug` 通过，且应用能在真实（或模拟）Android TV 上运行。
3. UI 改动请附截图。
4. 新的面向用户字符串必须加入 `values/strings.xml`（最好也加入 `values-fr`、`values-zh`）。

## 发布与 F-Droid

发布在默认分支上打 `vX.Y` 标签。F-Droid 构建配方位于 [`fdroid/com.conreo.couchytv.yml`](fdroid/com.conreo.couchytv.yml)。提交步骤见该文件头部说明。
