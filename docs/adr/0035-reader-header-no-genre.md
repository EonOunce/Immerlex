# 0035 阅读页顶栏：短篇只写标题，连载只写第几章（v4.2）

## 背景

用户 2026-11-12 提了一条 bug 和一条版式意见，并要这一刀直接发版：

> 「阅读页有个bug，长篇切换短篇的时候，短篇标题栏那里依然会显示上一个长篇的类型。
> 我觉得短篇保留标题，长篇只显示章节吧。改完这个bug提交release」

根因只有一处赋值。`prefs.genre`（SharedPreferences 里的 `genre`）**故意只在读连载章节时更新**
（`ReaderViewModel.render`：`if (chapter != null && chapter.kind == KIND_SERIAL) prefs.genre = chapter.genre`），
因为「写下一章」要续的就是这一本——读短篇时把它改掉，等于把连载的书名换成一个腔调名。
代价是读短篇时 `prefs.genre` 还指着上一本连载，而 `render` 把它原样递进
`showing(book = prefs.genre)`，界面就成了 `state.book`。旧顶栏第二行写的是
`if (title.isNotBlank() && title != book) Text(book)`，短篇的标题当然非空也不等于书名，
于是「灯下的信」下面挂着一个「宫斗」——**那是上一本书的名字，不是这一篇的**。

## 决定

### 1. 短篇没有「书」，在状态转换那一次就说清楚

`ReaderState.showing()` 里 `book = if (chapter?.kind == KIND_SHORT) "" else book`。
放在这个纯函数里而不是各调用点自觉，是因为 `render` 有两处 `showing(...)`（有章 / 无章），
以后再加入口也不会漏。`prefs.genre` 本身一个字没改：`ReaderViewModel` 照旧只从连载章节写它，
「写下一章」续的还是你上一本连载。

### 2. 顶栏一行只说一件事

```kotlin
when {
    loading -> reader_loading
    isShort -> title                       // 短篇：它自己的标题
    hasChapter -> reader_header_chapter    // 连载：「第 N 章」
    else -> book                           // 新建但还没写正文的那本：书名
}
```

题材那一行**两种都删了**——连载不再在顶栏写书名，短篇也不再有机会写别人的书名。
想知道在读哪一本书，去书架，那一本有边框；这一屏的主角是正文。
（用户 2026-10-29 定的「less is more」：界面只放操作本身需要的字。）

### 3. 进度行不再重复章号

`reader_progress` 从「第 %1$d 章 · 本章 %2$d 个词 · …」改成「本章 %1$d 个词 · 本次已学 %2$d · 本次已回忆 %3$d」。
章号刚被顶栏说过一遍。`reader_short_progress`（「短篇 · 本章 …」）不动。

### 4. 没有 Room 迁移，不动任何数据

改的只有一个纯函数的一个字段和两处文案；`chapters` 表、`prefs.genre` 的写法、阅读位置全部照旧。

## 后果

- 长篇 ↔ 短篇来回切，顶栏不再出现别人的书名；杀进程重开回到短篇那一篇，同样干净
  （`emulator-5554` 实测：`prefs.genre` 停在「宫斗」时打开「灯下的信」，顶栏只有「灯下的信」）。
- `state.book` 对短篇变成空串，检查过三个读它的地方都不受影响：书架「正在读」卡对短篇本来就走
  标题 + 腔调那两支（`if (state.isShort) shortToneLabel(short) else state.book`），
  `current = !state.isShort && …` 对短篇恒为假。
- **连载在顶栏不再显示书名**，这是用户要的取舍（「长篇只显示章节」）。同一本书里翻章时，
  顶栏只有「第 N 章」，题材要从书架看。
- 平板（≥600dp）上读短篇时，左边那栏「本章目录」会写「这个题材还没有其它章节。」——
  目录栏是按宽度出现的、不看 `kind`，这是 v3.0 就有的行为，这一刀没动它。要不要给短篇换成短篇列表，
  是另一件事。
- 单测：新增 `ReaderStateTest` 3 条——短篇不继承上一本连载、连载保留自己的书名、
  还没写正文的那本仍然叫得出名字。
