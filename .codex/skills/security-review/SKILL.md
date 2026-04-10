---
name: security-review
description: 在添加身份验证、处理用户输入、处理机密信息、创建 API 端点或实现支付/敏感功能时使用此技能。提供全面的安全检查清单和模式。
---
# 安全审查技能

此技能确保所有代码遵循安全最佳实践，并识别潜在的漏洞。

## 何时激活

- 实现身份验证或授权
- 处理用户输入或文件上传
- 创建新的 API 端点
- 使用密钥或凭证
- 实现支付功能
- 存储或传输敏感数据
- 集成第三方 API

## 安全检查清单

### 1. 密钥管理

#### 失败示例：绝不应该这样做
```typescript
const apiKey = "sk-proj-xxxxx"  // Hardcoded secret
const dbPassword = "password123" // In source code
```
#### 通过：始终执行此操作
```typescript
const apiKey = process.env.OPENAI_API_KEY
const dbUrl = process.env.DATABASE_URL

// Verify secrets exist
if (!apiKey) {
  throw new Error('OPENAI_API_KEY not configured')
}
```
#### 验证步骤
- [ ] 没有硬编码的 API 密钥、令牌或密码
- [ ] 所有秘密都在环境变量中
- [ ] `.env.local` 已加入 .gitignore
- [ ] git 历史中没有秘密
- [ ] 生产环境的秘密存放在托管平台（Vercel、Railway）

### 2. 输入验证

#### 始终验证用户输入
```typescript
import { z } from 'zod'

// Define validation schema
const CreateUserSchema = z.object({
  email: z.string().email(),
  name: z.string().min(1).max(100),
  age: z.number().int().min(0).max(150)
})

// Validate before processing
export async function createUser(input: unknown) {
  try {
    const validated = CreateUserSchema.parse(input)
    return await db.users.create(validated)
  } catch (error) {
    if (error instanceof z.ZodError) {
      return { success: false, errors: error.errors }
    }
    throw error
  }
}
```
#### 文件上传验证
```typescript
function validateFileUpload(file: File) {
  // Size check (5MB max)
  const maxSize = 5 * 1024 * 1024
  if (file.size > maxSize) {
    throw new Error('File too large (max 5MB)')
  }

  // Type check
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif']
  if (!allowedTypes.includes(file.type)) {
    throw new Error('Invalid file type')
  }

  // Extension check
  const allowedExtensions = ['.jpg', '.jpeg', '.png', '.gif']
  const extension = file.name.toLowerCase().match(/\.[^.]+$/)?.[0]
  if (!extension || !allowedExtensions.includes(extension)) {
    throw new Error('Invalid file extension')
  }

  return true
}
```
#### 验证步骤
- [ ] 所有用户输入都通过模式验证
- [ ] 文件上传受限（大小、类型、扩展名）
- [ ] 查询中不直接使用用户输入
- [ ] 白名单验证（而非黑名单）
- [ ] 错误信息不泄露敏感信息

### 3. SQL 注入防护

#### 失败：绝不要拼接 SQL
```typescript
// DANGEROUS - SQL Injection vulnerability
const query = `SELECT * FROM users WHERE email = '${userEmail}'`
await db.query(query)
```
#### 通过：始终使用参数化查询
```typescript
// Safe - parameterized query
const { data } = await supabase
  .from('users')
  .select('*')
  .eq('email', userEmail)

// Or with raw SQL
await db.query(
  'SELECT * FROM users WHERE email = $1',
  [userEmail]
)
```
#### 验证步骤
- [ ] 所有数据库查询都使用参数化查询
- [ ] SQL 中没有字符串拼接
- [ ] 正确使用 ORM/查询构建器
- [ ] Supabase 查询经过适当清理

### 4. 认证与授权

