# intellij-rancher-plugin

![Build](https://github.com/nfzsh/intellij-rancher-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/26333-rancher-remote.svg)](https://plugins.jetbrains.com/plugin/26333-rancher-remote)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/26333-rancher-remote.svg)](https://plugins.jetbrains.com/plugin/26333-rancher-remote)

<!-- Plugin description -->
<h2>Rancher Integration Plugin for JetBrains IDEs</h2>
<h2>JetBrains IDE 的 Rancher 集成插件</h2>

<p>
    The <strong>Rancher Integration Plugin</strong> seamlessly integrates Rancher with your JetBrains IDE, enabling you to manage and deploy your Docker containers directly from your development environment. With this plugin, you can:
</p>
<p>
    <strong>Rancher 集成插件</strong> 将 Rancher 无缝集成到您的 JetBrains IDE 中，使您能够直接从开发环境管理和部署 Docker 容器。通过此插件，您可以：
</p>

<ul>
    <li><strong>Re-deploy Applications:</strong> Quickly re-deploy your applications to Rancher clusters with a single click.</li>
    <li><strong>重新部署应用：</strong> 一键将应用程序重新部署到 Rancher 集群。</li>
    <li><strong>View Remote Logs:</strong> Access and monitor real-time logs from your Docker containers running on Rancher.</li>
    <li><strong>查看远程日志：</strong> 访问和监控运行在 Rancher 上的 Docker 容器的实时日志。</li>
    <li><strong>Open Remote Shell:</strong> Connect to your Docker containers via an integrated terminal for debugging and troubleshooting.</li>
    <li><strong>打开远程 Shell：</strong> 通过集成终端连接到 Docker 容器，进行调试和故障排除。</li>
</ul>

<p>
    Configure your Rancher server address and API key in the plugin settings, and start managing your containers effortlessly. This plugin is designed to streamline your development workflow by bringing Rancher's powerful container management capabilities directly into your IDE.
</p>
<p>
    在插件设置中配置您的 Rancher 服务器地址和 API 密钥，即可轻松管理容器。该插件旨在通过将 Rancher 强大的容器管理功能直接引入 IDE，简化您的开发工作流程。
</p>

<h3>Key Features:</h3>
<h3>主要功能：</h3>
<ul>
    <li>Easy configuration of Rancher server and API key.</li>
    <li>轻松配置 Rancher 服务器和 API 密钥。</li>
    <li>One-click re-deployment of applications.</li>
    <li>一键重新部署应用程序。</li>
    <li>Real-time Docker container logs.</li>
    <li>实时 Docker 容器日志。</li>
    <li>Integrated terminal for remote shell access.</li>
    <li>集成终端，用于远程 Shell 访问。</li>
</ul>

<h3>Why Use This Plugin?</h3>
<h3>为什么使用此插件？</h3>
<p>
    The Rancher Integration Plugin eliminates the need to switch between your IDE and external tools, saving you time and improving productivity. Whether you're debugging, deploying, or monitoring your containers, this plugin provides a seamless experience within your JetBrains IDE.
</p>
<p>
    Rancher 集成插件消除了在 IDE 和外部工具之间切换的需要，节省时间并提高生产力。无论您是调试、部署还是监控容器，此插件都能在 JetBrains IDE 中提供无缝体验。
</p>

<h3>Getting Started:</h3>
<h3>快速入门：</h3>
<ol>
    <li>Install the plugin from the JetBrains Marketplace.</li>
    <li>从 JetBrains Marketplace 安装插件。</li>
    <li>Configure your Rancher server address and API key in the plugin settings.</li>
    <li>在插件设置中配置您的 Rancher 服务器地址和 API 密钥。</li>
    <li>Use the plugin's intuitive UI to re-deploy applications, view logs, and access remote shells.</li>
    <li>使用插件的直观界面重新部署应用程序、查看日志并访问远程 Shell。</li>
</ol>

<p>
    <strong>Note:</strong> This plugin requires a valid Rancher server and API key to function. Ensure your Rancher server is accessible from your development environment.
</p>
<p>
    <strong>注意：</strong> 此插件需要有效的 Rancher 服务器和 API 密钥才能运行。请确保您的 Rancher 服务器可以从开发环境访问。
</p>

<p>
    For support, feature requests, or bug reports, please visit the <a href="https://github.com/your-repo/rancher-integration-plugin">GitHub repository</a>.
</p>
<p>
    如需支持、功能请求或错误报告，请访问 <a href="https://github.com/your-repo/rancher-integration-plugin">GitHub 仓库</a>。
</p>
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:
  
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "intellij-rancher-plugin"</kbd> >
  <kbd>Install</kbd>
  
- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/26333-rancher-remote) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/26333-rancher-remote/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/nfzsh/intellij-rancher-plugin/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
