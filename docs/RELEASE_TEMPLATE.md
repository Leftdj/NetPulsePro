# 发版模板

为保证项目的版本可追溯性与结构整洁，请遵循以下规范。

## 发版前检查清单

- [ ] 确定新 `versionCode`（递增整数）与 `versionName`（如 `4.1`）
- [ ] `app/build.gradle.kts` 中的 `versionCode` / `versionName` 已更新
- [ ] `app/src/main/java/com/flowstats/app/data/AppUpdate.kt` 的 `history` 列表已新增对应 `ReleaseNote`
- [ ] 本地编译通过：`./gradlew assembleDebug`
- [ ] 用 aapt 校验 APK 版本号与预期一致
- [ ] 真机安装验证核心流程（权限授予、流量统计、网络诊断）

## 发版步骤

### 1. 同步下载站版本清单

更新 `download-site/version.json`：

```json
{
  "versionCode": 20,
  "versionName": "4.1",
  "notes": "本次更新的简要说明",
  "apkUrl": "https://.../FlowStats-debug.apk?sharecode=...",
  "pageUrl": "https://.../?sharecode=...",
  "updatedAt": "YYYY-MM-DD"
}
```

> App 内「检测更新」读取此文件，而非 GitHub Releases 接口。版本号不一致会导致用户无法收到更新提示。

### 2. 构建并部署 APK

```bash
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ../download-site/FlowStats-debug.apk
```

重新部署下载站，确认 APK 直链带 `sharecode` 可正常下载（不带分享码会返回 403）。

### 3. 推送源码到 GitHub

将源码同步到 `outputs/NetPulsePro-repo/`，通过 GitHub API 推送到 `main` 分支（参见 `outputs/_push_git_tree.py`）。

### 4. 创建 GitHub Release（发行版）

1. 创建轻量标签 `v<versionName>`，指向 `main` 分支当前提交
2. 创建 Release：标题 `NetPulse Pro v<versionName>`，正文摘录 `CHANGELOG.md` 对应版本
3. 上传 APK 作为 Release 资产，供用户直接下载
4. 确认仓库主页出现「发行版」入口与版本号

### 5. 更新文档

- [ ] `CHANGELOG.md` 新增版本条目
- [ ] `docs/BUILD_HISTORY.md` 时间线新增一行
- [ ] `TEST_NOTES_current.md` 更新为当前验证版本

## 发布说明模板

```markdown
## NetPulse Pro vX.Y（build N）

### 新增
- ...

### 修复
- ...

### 说明
- 所有数据来自 Android 系统公开接口，不估算、不伪造。
- 未公开的接口项（MCS/NSS/SNR、整机 CPU 占用）在界面中标注「设备不支持」。
```

## Note

请勿在仓库根目录添加临时说明文件（如 `README_buildXXX.md`）。当前验证详情保持在 `TEST_NOTES_current.md`；构建版本迭代时，将旧版本记录归档至 `docs/BUILD_HISTORY.md`。