#### JWT 令牌处理
```typescript
// FAIL: WRONG: localStorage (vulnerable to XSS)
localStorage.setItem('token', token)

// PASS: CORRECT: httpOnly cookies
res.setHeader('Set-Cookie',
  `token=${token}; HttpOnly; Secure; SameSite=Strict; Max-Age=3600`)
```
#### 授权检查
```typescript
export async function deleteUser(userId: string, requesterId: string) {
  // ALWAYS verify authorization first
  const requester = await db.users.findUnique({
    where: { id: requesterId }
  })

  if (requester.role !== 'admin') {
    return NextResponse.json(
      { error: 'Unauthorized' },
      { status: 403 }
    )
  }

  // Proceed with deletion
  await db.users.delete({ where: { id: userId } })
}
```
#### 行级安全（Supabase）
```sql
-- Enable RLS on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

-- Users can only view their own data
CREATE POLICY "Users view own data"
  ON users FOR SELECT
  USING (auth.uid() = id);

-- Users can only update their own data
CREATE POLICY "Users update own data"
  ON users FOR UPDATE
  USING (auth.uid() = id);
```
#### 验证步骤
- [ ] 令牌存储在 httpOnly Cookie 中（而不是 localStorage）
- [ ] 在敏感操作前进行授权检查
- [ ] 在 Supabase 中启用行级安全
- [ ] 实施基于角色的访问控制
- [ ] 会话管理安全

### 5. XSS 防护

#### 清理 HTML
```typescript
import DOMPurify from 'isomorphic-dompurify'

// ALWAYS sanitize user-provided HTML
function renderUserContent(html: string) {
  const clean = DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['b', 'i', 'em', 'strong', 'p'],
    ALLOWED_ATTR: []
  })
  return <div dangerouslySetInnerHTML={{ __html: clean }} />
}
```
#### 内容安全策略
```typescript
// next.config.js
const securityHeaders = [
  {
    key: 'Content-Security-Policy',
    value: `
      default-src 'self';
      script-src 'self' 'unsafe-eval' 'unsafe-inline';
      style-src 'self' 'unsafe-inline';
      img-src 'self' data: https:;
      font-src 'self';
      connect-src 'self' https://api.example.com;
    `.replace(/\s{2,}/g, ' ').trim()
  }
]
```
#### 验证步骤
- [ ] 用户提供的 HTML 已清理
- [ ] 配置了 CSP 头
- [ ] 未呈现未经验证的动态内容
- [ ] 使用了 React 内置的 XSS 保护

### 6. CSRF 保护

#### CSRF 令牌
```typescript
import { csrf } from '@/lib/csrf'

export async function POST(request: Request) {
  const token = request.headers.get('X-CSRF-Token')

  if (!csrf.verify(token)) {
    return NextResponse.json(
      { error: 'Invalid CSRF token' },
      { status: 403 }
    )
  }

  // Process request
}
```
#### SameSite Cookie
```typescript
res.setHeader('Set-Cookie',
  `session=${sessionId}; HttpOnly; Secure; SameSite=Strict`)
```
#### 验证步骤
- [ ] 对状态更改操作使用 CSRF 令牌
- [ ] 所有 Cookie 设置 SameSite=Strict
- [ ] 实施双重提交 Cookie 模式

### 7. 速率限制

#### API 速率限制
```typescript
import rateLimit from 'express-rate-limit'

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // 100 requests per window
  message: 'Too many requests'
})

// Apply to routes
app.use('/api/', limiter)
```
#### 昂贵的操作
```typescript
// Aggressive rate limiting for searches
const searchLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 10, // 10 requests per minute
  message: 'Too many search requests'
})

app.use('/api/search', searchLimiter)
```
#### 验证步骤
- [ ] 对所有 API 端点进行速率限制
- [ ] 对高消耗操作实施更严格的限制
- [ ] 基于 IP 的速率限制
- [ ] 基于用户的速率限制（已认证）

### 8. 敏感数据泄露

#### 日志记录
```typescript
// FAIL: WRONG: Logging sensitive data
console.log('User login:', { email, password })
console.log('Payment:', { cardNumber, cvv })

// PASS: CORRECT: Redact sensitive data
console.log('User login:', { email, userId })
console.log('Payment:', { last4: card.last4, userId })
```
#### 错误消息
```typescript
// FAIL: WRONG: Exposing internal details
catch (error) {
  return NextResponse.json(
    { error: error.message, stack: error.stack },
    { status: 500 }
  )
}

// PASS: CORRECT: Generic error messages
catch (error) {
  console.error('Internal error:', error)
  return NextResponse.json(
    { error: 'An error occurred. Please try again.' },
    { status: 500 }
  )
}
```
#### 验证步骤
- [ ] 日志中没有密码、令牌或密钥
- [ ] 对用户显示的错误信息为通用信息
- [ ] 详细错误仅在服务器日志中记录
- [ ] 不向用户暴露堆栈跟踪

