---
name: continuous-learning-v2
description: 基于本能的学习系统，通过钩子观察会话，创建带有置信评分的原子本能，并将其发展为技能/命令/代理。v2.1 增加了项目范围的本能，以防止跨项目污染。
version: 2.1.0
---
# 持续学习 v2.1 - 本能
基于本能的架构

一个先进的学习系统，通过原子化的“本能”把 Claude Code 会话沉淀为可复用知识。“本能”是带有置信评分的小型习得行为。

**v2.1** 增加了**项目范围的本能**——React 模式保留在你的 React 项目中，Python 约定保留在你的 Python 项目中，通用模式（如“始终验证输入”）是全球共享的。

## 何时激活

- 设置从 Claude Code 会话中自动学习
- 通过钩子配置基于本能的行为提取
- 学习行为的置信阈值调优
- 审查、导出或导入本能库
- 将本能演化为完整的技能、命令或代理
- 管理项目范围与全局范围的本能
- 将本能从项目范围提升到全局范围

## v2.1 的新内容

|特色 |v2.0 |v2.1 |
|---------|------|------|
|存储 |全球（~/.claude/homunculus/） |项目范围（projects/<hash>/） |
|范围 |所有本能在任何地方都生效 |项目范围 + 全局 |
|检测 |无 |git remote URL / 仓库路径 |
|晋升 |无 |在 2 个以上项目中出现时，从项目提升为全局 |
|命令 |4（status/evolve/export/import）|6（+promote/projects） |
|跨项目 |污染风险 |默认隔离 |

## v2 有什么新内容（对比 v1）

|特色 |v1 |v2 |
|---------|----|----|
|观察 |停止钩子（会话结束） |PreToolUse/PostToolUse（100% 可靠） |
|分析 |主上下文 |后台代理（Haiku） |
|粒度 |完整技能 |原子“本能” |
|信心 |没有 |0.3-0.9 加权 |
|演变 |直达技能 |本能 -> 集群 -> 技能/命令/代理 |
|分享 |无 |导出/导入本能 |

## 本能模型
本能是一种小的学习行为：
```yaml
---
id: prefer-functional-style
trigger: "when writing new functions"
confidence: 0.7
domain: "code-style"
source: "session-observation"
scope: project
project_id: "a1b2c3d4e5f6"
project_name: "my-react-app"
---

# Prefer Functional Style

## Action
Use functional patterns over classes when appropriate.

## Evidence
- Observed 5 instances of functional pattern preference
- User corrected class-based approach to functional on 2025-01-15
```
**属性：**
- **原子性** -- 一个触发器，对应一个动作
- **置信度加权** -- 0.3 = 暂定，0.9 = 几乎确定
- **领域标记** -- 代码风格、测试、Git、调试、工作流程等
- **有证据支持** -- 追踪创建它的观察
- **范围感知** -- `project`（默认）或 `global`

## 工作原理
```
Session Activity (in a git repo)
      |
      | Hooks capture prompts + tool use (100% reliable)
      | + detect project context (git remote / repo path)
      v
+---------------------------------------------+
|  projects/<project-hash>/observations.jsonl  |
|   (prompts, tool calls, outcomes, project)   |
+---------------------------------------------+
      |
      | Observer agent reads (background, Haiku)
      v
+---------------------------------------------+
|          PATTERN DETECTION                   |
|   * User corrections -> instinct             |
|   * Error resolutions -> instinct            |
|   * Repeated workflows -> instinct           |
|   * Scope decision: project or global?       |
+---------------------------------------------+
      |
      | Creates/updates
      v
+---------------------------------------------+
|  projects/<project-hash>/instincts/personal/ |
|   * prefer-functional.yaml (0.7) [project]   |
|   * use-react-hooks.yaml (0.9) [project]     |
+---------------------------------------------+
|  instincts/personal/  (GLOBAL)               |
|   * always-validate-input.yaml (0.85) [global]|
|   * grep-before-edit.yaml (0.6) [global]     |
+---------------------------------------------+
      |
      | /evolve clusters + /promote
      v
+---------------------------------------------+
|  projects/<hash>/evolved/ (project-scoped)   |
|  evolved/ (global)                           |
|   * commands/new-feature.md                  |
|   * skills/testing-workflow.md               |
|   * agents/refactor-specialist.md            |
+---------------------------------------------+
```
## 项目检测

