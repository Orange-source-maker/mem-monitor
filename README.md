# 内存监控

只是一个看手机运行内存的小工具，练手的小项目

## 功能

- 整机内存实时刷新：占用百分比、已用/可用/总量、Swap，可选1/2/5 秒间隔
- 实时曲线：最近 300 个采样点，自绘控件，不引用第三方图表库
- 通知栏进度条：整条代表总内存，蓝色部分是已用占比
- 各应用内存排行：按系统 PSS 数据排序，多进程应用分行显示（需要 Shizuku，见下文）
- 悬浮窗：可拖动的小胶囊，松手自动吸到屏幕边缘，轻点可关闭；占用高了会变黄变红
- 开机自动监控（可选）

## 下载安装

去 [Releases](../../releases/latest) 下最新的 `MemMonitor-x.x.apk`

升级为覆盖安装，不用卸载。

要求 Android 8.0 以上。

## 如何使用

打开 App 点「开始监控」，允许通知权限，曲线和通知栏会开始工作。这部分不需要任何额外配置

想看各应用排行的话，需要装 Shizuku

## Shizuku

Android 7 之后系统不让普通应用随意读取进程的内存，这是系统限制，绕不开。

排行功能通过 [Shizuku](https://shizuku.rikka.app/)拿系统数据。整机监控不依赖它，不装 Shizuku 也能正常用。

1. 从 [Shizuku 官方 Releases](https://github.com/RikkaApps/Shizuku/releases) 下载安装
2. 激活，二选一：
   - 安卓 11 以上：打开 Shizuku，选「通过无线调试启动」，按提示在开发者选项里开无线调试、配对
   - 或者电脑 adb：`adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
3. 回到本 App，排行卡片点「授权」，Shizuku 弹窗点允许

[提醒]非 root 方式激活的 Shizuku，手机每次重启后要重新激活。这是系统限制，不是本 App 的问题。

## 常见问题

**排行显示未激活/未授权**
按上面步骤激活 Shizuku 并授权。手机重启后 Shizuku 重新激活一下，App 这边下一轮刷新就恢复。

**悬浮窗开不了**
开启「显示在其他应用上层」权限，App 里的开关会自动跳到系统设置页。

**数据问题**
整机数据来自系统 ActivityManager，排行来自系统 dumpsys meminfo 的 PSS，是系统自己的统计口径。多进程应用（比如微信）按进程分行显示，要看整个应用需要自行加总。

## 隐私

这个应用没有网络权限，系统层面联不了网，数据出不了手机

`adb shell dumpsys package com.memmonitor` 可以看权限清单。没有账号、没有统计、没有广告
源码全部公开，Shizuku 授权随时可以在它的应用里撤销。

签名密钥随仓库公开，请不要自行魔改后上架应用商店。

## 开发说明

本项目使用 AI 辅助编写，经真机测试验证。(已测 : 小米，魅族，vivo)

推送代码后 GitHub Actions 自动构建并发布到 Releases

本地构建用 Android Studio 打开即可（JDK 17，AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.7）。

```
app/src/main/java/com/memmonitor/
├── core/   # 前台采样服务、Shizuku 排行、悬浮窗、设置、开机接收
├── ui/     # 主界面、自绘曲线、排行列表
└── util/   # 格式化
```

## 许可

[MIT](LICENSE)
