# Whisper NDK 构建说明

由于Whisper需要使用NDK进行原生代码编译，请按照以下步骤配置：

## 1. 安装NDK

在Android Studio中：
1. 打开 `Tools` -> `SDK Manager`
2. 选择 `SDK Tools` 标签
3. 勾选 `NDK (Side by side)` 和 `CMake`
4. 点击 `Apply` 进行安装

## 2. 配置NDK版本

在 `app/build.gradle.kts` 中添加NDK配置：

```kotlin
android {
    defaultConfig {
        // ... 其他配置
        
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++11", "-O3", "-fPIC")
                cFlags += listOf("-std=c11", "-O3", "-fPIC")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_TOOLCHAIN=clang"
                )
            }
        }
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

## 3. 下载Whisper源码

运行以下脚本下载Whisper.cpp源码：

```bash
chmod +x scripts/download_whisper.sh
./scripts/download_whisper.sh
```

## 4. 构建项目

```bash
./gradlew assembleDebug
```

## 注意事项

- 首次构建可能需要较长时间，因为需要编译Whisper原生库
- 如果遇到NDK版本问题，请在 `local.properties` 中指定NDK版本：
  ```properties
  ndk.dir=/path/to/your/ndk
  ```
- 建议使用NDK版本 25.0 或更高版本
