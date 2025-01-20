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