系统会自动检测你当前的项目：

1. **`CLAUDE_PROJECT_DIR` 环境变量**（最高优先级）
2. **`git remote get-url origin`** —— 哈希生成可移植的项目 ID（同一仓库在不同机器上获得相同 ID）
3. **`git rev-parse --show-toplevel`** —— 使用仓库路径作为回退（机器特定）
4. **全局回退** —— 如果未检测到项目，则默认转到全局范围

每个项目都有一个 12 位字符的哈希 ID（例如：`a1b2c3d4e5f6`）。注册文件位于 `~/.claude/homunculus/projects.json`，将 ID 映射到可读名称。

## 快速开始

### 1. 启用观察钩子

添加到你的 `~/.claude/settings.json`。

**如果作为插件安装**（推荐）：
```json
{
  "hooks": {
    "PreToolUse": [{
      "matcher": "*",
      "hooks": [{
        "type": "command",
        "command": "${CLAUDE_PLUGIN_ROOT}/skills/continuous-learning-v2/hooks/observe.sh"
      }]
    }],
    "PostToolUse": [{
      "matcher": "*",
      "hooks": [{
        "type": "command",
        "command": "${CLAUDE_PLUGIN_ROOT}/skills/continuous-learning-v2/hooks/observe.sh"
      }]
    }]
  }
}
```
**如果手动安装** 到 `~/.claude/skills`：
```json
{
  "hooks": {
    "PreToolUse": [{
      "matcher": "*",
      "hooks": [{
        "type": "command",
        "command": "~/.claude/skills/continuous-learning-v2/hooks/observe.sh"
      }]
    }],
    "PostToolUse": [{
      "matcher": "*",
      "hooks": [{
        "type": "command",
        "command": "~/.claude/skills/continuous-learning-v2/hooks/observe.sh"
      }]
    }]
  }
}
```
### 2. 初始化目录结构

系统在首次使用时会自动创建目录，但您也可以手动创建它们：
```bash
# Global directories
mkdir -p ~/.claude/homunculus/{instincts/{personal,inherited},evolved/{agents,skills,commands},projects}

# Project directories are auto-created when the hook first runs in a git repo
```
### 3. 使用本能指令
```bash
/instinct-status     # Show learned instincts (project + global)
/evolve              # Cluster related instincts into skills/commands
/instinct-export     # Export instincts to file
/instinct-import     # Import instincts from others
/promote             # Promote project instincts to global scope
/projects            # List all known projects and their instinct counts
```
## 命令

| 命令 | 描述 |
|---------|-------------|
| `/instinct-status` | 显示所有本能（项目范围和全局）及其置信度 |
| `/evolve` | 将相关本能聚类为技能/命令，建议升级 |
| `/instinct-export` | 导出本能（可按范围/领域过滤） |
| `/instinct-import <file>` | 导入具有范围控制的本能 |
| `/promote [id]` | 将项目本能提升到全局范围 |
| `/projects` | 列出所有已知项目及其本能数量 |

## 配置

编辑 `config.json` 来控制后台观察者：
```json
{
  "version": "2.1",
  "observer": {
    "enabled": false,
    "run_interval_minutes": 5,
    "min_observations_to_analyze": 20
  }
}
```
| 键 | 默认值 | 描述 |
|-----|---------|-------------|
| `observer.enabled` | `false` | 启用后台观察者代理 |
| `observer.run_interval_minutes` | `5` | 观察者分析观察数据的频率 |
| `observer.min_observations_to_analyze` | `20` | 分析运行前的最少观察数 |

其他行为（观察捕捉、本能阈值、项目范围、升级标准）通过 `instinct-cli.py` 和 `observe.sh` 中的代码默认值进行配置。

