# Changelog

## 0.1.5

- 支持从原生分身发起跨应用跳转，可选择主应用或同一组的其他分身。
- 按实际来源空间读取允许列表及写入诊断记录；菜单标注当前空间。
- 跨空间传递时保留实际来源用户的 URI 标记；仅一个可用目标时直接打开。
- 加入桥接协议版本检查，防止更新未重启时混用主/分身列表。
- 新增空间权限边界及反向参数传递回归测试。

已在 Pixel 10 Pro Fold / Android 17 / LSPosed 2.2.0 (7854) 上验证「分身拼多多 → 主空间微信」双向选择。

## 0.1.4

First public release.

- Show a main/clone chooser when the destination Activity exists in an active native Clone Profile; continue the original launch when no eligible clone exists.
- Configure allowed source applications; no fixed destination package list.
- Preserve the original launch payload and reject invalid cross-profile destinations.
- Read settings in system_server to avoid source-app package-visibility conflicts.
- Match the system entry point by its complete signature, including ROMs that change access modifiers during optimization.
- Convert ResultReceiver callbacks to the framework class before IPC so system_server can decode them.
- Include on-device tests for payload preservation, the system hook contract, and callback transport.

Validated end to end with SMZDM → JD on Pixel 10 Pro Fold / Android 17 / LSPosed 2.2.0 (7854). Other combinations remain unverified.
