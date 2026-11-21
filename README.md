# 内存监控（MemMonitor）

安卓实时运行内存监控小工具：整机内存实时曲线 + 通知栏常驻显示 + 各应用内存占用排行（PSS）+ 可选悬浮窗。

- 系统要求：Android 8.0（API 26）及以上
- 整机监控：无需 root、无需任何特殊权限
- 各应用排行：需要 Shizuku（免费开源、免 root）；未激活时自动降级，只影响排行页，整机监控照常
- 语言：Kotlin，无第三方图表库，曲线为自绘控件

> ⚠️ 诚实声明：本项目代码与构建配置经过静态自检，但**未经真机编译与运行验证**。首次云端构建若报错，把 Actions 日志发回来即可定位修复。

---

## 一、功能与数据来源

| 功能 | 数据来源 | 权限要求 |
|---|---|---|
| 整机总内存/已用/可用/百分比 | `ActivityManager.MemoryInfo`（系统 API） | 无 |
| Swap 占用 | `/proc/meminfo` | 无 |
| 低内存状态警告 | 系统 `lowMemory` 标志 | 无 |
| 实时曲线（最多 300 点） | 上述采样历史 | 无 |
| 通知栏常驻显示 | 前台服务 | Android 13+ 需通知权限（应用内引导） |
| 各应用 PSS 排行 | Shizuku 以 shell 权限执行 `dumpsys meminfo` | Shizuku 激活 + 授权本应用 |
| 悬浮窗 | 系统悬浮窗 API | 「显示在其他应用上层」（应用内引导） |
| 开机自动监控 | 开机广播 | 可选开关，部分机型还需系统自启动白名单 |

采样间隔：整机 1/2/5 秒可调（默认 2 秒）；排行 15/30/60 秒可调（默认 30 秒，`dumpsys meminfo` 开销较大，不宜过密）。

**为什么排行需要 Shizuku**：Android 7 起系统禁止普通应用读取其他进程的内存数据，这是系统级限制【已验证】。Shizuku 通过 adb 权限级服务代为执行系统命令，免 root 即可拿到真实 PSS。

---

## 二、云端构建 APK（推荐，本机零安装）

前提：一个 GitHub 账号（免费）。

1. 登录 GitHub → 右上角 `+` → **New repository**，名字随意（如 `mem-monitor`），选 Private 或 Public 均可，**不要**勾选生成 README（本地已有）。
2. 把本文件夹（`内存监控` 目录下的全部内容，含隐藏的 `.github` 文件夹）上传到仓库：
   - 网页方式：仓库页 → **Add file → Upload files**，拖入全部文件。注意 `.github` 是隐藏文件夹，Windows 资源管理器需开启「显示隐藏的项目」；若网页拖拽不便，推荐用 GitHub Desktop 或 git 命令行。
   - git 方式（在本文件夹内执行）：
     ```
     git init
     git add .
     git commit -m "init"
     git branch -M main
     git remote add origin https://github.com/你的用户名/mem-monitor.git
     git push -u origin main
     ```
3. 推送后自动触发构建：仓库页 → **Actions** 标签 → 「构建 APK」工作流。首次约 5~10 分钟。
4. 构建成功后进入该次运行页面，底部 **Artifacts** 区域下载「内存监控-APK」压缩包，解压得到 `app-release.apk`。

> 签名说明：release 包使用调试签名（构建环境自动生成）。**不同次云端构建的签名不同**：覆盖升级前需先卸载旧版（`adb uninstall com.memmonitor` 或手机上直接卸载）；同一次构建的 APK 可反复覆盖安装。这是个人自用工具的分发方式，**不适用于上架应用商店**。

### 备选：本地 Android Studio 构建

用 Android Studio（Ladybug 或更新版本）打开本文件夹，等待 Gradle 同步完成，菜单 Build → Build App Bundle(s)/APK(s) → Build APK(s)。需要本机 JDK 17（Android Studio 自带）。

---

## 三、安装到手机

### 方式 A：数据线 + adb（你的电脑已开 USB 调试，推荐）

1. 下载 Google 官方 platform-tools（仅约 5MB，含 adb）：
   https://developer.android.com/tools/releases/platform-tools
   解压到任意目录，例如 `D:\platform-tools`。
2. 手机保持 USB 连接，PowerShell 执行（按实际解压路径调整）：
   ```powershell
   D:\platform-tools\adb.exe devices
   ```
   首次连接手机上会弹「允许 USB 调试」，勾选始终允许并确定。列表出现设备序列号即连接成功。
3. 安装 APK（按实际下载路径调整）：
   ```powershell
   D:\platform-tools\adb.exe install -r "C:\你的下载目录\app-release.apk"
   ```
   出现 `Success` 即安装完成。

### 方式 B：直接传文件

把 APK 通过微信/QQ/数据线传到手机，点击安装，允许「安装未知来源应用」。

---

## 四、激活 Shizuku（各应用排行必需）

你手机上已安装 Shizuku，只需激活并授权：

### 安卓 11 及以上：无线调试（无需电脑）

1. 手机设置 → 开发者选项 → 开启「无线调试」。
2. 打开 Shizuku → 「通过无线调试启动」→ 按提示配对（输入无线调试页面显示的配对码）。
3. 启动成功后 Shizuku 首页显示「正在运行」。

### 任意安卓版本：电脑 adb 激活（你已连数据线，一条命令）

```powershell
D:\platform-tools\adb.exe shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
```

> 注意：非 root 方式激活的 Shizuku **每次手机重启后需重新激活**（系统限制）。用上面的 adb 命令或无线调试均可。

### 授权本应用

打开「内存监控」→ 排行卡片右侧点「授权」→ Shizuku 弹窗选「允许」。下一轮排行刷新（默认 30 秒内）即显示真实数据。

---

## 五、使用要点与已知限制

1. **保活**：监控依赖前台服务。国产 ROM（小米/华为/OPPO/vivo 等）请在系统设置中把本应用加入电池优化白名单/允许后台运行，否则锁屏后可能被杀。
2. **排行数据口径**：PSS（比例分摊物理内存）是安卓官方推荐的跨应用内存比较口径；多进程应用（如微信）会按进程分行显示，同一应用的多个进程需自行加总。
3. **排行延迟**：`dumpsys meminfo` 本身耗时 1~3 秒，排行是周期快照而非逐秒实时；整机曲线才是逐秒实时。
4. **悬浮窗**：开启前需授予「显示在其他应用上层」；胶囊可拖动，占用 ≥75% 变黄、≥90% 变红。
5. **开机自启**：应用内开关只接收开机广播，部分机型还需在系统「自启动管理」中放行。
6. **Shizuku 兼容性**：依赖 `newProcess` 接口（官方已标记 deprecated，13.1.5 仍可用，官方计划 API 14 移除）。若未来 Shizuku 大版本升级导致排行失效，需迁移到 UserService 方案；整机监控不受任何影响。

## 六、项目结构

```
内存监控/
├── .github/workflows/build.yml   # GitHub Actions 云端构建
├── settings.gradle.kts           # 仓库与模块声明
├── build.gradle.kts              # 插件版本（AGP 8.5.2 / Kotlin 1.9.24）
├── gradle.properties
├── app/
│   ├── build.gradle.kts          # 依赖、签名、SDK 版本
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/memmonitor/
│       │   ├── core/             # 采样服务、Shizuku 排行、悬浮窗、设置、开机
│       │   ├── ui/               # 主界面、曲线控件、排行适配器
│       │   └── util/             # 格式化
│       └── res/                  # 布局、图标、文案、主题
└── README.md
```