## 文件结构
```
~/.claude/homunculus/
+-- identity.json           # Your profile, technical level
+-- projects.json           # Registry: project hash -> name/path/remote
+-- observations.jsonl      # Global observations (fallback)
+-- instincts/
|   +-- personal/           # Global auto-learned instincts
|   +-- inherited/          # Global imported instincts
+-- evolved/
|   +-- agents/             # Global generated agents
|   +-- skills/             # Global generated skills
|   +-- commands/           # Global generated commands
+-- projects/
    +-- a1b2c3d4e5f6/       # Project hash (from git remote URL)
    |   +-- project.json    # Per-project metadata mirror (id/name/root/remote)
    |   +-- observations.jsonl
    |   +-- observations.archive/
    |   +-- instincts/
    |   |   +-- personal/   # Project-specific auto-learned
    |   |   +-- inherited/  # Project-specific imported
    |   +-- evolved/
    |       +-- skills/
    |       +-- commands/
    |       +-- agents/
    +-- f6e5d4c3b2a1/       # Another project
        +-- ...
```
## 范围决策指南

| 模式类型 | 范围 | 示例 |
|-------------|-------|---------|
| 语言/框架约定 | **项目** | “使用 React hooks”，“遵循 Django REST 模式” |
| 文件结构偏好 | **项目** | “测试放在 `__tests__/`”，“组件放在 src/components/” |
| 代码风格 | **项目** | “使用函数式风格”，“优先使用数据类” |
| 错误处理策略 | **项目** | “使用 Result 类型处理错误” |
| 安全实践 | **全局** | “验证用户输入”，“清理 SQL” |
| 一般最佳实践 | **全局** | “先写测试”，“始终处理错误” |
| 工具工作流偏好 | **全局** | “编辑前先 grep”，“写入前先阅读” |
| Git 实践 | **全局** | “常规提交”，“小而集中的提交” |

## 直觉提升（项目 -> 全局）

当相同直觉在多个项目中高置信度出现时，它可以作为升格为全局范围的候选。

**自动升格标准:**
- 在 2 个项目中存在相同的直觉 ID
- 平均置信度 >= 0.8

**如何升格:**
```bash
# Promote a specific instinct
python3 instinct-cli.py promote prefer-explicit-errors

# Auto-promote all qualifying instincts
python3 instinct-cli.py promote

# Preview without changes
python3 instinct-cli.py promote --dry-run
```
`/evolve` 命令也会提示可晋升的候选项。

## 信心评分

自信会随着时间不断变化：

|评分 |含义 |行为 |
|-------|---------|----------|
|0.3 |暂定 |建议但未执行 |
|0.5 |中等 |适用时 |
|0.7 |坚强 |自动批准申请 |
|0.9 |几乎可以确定 |核心行为 |

**信心提升**当：
- 反复观察到模式
- 用户未纠正建议行为
- 其他来源的类似直觉也一致

**信心下降**当：
- 用户明确纠正行为
- 模式不会长时间被观察到
- 出现矛盾证据

## 为什么选择钩子 vs 观察技巧？

>“第一版依赖观察技能。技能是概率的——根据克劳德的判断，命中率大约有50%到80%。

钩子是**100%的时刻**，而且是确定性地命中。这意味着：
- 观察到每一个工具调用
- 没有遗漏的模式
- 学习是全面的

## 向下兼容

v2.1 与 v2.0 和 v1 完全兼容：
- 在“~/.claude/homunculus/instincts/'中已有的全局本能仍然作为全局本能起作用
- v1中已有的“~/.claude/skills/learned/”技能仍然有效
- 停止钩依然运行（但现在也进入 v2）
- 渐进迁移：两者并行运行

## 隐私

- 观测数据保持在你的机器上**本地**
- 每个项目的本能被隔离开来
- 只能导出**本能*（模式）——不能导出原始观察
- 不分享实际代码或对话内容
- 你控制哪些产品被出口和推广

## 相关

- [ECC-Tools GitHub 应用]（https://github.com/apps/ecc-tools） - 从仓库历史生成本能
- 小人计划（Homunculus）— 一个社区项目，启发了 v2 基于直觉的架构（原子观察、置信度评分、直觉进化管道）
- [长篇指南](https://x.com/affaanmustafa/status/2014040193557471352) — 持续学习部分

---

*基于直觉的学习：一次一个项目地教 Claude 你的模式。*
