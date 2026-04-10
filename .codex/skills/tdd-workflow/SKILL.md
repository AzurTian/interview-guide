---
name: tdd-workflow
description: 在编写新功能、修复错误或重构代码时使用此技能。通过包括单元测试、集成测试和端到端测试的 80% 覆盖率来强制执行测试驱动开发。
---
# 测试驱动开发工作流程

此技能确保所有代码开发均遵循 TDD 原则并具备全面的测试覆盖。

## 何时启用

- 编写新功能或功能模块
- 修复 bug 或问题
- 重构现有代码
- 添加 API 接口
- 创建新组件

## 核心原则

### 1. 先测试后编码
始终先编写测试，然后实现代码以通过测试。

### 2. 覆盖要求
- 最低覆盖率 80%（单元测试、集成测试、端到端测试）
- 覆盖所有边界情况
- 测试错误场景
- 验证边界条件

### 3. 测试类型

#### 单元测试
- 单个函数和工具
- 组件逻辑
- 纯函数
- 辅助工具和公用函数

#### 集成测试
- API 接口
- 数据库操作
- 服务交互
- 外部 API 调用

#### 端到端测试（Playwright）
- 关键用户流程
- 完整工作流程
- 浏览器自动化
- 用户界面交互

### 4. Git 检查点
- 如果仓库在 Git 下管理，每个 TDD 阶段后创建一个检查点提交
- 在工作流程完成之前，不要 squash 或重写这些检查点提交
- 每个检查点提交信息必须描述阶段及捕获的具体证据
- 仅计算当前任务的活动分支上创建的提交
- 不要将其他分支、先前无关工作或远程分支历史的提交视为有效的检查点证据
- 在将检查点视为完成之前，需确认提交可从活动分支当前的 `HEAD` 达到，并属于当前任务序列
- 推荐的简洁工作流程为：
  - 一个提交用于添加失败测试并验证为 RED
  - 一个提交用于应用最小修复并验证为 GREEN
  - 可选一个提交用于完成重构
- 如果测试提交明确对应RED，修复提交明确对应GREEN，则不需要单独的仅包含证据的提交

## TDD 工作流程步骤

### 步骤 1：编写用户旅程
```
As a [role], I want to [action], so that [benefit]

Example:
As a user, I want to search for markets semantically,
so that I can find relevant markets even without exact keywords.
```
### 第2步：生成测试用例
针对每个用户旅程，创建全面的测试用例：
```typescript
describe('Semantic Search', () => {
  it('returns relevant markets for query', async () => {
    // Test implementation
  })

  it('handles empty query gracefully', async () => {
    // Test edge case
  })

  it('falls back to substring search when Redis unavailable', async () => {
    // Test fallback behavior
  })

  it('sorts results by similarity score', async () => {
    // Test sorting logic
  })
})
```
### 第3步：运行测试（它们应该失败）
```bash
npm test
# Tests should fail - we haven't implemented yet
```
这一步是强制性的，是所有生产环境变更的 RED 门槛。

在修改业务逻辑或其他生产代码之前，必须通过以下任意路径验证有效的 RED 状态：
- 运行时 RED：
  - 相关测试目标成功编译
  - 新增或修改的测试实际上被执行
  - 结果为 RED
- 编译时 RED：
  - 新测试新实例化、引用或执行了有缺陷的代码路径
  - 编译失败本身就是预期的 RED 信号
- 在任一情况下，失败均由预期的业务逻辑错误、未定义行为或缺失实现引起
- 失败不能仅由无关的语法错误、测试设置损坏、依赖缺失或无关的回归引起

仅编写但未编译和执行的测试不算作 RED。

在确认 RED 状态之前，不要编辑生产代码。

如果仓库在 Git 下，在此阶段验证后立即创建一个检查点提交。
推荐的提交信息格式：
- `test: add reproducer for <feature or bug>`
- 如果复现测试已编译并执行且因预期原因失败，该提交也可作为 RED 验证检查点
- 继续前，请确保此检查点提交在当前活动分支上

### 第四步：实现代码
编写最小代码以使测试通过：
```typescript
// Implementation guided by tests
export async function searchMarkets(query: string) {
  // Implementation here
}
```
如果仓库在 Git 下，现在暂存最小修复，但在第 5 步验证 GREEN 之前，推迟检查点提交。

