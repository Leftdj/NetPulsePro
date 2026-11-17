# NetPulse Pro

专业网络监控与诊断工具，基于 Android 原生 Kotlin + Jetpack Compose 开发。

## 功能特性

- **实时网速**：按可配置采样间隔显示实时下载/上传速度与历史曲线，支持点按曲线查看数值
- **总览面板**：今日流量、月度套餐预警、连接质量（延迟/抖动/丢包）、内存占用、网络事件时间轴
- **应用流量统计**：按应用分别统计移动网络 / Wi-Fi 的下行与上行流量，支持日/周/月/年周期、日历选日期、搜索排序筛选
- **应用详情**：网络构成环形图、四类分项流量、近 7 天用量趋势、支持直达系统应用信息页
- **网络诊断**：Wi-Fi 链路详情（频段/代际/信道/频宽/RSSI）、IP/DNS/MTU/网关、DNS 解析对比、ICMP 延迟抖动丢包、公网 IP、网络事件时间轴
- **版本信息**：更新日志、下载站版本清单检测更新、一键下载 APK、开源地址跳转
- **主题与设置**：深色模式、刷新频率、系统应用显隐、月度套餐自定义

## 技术要点

- 使用 `NetworkStatsManager` 读取系统真实按 UID、按网络类型、按上下行的流量数据
- `TrafficStats` 差值采样实时网速，Canvas 绘制折线图
- 系统 `ping` 命令做真实 ICMP 测量；自建 DNS UDP 查询测解析延迟
- Jetpack Compose + Material3，弹簧动画与过渡效果

## 构建

环境：Android Studio（Android SDK 36）、JDK 17+

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

首次构建需在项目根目录创建 `local.properties` 并写入本机 SDK 路径：

```properties
sdk.dir=/path/to/Android/Sdk
```

## 权限说明

应用需要以下权限才能读取真实数据：

- `PACKAGE_USAGE_STATS`（使用情况访问权限）—— 需在系统设置中手动授予，用于读取各应用流量
- `ACCESS_FINE_LOCATION` / `NEARBY_WIFI_DEVICES` —— Android 10+ 读取 Wi-Fi 名称、BSSID、信号强度所需
- `INTERNET` / `ACCESS_NETWORK_STATE` —— 网络诊断与实时状态检测

所有数据均来自系统公开接口，不估算、不伪造。Android 未公开的接口项（如 MCS/NSS/SNR、整机 CPU 占用）在界面中明确标注"设备不支持"，不做填充。

## 开源地址

- 仓库：https://github.com/Leftdj/NetPulsePro
- 发行版（APK 下载）：https://github.com/Leftdj/NetPulsePro/releases

## 文档

- [CHANGELOG.md](CHANGELOG.md) —— 正式版本变更日志
- [TEST_NOTES_current.md](TEST_NOTES_current.md) —— 当前构建版本验证记录
- [docs/BUILD_HISTORY.md](docs/BUILD_HISTORY.md) —— 构建版本时间线
- [docs/RELEASE_TEMPLATE.md](docs/RELEASE_TEMPLATE.md) —— 发版标准化流程
- [design.md](design.md) —— 界面设计规范
- [LICENSE](LICENSE) —— Apache License 2.0
