# CloneChooser · 分身跳转选择

**有分身才选择，没有分身直接打开。**

CloneChooser 是一个 LSPosed 模块：从主空间或原生分身中的应用跳转到另一个应用时，如果目标在多个可用空间中存在，则弹出「主应用 / 分身应用」菜单；只有一个可用目标时直接打开。

An LSPosed module that asks which app instance to open only when a native Android clone is available. Otherwise, the original launch continues automatically.

## 下载

从 [Releases](https://github.com/renzhezhu1234/CloneChooser/releases) 下载 `CloneChooser-0.1.5.apk`。校验值与 APK 一起发布。

当前为早期版本。已在 **Pixel 10 Pro Fold / Android 17 / LSPosed 2.2.0 (7854)** 上验证「什么值得买 → 京东」以及「分身拼多多 → 主空间微信」；其他来源、目标或 ROM 需要各自测试。

## 使用

1. 安装 APK，在 LSPosed 中启用「分身跳转选择」。
2. 勾选 **系统框架** 和 **发起跳转的来源应用**。例如「什么值得买 → 京东」只需勾选系统框架、什么值得买；京东作为目标无需勾选。
3. 打开模块的「选择来源应用」，允许同样的来源。默认允许微信、什么值得买、拼多多。
4. 首次启用系统作用域或更新模块后重启手机。

0.1.5 增加双向选择，例如「分身拼多多 → 主微信」。分身发起跳转时，需要在 LSPosed 勾选该分身的来源应用，并在**分身空间内安装、打开模块设置，允许该来源**。各空间的允许列表分别保存，默认新增拼多多，已有自定义选择不会自动改变。更新后必须重启，避免系统端和来源端版本不一致。

0.1.5 已通过构建、lint、设备框架回归测试和「分身拼多多 → 主空间微信」端到端验证。0.1.4 不支持从分身发起跳转。

目标包名不设固定列表。京东、淘宝等仅为示例；是否出现菜单取决于目标 Activity 是否存在于可用的原生分身中。

| 目标状态 | 行为 |
| --- | --- |
| 只有一个可用目标空间 | 直接打开；选择当前空间时保留原跳转 |
| 有多个可用目标空间 | 显示主应用及分身选项，标注当前空间 |
| 用户取消选择 | 不打开目标 |
| 已选择的分身启动失败 | 显示错误，不自动换成主账号 |

## 支持范围

- 使用原生 `android.os.usertype.profile.CLONE` 的分身；不包括工作资料、第三方容器或厂商改包名的双开方案。
- 从主用户 0 或其原生分身发起的常规 `startActivity` 跨应用跳转，包括显式 Activity 和可解析到唯一目标的深链。
- 保留原 Intent 的链接、extras、ClipData 与启动参数；跨用户启动会增加新任务标志并修正内容 URI 的来源用户。
- 分身须启用、运行、解锁且未静默。目标 Activity 须启用、可导出、不要求额外组件权限。

目前不拦截 `startActivityForResult`、`FLAG_ACTIVITY_FORWARD_RESULT`、批量启动或 `PendingIntent`；不拦截应用内部页面和系统 Resolver/Chooser。当前系统入口按 Android 17 实机验证，不能保证所有 Android 版本通用。

## 排查

在模块中打开「查看检测记录」。ADB 用户也可读取同一份记录：

```sh
adb shell dumpsys activity provider io.github.clonechooser/.ConfigProvider
```

检测不可用时会保留原跳转并提示一次。若没有记录，先检查系统框架和来源作用域是否生效。其他跳转管理模块可能先行拦截；请避免多个模块同时处理同一次跳转。

设置由系统端读取，来源应用无需查询模块 Provider；显式目标也不依赖来源端的包查询结果，减少应用隐藏规则造成的冲突。

## 构建

需要 JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0。Gradle Wrapper 固定为 8.11.1，并校验下载内容。

在本地 `local.properties` 中配置 `sdk.dir`，或设置 `ANDROID_HOME`，然后运行：

```sh
./gradlew assembleDebug assembleRelease lintDebug
```

Windows 使用 `gradlew.bat`。Debug APK 和未签名 Release APK 输出到 `app/build/outputs/apk/`。GitHub Actions 同样执行构建及 lint，并上传构建产物。

CI 的 Debug APK 与 Releases 的 APK 签名不同，不能直接相互覆盖安装。公开 Release 附件保留经过真机验证的开发签名；后续同渠道更新需使用相同签名密钥。签名密钥不在仓库中。

另提供独立 Windows 构建脚本，使用 SDK 自带的 aapt2、D8、zipalign、apksigner：

```powershell
.\build.ps1 -Jdk 'C:\path\to\jdk17' -Sdk 'C:\path\to\android-sdk' -XposedJar 'C:\path\to\api-82.jar'
```

Xposed API 82 从[官方 Maven 仓库](https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar)获取，仅供编译，不打入 APK。

## 测试

`tests/` 内包含在真实 Android 框架运行的回归测试：

- `PayloadTest`：26 项链接编码、extras、ClipData、flags、原对象不变、主空间目标和反向 URI 来源用户校验。
- `ProfilePolicyTest`：16 项来源 UID、用户匹配、主用户/原生分身关系和菜单标签检查；拒绝工作资料、其他用户组、隔离 UID 和 SDK 沙箱 UID。
- `HookContractTest`：加载设备实际 `services.jar`，验证系统入口的完整方法签名。
- `ReceiverTransportTest`：复现模块匿名回调类的跨进程解码失败，验证框架 ResultReceiver 转换后能传递分身结果。

连接开启 USB 调试的测试设备后运行：

```powershell
.\scripts\test-device.ps1 -Jdk 'C:\path\to\jdk17' -Sdk 'C:\path\to\android-sdk'
```

这些测试不自动启用 LSPosed、不修改应用数据，也不能替代真实应用跳转的端到端验证。

## 实现与权限

来源进程保留原始启动请求，由系统进程查询主空间及其原生分身。系统桥核对真实 Binder UID、来源用户、来源空间的允许列表、目标组件和分身归属后，仅对这次跨空间启动跳过 incoming-user 检查；其余 Android 组件访问、URI 授权和后台启动检查继续执行。协议版本不匹配时保留原跳转，避免更新后未重启造成错误路由。

应用无联网权限。`QUERY_ALL_PACKAGES` 用于设置页的来源应用列表。诊断只保留流程状态、包名、用户编号与异常类型，不记录账号、原始链接或 Intent extras。

## 许可证

本项目原始代码采用 [MIT License](LICENSE)。第三方构建组件见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。项目与 Android、LSPosed 及示例应用的开发方无隶属关系。
