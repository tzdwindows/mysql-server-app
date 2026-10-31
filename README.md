# MySQL 8.0 Server & Lab for Android (arm64-v8a)

> **Android 原生 MySQL 8.0.40 独立服务器与 Claude 极简交互式 SQL 工作台**  
> **作者 (Author)**: `tzdwindows 7`

[![Release](https://img.shields.io/github/v/release/tzdwindows/mysql-server-app?style=flat-square&color=D97757)](https://github.com/tzdwindows/mysql-server-app/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B%20(API%2029%2B)-2E7D5B?style=flat-square)](https://github.com/tzdwindows/mysql-server-app)
[![Arch](https://img.shields.io/badge/Architecture-arm64--v8a-3B6E8C?style=flat-square)](https://github.com/tzdwindows/mysql-server-app)
[![License](https://img.shields.io/badge/License-GPLv2-B8732A?style=flat-square)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html)

---

## 📖 项目简介 (Introduction)

**MySQL 8.0 Server & Lab** 是一套专为 Android 移动平台量身定制的原生级关系型数据库解决方案。借助 Android NDK r27c，直接将官方 **Oracle MySQL 8.0.40 Community Server** 内核及 InnoDB 引擎编译为原生 64 位 ELF 动态库 (`libmysqld.so`)，在 Android 设备本地独立启动 `127.0.0.1:3306` 监听端口。

配套客户端采用 **Claude 极简设计美学**（温暖纸质色调 `#FAF9F5`、高品质纯白卡片、炭黑字迹与陶土橙重点色），零外部依赖构建，具备完整的桌面级 SQL 执行与数据管理体验。

---

## ✨ 核心特性 (Key Features)

### 1. 原生 MySQL 8.0.40 完整引擎支持
- **真正全功能 SQL**：支持 InnoDB 事务、ACID 保证、外键约束、行级锁与死锁检测。
- **现代 SQL 语法**：支持窗口函数（Window Functions）、递归公用表表达式（Recursive CTE）、JSON 字段提取与函数（`JSON_OBJECT`, `JSON_EXTRACT`, `JSON_PRETTY`）。
- **对象与过程**：支持存储过程（Stored Procedures）、自定义函数（Functions）、触发器（Triggers）、视图（Views）与 `EXPLAIN ANALYZE` 执行计划。
- **自动初始化**：首次启动自动调用 MySQL 数据字典初始化引擎生成系统库，并创建默认 `root` 用户。

### 2. Claude 极简主义人机交互设计
- **温润雅致质感**：采用 Claude 温暖象牙纸面背景 (`#FAF9F5`)、纯白容器卡片 (`#FFFFFF`)、1dp 精细暖灰边框 (`#E4E1DA`) 与深炭黑墨水文字 (`#141413`)，告别视觉疲劳。
- **硬件级圆角裁剪**：通过 `ViewOutlineProvider` 进行硬件加速轮廓裁剪，彻底杜绝圆角与内容溢出。
- **双向滚动可交互表格**：
  - 真正 2D 滚动（表头固定在顶部，支持大容量行自由上下滑动与横向滚动）。
  - **轻触任意单元格**：直接复制对应字段内容并弹出反馈提示。
  - **长按任意单元格**：触发触觉震动反馈，调出整行结构化数据抽屉，支持逐字段复制或一键复制整行 TSV。
  - **100% 全屏放大查看**：点击右上角「放大」按钮，立即展开铺满全屏的数据浏览视图。

### 3. 后台常驻服务与跨进程保活
- **前台保活服务**：服务启动时常驻系统通知栏，显示 `"mysql服务器已启动，点击管理"`，熄屏或切换至后台运行依然稳定在线。
- **异常恢复与孤儿进程守护**：内置自动扫描与探测机制，避免断电或闪退导致端口占用或 `ibdata1` 锁死错误。

### 4. 键盘鼠标与横竖屏响应式适配
- **自适应布局**：竖屏模式采用紧凑型底部导航栏，横屏 / 平板模式自动切换为左侧功能导航导轨（Navigation Rail），工作台支持左右双栏分屏。
- **物理快捷键完美支持**：
  - `Ctrl + Enter` / `F5`：运行当前选中或全部 SQL。
  - `Tab`：智能插入 4 空格缩进。
  - `Ctrl + /`：一键切换单行或多行 `--` 注释。
  - `Ctrl + L`：清空编辑器。
  - `Ctrl + H`：打开执行历史记录面板。
  - `Ctrl + 1 ~ 4`：在「服务器 / 工作台 / 数据浏览 / 关于」四大模块间快速切换。
  - `Esc` / `返回键`：快速关闭全屏表格或当前遮罩弹窗。

---

## 📱 界面预览 (Screenshots)

| 服务器控制台 | SQL 交互式工作台 |
| :---: | :---: |
| 实时状态徽标 · QPS 曲线 · 运行时间 | 语法高亮 · 多结果集 Tab · 放大全屏 |

| 数据结构浏览 | 关于与快捷键指南 |
| :---: | :---: |
| 库表结构 · DDL · 索引 · 数据预览 | 架构说明 · 快捷键一览 · 作者署名 |

---

## 📦 下载与安装 (Downloads)

可在本项目的 **[GitHub Releases](https://github.com/tzdwindows/mysql-server-app/releases)** 页面直接下载预编译打包并签名的 APK 文件：

- 🚀 **[mysql-server-app.apk](https://github.com/tzdwindows/mysql-server-app/releases/latest/download/mysql-server-app.apk)** (约 22.3 MB，内置 `arm64-v8a` 完整 native so)

> **系统要求**：Android 10.0 (API 29) 及以上，64 位 ARM (`arm64-v8a`) 架构设备。

---

## 🛠️ 本地构建指南 (Building from Source)

### 编译环境要求
- **Android SDK**：API 29+（需 `aapt2`, `d8`, `zipalign`, `apksigner`）
- **JDK**：OpenJDK / Oracle JDK 21+
- **构建工具**：内置 `build-apk.bat` 免 Gradle 极速编译脚本（单次编译仅需 2 秒）

### 一键构建
```cmd
cd app
build-apk.bat
```
构建完成后将在 `app/` 目录下生成通过校验的签名 APK：`app/mysql-server-app.apk`。

---

## 👥 作者与版权声明 (Author & Credits)

- **作者 (Author)**: `tzdwindows 7`
- **代码仓库**: [https://github.com/tzdwindows/mysql-server-app](https://github.com/tzdwindows/mysql-server-app)
- **底层内核**: Oracle MySQL 8.0.40 Community Server (GPLv2)
- **NDK 工具链**: Android NDK r27c (LLVM/Clang aarch64-linux-android29)
