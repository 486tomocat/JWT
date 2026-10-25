# Triage Labels

技能用**五个规范角色**表达 triage 状态。本文件把这些角色映射到本仓库 issue tracker 里实际使用的标签字符串。

| Label in mattpocock/skills | Label in our tracker | Meaning |
| -------------------------- | -------------------- | ------- |
| `needs-triage`             | `needs-triage`       | 维护者需要评估这条 issue |
| `needs-info`               | `needs-info`         | 等待提交者补充信息 |
| `ready-for-agent`          | `ready-for-agent`    | 已完全明确，可以交给 AFK agent |
| `ready-for-human`          | `ready-for-human`    | 需要人来实现 |
| `wontfix`                  | `wontfix`            | 不予处理 |

本仓库**保留默认五角色**，右列与左列同名，无需重命名。

技能提到某个角色时（例如「apply the AFK-ready triage label」），用上表对应行的标签字符串。

## Wayfinder 标签

`/wayfinder` 另需以下标签（与上面五个 role 标签并存）：

| 标签 | 用途 |
| --- | --- |
| `wayfinder:map` | 标记 wayfinding 的**地图** issue（一条 issue 承载 Notes / Decisions / Fog） |
| `wayfinder:research` | 子票类型：调研 |
| `wayfinder:prototype` | 子票类型：原型验证 |
| `wayfinder:grilling` | 子票类型：追问/压测想法 |
| `wayfinder:task` | 子票类型：具体实现任务 |
