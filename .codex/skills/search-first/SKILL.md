---
name: search-first
description: 编码前的研究工作流。在编写自定义代码之前，先搜索现有工具、库和模式。调用研究代理。
---
# /search-first — 在编码前先研究

将“在实现前先搜索现有解决方案”的工作流程系统化。

## 触发条件

在以下情况下使用此技能：
- 开始一个可能已有现有解决方案的新功能
- 添加依赖项或集成
- 用户提出“添加 X 功能”，而你正准备编写代码
- 在创建新的工具、辅助函数或抽象之前

## 工作流程
```
┌─────────────────────────────────────────────┐
│  1. NEED ANALYSIS                           │
│     Define what functionality is needed      │
│     Identify language/framework constraints  │
├─────────────────────────────────────────────┤
│  2. PARALLEL SEARCH (researcher agent)      │
│     ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│     │  npm /   │ │  MCP /   │ │  GitHub / │  │
│     │  PyPI    │ │  Skills  │ │  Web      │  │
│     └──────────┘ └──────────┘ └──────────┘  │
├─────────────────────────────────────────────┤
│  3. EVALUATE                                │
│     Score candidates (functionality, maint, │
│     community, docs, license, deps)         │
├─────────────────────────────────────────────┤
│  4. DECIDE                                  │
│     ┌─────────┐  ┌──────────┐  ┌─────────┐  │
│     │  Adopt  │  │  Extend  │  │  Build   │  │
│     │ as-is   │  │  /Wrap   │  │  Custom  │  │
│     └─────────┘  └──────────┘  └─────────┘  │
├─────────────────────────────────────────────┤
│  5. IMPLEMENT                               │
│     Install package / Configure MCP /       │
│     Write minimal custom code               │
└─────────────────────────────────────────────┘
```
## 决策矩阵

| 信号 | 行动 |
|--------|--------|
| 完全匹配，维护良好，MIT/Apache | **采用** — 直接安装并使用 |
| 部分匹配，基础良好 | **扩展** — 安装并编写轻量封装 |
| 多个弱匹配 | **组合** — 结合2-3个小型包 |
| 未找到合适的 | **开发** — 自行编写，但参考研究结果 |

## 如何使用

### 快速模式（行内）

在编写工具或添加功能之前，脑中运行以下流程：

0. 这在仓库中已经存在吗？→ 先用 `rg` 在相关模块/测试中搜索
1. 这是常见问题吗？→ 搜索 npm/PyPI
2. 是否有 MCP？→ 检查 `~/.claude/settings.json` 并搜索
3. 是否有现成技能？→ 检查 `~/.claude/skills/`
4. 是否有 GitHub 上的实现/模板？→ 在编写全新代码前，运行 GitHub 代码搜索已维护的开源项目

### 完整模式（代理）

对于非平凡功能，启动研究代理：
```
Task(subagent_type="general-purpose", prompt="
  Research existing tools for: [DESCRIPTION]
  Language/framework: [LANG]
  Constraints: [ANY]

  Search: npm/PyPI, MCP servers, Claude Code skills, GitHub
  Return: Structured comparison with recommendation
")
```
## 按类别搜索快捷方式

### 开发工具
- Linting → `eslint`、`ruff`、`textlint`、`markdownlint`
- 格式化 → `prettier`、`black`、`gofmt`
- 测试 → `jest`、`pytest`、`go test`
- 预提交 → `husky`、`lint-staged`、`pre-commit`

### 人工智能/大型语言模型集成
- Claude SDK → Context7 获取最新文档
- 提示词管理 → 检查 MCP 服务器
- 文档处理 → `unstructured`、`pdfplumber`、`mammoth`

### 数据与API
- HTTP 客户端 → `httpx`（Python）、`ky` / `got`（Node.js）
- 验证 → `zod`（TS）、`pydantic`（Python）
- 数据库 → 先检查 MCP 服务器

### 内容与出版
- Markdown 处理 → `remark`、`unified`、`markdown-it`
- 图像优化 → `sharp`、`imagemin`

## 整合点

### 与规划代理配合
规划代理应在第一阶段（架构审查）前调用研究代理：
- 研究代理识别可用工具
- 规划代理将其纳入实施计划
- 避免在计划中“重复造轮子”。

### 与架构代理配合
架构代理应咨询研究代理：
- 技术栈决策
- 现有模式发现
- 可参考的现有架构

### 与 iterative-retrieval 技能配合
结合它进行渐进式发现：
- 第一周期：广泛搜索（npm、PyPI、MCP）
- 第二周期：详细评估顶尖候选人
- 第三周期：测试与项目约束的兼容性

## 示例

### 示例1：“添加死链检查”
```
Need: Check markdown files for broken links
Search: npm "markdown dead link checker"
Found: textlint-rule-no-dead-link (score: 9/10)
Action: ADOPT — npm install textlint-rule-no-dead-link
Result: Zero custom code, battle-tested solution
```
### 示例 2："添加 HTTP 客户端包装器"
```
Need: Resilient HTTP client with retries and timeout handling
Search: npm "http client retry", PyPI "httpx retry"
Found: got (Node) with retry plugin, httpx (Python) with built-in retry
Action: ADOPT — use got/httpx directly with retry config
Result: Zero custom code, production-proven libraries
```
### 示例 3：“添加配置文件检查器”
```
Need: Validate project config files against a schema
Search: npm "config linter schema", "json schema validator cli"
Found: ajv-cli (score: 8/10)
Action: ADOPT + EXTEND — install ajv-cli, write project-specific schema
Result: 1 package + 1 schema file, no custom validation logic
```
## 反模式

- **直接写代码**：在没有检查现有工具的情况下编写实用程序
- **忽略 MCP**：不检查 MCP 服务器是否已提供该功能
- **过度自定义**：过度封装一个库以至于它失去了原有的优势
- **依赖膨胀**：为一个小功能安装庞大的包
