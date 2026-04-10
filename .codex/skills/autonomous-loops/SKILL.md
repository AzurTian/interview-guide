---
name: autonomous-loops
description: 用于自主 Claude Code 循环的模式和架构——从简单的顺序管道到基于 RFC 的多代理有向无环图系统。
---
# 自主循环技能

> 兼容性说明（v1.8.0）：“自主循环”保留在一个版本中。
> 官方技能名称现在是“连续代理循环”。新的环形制导
>应该在那里写，趁这项技能还在，避免使用
>破坏现有工作流程。

用于自动循环运行 Claude Code 的模式、架构和参考实现。涵盖从简单的“claude-p”流水线到完整的RFC驱动多代理DAG编排。

## 何时使用？

- 建立无需人工干预的自主开发工作流
- 为你的问题选择合适的环路架构（简单与复杂）
- 构建CI/CD风格的持续开发流水线
- 运行并行代理并并行协调
- 实现跨循环迭代的上下文持久性
- 向自主工作流程添加质量门和清理通道

## 环形模式光谱

从最简单到最复杂：

| 模式                                                 | 复杂性  | 最适合                 |
|----------------------------------------------------|------|---------------------|
| [顺序流水线]（#1-顺序-管道-克劳德--p）                           | 低    | 每日开发步骤，脚本化工作流程      |
| [纳米爪回响]（#2-纳爪-雷普尔）                                 | 低    | 交互式持久会话             |
| [无限能动循环]（#3-无限代理环）                                 | 中等   | 并行内容生成，规范驱动工作       |
| [连续的克洛德PR循环]（#4-连续-克劳德-普-循环）                       | 中等   | 多日迭代项目与CI门          |
| [去松弛图案]（#5-de-sloppify-pattern）                    | 附加组件 | 实现器步骤后的质量清理         |
| [Ralphinho / RFC驱动的DAG]（#6-Ralphinho——RFC驱动-DAG编排） | 高    | 大型功能，多单元并行工作，包含合并队列 |

---

## 1.顺序流水线（“claude -p”）
**最简单的循环。** 将日常开发分解为一系列非交互式的 `claude -p` 调用。每次调用都是一个有明确提示的专注步骤。

### 核心见解

> 如果你无法理解这样的循环，这意味着你甚至无法在交互模式下驱动 LLM 来修复你的代码。

`claude -p` 标志在非交互式模式下使用提示运行 Claude Code，完成后退出。通过链式调用来构建一个管道：
```bash
#!/bin/bash
# daily-dev.sh — Sequential pipeline for a feature branch

set -e

# Step 1: Implement the feature
claude -p "Read the spec in docs/auth-spec.md. Implement OAuth2 login in src/auth/. Write tests first (TDD). Do NOT create any new documentation files."

# Step 2: De-sloppify (cleanup pass)
claude -p "Review all files changed by the previous commit. Remove any unnecessary type tests, overly defensive checks, or testing of language features (e.g., testing that TypeScript generics work). Keep real business logic tests. Run the test suite after cleanup."

# Step 3: Verify
claude -p "Run the full build, lint, type check, and test suite. Fix any failures. Do not add new features."

# Step 4: Commit
claude -p "Create a conventional commit for all staged changes. Use 'feat: add OAuth2 login flow' as the message."
```
### 关键设计原则

