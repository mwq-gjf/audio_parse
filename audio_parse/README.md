# 听见 - 音频转文字应用

一款基于Android的离线音频转文字应用，支持视频音频提取、语音识别和AI智能总结。

## ⚠️ 免责声明

**本项目由AI生成，仅供学习和参考使用。**

- 本项目完全由人工智能（AI）生成，开发者不对代码的正确性、安全性或适用性做任何保证。
- 用户可以自由使用、修改和分发本项目，但需自行承担所有风险。
- 开发者不对使用本项目产生的任何直接或间接损失负责。
- 使用本项目前，请确保遵守当地法律法规。

## ✨ 功能特性

### 核心功能
- 🎥 **视频音频提取** - 支持从各种视频格式中提取音频
- 🎤 **离线语音识别** - 使用Vosk引擎，无需联网即可转写
- 🤖 **AI智能总结** - 支持接入大语言模型API进行文本总结
- 📝 **结果管理** - 支持复制、保存、分享转写结果
- 💾 **音频保存** - 可保存提取的音频文件
- 📜 **转写历史** - 自动保存最近10条转写记录，支持快速加载和删除

### 技术特性
- 📱 **Material Design** - 现代化的UI设计
- 🔄 **实时进度** - 显示转写和下载进度
- 📊 **长音频处理** - 支持分段处理长音频文件
- 🎯 **多模型支持** - 提供三种不同大小的语音识别模型

### 软件优势
- 🔒 **完全本地化** - 语音识别完全在本地运行，无需上传数据到服务器，保护隐私安全
- 🚫 **无需注册** - 无需注册账号，下载即可使用，无任何门槛
- 🎯 **无广告干扰** - 界面清爽，没有任何广告，专注核心功能
- 📖 **功能精简** - 界面简洁直观，操作流程清晰，新手也能快速上手
- 💰 **免费使用** - 核心功能完全免费，无内购，无订阅

### 关于AI总结功能
- 🤖 **AI智能总结** - 需要配置您自己的大模型API（支持OpenAI兼容接口）
- 🔑 **您的API Key** - 需要填写您自己的API Key，应用不会收集或上传您的密钥
- 🌐 **网络请求** - AI总结功能需要联网调用大模型服务，会产生API调用费用
- 💡 **可选功能** - AI总结为可选功能，不配置不影响核心的语音转文字功能

## 🛠️ 技术栈

- **开发语言**: Java
- **最低SDK**: Android 5.0 (API 21)
- **目标SDK**: Android 13 (API 33)
- **语音识别**: Vosk Android SDK
- **音频处理**: MediaCodec, MediaExtractor
- **网络请求**: OkHttp
- **UI框架**: Material Design Components

## 📋 环境要求

### 开发环境
- Android Studio Arctic Fox 或更高版本
- JDK 11 或更高版本
- Gradle 7.0 或更高版本

### 运行环境
- Android 5.0 (API 21) 或更高版本
- 至少 100MB 可用存储空间（用于模型）
- 建议使用 ARM64 架构设备

## 🚀 安装与打包

### 从源码构建

#### 1. 克隆项目
```bash
git clone https://github.com/your-username/audio_parse.git
cd audio_parse
```

#### 2. 打开项目
- 启动 Android Studio
- 选择 "Open an existing Android Studio project"
- 选择项目根目录

#### 3. 同步项目
- 等待 Gradle 同步完成
- 如果提示下载依赖，请确认下载

#### 4. 构建APK

**方式一：通过Android Studio构建**
1. 点击菜单 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
2. 等待构建完成
3. 点击通知中的 `locate` 链接，找到生成的APK文件
4. APK位置：`app/build/outputs/apk/debug/app-debug.apk`

**方式二：通过命令行构建**
```bash
# Debug版本
./gradlew assembleDebug

# Release版本（需要签名配置）
./gradlew assembleRelease
```

构建完成后，APK文件位于：
- Debug版本：`app/build/outputs/apk/debug/app-debug.apk`
- Release版本：`app/build/outputs/apk/release/app-release.apk`

#### 5. 安装到设备
```bash
# 通过adb安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 或直接将APK传输到设备安装
```

### 生成签名APK（用于发布）

