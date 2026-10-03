# Changelog

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
