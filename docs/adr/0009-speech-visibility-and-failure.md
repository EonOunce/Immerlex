# ADR-0009: 朗读的包可见性与失败提示

- 状态：已采用
- 日期：2026-10-29

## 背景

用户真机反馈点"朗读"没有反应。原实现只调用 `TextToSpeech`，忽略了 `setLanguage` 的返回值，也没有初始化失败或英文语音包缺失的提示；同时清单里没有声明 `TTS_SERVICE` 的 `<queries>`，而 Android 11+ 的包可见性限制会让应用找不到系统语音引擎。

## 决定

1. 清单声明 `TTS_SERVICE` 的 `<queries>`，保证 Android 11+ 能发现语音引擎。
2. `feature/reader/ReaderSpeech.kt` 的 `WordSpeaker` 显式区分三种失败：没有可用引擎、引擎没有英文语音包、`speak` 调用失败；英文按 en-US → en-GB → en 依次尝试。
3. 初始化是异步的：用户在就绪前点朗读，词先挂起，引擎就绪后补读一次，不让用户再点。
4. 失败原因直接显示在词卡里（引擎缺失 / 英文包缺失 / 本次没读出来），对应按钮禁用或可重试；不用 Toast，也不只写日志。

## 后果

- 没有 TTS 引擎或英文包的设备上，用户能知道该装什么，而不是以为功能坏了。
- 词卡只在失败时多一行提示，正常朗读的界面不变。
- 朗读仍限于词卡内的单词，不引入整章朗读。

## 落地（2026-10-29，模拟器自检）

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 通过。
- 模拟器点"朗读"后日志出现 `Synthesis request for locale eng-USA`、`TTS dispatch: en-us-x-iog-lstm-embedded` 与 AudioTrack 播放帧，说明初始化、语言选择、speak 全链路走通；失败分支尚未在真机复现，用户机型与语音包情况待真机确认。