#### 1. 生成签名密钥
```bash
keytool -genkey -v -keystore audio-parse.keystore \
  -alias audio-parse \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

#### 2. 配置签名
在 `app/build.gradle.kts` 中添加：
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("audio-parse.keystore")
            storePassword = "your_store_password"
            keyAlias = "audio-parse"
            keyPassword = "your_key_password"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

#### 3. 构建Release版本
```bash
./gradlew assembleRelease
```

## 📖 使用说明

### 首次使用

1. **下载语音模型**
   - 首次打开应用会提示下载模型
   - 推荐使用"小模型"（50MB），速度快且准确率适中
   - 也可以在设置中选择其他模型

2. **配置AI总结（可选）**
   - 进入设置页面
   - 填写API URL（支持OpenAI兼容接口）
   - 填写API Token
   - 填写模型名称（如：gpt-3.5-turbo）
   - 点击"测试连接"验证配置

### 基本操作

1. **选择文件**
   - 点击"选择文件"按钮
   - 选择视频或音频文件
   - 支持常见格式：MP4, MP3, WAV, M4A等

2. **开始转写**
   - 点击"开始转写"按钮
   - 等待音频提取和转写完成
   - 实时显示转写进度和结果

3. **查看结果**
   - 转写完成后可切换"转写结果"和"AI总结"标签
   - 点击"AI总结"按钮生成智能总结

4. **保存分享**
   - 复制：复制文本到剪贴板
   - 保存：保存文本到Downloads目录
   - 保存音频：保存提取的音频文件
   - 分享：通过系统分享功能分享文本

5. **转写历史**
   - 点击结果区域右上角的历史图标查看转写记录
   - 最多保存10条历史记录
   - 点击"使用"可快速加载历史记录
   - 支持删除单条记录或清空全部历史

### 模型说明

| 模型 | 大小 | 特点 | 适用场景 |
|------|------|------|----------|
| 小模型 | 50MB | 速度快，准确率适中 | 快速转写，日常使用 |
| 标准模型 | 1.3GB | 准确率高，速度较慢 | 正式场合，重要内容 |
| 多方言模型 | 1.8GB | 支持多种方言，准确率最高 | 专业场景，方言识别 |

## 🏗️ 项目结构

```
audio_parse/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/uai/audio_parse/
│   │   │   │   ├── MainActivity.java               # 主界面
│   │   │   │   ├── SettingsActivity.java           # 设置界面
│   │   │   │   ├── AudioExtractor.java             # 音频提取器
│   │   │   │   ├── SpeechRecognizer.java           # 语音识别器
│   │   │   │   ├── ModelDownloader.java            # 模型下载器
│   │   │   │   ├── PreferencesManager.java         # 偏好设置管理
│   │   │   │   ├── TranscriptionHistoryManager.java # 转写历史管理
│   │   │   │   └── LLMClient.java                  # LLM客户端
│   │   │   ├── res/                                # 资源文件
│   │   │   └── AndroidManifest.xml
│   │   └── test/                                   # 测试代码
│   └── build.gradle.kts                            # 模块构建配置
├── gradle/                                         # Gradle配置
├── build.gradle.kts                                # 项目构建配置
└── settings.gradle.kts                             # 项目设置
```

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

1. Fork 本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

## 📄 开源协议

本项目采用 MIT 协议开源，详见 [LICENSE](LICENSE) 文件。

## 📦 第三方开源库声明

本项目使用了以下开源库，感谢开源社区的贡献：

| 库名 | 用途 | 许可证 | 项目地址 |
|------|------|--------|----------|
| Vosk | 离线语音识别引擎 | Apache 2.0 | https://github.com/alphacep/vosk-api |
| OkHttp | HTTP网络请求 | Apache 2.0 | https://github.com/square/okhttp |
| Material Components | Material Design UI组件 | Apache 2.0 | https://github.com/material-components/material-components-android |
| Gson | JSON序列化/反序列化 | Apache 2.0 | https://github.com/google/gson |
| Kotlin Coroutines | Kotlin协程库 | Apache 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| AndroidX AppCompat | Android兼容性库 | Apache 2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Lifecycle | 生命周期管理 | Apache 2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX ConstraintLayout | 约束布局 | Apache 2.0 | https://developer.android.com/jetpack/androidx |

所有上述库均采用 Apache License 2.0 许可证，允许商业使用、修改和分发。

## 🙏 致谢

- [Vosk](https://alphacephei.com/vosk/) - 离线语音识别引擎
- [Material Design Components](https://material.io/develop/android) - UI组件库
- [OkHttp](https://square.github.io/okhttp/) - 网络请求库

## 📧 联系方式

如有问题或建议，请提交 [Issue](https://github.com/your-username/audio_parse/issues)。

---

**注意：本项目由AI生成，仅供参考学习，开发者不承担任何责任。**
