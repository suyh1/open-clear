# 出门

一个适合小米等安卓手机的高德地图桌面入口。打开后可以输入目的地查公交地铁，也可以直接进入高德打车页。

## 安装

在装有高德地图的 Android 8 或更高版本手机上，下载并安装 [最新版 Chumen.apk](https://github.com/suyh1/open-clear/releases/latest/download/Chumen.apk)。后续版本可以覆盖安装。

## 构建与发布

向 `main` 推送提交或提交拉取请求时，GitHub Actions 会编译 APK，并将临时签名的测试包保存在该次运行的 Artifacts 中。手机日常使用请安装 Releases 中的正式包。

要发布新版本，在目标提交上创建递增的版本标签并推送。例如：

```sh
git tag v1.0.1
git push origin v1.0.1
```

GitHub Actions 会从标签提取版本号，使用仓库 Secrets 中的签名密钥构建 APK，核对签名证书后自动创建 Release 并上传安装包。签名配置使用 `CHUMEN_KEYSTORE_BASE64` 和 `CHUMEN_SIGN_PASS`。
