# 轻记 · QingJi

原生安卓本地记账应用。无广告、无注册、无网络权限。

[下载最新版 APK](https://github.com/Mommrm/Qingji/releases/latest) · [版本记录](https://github.com/Mommrm/Qingji/releases) · [开发与构建](docs/DEVELOPMENT.md)

## 功能

- 收入、支出、退款，首页左滑账单可备注、删除，支持删除撤销。
- 银行卡、微信、支付宝、现金等资产，记账联动余额，支持转账与校准。
- 日/周/月/年与自定义区间统计、分类分析、预算、攒钱目标；记账时可标记「还款」或关闭「计入预算」，避免还款重复占用预算。
- 五套预设与自定义主题，浅色/深色/跟随系统。
- 本地JSON备份恢复、CSV导出、闹钟式记账提醒（每天／工作日／周末／自定义星期）。

## 安装

最低 Android 8.0（API 26）。在 Releases 下载 APK；升级前先导出完整JSON备份，再覆盖安装，勿卸载旧版。

资产为手工记录的账面余额，不连接银行或支付平台，不移动真实资金。

## 构建

Kotlin 2.0.21 · Jetpack Compose · Room 2.6.1 · DataStore · JDK 17 · Gradle 8.9 · Android SDK 35。

```bash
./gradlew testDebugUnitTest lintRelease assembleDebug
```

Windows 可使用 `gradlew.bat`，或参考 [开发说明](docs/DEVELOPMENT.md)。签名材料不包含在仓库中；自行构建的调试包与正式APK签名不同。

## 验证与设计

- [本版验证报告](TEST-REPORT.md)：41项业务测试通过；当前未执行安卓真机手势测试。
- [资产规则](docs/QingJi-Assets-Design-V1.md)
- [快速输入](docs/QingJi-Fast-Input-V1.md)
- [首页左滑](docs/QingJi-Swipe-Actions-V1.md)
- [主题设计](docs/QingJi-UI-Theme-V1.md)
- [交互预览源码](design/QingJi-UI-1.3.1.html)

第三方图标许可见 [licenses](licenses/)。微信、支付宝标识仅用于识别用户手工记录的账户类型。
