# CedarManager

Cedar 文件管理器（Cedar File Manager）—— 一个面向 Android 13+ 的 Material 3 / Compose 文件管理与终端工具，主打液体玻璃质感的界面动效与 root 场景下的文件操作。

## 功能

- **文件浏览**：多存储卷（内部存储、外置存储、root 视角的 `/`）切换，路径导航、搜索、排序。
- **文件操作**：新建、重命名、复制、移动、删除、解压 / 压缩、文本编辑、APK 安装与信息查看。
- **终端**：内置终端会话，支持 root shell（libsu）与普通 shell，可执行脚本、查看输出。
- **主页仪表盘**：存储占用、设备信息、常用入口。
- **界面**：Miuix + Kyant Backdrop 提供的模糊 / 液体玻璃组件，自适应弹窗动效（见 `docs/popup-motion-fit.md`）。
- **偏好设置**：主题（浅色 / 深色 / 跟随系统）、动画速度等。

## 技术栈

| 项目 | 说明 |
| --- | --- |
| 语言 | Kotlin（JVM 17） |
| UI | Jetpack Compose（Compose Multiplatform 1.11）、Miuix KMP 0.9.3 |
| 视觉效果 | Kyant Backdrop 2.0、Shapes 1.2，原生 C++ 采样层（CMake + NDK） |
| Root | libsu 6.0 |
| 终端 | termux-app terminal-view 0.118 |
| 构建 | AGP + Gradle 9.6.1，compileSdk 37，minSdk 33 |

## 目录结构

```text
app/                     应用模块
├── src/main/java/       Kotlin 源码（com.openxida.cedarmanager）
│   ├── data/            文件系统、RootShell、终端会话、偏好设置
│   ├── domain/          数据模型与 shell 命令构建
│   ├── ui/              Compose 界面、组件、动效
│   └── nativebridge/    JNI 桥接
├── src/main/cpp/        C++ 采样层（CMake）
├── src/main/assets/     图标与开源许可
├── src/main/res/        资源、字体（MiSans）
├── src/test/            单元测试
└── src/androidTest/     设备上测试
docs/                    设计文档、字体许可
tools/                   拟合 / 分析脚本
```

## 构建

需要 JDK 21、Android SDK（compileSdk 37、build-tools 37.0.0、NDK 29.0.14206865），SDK 路径在根目录 `local.properties` 的 `sdk.dir` 中配置。

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\gradlew.bat :app:assembleDebug        # 调试包
.\gradlew.bat :app:testDebugUnitTest    # 单元测试
.\gradlew.bat :app:assembleRelease      # 发布包（需签名配置）
```

## 签名

发布包签名信息由根目录 `keystore.properties` 读取，格式：

```properties
storeFile=keystore/cedar-release.jks
storePassword=******
keyAlias=cedarmanager
keyPassword=******
```

`keystore.properties`、`keystore/` 与所有 `*.jks` 已在 `.gitignore` 中被排除，请勿提交到仓库。

## 文档

- `docs/popup-motion-fit.md`：弹窗展开 / 收起曲线的最小二乘拟合与误差。
- `docs/MiSans-license.pdf`：MiSans 字体许可。
- `app/src/main/assets/licenses/`：第三方组件许可。

## 应用内声明

本项目仅供学习与个人自用；在 root 环境下操作文件、安装应用产生的风险由使用者自行承担。