### 第 5 步：再次运行测试
```bash
npm test
# Tests should now pass
```
在修复后重新运行相同的相关测试目标，并确认先前失败的测试现在已变为绿色。

只有在获得有效的绿色结果后，才可以继续进行重构。

如果仓库在 Git 下，在确认绿色结果后立即创建一个检查点提交。
推荐的提交信息格式：
- `fix: <功能或缺陷>`
- 如果重新运行相同的相关测试目标并通过，修复提交也可以作为绿色验证检查点
- 在继续操作之前，确认该检查点提交在当前活动分支上

### 第6步：重构
在保持测试绿色的情况下提高代码质量：
- 删除重复代码
- 改善命名
- 优化性能
- 提升可读性

如果仓库在 Git 下，在重构完成且测试仍然绿色后立即创建一个检查点提交。
推荐的提交信息格式：
- `refactor: clean up after <功能或缺陷> 实现`
- 在认为 TDD 循环完成之前，确认该检查点提交在当前活动分支上

### 第7步：验证覆盖率
```bash
npm run test:coverage
# Verify 80%+ coverage achieved
```
## 测试模式

### 单元测试模式 (Jest/Vitest)
```typescript
import { render, screen, fireEvent } from '@testing-library/react'
import { Button } from './Button'

describe('Button Component', () => {
  it('renders with correct text', () => {
    render(<Button>Click me</Button>)
    expect(screen.getByText('Click me')).toBeInTheDocument()
  })

  it('calls onClick when clicked', () => {
    const handleClick = jest.fn()
    render(<Button onClick={handleClick}>Click</Button>)

    fireEvent.click(screen.getByRole('button'))

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('is disabled when disabled prop is true', () => {
    render(<Button disabled>Click</Button>)
    expect(screen.getByRole('button')).toBeDisabled()
  })
})
```
### API 集成测试模式
```typescript
import { NextRequest } from 'next/server'
import { GET } from './route'

describe('GET /api/markets', () => {
  it('returns markets successfully', async () => {
    const request = new NextRequest('http://localhost/api/markets')
    const response = await GET(request)
    const data = await response.json()

    expect(response.status).toBe(200)
    expect(data.success).toBe(true)
    expect(Array.isArray(data.data)).toBe(true)
  })

  it('validates query parameters', async () => {
    const request = new NextRequest('http://localhost/api/markets?limit=invalid')
    const response = await GET(request)

    expect(response.status).toBe(400)
  })

  it('handles database errors gracefully', async () => {
    // Mock database failure
    const request = new NextRequest('http://localhost/api/markets')
    // Test error handling
  })
})
```
### 端到端测试模式（Playwright）
```typescript
import { test, expect } from '@playwright/test'

test('user can search and filter markets', async ({ page }) => {
  // Navigate to markets page
  await page.goto('/')
  await page.click('a[href="/markets"]')

  // Verify page loaded
  await expect(page.locator('h1')).toContainText('Markets')

  // Search for markets
  await page.fill('input[placeholder="Search markets"]', 'election')

  // Wait for debounce and results
  await page.waitForTimeout(600)

  // Verify search results displayed
  const results = page.locator('[data-testid="market-card"]')
  await expect(results).toHaveCount(5, { timeout: 5000 })

  // Verify results contain search term
  const firstResult = results.first()
  await expect(firstResult).toContainText('election', { ignoreCase: true })

  // Filter by status
  await page.click('button:has-text("Active")')

  // Verify filtered results
  await expect(results).toHaveCount(3)
})

test('user can create a new market', async ({ page }) => {
  // Login first
  await page.goto('/creator-dashboard')

  // Fill market creation form
  await page.fill('input[name="name"]', 'Test Market')
  await page.fill('textarea[name="description"]', 'Test description')
  await page.fill('input[name="endDate"]', '2025-12-31')

  // Submit form
  await page.click('button[type="submit"]')

  // Verify success message
  await expect(page.locator('text=Market created successfully')).toBeVisible()

  // Verify redirect to market page
  await expect(page).toHaveURL(/\/markets\/test-market/)
})
```
## 测试文件组织
```
src/
├── components/
│   ├── Button/
│   │   ├── Button.tsx
│   │   ├── Button.test.tsx          # Unit tests
│   │   └── Button.stories.tsx       # Storybook
│   └── MarketCard/
│       ├── MarketCard.tsx
│       └── MarketCard.test.tsx
├── app/
│   └── api/
│       └── markets/
│           ├── route.ts
│           └── route.test.ts         # Integration tests
└── e2e/
    ├── markets.spec.ts               # E2E tests
    ├── trading.spec.ts
    └── auth.spec.ts
```
## 模拟外部服务

