# 儿童模式

给孩子管时长用的：看满一段时间弹出题目，答对继续；每天有总上限，到点锁屏；只有家长能关掉整个模式。

## 怎么用

1. **开启**：桌面点右上角齿轮 → 下拉菜单点「儿童模式」。立即生效，什么都不用填。
2. **平时**：孩子看视频时计时（回到桌面、待机、答题、相框模式时不计）。每满 10 分钟（可配）弹出 3 道题（默认二年级难度），全部答对就继续看 10 分钟。
3. **每天 60 分钟后**（可配 30/60/90/120/不限）：全屏锁定，重启电视也没用。
4. **家长退出 / 解锁**：在锁定页或答题窗点「家长入口」，或回到桌面点齿轮下拉菜单里的「儿童模式」——答一道混合运算题（如 47×8+135−59），答对后可选「今日解锁（家长陪同）」或「关闭儿童模式」。
5. **配置**：菜单里「儿童模式设置…」——年级（1–6，孩子长大了改这里）、单次时长、每次题数、每日上限、门禁难度（和年级无关，独立调）。

## 一次性准备（可选但强烈建议）

悬浮窗授权让答题/锁定页能盖住正在播放的视频，否则只能在回到桌面时补弹：

```bash
adb shell appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow
```

配置页里会显示这条命令（包名以实际 applicationId 为准）。辅助功能服务（HomeKeyService）开启时计时最准；没开也能用，只是从「桌面启动应用」开始计。

## 边界（说实话的部分）

- 门禁题是减速带不是保险锁：拿手机计算器 30 秒能解。真正的防线是电视在客厅。
- HDMI 信源（机顶盒/游戏机）、拿别的设备看，管不到；清数据/卸载桌面技术上挡不住，但一旦发生桌面立刻变样，家长马上能发现。
- 答题时视频的声音还在放（launcher 压不住别的应用的音频），画面被挡住了。

## 开发者

纯逻辑（`KidSessionPolicy` / `KidQuiz` / `ParentGatePolicy`）有单元测试（`KidQuizTest`、`KidSessionPolicyTest`、`ParentGatePolicyTest`）；接线在 `MainActivity`、`HomeKeyService`、`AppAdapter`、`QuickSettingsActivity`、`HomeSettingsActivity`、`AppLibraryDialog`。计时状态全部持久化在 `kids_mode` SharedPreferences，进程被杀最多丢 60 秒计量。悬浮窗答题层需要 `SYSTEM_ALERT_WINDOW`（appops 授权，见上）。
