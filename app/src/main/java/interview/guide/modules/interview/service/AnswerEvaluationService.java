package interview.guide.modules.interview.service;

import interview.guide.common.ai.AiTextClient;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewReportDTO;
import interview.guide.modules.interview.model.InterviewReportDTO.CategoryScore;
import interview.guide.modules.interview.model.InterviewReportDTO.QuestionEvaluation;
import interview.guide.modules.interview.model.InterviewReportDTO.ReferenceAnswer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 答案评估服务
 * 支持单题评估与最终面试总结
 */
@Service
public class AnswerEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerEvaluationService.class);

    private final AiTextClient aiTextClient;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<EvaluationReportDTO> outputConverter;
    private final PromptTemplate summarySystemPromptTemplate;
    private final PromptTemplate summaryUserPromptTemplate;
    private final BeanOutputConverter<FinalSummaryDTO> summaryOutputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;

    private record EvaluationReportDTO(
        int overallScore,
        String overallFeedback,
        List<String> strengths,
        List<String> improvements,
        List<QuestionEvaluationDTO> questionEvaluations
    ) {
    }

    private record QuestionEvaluationDTO(
        int questionIndex,
        int score,
        String feedback,
        String referenceAnswer,
        List<String> keyPoints
    ) {
    }

    private record FinalSummaryDTO(
        String overallFeedback,
        List<String> strengths,
        List<String> improvements
    ) {
    }

    public AnswerEvaluationService(
        AiTextClient aiTextClient,
        StructuredOutputInvoker structuredOutputInvoker,
        @Value("classpath:prompts/interview-evaluation-system.st") Resource systemPromptResource,
        @Value("classpath:prompts/interview-evaluation-user.st") Resource userPromptResource,
        @Value("classpath:prompts/interview-evaluation-summary-system.st") Resource summarySystemPromptResource,
        @Value("classpath:prompts/interview-evaluation-summary-user.st") Resource summaryUserPromptResource
    ) throws IOException {
        this.aiTextClient = aiTextClient;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(EvaluationReportDTO.class);
        this.summarySystemPromptTemplate = new PromptTemplate(summarySystemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.summaryUserPromptTemplate = new PromptTemplate(summaryUserPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.summaryOutputConverter = new BeanOutputConverter<>(FinalSummaryDTO.class);
    }

    /**
     * 评估单题，返回带评分与参考答案的问题快照。
     */
    public InterviewQuestionDTO evaluateQuestion(String sessionId, String resumeText, InterviewQuestionDTO question) {
        try {
            EvaluationReportDTO report = evaluateQuestionBatch(sessionId, summarizeResume(resumeText), List.of(question));
            QuestionEvaluationDTO evaluation = report != null && report.questionEvaluations() != null
                ? report.questionEvaluations().stream().filter(item -> item != null).findFirst().orElse(null)
                : null;

            int score = question.userAnswer() == null || question.userAnswer().isBlank()
                ? 0
                : evaluation != null ? evaluation.score() : 0;
            String feedback = evaluation != null && evaluation.feedback() != null
                ? evaluation.feedback()
                : "该题未成功生成评估反馈。";
            String referenceAnswer = evaluation != null && evaluation.referenceAnswer() != null
                ? evaluation.referenceAnswer()
                : "";
            List<String> keyPoints = evaluation != null && evaluation.keyPoints() != null
                ? evaluation.keyPoints()
                : List.of();

            return question.withEvaluation(score, feedback, referenceAnswer, keyPoints);
        } catch (Exception exception) {
            log.error("单题评估失败: sessionId={}, questionIndex={}", sessionId, question.questionIndex(), exception);
            throw new BusinessException(ErrorCode.INTERVIEW_EVALUATION_FAILED, "单题评估失败：" + exception.getMessage());
        }
    }

    /**
     * 兼容保留：对缺失评估的题目补评后生成整场报告。
     */
    public InterviewReportDTO evaluateInterview(String sessionId, String resumeText, List<InterviewQuestionDTO> questions) {
        List<InterviewQuestionDTO> evaluatedQuestions = questions.stream()
            .map(question -> hasEvaluation(question) ? question : evaluateQuestion(sessionId, resumeText, question))
            .toList();
        return buildReport(sessionId, resumeText, evaluatedQuestions);
    }

    /**
     * 基于已完成的单题评估构建整场报告。
     */
    public InterviewReportDTO buildReport(String sessionId, String resumeText, List<InterviewQuestionDTO> questions) {
        String resumeSummary = summarizeResume(resumeText);
        FinalSummaryDTO finalSummary = summarizeInterview(sessionId, resumeSummary, questions);
        return convertToReport(
            sessionId,
            questions,
            finalSummary.overallFeedback(),
            finalSummary.strengths(),
            finalSummary.improvements()
        );
    }

    public boolean hasEvaluation(InterviewQuestionDTO question) {
        return question.score() != null
            && question.feedback() != null
            && question.referenceAnswer() != null
            && question.keyPoints() != null;
    }

    private EvaluationReportDTO evaluateQuestionBatch(
        String sessionId,
        String resumeSummary,
        List<InterviewQuestionDTO> questions
    ) {
        String qaRecords = buildQARecords(questions);
        String systemPrompt = systemPromptTemplate.render();

        Map<String, Object> variables = new HashMap<>();
        variables.put("resumeText", resumeSummary);
        variables.put("qaRecords", qaRecords);
        String userPrompt = userPromptTemplate.render(variables);

        String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();
        return structuredOutputInvoker.invoke(
            aiTextClient,
            systemPromptWithFormat,
            userPrompt,
            outputConverter,
            ErrorCode.INTERVIEW_EVALUATION_FAILED,
            "面试评估失败：",
            "单题评估",
            log
        );
    }

    private FinalSummaryDTO summarizeInterview(String sessionId, String resumeSummary, List<InterviewQuestionDTO> questions) {
        String fallbackOverallFeedback = buildFallbackOverallFeedback(questions);
        List<String> fallbackStrengths = buildFallbackStrengths(questions);
        List<String> fallbackImprovements = buildFallbackImprovements(questions);

        try {
            String summarySystemPrompt = summarySystemPromptTemplate.render();
            Map<String, Object> variables = new HashMap<>();
            variables.put("resumeText", resumeSummary);
            variables.put("categorySummary", buildCategorySummary(questions));
            variables.put("questionHighlights", buildQuestionHighlights(questions));
            variables.put("fallbackOverallFeedback", fallbackOverallFeedback);
            variables.put("fallbackStrengths", String.join("\n", fallbackStrengths));
            variables.put("fallbackImprovements", String.join("\n", fallbackImprovements));
            String summaryUserPrompt = summaryUserPromptTemplate.render(variables);

            String systemPromptWithFormat = summarySystemPrompt + "\n\n" + summaryOutputConverter.getFormat();
            FinalSummaryDTO dto = structuredOutputInvoker.invoke(
                aiTextClient,
                systemPromptWithFormat,
                summaryUserPrompt,
                summaryOutputConverter,
                ErrorCode.INTERVIEW_EVALUATION_FAILED,
                "面试总结失败：",
                "总结评估",
                log
            );

            String overallFeedback = dto != null && dto.overallFeedback() != null && !dto.overallFeedback().isBlank()
                ? dto.overallFeedback()
                : fallbackOverallFeedback;
            List<String> strengths = sanitizeSummaryItems(dto != null ? dto.strengths() : null, fallbackStrengths);
            List<String> improvements = sanitizeSummaryItems(dto != null ? dto.improvements() : null, fallbackImprovements);
            return new FinalSummaryDTO(overallFeedback, strengths, improvements);
        } catch (Exception exception) {
            log.warn("总结评估失败，回退到本地汇总: sessionId={}, error={}", sessionId, exception.getMessage());
            return new FinalSummaryDTO(fallbackOverallFeedback, fallbackStrengths, fallbackImprovements);
        }
    }

    private String summarizeResume(String resumeText) {
        if (resumeText == null) {
            return "";
        }
        return resumeText.length() > 500 ? resumeText.substring(0, 500) + "..." : resumeText;
    }

    private String buildQARecords(List<InterviewQuestionDTO> questions) {
        StringBuilder builder = new StringBuilder();
        for (InterviewQuestionDTO question : questions) {
            builder.append("问题")
                .append(question.questionIndex() + 1)
                .append(" [")
                .append(question.category())
                .append("]: ")
                .append(question.question())
                .append("\n");
            builder.append("回答: ")
                .append(question.userAnswer() != null ? question.userAnswer() : "(未回答)")
                .append("\n\n");
        }
        return builder.toString();
    }

    private String buildFallbackOverallFeedback(List<InterviewQuestionDTO> questions) {
        long answeredCount = questions.stream()
            .filter(question -> question.userAnswer() != null && !question.userAnswer().isBlank())
            .count();
        double averageScore = questions.stream()
            .filter(question -> question.score() != null)
            .mapToInt(question -> question.score() != null ? question.score() : 0)
            .average()
            .orElse(0);

        if (answeredCount == 0) {
            return "本次面试未提供有效回答，暂无法评估真实技术能力。";
        }
        if (averageScore >= 80) {
            return "本次面试整体表现较好，回答覆盖面与技术深度都较为扎实。";
        }
        if (averageScore >= 60) {
            return "本次面试具备一定技术基础，但部分题目仍停留在表层回答，建议继续补强底层原理和实战细节。";
        }
        return "本次面试暴露出较多基础薄弱点，建议系统复盘核心知识并结合真实场景练习表达。";
    }

    private List<String> buildFallbackStrengths(List<InterviewQuestionDTO> questions) {
        return questions.stream()
            .filter(question -> question.score() != null && question.score() >= 75)
            .map(InterviewQuestionDTO::category)
            .distinct()
            .limit(5)
            .map(category -> category + "方向回答相对完整，具备一定表达与理解基础。")
            .toList();
    }

    private List<String> buildFallbackImprovements(List<InterviewQuestionDTO> questions) {
        List<String> improvements = questions.stream()
            .filter(question -> question.score() == null || question.score() < 60)
            .map(InterviewQuestionDTO::category)
            .distinct()
            .limit(5)
            .map(category -> "建议优先补强" + category + "方向的底层原理、边界条件和故障排查思路。")
            .collect(Collectors.toCollection(ArrayList::new));
        if (improvements.isEmpty()) {
            improvements.add("建议继续通过真实项目复盘沉淀架构取舍和性能优化案例。");
        }
        return improvements;
    }

    private List<String> sanitizeSummaryItems(List<String> primary, List<String> fallback) {
        List<String> source = (primary != null && !primary.isEmpty()) ? primary : fallback;
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .distinct()
            .limit(8)
            .toList();
    }

    private String buildCategorySummary(List<InterviewQuestionDTO> questions) {
        Map<String, List<Integer>> categoryScores = new HashMap<>();
        for (InterviewQuestionDTO question : questions) {
            categoryScores.computeIfAbsent(question.category(), ignored -> new ArrayList<>())
                .add(question.score() != null ? question.score() : 0);
        }

        return categoryScores.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                int count = entry.getValue().size();
                int average = (int) entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0);
                return "- " + entry.getKey() + ": 平均分 " + average + ", 题数 " + count;
            })
            .collect(Collectors.joining("\n"));
    }

    private String buildQuestionHighlights(List<InterviewQuestionDTO> questions) {
        return questions.stream()
            .map(question -> {
                String shortQuestion = question.question().length() > 50
                    ? question.question().substring(0, 50) + "..."
                    : question.question();
                String feedback = question.feedback() != null ? question.feedback() : "";
                String shortFeedback = feedback.length() > 80 ? feedback.substring(0, 80) + "..." : feedback;
                int score = question.score() != null ? question.score() : 0;
                return "- Q" + (question.questionIndex() + 1)
                    + " | " + shortQuestion
                    + " | 分数:" + score
                    + " | 反馈:" + shortFeedback;
            })
            .limit(20)
            .collect(Collectors.joining("\n"));
    }

    private InterviewReportDTO convertToReport(
        String sessionId,
        List<InterviewQuestionDTO> questions,
        String overallFeedback,
        List<String> strengths,
        List<String> improvements
    ) {
        List<QuestionEvaluation> questionDetails = new ArrayList<>();
        List<ReferenceAnswer> referenceAnswers = new ArrayList<>();
        Map<String, List<Integer>> categoryScoresMap = new HashMap<>();

        long answeredCount = questions.stream()
            .filter(question -> question.userAnswer() != null && !question.userAnswer().isBlank())
            .count();

        for (InterviewQuestionDTO question : questions) {
            int score = question.userAnswer() == null || question.userAnswer().isBlank()
                ? 0
                : question.score() != null ? question.score() : 0;
            String feedback = question.feedback() != null
                ? question.feedback()
                : "该题未成功生成评估反馈。";
            String referenceAnswer = question.referenceAnswer() != null ? question.referenceAnswer() : "";
            List<String> keyPoints = question.keyPoints() != null ? question.keyPoints() : List.of();

            questionDetails.add(new QuestionEvaluation(
                question.questionIndex(),
                question.question(),
                question.category(),
                question.userAnswer(),
                score,
                feedback
            ));
            referenceAnswers.add(new ReferenceAnswer(
                question.questionIndex(),
                question.question(),
                referenceAnswer,
                keyPoints
            ));
            categoryScoresMap.computeIfAbsent(question.category(), ignored -> new ArrayList<>()).add(score);
        }

        List<CategoryScore> categoryScores = categoryScoresMap.entrySet().stream()
            .map(entry -> new CategoryScore(
                entry.getKey(),
                (int) entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0),
                entry.getValue().size()
            ))
            .toList();

        int overallScore = answeredCount == 0
            ? 0
            : (int) questionDetails.stream().mapToInt(QuestionEvaluation::score).average().orElse(0);

        return new InterviewReportDTO(
            sessionId,
            questions.size(),
            overallScore,
            categoryScores,
            questionDetails,
            overallFeedback,
            strengths != null ? strengths : List.of(),
            improvements != null ? improvements : List.of(),
            referenceAnswers
        );
    }
}