### Supabase 模拟
```typescript
jest.mock('@/lib/supabase', () => ({
  supabase: {
    from: jest.fn(() => ({
      select: jest.fn(() => ({
        eq: jest.fn(() => Promise.resolve({
          data: [{ id: 1, name: 'Test Market' }],
          error: null
        }))
      }))
    }))
  }
}))
```
### Redis 模拟
```typescript
jest.mock('@/lib/redis', () => ({
  searchMarketsByVector: jest.fn(() => Promise.resolve([
    { slug: 'test-market', similarity_score: 0.95 }
  ])),
  checkRedisHealth: jest.fn(() => Promise.resolve({ connected: true }))
}))
```
### OpenAI 模拟
```typescript
jest.mock('@/lib/openai', () => ({
  generateEmbedding: jest.fn(() => Promise.resolve(
    new Array(1536).fill(0.1) // Mock 1536-dim embedding
  ))
}))
```
## 测试覆盖率验证

### 运行覆盖率报告
```bash
npm run test:coverage
```
### 覆盖阈值
```json
{
  "jest": {
    "coverageThresholds": {
      "global": {
        "branches": 80,
        "functions": 80,
        "lines": 80,
        "statements": 80
      }
    }
  }
}
```
## 常见测试错误需避免

### 失败：错误：测试实现细节
```typescript
// Don't test internal state
expect(component.state.count).toBe(5)
```
### 通过：正确：测试用户可见行为
```typescript
// Test what users see
expect(screen.getByText('Count: 5')).toBeInTheDocument()
```
### 失败：错误：脆弱的选择器
```typescript
// Breaks easily
await page.click('.css-class-xyz')
```
### 通过：正确：语义选择器
```typescript
// Resilient to changes
await page.click('button:has-text("Submit")')
await page.click('[data-testid="submit-button"]')
```
### 失败：错误：没有测试隔离
```typescript
// Tests depend on each other
test('creates user', () => { /* ... */ })
test('updates same user', () => { /* depends on previous test */ })
```
### 通过：正确：独立测试
```typescript
// Each test sets up its own data
test('creates user', () => {
  const user = createTestUser()
  // Test logic
})

test('updates user', () => {
  const user = createTestUser()
  // Update logic
})
```
## 持续测试

### 开发期间的观察模式
```bash
npm test -- --watch
# Tests run automatically on file changes
```
### 提交前钩子
```bash
# Runs before every commit
npm test && npm run lint
```
### CI/CD 集成
```yaml
# GitHub Actions
- name: Run Tests
  run: npm test -- --coverage
- name: Upload Coverage
  uses: codecov/codecov-action@v3
```
## 最佳实践

1. **先写测试** - 始终采用测试驱动开发(TDD)
2. **每个测试一个断言** - 关注单一行为
3. **描述性测试名称** - 说明测试内容
4. **排列-执行-断言** - 清晰的测试结构
5. **模拟外部依赖** - 隔离单元测试
6. **测试边界情况** - 空值、未定义、空数组、大数据
7. **测试错误路径** - 不仅测试正常路径
8. **保持测试快速** - 单元测试每个 < 50ms
9. **测试后清理** - 避免副作用
10. **审查覆盖率报告** - 找出遗漏

## 成功指标

- 代码覆盖率达到 80%
- 所有测试通过（绿色）
- 没有跳过或禁用的测试
- 测试执行快速（单元测试 < 30 秒）
- 端到端(E2E)测试覆盖关键用户流程
- 测试在生产前发现漏洞

---

**记住**：测试不是可选项。它们是保障安全的网，使得重构自信、快速开发和生产可靠性成为可能。
