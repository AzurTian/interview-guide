# Java 后端规范

## 目录

- 项目结构与边界
- 命名与建模
- 构造函数注入与事务
- 流、集合与 Optional
- 异常处理与空安全
- 日志记录与可观测性
- 格式与代码异味
- 测试期望

## 项目结构与边界

- 后端代码放在 `app/src/main/java/interview/guide` 下。
- 只有在代码确实会被多个模块复用时，才把横切关注点放到 `common` 或 `infrastructure`。
- 功能逻辑放在 `modules/resume`、`modules/interview` 或 `modules/knowledgebase` 中。
- 控制器应专注于请求校验、参数转换和 `Result<T>` 响应。
- 业务规则、事务、重试和编排逻辑放在 Service 层中。
- Repository 只负责持久化和查询相关内容。
- 映射器、解析器和存储适配器放在基础设施层或命名清晰的辅助类中。

正确：
```java
@RestController
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeUploadService resumeUploadService;

    @PostMapping("/api/resumes")
    public Result<ResumeUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(resumeUploadService.upload(file));
    }
}
```
错误：
```java
@RestController
public class ResumeController {

    @PostMapping("/api/resumes")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        String text = tika.parseToString(file.getInputStream());
        String key = s3Client.putObject(...);
        aiClient.call(...);
        return Result.success(Map.of("fileKey", key, "text", text));
    }
}
```
## 命名与建模

- 使用小写包名。
- 类和 `record` 使用 PascalCase。
- 有意使用后缀：`Controller`、`Service`、`Repository`、`Entity`、`DTO`、`Request`、`Response`、`Mapper`。
- 方法名使用动词开头，例如 `uploadResume`、`deleteKnowledgeBase`、`findUnfinishedSession`。
- 优先使用领域名词而非通用占位符。
- 对于不可变的请求、响应和投影模型，优先使用 Java 21 的 `record`。
- JPA 实体和可变的 Spring 管理状态使用 `class`。
- 对于新的跨层契约，优先使用类型化的 DTO 或 record。
- 除非兼容性要求，否则避免引入新的 `Map<String, Object>` 响应。
- Lombok 使用应谨慎。优先使用 `@RequiredArgsConstructor`、`@Getter` 和 `@Slf4j`，避免在实体上广泛使用 `@Data`。
- 不要使用原始类型。保持泛型签名完整。
- 尽量减少使用 `@SuppressWarnings`；仅在有局部具体说明时添加。

正确：
```java
public record ResumeUploadResponse(
    Long id,
    String filename,
    AsyncTaskStatus analyzeStatus,
    StorageInfo storage,
    boolean duplicate
) {}
```
错误：
```java
public Map upload(MultipartFile file) {
    return Map.of("id", 1L, "filename", file.getOriginalFilename());
}
```
正确：
```java
public Result<List<ResumeListItemDTO>> listResumes() {
    List<ResumeListItemDTO> items = resumeHistoryService.list();
    return Result.success(items);
}
```
错误：
```java
public Result listResumes() {
    List items = resumeHistoryService.list();
    return Result.success(items);
}
```
## 构造函数注入与事务

- 优先通过 `final` 字段进行构造函数注入。
- 避免字段注入。
- 将 `@Transactional` 保持在定义业务事务边界的服务方法上。
- 不要在控制器和仓库层之间扩展同一事务。
- 将重试和异步入队逻辑隔离在服务或专用异步组件中。

正确：
```java
@Service
@RequiredArgsConstructor
public class KnowledgeBaseDeleteService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;

    @Transactional
    public void delete(Long id) {
        knowledgeBaseRepository.deleteById(id);
    }
}
```
错误：
```java
@Service
public class KnowledgeBaseDeleteService {

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;
}
```
## 流、集合与 Optional

- 当管道是纯净且短小的时使用 Stream。
- 当代码需要记录日志、修改多个值、重试或大量分支时使用循环。
- 不要在业务逻辑中使用 `peek`。
- 保持流中的 lambda 简洁。当 lambda 不再直观时抽取命名的辅助方法。
- 当符合需求时，优先使用 `toList()` 获取不可变的结果集合。
- 返回 `List.of()` 或其他空集合，而不是 `null`。
- 仅对真正表示缺失的返回值使用 `Optional`。
- 不要将仓库中的 `Optional` 值转换为 `null`。
- 不要接受 `Optional` 作为参数。

正确：
```java
List<Long> uniqueIds = knowledgeBaseIds.stream()
    .filter(Objects::nonNull)
    .distinct()
    .toList();
```
错误：
```java
List<Long> uniqueIds = new ArrayList<>();
knowledgeBaseIds.stream()
    .peek(uniqueIds::add)
    .forEach(id -> log.info("id={}", id));
```
正确：
```java
public Optional<ResumeEntity> findById(Long id) {
    return resumeRepository.findById(id);
}
```
错误：
```java
public ResumeEntity findById(Long id) {
    return resumeRepository.findById(id).orElse(null);
}
```
正确：
```java
for (Document document : documents) {
    if (!isValid(document)) {
        log.warn("跳过无效文档: id={}", document.getId());
        continue;
    }
    saveDocument(document);
}
```
错误：
```java
documents.stream()
    .peek(document -> log.warn("检查文档: {}", document))
    .filter(this::isValid)
    .forEach(this::saveDocument);
```
## 异常处理与空安全

