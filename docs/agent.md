# 轻投安装说明（给 AI Agent）

你在帮用户把「轻投 LiteCast」（免费开源的电视投屏接收端，https://github.com/Jaqennn/LiteCast ）装到安卓电视上。装好以后，iPhone 的屏幕镜像、AirPlay，以及各家视频 App 的投屏按钮（DLNA）都能投到这台电视。整个过程在用户电脑上用 adb 完成，电视不需要 root，也不改电视的系统设置。

- 包名：`io.github.litecast`
- 启动界面：`io.github.litecast/io.github.litecast.tv.HomeActivity`
- 支持：Android 5.0 及以上（SDK 21+），ARM 芯片

## 规则

1. **亲自在用户电脑上运行命令**，不要只把步骤列给用户。如果你不能执行命令（比如你是手机版或网页版聊天），直接告诉用户：请换成能执行命令的电脑版 Agent（Claude Code、Codex、豆包电脑版、千问电脑版、Trae、WorkBuddy 等），并允许它执行命令、访问网络。
2. **不要假装成功。** 每一步都看命令的真实输出；最后必须做完「验收」，把原始输出原样贴给用户。没看到这些输出，就不能说装好了。
3. **不要卸载、停用任何系统应用**，包括电视自带的投屏（乐播等）和原来的桌面；不要删除电视上的文件，不要恢复出厂设置。只能删除这次安装自己放上去的 `/sdcard/Download/LiteCast.apk`。
4. **只安装轻投**，APK 只从 https://github.com/Jaqennn/LiteCast/releases 下载。不要下载、安装任何其他 App（应用商店、ADB 开启器、别的投屏软件），也不要从别的网站找「轻投」的 APK。按这份说明装不上时，停下来把输出给用户看。
5. 重启电视（`adb reboot`）前先征得用户同意。
6. 需要用户在电视上操作时（允许调试、同意安装），明确告诉用户按遥控器的哪个键。
7. 失败时把命令和输出原样给用户看，再对照下面的「常见情况」处理。

## 第 1 步：准备 adb

先运行 `adb version`。有输出就跳到第 2 步。

没有 adb 时，下载 Google 的 platform-tools（`dl.google.com` 在国内可以访问）：

- **Windows**（PowerShell）：
  ```powershell
  cd $env:USERPROFILE
  curl.exe -fL -o platform-tools.zip https://dl.google.com/android/repository/platform-tools-latest-windows.zip
  Expand-Archive -Force platform-tools.zip .
  $env:Path = "$env:USERPROFILE\platform-tools;$env:Path"
  adb version
  ```
- **macOS**：`brew install android-platform-tools`；没有 Homebrew 时下载 `https://dl.google.com/android/repository/platform-tools-latest-darwin.zip` 解压使用。
- **Linux**：用系统的包管理器装 `adb`（或 `android-tools`），或下载 `platform-tools-latest-linux.zip`。

## 第 2 步：连接电视

电脑和电视要在同一个网络。用户给你的电视 IP 记为 `<IP>`（电视上一般在 设置 → 网络 → 已连接的网络 里看）。

```
adb connect <IP>:5555
adb devices
```

`adb devices` 里这台电视后面是 `device` 才算连上。

- **`unauthorized`**：电视上弹出了「允许 USB 调试吗？」，请用户选「允许」并勾选「始终允许」，再运行一次 `adb devices`。
- **`failed to connect` / `Connection refused` / 超时**：电视的 ADB 调试没打开，或 IP 不对。让用户按下表打开，再试。
- **5555 连不上、电视的开发者选项里只有「无线调试」**（索尼 BRAVIA 的 Android 14、部分 TCL 等 Android 11+ 机型）：要先配对。请用户在电视上打开 开发者选项 → 无线调试 → **使用配对码配对设备**，报给你两组 IP:端口和 6 位配对码：一组在配对码画面上，一组在无线调试主画面上（两个端口不一样）。然后：
  ```
  adb pair <配对画面的IP:端口> <配对码>
  adb connect <无线调试主画面的IP:端口>
  adb devices
  ```

