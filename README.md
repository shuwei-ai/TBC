# 📱 TBC - TVBox AI 智能管家 / 智能遥控器

<div align="center">

![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-API%2024+-3DDC84?style=flat-square&logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![Ktor](https://img.shields.io/badge/Network-Ktor%20CIO-087CFA?style=flat-square&logo=ktor&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)

**基于大语言模型与智能 Agent 工具链调度的下一代智能电视控制终端**

</div>

---

## 📖 项目简介

**TBC (TVBox AI Companion / Controller)** 是一款专为智能电视 / TVBox 打造的 Android 智能遥控与交互中枢。区别于传统按键式遥控器，TBC 深度结合了 **大语言模型（LLM）** 与 **智能 Agent 工具链（Tool Calling）**，支持通过自然语言对话下发复杂、多步骤的电视控制与影视播放指令，实现真正的人性化智慧客厅体验。

---

## ✨ 核心特性

- 🤖 **多意图链式调度与任务规划**
  - 支持理解自然语言中的复合指令（例如：“帮我搜一下周星驰的电影并播放第一个，然后把声音调大一点”）。
  - 后端 Agent 自动分解意图，规划任务执行路径并依次调度 TV 终端执行。

- ⚡ **流式响应与工具执行可视化**
  - 基于 Server-Sent Events (SSE) 流式传输协议，实现极低延迟的打字机输出体验。
  - **Tool-Calling 过程透明化**：在聊天界面直观呈现当前正在调用的工具名称（如 `[⏳ 正在执行: search_media...]`）与执行观测反馈（Observation）。

- 🎯 **多设备动态发现与智能路由**
  - **动态发现**：点击设备选择器自动请求后端（`/v1/models`）拉取局域网/在线的可用 TV 终端列表。
  - **智能路由**：支持默认 `🌟 自动路由 (auto)` 或手动指定目标电视设备。

- 🎨 **现代化暗黑美学 UI**
  - 全面基于 **Jetpack Compose + Material 3** 构建。
  - 沉浸式暗黑风格设计（Dark Theme），视效出众。
  - 优化的软键盘避让（`imePadding` + `adjustResize`）及流式消息“死死跟随”自动置底滚动。

- ⚙️ **灵活配置与持久化存储**
  - 支持自定义后端服务地址（Server URL）与鉴权凭证（API Key）。
  - 内置参数自动持久化保存（SharedPreferences / 本地存储），开箱即用。

---

## 🛠️ 技术栈

| 模块 | 技术选型 | 说明 |
| :--- | :--- | :--- |
| **开发语言** | Kotlin | 现代、简洁、安全的 Android 开发语言 |
| **UI 框架** | Jetpack Compose + Material 3 | 声明式现代 UI 框架 |
| **异步框架** | Kotlin Coroutines (协程) | 高性能非阻塞异步调用 |
| **网络请求** | Ktor Client (CIO 引擎) | 轻量、支持 SSE 流式读取与长连接 |
| **数据序列化** | Kotlinx Serialization (JSON) | 官方高性能 JSON 解析库 |
| **最低支持** | Android 7.0 (API 24+) | 兼容绝大多数主流 Android 手机/平板设备 |

---

## 📡 通信协议与后端对接规范

TBC 客户端设计为与兼容 OpenAI 标准规范的后端服务对接（如 FastAPI / Python Agent 中控服务）：

### 1. 设备列表查询 (`GET /v1/models`)
用于拉取当前已连接或可用的电视设备列表：
```http
GET /v1/models
Authorization: Bearer <YOUR_API_KEY>
```
**响应格式**：
```json
{
  "data": [
    { "id": "living-room-tv" },
    { "id": "bedroom-tv" }
  ]
}
```

### 2. 对话与流式指令下发 (`POST /v1/chat/completions`)
客户端发送用户自然语言指令，后端以 `text/event-stream` 格式持续推送执行结果：
```http
POST /v1/chat/completions
Authorization: Bearer <YOUR_API_KEY>
Content-Type: application/json

{
  "model": "auto",
  "messages": [
    { "role": "user", "content": "打开电视并调大音量" }
  ],
  "stream": true
}
```

**SSE 推送协议支持**：
- **普通文本增量**：
  ```
  data: {"choices": [{"delta": {"content": "正在为您调节音量..."}}]}
  ```
- **工具调用中状态**：
  ```
  data: {"tool_call": {"name": "adjust_volume", "status": "running"}}
  ```
- **工具执行观测结果**：
  ```
  data: {"tool_call": {"name": "adjust_volume", "observation": "音量已提升至 25"}}
  ```
- **结束标识**：
  ```
  data: [DONE]
  ```

---

## 🚀 快速开始

### 1. 环境准备
- **Android Studio**: Ladybug / Meerkat (或更高版本，推荐最新 Canary/Stable)
- **JDK**: Java 11 或 17 / 21
- **Gradle**: 8.x +
- **Android 设备 / 模拟器**: Android 7.0 (API 24) 及以上

### 2. 编译与运行
克隆或拉取项目后，在项目根目录下通过 Gradle 构建：

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 安装到已连接的 Android 设备
./gradlew installDebug
```

### 3. 配置连接
1. 打开应用，点击右上角 **设置（⚙️）** 图标。
2. 填入您的 Agent 后端服务地址（如 `http://192.168.x.x:8000/`）以及 `API Key`。
3. 点击 **保存配置**。
4. 返回主界面，在顶部下拉框选择目标电视（或保持默认 `🌟 自动路由`），即可通过文字与智能管家互动控制电视。

---

## 📂 项目结构

```text
TBC/
├── app/
│   ├── build.gradle.kts                # 模块 Gradle 配置
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml     # 清单文件（声明 INTERNET 与 adjustResize）
│           ├── java/com/lsy/tbc/
│           │   ├── MainActivity.kt     # 主入口、UI (Compose)、网络通信与 SSE 解析
│           │   └── ui/theme/           # 主题配置 (Color, Theme, Type)
│           └── res/                    # 应用图标与资源文件
├── gradle/
│   └── libs.versions.toml              # 依赖版本管理 (Version Catalog)
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🗺️ 开源计划与生态说明

- **📱 TBC 智能遥控器客户端 (Android)**：当前仓库，已全面开源 🎉。
- **🧠 后端 Agent 服务 & 📺 TVBox 定制客户端**：目前已占位并处于准备阶段。

> [!TIP]
> **开源规划与社区互动**：
> 
> 目前后端（基于 LLM 多意图规划与 Tool-Calling 调度的 Agent 中枢）和配套的 **TVBox 电视端** 正根据社区反馈推进中。
> 
> **如果本项目点赞（Star ⭐️）、分享和讨论反馈热烈，我们将陆续开源全套后端 Agent 服务代码以及 TVBox 客户端！**
> 
> 欢迎大家点击右上角 **Star ⭐️** 支持与分享，你的支持是我们持续开源与迭代的最大动力！

---

## 💬 获取使用方式

如果您需要使用本智能遥控器系统或体验相关功能，请扫描下方二维码关注微信公众号，联系获取详细的使用方式与体验配置：

<div align="center">

<img src="app/src/main/res/raw/qrcode_for_gh_4d02030783b8_344.jpg" width="220" alt="微信公众号二维码" />

<p><em>扫码关注公众号，回复或联系获取使用方式与交流反馈</em></p>

</div>

---

## 📄 开源许可证

本项目采用 [MIT License](LICENSE) 授权许可。


