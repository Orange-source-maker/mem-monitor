# 内存监控（MemMonitor）

一款轻量的安卓运行内存实时监控工具。**无广告、无网络权限、数据不出手机**。

![平台](https://img.shields.io/badge/平台-Android%208.0%2B-blue) ![许可](https://img.shields.io/badge/源码-公开可审计-green)

## 功能

- **整机内存实时监控**：占用百分比、已用/可用/总内存、Swap、低内存警告，1/2/5 秒刷新
- **实时曲线**：最近 300 个采样点的占用走势，自绘控件、零第三方图表库
- **通知栏进度条**：整条 = 总内存，蓝色部分 = 已用占比，常驻显示随时可看
- **各应用内存排行**：按真实 PSS 排序，多进程应用分行显示（需 Shizuku，见下文）
- **悬浮窗**：可拖动小胶囊，占用 ≥75% 变黄、≥90% 变红（可选）
- **开机自动监控**（可选）

## 下载

前往 **[Releases 页面](../../releases/latest)**，下载最新的「内存监控-x.x.apk」，**无需登录**。

传到手机点击安装，允许「安装未知来源应用」即可。覆盖升级无需卸载。

## 快速上手

1. 打开 App → 点「开始监控」→ 允许通知权限 → 整机曲线与通知栏进度条开始工作（**此部分不需要任何额外配置**）
2. 想要各应用排行 → 安装并激活 Shizuku（下一节）→ 回 App 点「授权」→ 允许

## Shizuku 激活（仅各应用排行需要）

Android 7 起系统禁止普通应用读取其他进程内存，排行功能通过 [Shizuku](https://shizuku.rikka.app/)（免费开源、**免 root**）以 adb 权限读取系统数据。整机监控不依赖它。

1. 从 [Shizuku 官方 Releases](https://github.com/RikkaApps/Shizuku/releases) 下载安装
2. 激活（二选一）：
   - **安卓 11+（推荐）**：打开 Shizuku →「通过无线调试启动」→ 按提示在开发者选项开启无线调试并配对
   - **电脑 adb**：`adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
3. 回到本 App → 排行卡片点「授权」→ Shizuku 弹窗点「允许」

> 注意：非 root 方式激活的 Shizuku **每次重启手机后需重新激活**（系统限制，非本 App 问题）。

## 常见问题

**锁屏后监控停了？**
国产 ROM 后台管理较严。把本应用加入电池优化白名单/允许后台运行（设置 → 电池 → 应用耗电管理）。

**排行显示「未激活/未授权」？**
按上面步骤激活 Shizuku 并授权。Shizuku 重启后本 App 会自动恢复连接，下一轮刷新即出数据。

**悬浮窗开不了？**
需先授予「显示在其他应用上层」权限，App 内开关会自动跳转到系统设置页。

**数据准吗？**
整机数据来自系统 `ActivityManager.MemoryInfo`；排行来自系统 `dumpsys meminfo` 的 PSS（安卓官方推荐的跨应用内存比较口径）。多进程应用（如微信）按进程分行，需自行加总。

## 隐私与安全

- **无 INTERNET 权限**：系统层面禁止联网，任何数据无法离开手机（可自行用 `adb shell dumpsys package com.memmonitor` 核验权限清单）
- 无账号、无统计、无广告、无第三方追踪
- 全部源码公开可审计；Shizuku 授权可随时在其应用内撤销
- 固定签名分发：密钥库随仓库公开，属个人开源工具的常规做法，**不适用于上架应用商店**

## 从源码构建（开发者）

推送代码后 GitHub Actions 自动构建并发布到 Releases；也可本地用 Android Studio 打开构建（JDK 17，AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.7）。

```
app/src/main/java/com/memmonitor/
├── core/   # 前台采样服务、Shizuku 排行、悬浮窗、设置、开机接收
├── ui/     # 主界面、自绘曲线、排行列表
└── util/   # 格式化
```
