# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues. Use the `gh` CLI for all operations.

Repository: `486tomocat/JWT`（由 `git remote -v` 推断，`gh` 在克隆内会自动识别）。

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`。多行正文用 heredoc。
- **Read an issue**: `gh issue view <number> --comments`，可用 `jq` 过滤评论，并一并取标签。
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`，配合 `--label` 与 `--state` 过滤。
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

## Pull requests as a triage surface

**PRs as a request surface: no.**（若本仓库把外部 PR 当功能请求，改成 `yes`；`/triage` 会读这个开关。）

设为 `yes` 时，PR 走与 issue 相同的标签和状态，命令换成 `gh pr`：

- **Read a PR**: `gh pr view <number> --comments`，diff 用 `gh pr diff <number>`。
- **List external PRs for triage**: `gh pr list --state open --json number,title,body,labels,author,authorAssociation,comments`，只保留 `authorAssociation` 为 `CONTRIBUTOR` / `FIRST_TIME_CONTRIBUTOR` / `NONE`（丢掉 `OWNER`/`MEMBER`/`COLLABORATOR`）。
- **Comment / label / close**: `gh pr comment`、`gh pr edit --add-label`/`--remove-label`、`gh pr close`。

GitHub 的 issue 和 PR 共用一套编号，所以裸 `#42` 可能是任意一种：先 `gh pr view 42`，失败再 `gh issue view 42`。

## When a skill says "publish to the issue tracker"

创建一条 GitHub issue。

## When a skill says "fetch the relevant ticket"

运行 `gh issue view <number> --comments`。

## Wayfinding operations

由 `/wayfinder` 使用。**地图（map）** 是一条 issue，**子票（child）** 是它的子 issue。

- **Map**: 一条标了 `wayfinder:map` 的 issue，正文放 Notes / Decisions-so-far / Fog。`gh issue create --label wayfinder:map`。
- **Child ticket**: 作为该 map 的 GitHub **sub-issue** 关联（用 `gh api` 调 sub-issues 端点）。若仓库未启用 sub-issue，就把子票加进 map 正文的 task list，并在子票正文顶部写 `Part of #<map>`。标签用 `wayfinder:<type>`（`research`/`prototype`/`grilling`/`task`）。认领后指给负责的人。
- **Blocking**: 用 GitHub **原生 issue 依赖**（UI 可见的规范表示）。加边：`gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`，其中 `<blocker-db-id>` 是阻塞方的**数据库 id**（`gh api repos/<owner>/<repo>/issues/<n> --jq .id`，**不是** `#number`，也不是 `node_id`）。GitHub 用 `issue_dependencies_summary.blocked_by` 报告（只算未关闭的阻塞方，这也是实际的解锁判断）。若依赖功能不可用，退回在子票正文顶部写 `Blocked by: #<n>, #<n>`。所有阻塞方都关闭后，子票才算解锁。
- **Frontier query**: 列出 map 下所有未关闭子票（`gh issue list --state open`，范围限定在 map 的 sub-issues / task list），去掉有未关闭阻塞方的（`issue_dependencies_summary.blocked_by > 0`，或 `Blocked by` 行里有未关闭的 issue）以及已有 assignee 的；**按 map 顺序取第一个**。
- **Claim**: `gh issue edit <n> --add-assignee @me`（本次会话的第一次写操作）。
- **Resolve**: `gh issue comment <n> --body "<answer>"` → `gh issue close <n>` → 把上下文指针（gist + 链接）追加到 map 的 Decisions-so-far。
