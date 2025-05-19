<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intellij-rancher-plugin Changelog
# intellij-rancher-plugin 更新日志

## [Unreleased]

### Added
- Initial scaffold created from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
- 基于 [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) 创建初始项目脚手架。

## [1.0.0] - 2025-01-15

### Added
- Implemented basic functionality for Rancher integration, including:
    - Re-deploy applications to Rancher clusters.
    - View real-time logs from Docker containers.
    - Open remote shells for debugging and troubleshooting.
- 实现了 Rancher 集成的基本功能，包括：
    - 重新部署应用程序到 Rancher 集群。
    - 查看 Docker 容器的实时日志。
    - 打开远程 Shell 进行调试和故障排除。

## [1.0.1] - 2025-01-17

### Changed
- Improved configuration validation logic to ensure Rancher server address and API key are correctly formatted and accessible.
- 改进了配置校验逻辑，确保 Rancher 服务器地址和 API 密钥格式正确且可访问。

### Fixed
- Fixed an issue where the plugin would fail to load if the Rancher server was unreachable.
- 修复了当 Rancher 服务器无法访问时插件加载失败的问题。

### Known Issues
- Remote shell connection may occasionally time out due to network latency.
- 由于网络延迟，远程 Shell 连接偶尔会超时。

## [1.1.0] - 2025-05-19

### Fixed
- Fixed shell Chinese character encoding issue.
- 修复了shell中的中文字符乱码问题。
- Resolved compatibility and duplicate registration issues.
- 解决了兼容性问题和重复注册问题。
- Fixed multi-project conflict issue.
- 修复了多project冲突问题。
- Fixed data asynchronous processing issue.
- 修复了数据异步处理的问题。

### Added
- Optimized display logic, refresh display, and speed, supports dynamic namespace update.
- 优化了展示逻辑、刷新展示和刷新速度，支持动态更新namespace。
- Added clear functionality.
- 添加了清除功能。
- Enhanced description information.
- 增强了描述信息。
- UI improvements including multiple user interface enhancements.
- UI改进，包括多项用户界面增强功能。
- Supported versions as far back as 2021.1.
- 支持最早到2021.1的版本。

### Changed
- Upgraded several dependencies including:
- 升级了多个依赖项，包括但不限于：
    - JetBrains/qodana-action to 2024.3
    - gradle/actions to 4
    - codecov/codecov-action to 5
    - org.gradle.toolchains.foojay-resolver-convention to 0.9.0
    - org.jetbrains.kotlinx.kover to 0.9.1
    - org.jetbrains.kotlin.jvm to 2.1.20
    - org.jetbrains.intellij.platform to 2.4.0