### 9. 区块链安全（Solana）

#### 钱包验证
```typescript
import { verify } from '@solana/web3.js'

async function verifyWalletOwnership(
  publicKey: string,
  signature: string,
  message: string
) {
  try {
    const isValid = verify(
      Buffer.from(message),
      Buffer.from(signature, 'base64'),
      Buffer.from(publicKey, 'base64')
    )
    return isValid
  } catch (error) {
    return false
  }
}
```
#### 交易验证
```typescript
async function verifyTransaction(transaction: Transaction) {
  // Verify recipient
  if (transaction.to !== expectedRecipient) {
    throw new Error('Invalid recipient')
  }

  // Verify amount
  if (transaction.amount > maxAmount) {
    throw new Error('Amount exceeds limit')
  }

  // Verify user has sufficient balance
  const balance = await getBalance(transaction.from)
  if (balance < transaction.amount) {
    throw new Error('Insufficient balance')
  }

  return true
}
```
#### 验证步骤
- [ ] 钱包签名已验证
- [ ] 交易详情已确认
- [ ] 交易前余额检查
- [ ] 不进行盲目交易签名

### 10. 依赖安全

#### 定期更新
```bash
# Check for vulnerabilities
npm audit

# Fix automatically fixable issues
npm audit fix

# Update dependencies
npm update

# Check for outdated packages
npm outdated
```
#### 锁定文件
```bash
# ALWAYS commit lock files
git add package-lock.json

# Use in CI/CD for reproducible builds
npm ci  # Instead of npm install
```
#### 验证步骤
- [ ] 依赖项已更新
- [ ] 无已知漏洞（npm 审计清理）
- [ ] 已提交锁文件
- [ ] 在 GitHub 上启用 Dependabot
- [ ] 定期进行安全更新

## 安全测试

### 自动化安全测试
```typescript
// Test authentication
test('requires authentication', async () => {
  const response = await fetch('/api/protected')
  expect(response.status).toBe(401)
})

// Test authorization
test('requires admin role', async () => {
  const response = await fetch('/api/admin', {
    headers: { Authorization: `Bearer ${userToken}` }
  })
  expect(response.status).toBe(403)
})

// Test input validation
test('rejects invalid input', async () => {
  const response = await fetch('/api/users', {
    method: 'POST',
    body: JSON.stringify({ email: 'not-an-email' })
  })
  expect(response.status).toBe(400)
})

// Test rate limiting
test('enforces rate limits', async () => {
  const requests = Array(101).fill(null).map(() =>
    fetch('/api/endpoint')
  )

  const responses = await Promise.all(requests)
  const tooManyRequests = responses.filter(r => r.status === 429)

  expect(tooManyRequests.length).toBeGreaterThan(0)
})
```
## 部署前安全检查清单

在任何生产部署之前：

- [ ] **密钥**：不使用硬编码密钥，全部存放在环境变量中
- [ ] **输入验证**：所有用户输入均需验证
- [ ] **SQL 注入**：所有查询均使用参数化
- [ ] **XSS**：用户内容已清理
- [ ] **CSRF**：启用防护
- [ ] **认证**：正确处理令牌
- [ ] **授权**：角色检查到位
- [ ] **速率限制**：对所有端点启用
- [ ] **HTTPS**：在生产环境中强制使用
- [ ] **安全头**：配置 CSP、X-Frame-Options
- [ ] **错误处理**：错误中不含敏感信息
- [ ] **日志**：不记录敏感数据
- [ ] **依赖项**：保持最新，无漏洞
- [ ] **行级安全**：在 Supabase 中启用
- [ ] **CORS**：正确配置
- [ ] **文件上传**：已验证（大小、类型）
- [ ] **钱包签名**：经过验证（如果使用区块链）

## 资源

- [OWASP 前十](https://owasp.org/www-project-top-ten/)
- [Next.js 安全](https://nextjs.org/docs/security)
- [Supabase 安全](https://supabase.com/docs/guides/auth)
- [Web 安全学院](https://portswigger.net/web-security)

---

**提示**：安全不可选。一处漏洞可能危及整个平台。如有疑问，应倾向谨慎处理。