- 在边界处验证请求输入和外部数据。
- 对可恢复的领域失败，使用正确的 `ErrorCode` 抛出 `BusinessException`。
- 仅在翻译边界捕获广泛异常，在那里记录并重新抛出或转换一次。
- 永远不要静默地吞掉异常。
- 不要使用 `null` 来表示空列表、空映射或缺失的可选值。
- 在取消引用外部库的返回值之前，防护 `null`。
- `Objects.requireNonNull` 仅用于内部不变量或程序员错误，而非用户可见的验证。
- 保持异常信息可操作且与领域相关。
```java
if (resumeText == null || resumeText.isBlank()) {
    throw new BusinessException(ErrorCode.RESUME_PARSE_FAILED, "无法从文件中提取文本内容");
}
```
错误：
```java
try {
    return parseService.parseResume(file);
} catch (Exception ignored) {
    return null;
}
```
正确：
```java
try {
    vectorStore.add(documents);
} catch (RuntimeException exception) {
    log.error("向量化知识库失败: kbId={}", knowledgeBaseId, exception);
    throw new RuntimeException("向量化知识库失败: " + exception.getMessage(), exception);
}
```
错误：
```java
try {
    vectorStore.add(documents);
} catch (Exception exception) {
    log.warn("失败了");
}
```
## 日志记录与可观测性

- 使用带有稳定标识符的参数化日志，例如 `resumeId`、`sessionId`、`kbId` 或 `messageId`。
- 记录异步工作的生命周期状态：入队、开始、重试、成功、失败。
- 对外部存储、AI 或 Redis 的故障记录错误级别的堆栈信息。
- 对于预期但值得注意的失败（如验证失败、重复数据或降级回退），使用 `warn`。
- 避免记录简历内容、知识库内容、机密信息、原始提示、完整请求体或访问令牌。
- 避免在日志中使用字符串拼接。
- 除非信号的价值超过日志量，否则不要在紧密循环中发出杂乱日志。
```java
log.info("向量化任务已重新入队: kbId={}, retryCount={}", kbId, retryCount);
log.error("上传文件到 RustFS 失败: fileKey={}", fileKey, exception);
```
错误：
```java
log.info("retry " + kbId + " " + content);
log.error("调用失败: " + apiKey + " " + prompt);
```
## 格式与代码异味

- 使用 4 空格缩进。
- 保持导入整洁，并与周围文件保持一致。
- 不要重新格式化无关文件。
- 每个类优先承担单一顶层责任。
- 当一个方法难以浏览或混合了无关步骤时，提取辅助函数。
- 避免布尔标志参数集，例如 `handle(id, true, false, true)`。
- 避免把无关内容堆进通用工具类。
- 避免魔法数字，当使用命名常量或配置项能更清晰表达意图时。
- 保持注释用于非显而易见的意图，而不是叙述显而易见的代码。

正确：
```java
private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
```
错误：
```java
if (file.getSize() > 10485760) {
    ...
}
```
正确：
```java
public void enqueueAnalyzeTask(Long resumeId) {
    ...
}

public void retryAnalyzeTask(Long resumeId) {
    ...
}
```
错误：
```java
public void handleAnalyzeTask(Long resumeId, boolean retry, boolean updateStatus, boolean ignoreFailure) {
    ...
}
```
## 测试期望

- 为服务逻辑、映射器逻辑、验证逻辑或转换代码添加或更新单元测试。
- 为 Redis、数据库、存储或其他基础设施集成添加或更新集成测试。
- 保持单元测试命名为 `*Test.java`。
- 保持依赖基础设施的测试命名为 `*IntegrationTest.java`；当依赖不可靠时使用 `@Disabled`。
- 断言可观察的行为、返回的数据、抛出的异常和产生的副作用。
- 优先使用有针对性的模拟和捕获器，而不是过度模拟整个流程。
- 验证异步和流相关的状态转换、重试和失败路径。

正确：
```java
@Test
void testVectorizeFailureThrowsException() {
    doThrow(new RuntimeException("VectorStore 连接失败"))
        .when(vectorStore).add(anyList());

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> vectorService.vectorizeAndStore(knowledgeBaseId, content)
    );

    assertTrue(exception.getMessage().contains("向量化知识库失败"));
}
```
错误：
```java
@Test
void testVectorize() {
    vectorService.vectorizeAndStore(knowledgeBaseId, content);
    assertTrue(true);
}
```
