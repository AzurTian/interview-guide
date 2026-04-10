---
description: 用于审核 Claude 技能和指令的质量。支持快速扫描（仅更改的技能）和全面盘点模式，并进行顺序子代理批量评估。
---
# 技能盘点

斜杠命令（`/skill-stocktake`）使用质量检查表和 AI 整体判断来审核所有 Claude 技能和命令。支持两种模式：快速扫描用于最近更改的技能，全面盘点用于完整检查。

## 范围

该命令针对以下路径 **相对于其被调用的目录**：

| 路径 | 描述 |
|------|-------------|
| `~/.claude/skills/` | 全局技能（所有项目） |
| `{cwd}/.claude/skills/` | 项目级技能（如果该目录存在） |

**在第一阶段开始时，命令会明确列出找到并扫描的路径。**

### 针对特定项目

要包含项目级技能，请从该项目的根目录运行：
```bash
cd ~/path/to/my-project
/skill-stocktake
```
如果项目没有 `.claude/skills/` 目录，则只能评估全局技能和命令。

## 模式

| 模式 | 触发条件 | 持续时间 |
|------|---------|---------|
| 快速扫描 | 存在 `results.json`（默认） | 5–10 分钟 |
| 全面盘点 | `results.json` 不存在，或 `/skill-stocktake full` | 20–30 分钟 |

**结果缓存：** `~/.claude/skills/skill-stocktake/results.json`

## 快速扫描流程

仅重新评估自上次运行以来发生变化的技能（5–10 分钟）。

1. 读取 `~/.claude/skills/skill-stocktake/results.json`
2. 运行： `bash ~/.claude/skills/skill-stocktake/scripts/quick-diff.sh \
         ~/.claude/skills/skill-stocktake/results.json`
   （项目目录由 `$PWD/.claude/skills` 自动检测；只有在必要时才显式传入）
3. 如果输出为 `[]`：报告“自上次运行以来无变化。”并停止
4. 使用相同的第 2 阶段标准仅重新评估那些已更改的文件
5. 从以前的结果中保留未更改的技能
6. 仅输出差异
7. 运行： `bash ~/.claude/skills/skill-stocktake/scripts/save-results.sh \
         ~/.claude/skills/skill-stocktake/results.json <<< "$EVAL_RESULTS"`

## 全面盘点流程

### 第 1 阶段 — 清单

运行： `bash ~/.claude/skills/skill-stocktake/scripts/scan.sh`

该脚本会列出技能文件、提取 frontmatter，并收集 UTC 修改时间。
项目目录由 `$PWD/.claude/skills` 自动检测；只有在必要时才显式传入。
从脚本输出中呈现扫描摘要和清单表：
```
Scanning:
  ✓ ~/.claude/skills/         (17 files)
  ✗ {cwd}/.claude/skills/    (not found — global skills only)
```
| 技能 | 7天使用次数 | 30天使用次数 | 描述 |
|-------|--------|---------|-------------|

### 第二阶段 — 质量评估

启动一个代理工具子代理（**通用代理**），使用完整的库存和清单：
```text
Agent(
  subagent_type="general-purpose",
  prompt="
Evaluate the following skill inventory against the checklist.

[INVENTORY]

[CHECKLIST]

Return JSON for each skill:
{ \"verdict\": \"Keep\"|\"Improve\"|\"Update\"|\"Retire\"|\"Merge into [X]\", \"reason\": \"...\" }
"
)
```
子代理读取每个技能，应用检查清单，并返回每个技能的 JSON：

`{ "verdict": "保留"|"改进"|"更新"|"弃用"|"合并至 [X]", "reason": "..." }`

**分块指南:** 每次子代理调用处理约 20 个技能，以保持上下文可管理。每处理完一块后，将中间结果保存到 `results.json` (`status: "in_progress"`)。

在所有技能评估完成后：将 `status` 设置为 "completed"，并进入第 3 阶段。

**恢复检测:** 如果启动时发现 `status: "in_progress"`，则从第一个未评估技能开始恢复。

每个技能将根据以下检查清单进行评估:
```
- [ ] Content overlap with other skills checked
- [ ] Overlap with MEMORY.md / CLAUDE.md checked
- [ ] Freshness of technical references verified (use WebSearch if tool names / CLI flags / APIs are present)
- [ ] Usage frequency considered
```
裁定标准：

