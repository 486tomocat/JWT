# Domain Docs

工程技能在探索本仓库代码库时，应该如何消费领域文档。

## 布局：single-context

**本仓库采用 single-context** —— 一个仓库共用**一份根目录的 `GLOSSARY.md`**，架构决定（ADR）统一放在根目录的 `docs/adr/`。

这里没有 monorepo 信号（无 `pnpm-workspace.yaml`、无 `workspaces` 字段、无 `packages/*`），所以不采用 multi-context 布局，也不需要 `GLOSSARY-MAP.md` 或各子项目的 `GLOSSARY.md`。

> 当前**尚未创建** `GLOSSARY.md` 与 `docs/adr/` —— 这是有意的：领域词条和架构决定**按需懒创建**，第一次真正写下词条 / 决定时再建，不预先铺空文件。

## Before exploring, read these

- **`GLOSSARY.md`**（仓库根目录）—— single-context 布局只用这一份。
- **`docs/adr/`** —— 读与即将改动的区域相关的 ADR。

如果这些文件不存在，**静默继续**。不要提示它们缺失，也不要主动建议创建。`/domain-modeling` 技能（由 `/grill-with-docs` 和 `/improve-codebase-architecture` 触达）会在真正解析出术语或决定时**懒创建**它们。

## File structure

```
/
├── GLOSSARY.md            ← 根目录唯一一份领域词汇表（尚未创建）
├── docs/
│   ├── adr/               ← 架构决定记录（尚未创建）
│   │   ├── 0001-xxx.md
│   │   └── 0002-xxx.md
│   └── agents/            ← 本技能套件的配置（已创建）
│       ├── issue-tracker.md
│       ├── triage-labels.md
│       └── domain.md
├── Jwt-backend/
└── jwt-frontend/
```

## Use the glossary's vocabulary

当你的产出要命名某个领域概念时（issue 标题、重构提案、假设、测试名），**用 `GLOSSARY.md` 里定义的词**。不要漂移到词汇表明确避免的同义词。

如果需要的概念还不在词汇表里，这本身是个信号：要么你在发明项目不使用的语言（重新考虑），要么确实存在缺口（记下来，交给 `/domain-modeling`）。

## Flag ADR conflicts

如果你的产出与已有 ADR 冲突，**明确指出来**，而不是悄悄覆盖：

> _与 ADR-0007（event-sourced orders）冲突，但值得重新讨论，因为……_