打开 ADB 的方法（参考 [Aerial](https://github.com/pengchujin/aerial-tv) 项目整理的用户实测说明，更多品牌见它的 [分品牌说明](https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/adb-by-brand.md)）：

| 电视 | 怎么打开 ADB |
|---|---|
| 索尼、海信、原生 Android TV / Google TV | 设置 → 设备偏好设置 → 关于 → 连续按 **内部版本号**（Build）7 次，再到 开发者选项 打开 **USB 调试**（也可能叫「网络调试」） |
| 小米 / Redmi | 设置 → 关于 → 连续按 **产品型号**，直到提示进入开发者模式；再到 设置 → 账号与安全，打开 **ADB 调试** 和 **安装未知来源的应用** |
| TCL / 雷鸟 | 设置 → 关于 → **本机信息**，快速依次按遥控器 **上、下、左、右**，底部出现「更多」，在里面打开 **ADB** |
| OPPO 电视 | 设置 → 系统 → 关于本机 → **设备型号**，依次按 **上、上、右、右** 再按确认；再到 开发者选项 打开 **USB 调试** |

打不开 ADB 的电视（老乐视、荣耀智慧屏等）：直接告诉用户可以改用 U 盘安装（见本仓库 README「安装」），不要反复尝试。

## 第 3 步：看系统版本和芯片（必须）

```
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abilist
```

- 第一条 **21 及以上**（Android 5.0+）：继续。
- 第一条 **小于 21**（Android 4.4 及更老）：不支持，告诉用户，不要继续。
- 第三条里要有 `armeabi-v7a` 或 `arm64-v8a`。Android 5.0 上这条可能是空的，改看 `adb shell getprop ro.product.cpu.abi`。只有 `x86` 的设备不支持。
- 没有输出或报错：先弄清原因（`adb devices` 是否还是 `device`），不要猜版本。

## 第 4 步：下载 APK

```powershell
curl.exe -fL --retry 3 -o LiteCast.apk https://github.com/Jaqennn/LiteCast/releases/latest/download/LiteCast.apk
```

（macOS / Linux 把 `curl.exe` 换成 `curl`。）

下载很慢、失败，或者连不上 github.com（国内常见）时，换成加速地址再下一次：

```powershell
curl.exe -fL --retry 3 -o LiteCast.apk https://ghfast.top/https://github.com/Jaqennn/LiteCast/releases/latest/download/LiteCast.apk
```

加速地址是第三方镜像，用了它就**必须**核对文件和官方的一样，才能往下走（直连下载的也建议核对）：

```powershell
(Get-FileHash LiteCast.apk -Algorithm SHA256).Hash.ToLower()
(Invoke-RestMethod https://api.github.com/repos/Jaqennn/LiteCast/releases/latest).assets | Where-Object name -eq 'LiteCast.apk' | ForEach-Object digest
```

（macOS / Linux：`shasum -a 256 LiteCast.apk`；官方值用 `curl -s https://api.github.com/repos/Jaqennn/LiteCast/releases/latest`，看 `LiteCast.apk` 的 `digest`。）

- 第二行输出的 `sha256:` 后面那一串，要和第一行完全一样。不一样就删掉这个文件，不要装，告诉用户。
- 连 api.github.com 也打不开：把算出来的 SHA-256 给用户，请他和发布页说明末尾写的对一下；对不上就不要装。
- 两个地址都下不下来：请用户自己用浏览器下载，再告诉你文件路径。国内用户用蓝奏云备用下载 https://wwary.lanzoub.com/b01i5zvujg （提取码 `47il`，点里面的 LiteCast.apk）；能上 GitHub 的用发布页 https://github.com/Jaqennn/LiteCast/releases/latest 「Assets」里的 `LiteCast.apk`。拿到文件照样核对 SHA-256。

## 第 5 步：安装

```
adb install -r LiteCast.apk
```

输出里有 `Success` 才算装上（有的固件失败时也返回 0，只看输出）。失败时：

- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`** 或 **`INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES`**：电视上已经有一个签名不同的轻投（比如自己用源码编译的版本）。先告诉用户：卸载旧版会丢掉在轻投里改过的电视名，用户同意后运行 `adb uninstall io.github.litecast`，再装一次。
- **`INSTALL_FAILED_OLDER_SDK`**：电视是 Android 5.0 以前的系统，不支持。
- **`INSTALL_FAILED_NO_MATCHING_ABIS`**：设备不是 ARM 芯片，不支持。
- **`INSTALL_FAILED_USER_RESTRICTED`**（创维等）：先看电视上有没有弹出安装确认，请用户同意；没有的话运行 `adb shell settings put global verifier_verify_adb_installs 0` 再装。小米要先在 设置 → 账号与安全 里打开「安装未知来源的应用」。
- **`INSTALL_FAILED_VERIFICATION_FAILURE`**（部分 TCL 固件拒绝一切 adb 安装）：改成让电视自己装：
  ```
  adb push LiteCast.apk /sdcard/Download/LiteCast.apk
  ```
  再请用户用电视上**已有的**文件管理器（或「本地安装」）打开 `Download` 里的 `LiteCast.apk` 安装。电视上没有这类应用时，停下来告诉用户，不要去装应用商店。

## 第 6 步：授权和启动

1. 允许轻投显示在其他应用上层（这样用户在看别的节目时，手机一投屏画面就会弹出来）：
   ```
   adb shell appops set io.github.litecast SYSTEM_ALERT_WINDOW allow
   ```
   Android 5.x 不需要这一步，报错可以忽略。
2. TCL / 雷鸟 等有「自启动管理」的电视，再运行下面几条，让轻投开机能自己启动（不同固件只认其中一两条，其他电视报错是正常的）：
   ```
   adb shell appops set io.github.litecast APP_AUTO_START allow
   adb shell appops set io.github.litecast AUTO_START allow
   ```
3. 启动一次轻投：
   ```
   adb shell am start -W -n io.github.litecast/io.github.litecast.tv.HomeActivity
   ```
   输出里要有 `Status: ok`。启动过一次以后，轻投会在后台待命，电视重启后也会自己启动。

## 验收

依次运行，把**原始输出**原样贴给用户（Windows 把 `grep` 换成 `findstr`）：

```
adb shell pm list packages io.github.litecast
adb shell dumpsys package io.github.litecast | grep versionName
adb shell dumpsys activity services io.github.litecast | grep ServiceRecord
adb shell appops get io.github.litecast SYSTEM_ALERT_WINDOW
```

应该看到：

- 第一条：`package:io.github.litecast`
- 第二条：`versionName=` 后面是发布页上的最新版本号
- 第三条：有一行包含 `AirPlayService`（后台接收服务在运行）
- 第四条：`allow`（Android 5.x 上没有这一项，跳过）

然后请用户看电视：轻投主页上大字是电视在手机上显示的名字，两个状态点是绿的。再请用户实测一次：iPhone 打开 控制中心 → 屏幕镜像，或者在视频 App 里点投屏按钮，列表里能看到带「· 轻投」的名字，选它能投过去。用户确认能投，才算装好。

## 常见情况

- **手机上找不到电视**：手机和电视要在同一个局域网；路由器的「访客网络」「AP 隔离」会挡住发现；手机开着 VPN 时可能看不到；iPhone 上的视频 App 要在 设置 → 隐私与安全性 → 本地网络 里允许。装了乐播的电视上，手机列表可能要等几秒才出现轻投。
- **投屏了但电视没反应**：确认第 6 步第 1 条成功了；或者请用户先打开轻投再投。
- **列表里有两个电视名字**：带「· 轻投」的是轻投，另一个是电视自带的投屏。不要替用户停用自带的投屏；用户自己想停用时，告诉他在 设置 → 应用 里找。

## 还原

用户想恢复原样时：

```
adb uninstall io.github.litecast
```

输出 `Success` 就还原好了。轻投不改电视的系统设置，也不停用任何应用，卸载后电视和装之前一样。如果用户自己停用过电视自带的投屏、现在想恢复：先运行 `adb shell pm list packages -d` 找到它的包名，告诉用户，用户同意后运行 `adb shell pm enable <包名>`。
