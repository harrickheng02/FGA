# Fate/Grand Automata（主线推进 Fork）

基于 [Fate-Grand-Automata/FGA](https://github.com/Fate-Grand-Automata/FGA) 的个人分支，在原版刷本能力上补齐**主线剧情自动推进**相关逻辑。

- 上游项目：https://github.com/Fate-Grand-Automata/FGA  
- 本仓库：https://github.com/harrickheng02/FGA  
- 正式包发布：https://github.com/harrickheng02/FGA/releases  
- 英文原版说明：见 [README.md](README.md)

**当前状态：仅在国服（Cn）实测验证。** 台服 / 日服 / 美服的主线 UI、模板与坐标尚未专门适配，请勿默认能在其他服稳定使用。资源目录里虽有部分 `Tw` 文件，不代表台服已完成测试。

原版定位是日常刷本；本 fork 额外把「主线地图 → 关卡详情 → 确认 → 助战 → 编队 → 战斗」这条链路在国服上跑通，减少卡在剧情地图上的手动操作。

---

## 本 Fork 改了什么

### 1. 国服主线地图选关

| 能力 | 说明 |
|------|------|
| 识别「下一个」 | 用黄色箭头 / 文案模板定位当前主线节点（全屏搜索，覆盖右侧节点） |
| 识别「管理室」 | 左上角「管理室」判定仍在地图上；箭头分数偏低时放宽匹配，且不会误点自由本坐标 |
| 识别「关闭」 | 选中节点后左上角变为「关闭」，进入关卡详情条 |
| 两步进入关卡 | 先点节点下方打开蓝色关卡条，再点关卡条本体进入（避免只点地图节点） |
| 确认弹窗 | 通用 `confirmation` 水印对话框固定点右侧第二按钮（如「开始」） |
| 队伍确认 | 「队伍确认」界面持续点「战斗开始」 |

相关资源：`app/src/main/assets/Cn/` 下的 `story_next*.png`、`story_map_myroom.png`、`story_quest_close.png` 等（以国服截图为准）。

### 2. 主线助战职阶栏（国服）

主线助战会插入「推荐」职阶，原版按固定坐标点「All」会点偏。

- 在职阶栏首格识别「推荐」并测量偏移  
- 再按偏移点击配置的职阶（含 Extra 等）  
- 滚动条在**整条轨道**检测，避免滑一次就误判提前刷新  

### 3. 战斗稳定性（服务主线连战）

| 问题 | 处理 |
|------|------|
| 从者 / 宝具「状态数值」浮层挡住战斗 | 同时识别「状态数值」与菱形关闭键后关闭 |
| 点「攻击」未进选卡却去读指令卡 | 确认战斗 UI 消失后才解析卡牌；失败则重试点攻击 |
| 读卡失败弹 Toast 空转 | 与上项配套，减少在技能界面误读卡导致的循环 |

上述战斗相关逻辑在国服主线连战中验证；其他服界面文案 / 模板可能不同，效果未保证。

### 4. 发布构建

本 fork **没有**上游 Play 商店签名密钥。`assembleRelease` 在缺少 `KEYSTORE_PASS` 时回退到 debug keystore，便于在 GitHub Releases 发布可安装的正式包（包名仍为 `io.github.fate_grand_automata`）。

该签名与官方 Play / 上游 Release **不兼容**：若已安装官方版，需先卸载再装本 fork 包。日常调试用的 `*.test` 包可与正式包并存。

---

## 安装

1. 打开 [Releases](https://github.com/harrickheng02/FGA/releases)，下载最新 `FGA-xxxx.apk`（或 zip）。  
2. 安装后打开应用 → **Start Service**，授予无障碍与投屏权限。  
3. Android 14+ 若无法开启无障碍，可参考上游说明使用 [APKMirror Installer](https://play.google.com/store/apps/details?id=com.apkmirror.helper.prod)。  

本地自行打包：

```bat
set FGA_VERSION_CODE=3100
set FGA_VERSION_NAME=3100
gradlew.bat :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。

---

## 主线怎么用（国服）

1. 游戏服务器选 **Cn**，在 FGO **主线地图**停好（能看见「管理室」与「下一个」）。  
2. 配好脚本：助战职阶、技能轴按关卡需要设置。  
3. 点悬浮球 Play；脚本会尝试：找下一个节点 → 开详情条 → 确认 → 助战 → 编队 → 战斗 → 结算后再回地图。  
4. 剧情对话可打开应用内 **Story Skip** 相关选项（与上游一致）。  

建议先用单关验证选关与助战，再挂长线。特异点地图、FX（沙尘等）过强时，「下一个」可能暂时认不出，本 fork 会借助「管理室」等待重试，而不是乱点刷本入口。

---

## 已知限制

- **仅国服主线做过实机测试**；其他服未纳入当前适配与验收范围。  
- 部分剧情战使用特殊卡面文案（如「技击 / 力击」），指令卡类型识别可能弱于普通刷本。  
- 客将形象极相似（如双奥尔加）时，从者脸卡归属可能不准，影响优先出卡策略。  
- 不替代上游的刷本、抽卡、礼装制作等能力；那些仍沿用原版逻辑。  
- 请遵守游戏服务条款；本工具通过截屏 + 无障碍模拟点击，不修改游戏客户端。  

---

## 相对上游的提交（摘要）

近期本分支相对上游 `master` 的主要提交：

- 主线地图选关 / 关卡条 / 确认弹窗 / 管理室判定（国服）  
- 主线助战「推荐」职阶偏移与滚动条检测  
- 战斗状态浮层关闭与攻击键重试  
- 无 Play 密钥时的 Release 打包回退  

完整差异见：`git log upstream/master..HEAD`。

---

## 原版说明与致谢

使用方式、原理（OpenCV + MediaProjection + Accessibility）、贡献指南等仍以**上游**为准：

- 网站：https://fate-grand-automata.github.io  
- Wiki / 排错：https://github.com/Fate-Grand-Automata/FGA/wiki  
- 贡献：见上游 [CONTRIBUTING.md](https://github.com/Fate-Grand-Automata/FGA/blob/master/CONTRIBUTING.md)  

感谢 [Fate-Grand-Automata/FGA](https://github.com/Fate-Grand-Automata/FGA) 与 [FGO-Lua](https://github.com/29988122/Fate-Grand-Order_Lua) 作者们的工作。
