# 🤖 JobFloatingAssistant (AI 求职悬浮助手)

> **基于 Android 原生无障碍与全局悬浮窗的“千岗千面”智能求职辅助 App**  
> 专为 **BOSS直聘、猎聘** 等移动端招聘场景打造：屏幕秒级提取 JD · 本地直连 DeepSeek 大模型 · 个性化定制招呼语 · 一键复制防封号 · 纯单机 0 服务器依赖。

---

## ✨ 核心特性

- 🎯 **全局轻量悬浮球**：常驻屏幕侧边缘，随意拖动吸附，不干扰正常浏览招聘软件。
- 🔍 **无障碍读屏 JD 抓取**：基于 Android 系统 `AccessibilityService`，点击悬浮球一瞬间遍历屏幕 View 节点，精准抓取岗位名、薪资与职责要求，**无需 OCR，零识别延迟**。
- ⚡ **DeepSeek / 大模型直连**：直接通过 OkHttp 发起安全加密通信，不经过任何中间服务器，**100% 保障简历隐私与极速响应**。
- 🛡️ **安全零封号风险**：完全不注入任何目标 App，不模拟点击投递，仅作为“读屏 + 话术生成 + 写入剪贴板”的辅助工具。
- 💼 **内置 7 年前端高分简历模板**：默认已预置针对 AI 落地、直播渲染与多端架构的专属亮点简历。

---

## 🚀 手机安装方法（GitHub Actions 云端自动构建）

无需在电脑上配置任何 Android Studio 或几千兆的安卓开发环境：

1. **推送代码**：每次向 `main` 分支执行 `git push`，GitHub Actions 将自动启动云端虚拟机构建。
2. **下载安装包**：
   - 打开 GitHub 仓库页面，点击顶部的 **Actions** 标签；
   - 点击最新一条绿色的构建记录；
   - 在页面底部的 **Artifacts** 区域，点击 **`JobAssistant-Debug-APK`** 下载 zip 包；
   - 解压后得到 `.apk`，直接在安卓手机上点击安装即可。

---

## 📱 手机使用指南

1. **初次启动授权**：
   - 打开 App，点击 **【去授权】** 开启 **悬浮窗权限**（允许显示在其他应用上层）；
   - 点击 **【去开启】** 进入系统设置，找到 **求职助手屏幕识别服务** 并开启（用于读屏）；
   - 填入您的 **DeepSeek API Key**（在 [DeepSeek 开放平台](https://platform.deepseek.com/) 注册充值 5 块钱即可用半年）；
   - 点击 **【▶ 启动悬浮球】**。
2. **前往 BOSS直聘 / 猎聘**：
   - 正常浏览并进入任意岗位详情页；
   - 点击屏幕侧边的 **【AI招呼】** 悬浮球；
   - 悬浮卡片自动识别当前岗位痛点，并展示 2 种风格的定制话术（【技术硬核攻坚型】与【全面契合交流型】）；
   - 点击 **【📋 复制此话术并关闭】**；
   - 点击【立即沟通】进入聊天框，长按粘贴发出！

---

## 🛠️ 项目工程结构

```text
job-floating-assistant/
├── .github/workflows/
│   └── build-apk.yml               # GitHub Actions 云端打包流水线
├── app/
│   ├── src/main/
│   │   ├── java/com/assistant/job/
│   │   │   ├── MainActivity.kt             # Jetpack Compose 设置与权限中心
│   │   │   ├── FloatingService.kt          # WindowManager 全局悬浮球与卡片服务
│   │   │   ├── JobAccessibilityService.kt  # 无障碍屏幕 JD 节点提取服务
│   │   │   ├── DeepSeekClient.kt           # OkHttp 直连大模型生成引擎
│   │   │   └── StorageManager.kt           # 本地持久化配置与简历模板
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── settings.gradle.kts
└── build.gradle.kts
```
