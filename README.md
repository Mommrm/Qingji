# 轻记 · QingJi

原生安卓本地记账应用。无广告、无注册、无网络权限。

[下载最新版 APK](https://github.com/Mommrm/Qingji/releases/latest) · [版本记录](https://github.com/Mommrm/Qingji/releases) · [开发与构建](docs/DEVELOPMENT.md)

## 功能

- 收入、支出、退款，首页左滑账单可备注、删除，支持短时撤销与「我的 → 已删除账单」回收站恢复。
- 账户分资产与负债两类：资产类（银行卡／微信／支付宝／现金／其他）可分「流动资金」与「理财资金」，记账时选流动账单或理财账单，只联动对应资金池的账户；负债类（信用卡／花呗／信用支付／其他）只记欠款，不参与分池。
- 负债录入填正数即欠款，不需要负号，界面统一红色显示；账户列表按「资产账户 / 负债账户」分组小计。总资产只累加资产类账户的正余额，负债不冲减，另用独立饼图列出欠款构成；首页「资产」同样只显示余额合计。
- 资产 → 负债的转账自动标为「还款」，资产与欠款同步减少，且不允许还款超过当前欠款；反向为「借款」。两类账户均支持记账联动、转账与余额／欠款校准。
- 首页搜索覆盖全部账本（备注／分类／资产），不受当前选中月份限制。
- 日/周/月/年与自定义区间统计（趋势图标注峰值与零线）、分类分析、预算、攒钱目标；记账时可标记「还款」或关闭「计入预算」，标记随账单存本地数据库，避免还款重复占用预算。
- 五套预设与自定义主题，浅色/深色/跟随系统。
- 本地JSON备份恢复、CSV导出、闹钟式记账提醒（每天／工作日／周末／自定义星期）；备份恢复含未来日期校验与中断保护，备份格式升级到 schema 3 并兼容旧版备份。

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
