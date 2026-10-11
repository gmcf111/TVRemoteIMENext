# 原生库基线（2026-10，来源旧版 v1.4.1，原样拷贝）

按 07 文档规范：以下 so **原样保留、永不改动**。任何变更须独立成提交并更新本清单。

## ijkplayer（B 站官方归档项目产出，v0.8.4 时代）

| 文件 | SHA-256 |
|---|---|
| libijkffmpeg.so | 69a7ff71712e9f7aaf59d31e77714f453d915ca94ab9ee64b08ceaac2b95a7b9 |
| libijkplayer.so | 405ec325d524dd701212820562c84413c1840a81141329c81fa72038365b6cea |
| libijksdl.so | 22a1b6ef309a91a6930b2a22dc4226ab281a14c41bed4347076cb11f21587a9c |

## 迅雷 MiniThunder（2016-17 逆向提取，上游 2017 停更，全部 fork 同源同一份）

| 文件 | SHA-256 |
|---|---|
| libxl_stat.so | 3d51b27a3d218a68c394a4d2b63718d4f6bd00ad7149ad50196f0d8661b3f31e |
| libxl_thunder_iface.so | 170c901575c1753aae551e6ec221c40b8d4ff63401bd646a1a57c0c21c59b78a |
| libxl_thunder_sdk.so | 895240bc1d603657de50300c7e7071e7958bcb4164a7f2830afc0c3638191fa1 |
| libxluagc.so | 270aa8f7a4ecb015c5bbe3a8c245ce8f917dd47cc7d7b4ed2eb501e5c85861a7 |

备注：仅 armeabi-v7a（32 位决策，03 文档）；64 位化时须从官方渠道重新获取/自编 so，不得使用本基线。
