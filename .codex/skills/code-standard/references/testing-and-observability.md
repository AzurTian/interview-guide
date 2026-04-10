# 测试与可观测性

## 目录

- 验证矩阵
- 可观测性规则
- 日志示例
- 测试质量示例
- 最终交付清单

## 验证矩阵

- 后端 Java 逻辑、服务、映射器和异常路径：
  如果有针对性的测试已经足够，就运行对应测试；否则从仓库根目录运行 `.\gradlew.bat test`。
- 后端基础设施或集成行为：
  优先运行相关的 `*IntegrationTest.java`；当本地无法稳定提供依赖时，保留 `@Disabled`。
- 前端 TypeScript 或 TSX：
  在 `frontend/` 内运行 `pnpm build`。
- 跨层契约变更：
  在可能的情况下，同时验证后端和前端。
- 影响行为的配置，例如 `application.yml`、`docker-compose.yml` 或前端构建配置：
  运行最接近的可用冒烟检查，并说明任何尚未验证的运行时假设。

选择最小但足以建立真实信心的验证集。如果全量测试过重或无法运行，就执行最合适的定向检查，并明确说明缺口。

## 可观测性规则

- 使重要的状态转变可见。
- 为后台任务或流式任务记录入队、开始、重试、成功和失败。
- 在故障变得可操作的节点记录外部依赖失败，并附带栈追踪。
- 保持日志结构化，使用占位符而非串接字符串。
- 包含稳定标识符，例如 `resumeId`、`sessionId`、`kbId`、`messageId` 或 `fileKey`。
- 避免在日志中输出密钥、令牌、提示词内容、文件内容、简历正文和知识库正文。
- 避免制造前端控制台噪声；更优先使用 UI 状态配合后端日志来形成可持续的可观测性。

正确：
```java
log.info("开始处理分析任务: resumeId={}, retryCount={}", resumeId, retryCount);
log.error("RAG 聊天流式错误: sessionId={}", sessionId, exception);
```
错误：
```java
log.info("开始处理任务 " + resumeId + " " + prompt);
log.error("失败了");
```
## 测试质量示例

优先编写验证行为、领域结果和副作用的测试。

正确：
```java
@Test
void reanalyzeThrowsWhenResumeMissing() {
    when(resumeRepository.findById(99L)).thenReturn(Optional.empty());

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> resumeUploadService.reanalyze(99L)
    );

    assertEquals(ErrorCode.RESUME_NOT_FOUND.getCode(), exception.getCode());
    verify(analyzeStreamProducer, never()).sendAnalyzeTask(anyLong(), anyString());
}
```
错误：
```java
@Test
void reanalyzeWorks() {
    resumeUploadService.reanalyze(99L);
    assertTrue(true);
}
```
正确：
```ts
const handleSubmit = async () => {
  setLoading(true);
  setError('');

  try {
    await interviewApi.submitAnswer(payload);
  } catch (error: unknown) {
    setError(getErrorMessage(error));
  } finally {
    setLoading(false);
  }
};
```
错误：
```ts
const handleSubmit = async () => {
  await interviewApi.submitAnswer(payload);
};
```
## 最终交付清单

在最终确定更改之前，确认答案可以说明：

- 哪个区域发生了变化：后端、前端、跨层或配置
- 使用了该技能的哪些参考文件
- 运行了哪些验证命令以及是否通过
- 哪些验证无法运行，以及原因
- 是否有任何剩余风险涉及运行时环境、外部依赖或未处理的遗留代码