| 裁定 | 含义 |
|---------|---------|
| 保留 | 有用且最新 |
| 改进 | 值得保留，但需要具体改进 |
| 更新 | 引用的技术已过时（通过 WebSearch 验证） |
| 退休 | 质量低、陈旧或成本不对称 |
| 合并到 [X] | 与其他技能有大量重叠；命名合并目标 |

评估是**整体 AI 判断**——而非数值评分。指导维度：
- **可操作性**：代码示例、命令或步骤，使你能立即执行
- **范围适配**：名称、触发器和内容一致；不要过于宽泛或狭窄
- **独特性**：价值不能被 MEMORY.md / CLAUDE.md / 其他技能替代
- **及时性**：技术引用在当前环境中可用

**理由质量要求**——`reason` 字段必须自包含并能支持决策：
- 不要单独写“未变更”——必须重述核心证据
- 对于 **退休**：说明 (1) 发现的具体缺陷，(2) 已用什么覆盖相同需求
  - 错误示例：`"Superseded"`
  - 正确示例：`"disable-model-invocation: true 已设置；由 continuous-learning-v2 替代，它覆盖所有相同模式并加上置信评分。没有剩余的独特内容。"`
- 对于 **合并**：命名目标并描述要整合的内容
  - 错误示例：`"与 X 重叠"`
  - 正确示例：`"42 行内容较薄；chatlog-to-article 的第 4 步已覆盖相同工作流。将 '文章角度' 提示作为笔记整合到该技能中。"`
- 对于 **改进**：描述需要的具体更改（哪一部分、采取何种操作、目标长度如相关）
  - 错误示例：`"太长"`
  - 正确示例：`"276 行；'框架比较' 部分（第 80–140 行）重复了 ai-era-architecture-principles；删除该部分以达到约 150 行。"`
- 对 **保留**（仅 mtime 变化的快速扫描）：重述原始判决理由，不要写“未更改”
  - 错误示例：`"未更改"`
  - 正确示例：`"mtime 已更新，但内容未变。rules/python/ 明确导入了唯一 Python 引用；未发现重叠。"`

### 第三阶段 — 汇总表

| 技能 | 7天内使用 | 判决 | 原因 |
|-------|--------|---------|--------|

### 第四阶段 — 整合

1. **退役 / 合并**：在向用户确认前，针对每个文件提供详细理由：
   - 发现了什么具体问题（重叠、陈旧、引用损坏等）
   - 哪种替代方案覆盖了相同功能（退役：哪个现有技能/规则；合并：目标文件及要整合的内容）
   - 删除的影响（任何依赖技能、MEMORY.md 的引用或受影响的工作流）
2. **改进**：提供具体改进建议及理由：
   - 要更改什么以及为什么（例如，“将 430 行剪裁至 200 行，因为 X/Y 部分重复 python-patterns”）
   - 由用户决定是否执行
3. **更新**：展示更新后的内容并检查来源
4. 检查 MEMORY.md 行数；若超过 100 行，建议压缩

## 结果文件架构

`~/.claude/skills/skill-stocktake/results.json`：

**`evaluated_at`**：必须设置为评估完成的实际 UTC 时间。
通过 Bash 获取：`date -u  %Y-%m-%dT%H:%M:%SZ`。切勿使用仅日期的近似值，例如 `T00:00:00Z`。
```json
{
  "evaluated_at": "2026-02-21T10:00:00Z",
  "mode": "full",
  "batch_progress": {
    "total": 80,
    "evaluated": 80,
    "status": "completed"
  },
  "skills": {
    "skill-name": {
      "path": "~/.claude/skills/skill-name/SKILL.md",
      "verdict": "Keep",
      "reason": "Concrete, actionable, unique value for X workflow",
      "mtime": "2026-01-15T08:30:00Z"
    }
  }
}
```
## 注意事项

- 评估是盲审的：相同的检查清单适用于所有技能，无论来源（ECC、自创、自动提取）
- 归档/删除操作始终需要用户明确确认
- 不根据技能来源分支裁决
