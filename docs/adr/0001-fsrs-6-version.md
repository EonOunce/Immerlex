# ADR-0001: FSRS 版本从 5 升到 6

- 状态：已采用（用户 2026-10-27 当场确认"可以用 FSRS-6 升级"）
- 日期：2026-10-27

## 背景

AGENTS.md 原定移植 FSRS-5。实际准备参考实现时发现 open-spaced-repetition 的当前参考实现（py-fsrs，及同步的 ts-fsrs）默认已是 FSRS-6（21 参数），拒绝加载 19 参数的 FSRS-5 配置；FSRS-5 的默认权重也不再是社区推荐值。

## 决定

移植 **FSRS-6**，用 py-fsrs 生成参考向量（`core/fsrs/src/test/resources/vectors.json`），Kotlin 实现与其逐条对齐。应用侧配置：无学习/重学步（单词当次评级直接进复习态）、fuzzing 关闭、最大间隔 3650 天、desired_retention 0.9。

## 后果

- 算法族与"从 GitHub 权威实现取记忆曲线"的原意一致，仅版本号上调，公式是 FSRS-5 的超集（新增 w19-w20 短期稳定项 + w20 decay）。
- 偏离 AGENTS.md 已定决策表，需用户确认；若用户坚持 FSRS-5，需改用旧版 py-fsrs 重新生成向量，移植层差异很小。
