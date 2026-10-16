# 原生库基线（2026-10，来源旧版 v1.4.1，原样拷贝）

按 07 文档规范：以下 so **原样保留、永不改动**。任何变更须独立成提交并更新本清单。

## ijkplayer（B 站官方归档项目产出，v0.8.4 时代）

| 文件 | SHA-256 |
|---|---|
| libijkffmpeg.so | 69a7ff71712e9f7aaf59d31e77714f453d915ca94ab9ee64b08ceaac2b95a7b9 |
| libijkplayer.so | 405ec325d524dd701212820562c84413c1840a81141329c81fa72038365b6cea |
| libijksdl.so | 22a1b6ef309a91a6930b2a22dc4226ab281a14c41bed4347076cb11f21587a9c |

备注：
- 仅 armeabi-v7a（32 位决策，03 文档）；64 位化时须从官方渠道重新获取/自编 so，不得使用本基线。
- v3.0 起迅雷 MiniThunder 原生库（libxl_stat.so / libxl_thunder_iface.so / libxl_thunder_sdk.so / libxluagc.so）
  随边下边播功能一并移除，不再随包分发。