1. **每个步骤都是独立的** — 每次 `claude -p` 调用使用全新的上下文窗口，确保步骤之间的上下文不会相互干扰。
2. **顺序很重要** — 步骤按顺序执行。每一步都基于前一步留下的文件系统状态。
3. **负面指令很危险** — 不要说“不要测试类型系统”。相反，应添加一个单独的清理步骤（参见 [去凌乱模式](#5-the-de-sloppify-pattern)）。
4. **退出代码会传播** — `set -e` 会在失败时停止整个流程。

### 变体

**使用模型路由：**
```bash
# Research with Opus (deep reasoning)
claude -p --model opus "Analyze the codebase architecture and write a plan for adding caching..."

# Implement with Sonnet (fast, capable)
claude -p "Implement the caching layer according to the plan in docs/caching-plan.md..."

# Review with Opus (thorough)
claude -p --model opus "Review all changes for security issues, race conditions, and edge cases..."
```
**有环境上下文：**
```bash
# Pass context via files, not prompt length
echo "Focus areas: auth module, API rate limiting" > .claude-context.md
claude -p "Read .claude-context.md for priorities. Work through them in order."
rm .claude-context.md
```
**使用 `--allowedTools` 限制时：**
```bash
# Read-only analysis pass
claude -p --allowedTools "Read,Grep,Glob" "Audit this codebase for security vulnerabilities..."

# Write-only implementation pass
claude -p --allowedTools "Read,Write,Edit,Bash" "Implement the fixes from security-audit.md..."
```
---

## 2. NanoClaw REPL

**ECC 内置的持久循环。** 一个具备会话感知的 REPL，能够同步调用 `claude -p`，并保留完整的对话历史。
```bash
# Start the default session
node scripts/claw.js

# Named session with skill context
CLAW_SESSION=my-project CLAW_SKILLS=tdd-workflow,security-review node scripts/claw.js
```
### 工作原理

1. 从 `~/.claude/claw/{session}.md` 加载对话历史
2. 每条用户消息会与完整历史一起发送到 `claude -p` 作为上下文
3. 回复会被追加到会话文件中（Markdown 作为数据库）
4. 会话在重启后依然保持

### NanoClaw 与顺序管道的适用场景

| 用例 | NanoClaw | 顺序管道 |
|------|----------|-----------|
| 交互式探索 | 是 | 否 |
| 脚本化自动化 | 否 | 是 |
| 会话持久化 | 内置 | 手动 |
| 上下文累积 | 每轮增长 | 每步刷新 |
| CI/CD 集成 | 较差 | 优秀 |

完整详情请参阅 `/claw` 命令文档。

---

## 3. 无限代理循环

**一个双提示系统**，用于协调并行子代理进行基于规格的生成。由 disler 开发（感谢：@disler）。

### 架构：双提示系统
```
PROMPT 1 (Orchestrator)              PROMPT 2 (Sub-Agents)
┌─────────────────────┐             ┌──────────────────────┐
│ Parse spec file      │             │ Receive full context  │
│ Scan output dir      │  deploys   │ Read assigned number  │
│ Plan iteration       │────────────│ Follow spec exactly   │
│ Assign creative dirs │  N agents  │ Generate unique output │
│ Manage waves         │             │ Save to output dir    │
└─────────────────────┘             └──────────────────────┘
```
### 模式

1. **规格分析** — Orchestrator 读取定义生成内容的规范文件（Markdown）
2. **目录侦查** — 扫描现有输出以找到最高的迭代编号
3. **并行部署** — 启动 N 个子代理，每个代理拥有：
   - 完整规范
   - 独特的创意方向
   - 特定的迭代编号（无冲突）
   - 现有迭代的快照（用于唯一性）
4. **波次管理** — 对于无限模式，部署 3-5 个代理的波次，直到上下文耗尽

### 通过 Claude 代码命令实现

创建 `.claude/commands/infinite.md`：
```markdown
Parse the following arguments from $ARGUMENTS:
1. spec_file — path to the specification markdown
2. output_dir — where iterations are saved
3. count — integer 1-N or "infinite"

PHASE 1: Read and deeply understand the specification.
PHASE 2: List output_dir, find highest iteration number. Start at N+1.
PHASE 3: Plan creative directions — each agent gets a DIFFERENT theme/approach.
PHASE 4: Deploy sub-agents in parallel (Task tool). Each receives:
  - Full spec text
  - Current directory snapshot
  - Their assigned iteration number
  - Their unique creative direction
PHASE 5 (infinite mode): Loop in waves of 3-5 until context is low.
```
**调用：**
```bash
/project:infinite specs/component-spec.md src/ 5
/project:infinite specs/component-spec.md src/ infinite
```
### 批处理策略

| 数量 | 策略 |
|------|------|
| 1-5  | 所有代理同时进行 |
| 6-20 | 每批 5 个 |
| 无限 | 每波 3-5 个，逐步提高复杂度 |

### 关键见解：通过分配实现独特性

不要依赖代理自行区分。协调器**为每个代理分配**特定的创作方向和迭代编号。这可以防止并行代理之间出现重复概念。

---

## 4. 持续 Claude PR 循环

**一个生产级的 shell 脚本**，可以连续运行 Claude Code，创建 PR、等待 CI 并自动合并。由 AnandChowdhary 创建（致谢：@AnandChowdhary）。

### 核心循环
```
┌─────────────────────────────────────────────────────┐
│  CONTINUOUS CLAUDE ITERATION                        │
│                                                     │
│  1. Create branch (continuous-claude/iteration-N)   │
│  2. Run claude -p with enhanced prompt              │
│  3. (Optional) Reviewer pass — separate claude -p   │
│  4. Commit changes (claude generates message)       │
│  5. Push + create PR (gh pr create)                 │
│  6. Wait for CI checks (poll gh pr checks)          │
│  7. CI failure? → Auto-fix pass (claude -p)         │
│  8. Merge PR (squash/merge/rebase)                  │
│  9. Return to main → repeat                         │
│                                                     │
│  Limit by: --max-runs N | --max-cost $X             │
│            --max-duration 2h | completion signal     │
└─────────────────────────────────────────────────────┘
```
### 安装

> **警告：** 在查看代码后，从其仓库安装 continuous-claude。不要将外部脚本直接通过 bash 执行。

### 使用
```bash
# Basic: 10 iterations
continuous-claude --prompt "Add unit tests for all untested functions" --max-runs 10

# Cost-limited
continuous-claude --prompt "Fix all linter errors" --max-cost 5.00

# Time-boxed
continuous-claude --prompt "Improve test coverage" --max-duration 8h

# With code review pass
continuous-claude \
  --prompt "Add authentication feature" \
  --max-runs 10 \
  --review-prompt "Run npm test && npm run lint, fix any failures"

# Parallel via worktrees
continuous-claude --prompt "Add tests" --max-runs 5 --worktree tests-worker &
continuous-claude --prompt "Refactor code" --max-runs 5 --worktree refactor-worker &
wait
```
### 跨迭代上下文：SHARED_TASK_NOTES.md

关键创新：一个 `SHARED_TASK_NOTES.md` 文件在各迭代中持续存在：
```markdown
## Progress
- [x] Added tests for auth module (iteration 1)
- [x] Fixed edge case in token refresh (iteration 2)
- [ ] Still need: rate limiting tests, error boundary tests

## Next Steps
- Focus on rate limiting module next
- The mock setup in tests/helpers.ts can be reused
```
Claude 在迭代开始时读取此文件，并在迭代结束时更新它。这弥合了独立 `claude -p` 调用之间的上下文差距。

### CI 失败恢复

当 PR 检查失败时，Continuous Claude 会自动：
1. 通过 `gh run list` 获取失败的运行 ID
2. 启动一个带有 CI 修复上下文的新 `claude -p`
3. Claude 通过 `gh run view` 检查日志，修复代码，提交并推送
4. 重新等待检查（最多尝试 `--ci-retry-max` 次）

### 完成信号

Claude 可以通过输出一个魔法短语来表示“我完成了”：
```bash
continuous-claude \
  --prompt "Fix all bugs in the issue tracker" \
  --completion-signal "CONTINUOUS_CLAUDE_PROJECT_COMPLETE" \
  --completion-threshold 3  # Stops after 3 consecutive signals
```
连续三次迭代完成信号会停止循环，防止对已完成的工作进行无用的运行。

### 关键配置

| 标志 | 目的 |
|------|---------|
| `--max-runs N` | 成功迭代 N 次后停止 |
| `--max-cost $X` | 花费达到 $X 后停止 |
| `--max-duration 2h` | 时间到达后停止 |
| `--merge-strategy squash` | squash、merge 或 rebase |
| `--worktree <name>` | 通过 git 工作树并行执行 |
| `--disable-commits` | 测试运行模式（不进行 git 操作） |
| `--review-prompt "..."` | 每次迭代增加审查步骤 |
| `--ci-retry-max N` | 自动修复 CI 失败（默认：1） |

---

## 5. 去繁就简模式（De-Sloppify Pattern）

**适用于任何循环的附加模式。** 在每次实现步骤（Implementer step）后增加专门的清理/重构步骤。

### 问题

当你要求 LLM 用 TDD 实现时，它对“写测试”理解得过于字面化：
- 验证 TypeScript 类型系统的测试（测试 `typeof x === 'string'`）
- 对类型系统已保证的内容进行过度的运行时检查
- 测试框架行为而非业务逻辑
- 过多的错误处理掩盖了实际代码

### 为什么不使用负面指令？

在 Implementer 提示中添加“不要测试类型系统”或“不要添加不必要的检查”会产生下游影响：
- 模型对所有测试产生犹豫
- 跳过合法的边界情况测试
- 质量不可预测地下降

### 解决方案：单独执行一次清理

与其约束 Implementer，让它尽可能详尽。然后增加一个专注的清理代理：
```bash
# Step 1: Implement (let it be thorough)
claude -p "Implement the feature with full TDD. Be thorough with tests."

# Step 2: De-sloppify (separate context, focused cleanup)
claude -p "Review all changes in the working tree. Remove:
- Tests that verify language/framework behavior rather than business logic
- Redundant type checks that the type system already enforces
- Over-defensive error handling for impossible states
- Console.log statements
- Commented-out code

Keep all business logic tests. Run the test suite after cleanup to ensure nothing breaks."
```
### 在循环上下文中
```bash
for feature in "${features[@]}"; do
  # Implement
  claude -p "Implement $feature with TDD."

  # De-sloppify
  claude -p "Cleanup pass: review changes, remove test/code slop, run tests."

  # Verify
  claude -p "Run build + lint + tests. Fix any failures."

  # Commit
  claude -p "Commit with message: feat: add $feature"
done
```
### 关键见解

> 与其添加会对下游质量产生影响的负面指令，不如增加一个单独的去粗糙化流程。两个专注的代理胜过一个受限的代理。

---

## 6. Ralphinho / RFC 驱动的 DAG 编排

**最复杂的模式。** 一个 RFC 驱动的多代理流程，将规范分解为依赖 DAG，通过分层质量流程运行每个单元，并通过代理驱动的合并队列落地。由 enitrat 创建（致谢：@enitrat）。

### 架构概览
```
RFC/PRD Document
       │
       ▼
  DECOMPOSITION (AI)
  Break RFC into work units with dependency DAG
       │
       ▼
┌──────────────────────────────────────────────────────┐
│  RALPH LOOP (up to 3 passes)                         │
│                                                      │
│  For each DAG layer (sequential, by dependency):     │
│                                                      │
│  ┌── Quality Pipelines (parallel per unit) ───────┐  │
│  │  Each unit in its own worktree:                │  │
│  │  Research → Plan → Implement → Test → Review   │  │
│  │  (depth varies by complexity tier)             │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ┌── Merge Queue ─────────────────────────────────┐  │
│  │  Rebase onto main → Run tests → Land or evict │  │
│  │  Evicted units re-enter with conflict context  │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
└──────────────────────────────────────────────────────┘
```
### RFC 分解

AI 阅读 RFC 并生成工作单元：
```typescript
interface WorkUnit {
  id: string;              // kebab-case identifier
  name: string;            // Human-readable name
  rfcSections: string[];   // Which RFC sections this addresses
  description: string;     // Detailed description
  deps: string[];          // Dependencies (other unit IDs)
  acceptance: string[];    // Concrete acceptance criteria
  tier: "trivial" | "small" | "medium" | "large";
}
```
**分解规则：**
- 优先使用更少、紧密的单元（最小化合并风险）
- 最小化跨单元文件重叠（避免冲突）
- 测试与实现保持在一起（绝不分开“实现 X”和“测试 X”）
- 只有在实际代码依赖存在时才使用依赖

依赖有向无环图 (DAG) 决定执行顺序：
```
Layer 0: [unit-a, unit-b]     ← no deps, run in parallel
Layer 1: [unit-c]             ← depends on unit-a
Layer 2: [unit-d, unit-e]     ← depend on unit-c
```
### 复杂度级别

不同级别有不同的流水线阶段：

| 级别 | 流水线阶段 |
|------|----------------|
| **简单** | 实现 → 测试 |
| **小型** | 实现 → 测试 → 代码审查 |
| **中型** | 研究 → 计划 → 实现 → 测试 → PRD 审查 → 代码审查 → 审查修复 |
| **大型** | 研究 → 计划 → 实现 → 测试 → PRD 审查 → 代码审查 → 审查修复 → 最终审查 |

这可以防止在简单修改上进行昂贵操作，同时确保架构性更改得到充分审查。

### 独立上下文窗口（消除作者偏差）

每个阶段在自己的代理进程中运行，并拥有自己的上下文窗口：

| 阶段 | 模型 | 目的 |
|-------|-------|---------|
| 研究 | Sonnet | 阅读代码库、RFC，生成上下文文档 |
| 计划 | Opus | 设计实现步骤 |
| 实现 | Codex | 按计划编写代码 |
| 测试 | Sonnet | 运行构建和测试套件 |
| PRD 审查 | Sonnet | 规范合规性检查 |
| 代码审查 | Opus | 质量和安全检查 |
| 审查修复 | Codex | 处理审查问题 |
| 最终审查 | Opus | 质量门控（仅大型级别） |

**关键设计：** 审查者从未编写其审查的代码。这消除了作者偏差——自我审查中最常见的漏检问题来源。

### 带剔除的合并队列

在质量流水线完成后，单元将进入合并队列：
```
Unit branch
    │
    ├─ Rebase onto main
    │   └─ Conflict? → EVICT (capture conflict context)
    │
    ├─ Run build + tests
    │   └─ Fail? → EVICT (capture test output)
    │
    └─ Pass → Fast-forward main, push, delete branch
```
**文件重叠智能：**
- 不重叠的单元并行投放，具有投机性
- 重叠的单元逐个投放，每次进行重新基准化

**驱逐恢复：**
当被驱逐时，会捕获完整上下文（冲突文件、差异、测试输出），并在下一次 Ralph 执行中反馈给实现者：
```markdown
## MERGE CONFLICT — RESOLVE BEFORE NEXT LANDING

Your previous implementation conflicted with another unit that landed first.
Restructure your changes to avoid the conflicting files/lines below.

{full eviction context with diffs}
```
### 阶段之间的数据流
```
research.contextFilePath ──────────────────→ plan
plan.implementationSteps ──────────────────→ implement
implement.{filesCreated, whatWasDone} ─────→ test, reviews
test.failingSummary ───────────────────────→ reviews, implement (next pass)
reviews.{feedback, issues} ────────────────→ review-fix → implement (next pass)
final-review.reasoning ────────────────────→ implement (next pass)
evictionContext ───────────────────────────→ implement (after merge conflict)
```
### 工作树隔离

每个单元在隔离的工作树中运行（使用 jj/Jujutsu，而不是 git）：
```
/tmp/workflow-wt-{unit-id}/
```
同一单元的流水线阶段**共享**一个工作树，可以在研究 → 计划 → 实施 → 测试 → 复审过程中保留状态（上下文文件、计划文件、代码更改）。

### 关键设计原则

1. **确定性执行** — 预先拆解锁定并行性和执行顺序  
2. **在关键点进行人工复审** — 工作计划是最高杠杆的干预点  
3. **关注点分离** — 每个阶段在单独的上下文窗口中由独立代理处理  
4. **基于上下文的冲突恢复** — 完整的清理上下文支持智能重跑，而非盲目重试  
5. **分层驱动深度** — 微小更改可以跳过研究/复审；重大更改需要最大审查  
6. **可续流程** — 全部状态持久化到 SQLite；可从任何点恢复

### 何时使用 Ralphinho 与更简单的模式

| 信号 | 使用 Ralphinho | 使用更简单的模式 |
|------|----------------|-----------------|
| 多个相互依赖的工作单元 | 是 | 否 |
| 需要并行实施 | 是 | 否 |
| 可能出现合并冲突 | 是 | 否（顺序执行即可） |
| 单文件更改 | 否 | 是（顺序流水线） |
| 多日项目 | 是 | 可能（continuous-claude） |
| 规范/RFC 已撰写 | 是 | 可能 |
| 快速迭代单一事项 | 否 | 是（NanoClaw 或流水线） |

---

## 选择合适的模式

### 决策矩阵
```
Is the task a single focused change?
├─ Yes → Sequential Pipeline or NanoClaw
└─ No → Is there a written spec/RFC?
         ├─ Yes → Do you need parallel implementation?
         │        ├─ Yes → Ralphinho (DAG orchestration)
         │        └─ No → Continuous Claude (iterative PR loop)
         └─ No → Do you need many variations of the same thing?
                  ├─ Yes → Infinite Agentic Loop (spec-driven generation)
                  └─ No → Sequential Pipeline with de-sloppify
```
### 组合模式

这些模式组合得很好：

1. **顺序流水线   去乱化** — 最常见的组合方式。每个实现步骤都会进行清理处理。

2. **持续 Claude   去乱化** — 在每次迭代中添加带有去乱化指令的 `--review-prompt`。

3. **任何循环   验证** — 在提交前使用 ECC 的 `/verify` 命令或 `verification-loop` 技能作为检查门。

4. **Ralphinho 的分层方法在更简单的循环中** — 即使在顺序流水线中，也可以将简单任务路由到 Haiku，将复杂任务路由到 Opus：
   ```bash
   # Simple formatting fix
   claude -p --model haiku "Fix the import ordering in src/utils.ts"

   # Complex architectural change
   claude -p --model opus "Refactor the auth module to use the strategy pattern"
   ```
---

## 反模式

### 常见错误

1. **没有退出条件的无限循环** — 始终设置最大运行次数、最大成本、最大持续时间或完成信号。

2. **迭代之间没有上下文桥接** — 每次 `claude -p` 调用都是全新开始。使用 `SHARED_TASK_NOTES.md` 或文件系统状态来桥接上下文。

3. **重复同样的失败** — 如果一次迭代失败，不要只是重试。捕获错误上下文并将其传给下一次尝试。

4. **负面指令而非清理步骤** — 不要说“不要做 X”。添加一个单独的步骤来移除 X。

5. **所有代理在一个上下文窗口中** — 对于复杂工作流程，将不同关注点分离到不同的代理进程中。审阅者绝不能兼任作者。

6. **忽略并行工作中文件重叠** — 如果两个并行代理可能编辑同一个文件，则需要一个合并策略（顺序落地、变基或冲突解决）。

---

## 参考资料

| 项目 | 作者 | 链接 |
|---------|--------|------|
| Ralphinho | enitrat | 版权：@enitrat |
| Infinite Agentic Loop | disler | 版权：@disler |
| Continuous Claude | AnandChowdhary | 版权：@AnandChowdhary |
| NanoClaw | ECC | 本仓库 `/claw` 命令 |
| Verification Loop | ECC | 本仓库 `skills/verification-loop/` |
