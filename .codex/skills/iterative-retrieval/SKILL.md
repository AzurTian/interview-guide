---
name: iterative-retrieval
description: 用于逐步改进上下文检索以解决子代理上下文问题的模式
---
# 迭代检索模式

解决多代理工作流中的“上下文问题”，当子代理在开始工作前不知道所需的上下文时。

## 何时激活

- 生成需要代码库上下文但无法提前预测的子代理
- 构建上下文逐步完善的多代理工作流
- 遇到代理任务中的“上下文过大”或“缺失上下文”失败
- 设计用于代码探索的 RAG 类检索管道
- 优化代理编排中的令牌使用

## 问题

子代理在上下文有限的情况下生成。它们不知道：
- 哪些文件包含相关代码
- 代码库中存在哪些模式
- 项目使用了哪些术语

标准方法失败：
- **发送所有内容**：超出上下文限制
- **不发送任何内容**：代理缺少关键信息
- **猜测所需内容**：通常错误

## 解决方案：迭代检索

一个四阶段循环，逐步完善上下文：
```
┌─────────────────────────────────────────────┐
│                                             │
│   ┌──────────┐      ┌──────────┐            │
│   │ DISPATCH │─────│ EVALUATE │            │
│   └──────────┘      └──────────┘            │
│        ▲                  │                 │
│        │                  ▼                 │
│   ┌──────────┐      ┌──────────┐            │
│   │   LOOP   │─────│  REFINE  │            │
│   └──────────┘      └──────────┘            │
│                                             │
│        Max 3 cycles, then proceed           │
└─────────────────────────────────────────────┘
```
### 阶段 1：调度

初步广泛查询以收集候选文件：
```javascript
// Start with high-level intent
const initialQuery = {
  patterns: ['src/**/*.ts', 'lib/**/*.ts'],
  keywords: ['authentication', 'user', 'session'],
  excludes: ['*.test.ts', '*.spec.ts']
};

// Dispatch to retrieval agent
const candidates = await retrieveFiles(initialQuery);
```
### 阶段 2：评估

评估检索到的内容是否相关：
```javascript
function evaluateRelevance(files, task) {
  return files.map(file => ({
    path: file.path,
    relevance: scoreRelevance(file.content, task),
    reason: explainRelevance(file.content, task),
    missingContext: identifyGaps(file.content, task)
  }));
}
```
评分标准：
- **高（0.8-1.0）**：直接实现目标功能
- **中（0.5-0.7）**：包含相关模式或类型
- **低（0.2-0.4）**：关系间接相关
- **无（0-0.2）**：无关，排除

### 第三阶段：优化

根据评估更新搜索标准：
```javascript
function refineQuery(evaluation, previousQuery) {
  return {
    // Add new patterns discovered in high-relevance files
    patterns: [...previousQuery.patterns, ...extractPatterns(evaluation)],

    // Add terminology found in codebase
    keywords: [...previousQuery.keywords, ...extractKeywords(evaluation)],

    // Exclude confirmed irrelevant paths
    excludes: [...previousQuery.excludes, ...evaluation
      .filter(e => e.relevance < 0.2)
      .map(e => e.path)
    ],

    // Target specific gaps
    focusAreas: evaluation
      .flatMap(e => e.missingContext)
      .filter(unique)
  };
}
```
### 第四阶段：循环

使用优化后的标准重复（最多三轮）：
```javascript
async function iterativeRetrieve(task, maxCycles = 3) {
  let query = createInitialQuery(task);
  let bestContext = [];

  for (let cycle = 0; cycle < maxCycles; cycle++) {
    const candidates = await retrieveFiles(query);
    const evaluation = evaluateRelevance(candidates, task);

    // Check if we have sufficient context
    const highRelevance = evaluation.filter(e => e.relevance >= 0.7);
    if (highRelevance.length >= 3 && !hasCriticalGaps(evaluation)) {
      return highRelevance;
    }

    // Refine and continue
    query = refineQuery(evaluation, query);
    bestContext = mergeContext(bestContext, highRelevance);
  }

  return bestContext;
}
```
## 实际示例

### 示例 1：错误修复上下文
```
Task: "Fix the authentication token expiry bug"

Cycle 1:
  DISPATCH: Search for "token", "auth", "expiry" in src/**
  EVALUATE: Found auth.ts (0.9), tokens.ts (0.8), user.ts (0.3)
  REFINE: Add "refresh", "jwt" keywords; exclude user.ts

Cycle 2:
  DISPATCH: Search refined terms
  EVALUATE: Found session-manager.ts (0.95), jwt-utils.ts (0.85)
  REFINE: Sufficient context (2 high-relevance files)

Result: auth.ts, tokens.ts, session-manager.ts, jwt-utils.ts
```
### 示例 2：功能实现
```
Task: "Add rate limiting to API endpoints"

Cycle 1:
  DISPATCH: Search "rate", "limit", "api" in routes/**
  EVALUATE: No matches - codebase uses "throttle" terminology
  REFINE: Add "throttle", "middleware" keywords

Cycle 2:
  DISPATCH: Search refined terms
  EVALUATE: Found throttle.ts (0.9), middleware/index.ts (0.7)
  REFINE: Need router patterns

Cycle 3:
  DISPATCH: Search "router", "express" patterns
  EVALUATE: Found router-setup.ts (0.8)
  REFINE: Sufficient context

Result: throttle.ts, middleware/index.ts, router-setup.ts
```
## 与代理的整合

在代理提示中使用：
```markdown
When retrieving context for this task:
1. Start with broad keyword search
2. Evaluate each file's relevance (0-1 scale)
3. Identify what context is still missing
4. Refine search criteria and repeat (max 3 cycles)
5. Return files with relevance >= 0.7
```
## 最佳实践

1. **先宽后窄，逐步聚焦** - 不要在初始查询中过度指定
2. **学习代码库术语** - 第一次循环通常会揭示命名约定
3. **跟踪缺失内容** - 明确识别空缺有助于改进
4. **达到“足够好”即可** - 3 个高相关文件胜过 10 个普通文件
5. **自信地排除** - 低相关文件不会变得相关

## 相关内容

- [长篇指南](https://x.com/affaanmustafa/status/2014040193557471352) - 子代理编排章节
- `continuous-learning` 技能 - 用于随时间改进的模式
- 与 ECC 捆绑的代理定义（手动安装路径：`agents/`）
