# 前端 TypeScript 规范

## 目录

- 项目结构与文件放置
- 命名与类型
- API 使用和异步边界
- 组件、状态和副作用
- 错误处理与空安全
- 样式和格式
- 应避免的代码异味
- 验证预期

## 项目结构与文件放置

- 页面级组件放在 `frontend/src/pages`。
- 可复用 UI 组件放在 `frontend/src/components`。
- HTTP 调用和服务端契约辅助函数放在 `frontend/src/api`。
- 共享领域类型放在 `frontend/src/types`；如果类型只在单个功能内使用，可放在该功能的本地 API 模块中。
- 纯辅助函数放在 `frontend/src/utils`。
- 命名为 `useX.ts` 的 Hook 放在 `frontend/src/hooks`。
- 如果 `src/api` 中已经有合适的类型化 API 辅助函数，就不要在页面或展示组件里直接发起网络请求。

正确：
```ts
export async function fetchKnowledgeBases(): Promise<KnowledgeBaseItem[]> {
  return request.get<KnowledgeBaseItem[]>('/api/knowledge-bases');
}
```
错误：
```ts
export default function KnowledgeBasePage() {
  const load = async () => {
    const response = await axios.get('/api/knowledge-bases');
    setItems(response.data.data);
  };
}
```
## 命名与类型

- 组件和页面文件使用 PascalCase。
- Hooks 使用 `useX`。
- 辅助函数、变量和函数使用 camelCase。
- 对于表示稳定契约的导出对象结构，使用 `interface`。
- 对于联合类型、别名、元组和派生辅助类型，使用 `type`。
- 避免使用 `any`。
- 在不安全边界优先使用 `unknown`，然后显式缩小类型。
- 为每个导出函数、异步返回值、props 对象和 API 载荷补全类型。
- 将服务端响应类型保持在 API 层附近，除非类型被广泛共享。
- 对于状态机（如 `PENDING | PROCESSING | COMPLETED | FAILED`），优先使用可辨别联合或字面量联合类型。

正确：
```ts
export interface UploadKnowledgeBaseResponse {
  knowledgeBase: KnowledgeBaseItem;
  duplicate: boolean;
}

export type VectorStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
```
错误：
```ts
export type UploadKnowledgeBaseResponse = any;
export let status = 'done';
```
正确：
```ts
export function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return '未知错误';
}
```
错误：
```ts
export function getErrorMessage(error: any) {
  return error.message;
}
```
## API 使用和异步边界

- 除非新的传输需求强制要求专用客户端，否则使用 `frontend/src/api/request.ts` 中的共享 `request` 包装器。
- 将响应解包和后端 `Result` 约定集中处理。
- 从 API 函数返回类型化的 `Promise<T>` 值。
- 在边界处解析或缩小未知数据，而不是在组件中散布类型转换。
- 优先使用按功能划分的 API 模块，例如 `resume.ts`、`interview.ts` 和 `knowledgebase.ts`。
- 尽可能将上传、下载和流处理的特殊情况封装在 API 辅助函数内部。

正确：
```ts
export async function uploadKnowledgeBase(file: File, name?: string): Promise<UploadKnowledgeBaseResponse> {
  const formData = new FormData();
  formData.append('file', file);
  if (name) {
    formData.append('name', name);
  }
  return request.upload<UploadKnowledgeBaseResponse>('/api/knowledge-bases/upload', formData);
}
```
错误：
```ts
export async function uploadKnowledgeBase(file: File) {
  const response = await fetch('/api/knowledge-bases/upload', {
    method: 'POST',
    body: file,
  });
  return response.json() as any;
}
```
## 组件、状态与副作用

- 保持组件专注于渲染和用户交互。
- 保持页面负责页面组合、路由状态和高级协调。
- 仅存储无法通过 props 或其他状态廉价推导的状态。
- 仅在外部同步（如获取数据、计时器或 DOM 订阅）时使用 `useEffect`。
- 不要使用 `useEffect` 来计算属于渲染的简单派生值。
- 当完成路径应始终清除状态时，在 `finally` 块中重置加载标志。
- 在复杂事件处理器变成长程序块之前，将其拆分为命名的辅助函数。
- 保持 props 接口简洁且表达明确意图。

正确：
```ts
const handleUpload = async (file: File, name?: string) => {
  setUploading(true);
  setError('');

  try {
    const data = await knowledgeBaseApi.uploadKnowledgeBase(file, name);
    onUploadComplete(data);
  } catch (error: unknown) {
    setError(getErrorMessage(error));
  } finally {
    setUploading(false);
  }
};
```
错误：
```ts
const handleUpload = async (file: File) => {
  setUploading(true);
  try {
    const result = await knowledgeBaseApi.uploadKnowledgeBase(file);
    onUploadComplete(result);
  } catch (error) {
    setError(error.message);
    setUploading(false);
  }
};
```
正确：
```ts
const filteredItems = items.filter((item) => item.name.includes(searchKeyword));
```
错误：
```ts
const [filteredItems, setFilteredItems] = useState<KnowledgeBaseItem[]>([]);

useEffect(() => {
  setFilteredItems(items.filter((item) => item.name.includes(searchKeyword)));
}, [items, searchKeyword]);
```
## 错误处理与空安全

- 将面向用户的错误显示为可操作的信息。
- 保持一致的错误转换路径；重用诸如 `getErrorMessage` 的辅助函数。
- 在渲染或解引用之前保护可选值。
- 优先使用 `??`、可选链和窄化检查，而不是非空断言。
- 不要忽略异步失败。
- 在完成之前删除临时的 `console.log` 和 `console.error` 语句，除非任务明确要求浏览器调试输出。

正确：
```ts
const fileUrl = detail.storage?.fileUrl ?? '';
```
错误：
```ts
const fileUrl = detail.storage!.fileUrl;
```
正确：
```ts
if (!sessionId) {
  setError('缺少会话 ID');
  return;
}
```
错误：
```ts
loadSession(sessionId as string);
```
## 样式与格式

- 使用 2 空格缩进，分号，以及单引号。
- 遵循 `frontend/eslint.config.js` 的要求。
- 在 JSX 中保持 Tailwind 工具类内联，除非某个重复模式明显需要提取。
- 避免无意义的包裹 div 和类名的频繁变化。
- 保持现有设计语言，除非任务明确要求重新设计。
- 不要重新格式化无关的文件。

## 避免的代码异味

- 避免 `any` 类型、宽泛的 `as` 类型转换，以及未类型化的 JSON 管道。
- 避免在页面之间重复 API 路径或请求构建逻辑。
- 避免体量巨大的页面组件，将获取、转换、渲染、对话框和提交逻辑混合在一起而不进行提取。
- 避免状态变量只是镜像 props 或其他状态，而没有新增意义。
- 避免布尔属性爆炸，比如在同一组件上使用 `isSmall`、`isCompact`、`isTight`、`isDense`。
- 避免将不相关行为收集在通用助手文件中，比如 `utils.ts`。
```ts
interface FileUploadCardProps {
  title: string;
  subtitle: string;
  onUpload: (file: File, name?: string) => Promise<void>;
}
```
错误：
```ts
interface FileUploadCardProps {
  title: string;
  subtitle: string;
  data?: any;
  onUpload?: any;
  mode?: string;
}
```
## 验证期望

- 前端代码有改动时，在 `frontend/` 目录下运行 `pnpm build`。
- 如果前端更改依赖于后端契约更改，请验证双方，或明确说明未运行的部分。
- 最终响应需明确说明构建状态及任何剩余的差距。
