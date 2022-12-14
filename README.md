# kotlin-float-clock

类似 [zClock Lite](https://apps.apple.com/us/app/zclock-lite-topmost-clock/id1489475245?mt=12) 的桌面置顶时钟。只有时钟功能。

一直很喜欢macOS下的应用zClock，能清楚地提醒我当前时间，掌握摸鱼节奏。

![preview](doc/preview.png)

目前暂时不会自动加开机启动，因为适配起来有点麻烦。反正这对你来说不难吧。

## 支持功能

- 时间显示
- 随机颜色
- 数码管风格主题

## 支持系统

- KUbuntu 22.04
- deepin 20.3
- Windows 11

我目前没找到java swing如何在macOS上实现显示到当前屏幕（包括全屏窗口所在屏幕）上的方法，如果有大佬知道还望不吝赐教。

## 存储

程序配置放在用户目录的 `.config/floatclock` 文件夹下（Windows在 `%LOCALAPPDATA%\floatclock` 下）。目前只会写入一个mapdb数据库文件。

## 致谢

本项目参考或使用了以下项目或其中的一部分。

- [Apache Commons Lang](https://github.com/apache/commons-lang)
- [Compose Desktop](https://github.com/JetBrains/compose-jb)
- [Decompose](https://github.com/arkivanov/Decompose)
- [digital7.font](https://www.dafont.com/digital-7.font)
- [mapdb](https://mapdb.org/)
- [misc](https://github.com/jjYBdx4IL/misc)
