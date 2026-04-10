package interview.guide.modules.interview.service;

import interview.guide.common.ai.AiTextClient;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewQuestionDTO.QuestionType;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面试问题生成服务
 * 基于简历内容生成针对性的面试问题
 */
@Service
public class InterviewQuestionService {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);

    private static final int MAX_FOLLOW_UP_COUNT = 2;
    private static final int GENERATE_RETRY_TIMES = 2;

    // 问题类型权重分配（按优先级）
    private static final double PROJECT_RATIO = 0.20;
    private static final double MYSQL_RATIO = 0.20;
    private static final double REDIS_RATIO = 0.20;
    private static final double JAVA_BASIC_RATIO = 0.10;
    private static final double JAVA_COLLECTION_RATIO = 0.10;
    private static final double JAVA_CONCURRENT_RATIO = 0.10;
    private static final double SPRING_RATIO = 0.10;

    private final AiTextClient aiTextClient;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<QuestionListDTO> outputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final int followUpCount;

    private record QuestionListDTO(List<QuestionDTO> questions) {
    }

    private record QuestionDTO(
        String question,
        String type,
        String category,
        List<String> followUps
    ) {
    }

    public record QuestionSlot(
        int slotOrder,
        int mainQuestionIndex,
        QuestionType questionType,
        int followUpCount
    ) {
    }

    public record GeneratedQuestionSlot(
        QuestionSlot slot,
        List<InterviewQuestionDTO> questions
    ) {
    }

    private record QuestionDistribution(
        int project,
        int mysql,
        int redis,
        int javaBasic,
        int javaCollection,
        int javaConcurrent,
        int spring
    ) {
    }

    public InterviewQuestionService(
        AiTextClient aiTextClient,
        StructuredOutputInvoker structuredOutputInvoker,
        @Value("classpath:prompts/interview-question-system.st") Resource systemPromptResource,
        @Value("classpath:prompts/interview-question-user.st") Resource userPromptResource,
        @Value("${app.interview.follow-up-count:1}") int followUpCount
    ) throws IOException {
        this.aiTextClient = aiTextClient;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(QuestionListDTO.class);
        this.followUpCount = Math.max(0, Math.min(followUpCount, MAX_FOLLOW_UP_COUNT));
    }

    public int getFollowUpCount() {
        return followUpCount;
    }

    public int calculateTotalQuestionCount(int requestedQuestionCount) {
        return Math.max(0, requestedQuestionCount);
    }

    /**
     * 兼容保留：同步生成全量题目。
     */
    public List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount, List<String> historicalQuestions) {
        Set<String> generatedMainQuestions = new java.util.concurrent.ConcurrentSkipListSet<>();
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        for (QuestionSlot slot : buildQuestionSlots(questionCount)) {
            questions.addAll(generateQuestionSlot(resumeText, slot, historicalQuestions, generatedMainQuestions).questions());
        }
        return questions;
    }

    public List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount) {
        return generateQuestions(resumeText, questionCount, null);
    }

    /**
     * 根据题型分布构建题目槽位，槽位顺序固定后即可为并发结果预分配 questionIndex。
     */
    public List<QuestionSlot> buildQuestionSlots(int questionCount) {
        if (questionCount <= 0) {
            return List.of();
        }

        int slotCapacity = followUpCount + 1;
        int slotCount = (questionCount + slotCapacity - 1) / slotCapacity;
        int remainingFollowUps = Math.max(0, questionCount - slotCount);

        QuestionDistribution distribution = calculateDistribution(slotCount);
        List<QuestionType> slotTypes = new ArrayList<>(slotCount);
        appendSlotTypes(slotTypes, distribution.project(), QuestionType.PROJECT);
        appendSlotTypes(slotTypes, distribution.mysql(), QuestionType.MYSQL);
        appendSlotTypes(slotTypes, distribution.redis(), QuestionType.REDIS);
        appendSlotTypes(slotTypes, distribution.javaBasic(), QuestionType.JAVA_BASIC);
        appendSlotTypes(slotTypes, distribution.javaCollection(), QuestionType.JAVA_COLLECTION);
        appendSlotTypes(slotTypes, distribution.javaConcurrent(), QuestionType.JAVA_CONCURRENT);
        appendSlotTypes(slotTypes, distribution.spring(), QuestionType.SPRING);

        List<QuestionSlot> slots = new ArrayList<>(slotTypes.size());
        int nextQuestionIndex = 0;
        for (int slotOrder = 0; slotOrder < slotTypes.size(); slotOrder++) {
            int slotFollowUpCount = Math.min(followUpCount, remainingFollowUps);
            slots.add(new QuestionSlot(slotOrder, nextQuestionIndex, slotTypes.get(slotOrder), slotFollowUpCount));
            nextQuestionIndex += slotFollowUpCount + 1;
            remainingFollowUps -= slotFollowUpCount;
        }
        return slots;
    }

    /**
     * 生成单个槽位的问题及追问。
     */
    public GeneratedQuestionSlot generateQuestionSlot(
        String resumeText,
        QuestionSlot slot,
        List<String> historicalQuestions,
        Set<String> existingMainQuestions
    ) {
        for (int attempt = 1; attempt <= GENERATE_RETRY_TIMES; attempt++) {
            try {
                QuestionListDTO dto = invokeQuestionGeneration(resumeText, slot, historicalQuestions);
                GeneratedQuestionSlot generatedSlot = normalizeGeneratedSlot(slot, dto);
                String normalizedMainQuestion = normalizeQuestionKey(generatedSlot.questions().getFirst().question());
                if (existingMainQuestions.add(normalizedMainQuestion)) {
                    return generatedSlot;
                }
                log.warn("生成到重复题目，准备重试: slotOrder={}, type={}, attempt={}",
                    slot.slotOrder(), slot.questionType(), attempt);
            } catch (Exception exception) {
                log.warn("单槽位出题失败，准备重试: slotOrder={}, type={}, attempt={}, error={}",
                    slot.slotOrder(), slot.questionType(), attempt, exception.getMessage());
            }
        }

        GeneratedQuestionSlot fallbackSlot = buildDefaultSlot(slot);
        existingMainQuestions.add(normalizeQuestionKey(fallbackSlot.questions().getFirst().question()));
        log.warn("槽位出题回退默认题: slotOrder={}, type={}", slot.slotOrder(), slot.questionType());
        return fallbackSlot;
    }

    private QuestionListDTO invokeQuestionGeneration(
        String resumeText,
        QuestionSlot slot,
        List<String> historicalQuestions
    ) {
        String systemPrompt = systemPromptTemplate.render();

        QuestionDistribution distribution = distributionForSingleSlot(slot.questionType());
        Map<String, Object> variables = new HashMap<>();
        variables.put("questionCount", 1);
        variables.put("projectCount", distribution.project());
        variables.put("mysqlCount", distribution.mysql());
        variables.put("redisCount", distribution.redis());
        variables.put("javaBasicCount", distribution.javaBasic());
        variables.put("javaCollectionCount", distribution.javaCollection());
        variables.put("javaConcurrentCount", distribution.javaConcurrent());
        variables.put("springCount", distribution.spring());
        variables.put("followUpCount", slot.followUpCount());
        variables.put("resumeText", resumeText);
        if (historicalQuestions != null && !historicalQuestions.isEmpty()) {
            variables.put("historicalQuestions", String.join("\n", historicalQuestions));
        } else {
            variables.put("historicalQuestions", "暂无历史提问");
        }

        String userPrompt = userPromptTemplate.render(variables);
        String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();
        return structuredOutputInvoker.invoke(
            aiTextClient,
            systemPromptWithFormat,
            userPrompt,
            outputConverter,
            ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
            "面试问题生成失败：",
            "单槽位问题生成",
            log
        );
    }

    private GeneratedQuestionSlot normalizeGeneratedSlot(QuestionSlot slot, QuestionListDTO dto) {
        if (dto == null || dto.questions() == null || dto.questions().isEmpty()) {
            throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED, "AI 未返回有效题目");
        }

        QuestionDTO questionDTO = dto.questions().stream()
            .filter(item -> item != null && item.question() != null && !item.question().isBlank())
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED, "AI 未返回有效题目"));

        String category = defaultIfBlank(questionDTO.category(), defaultCategory(slot.questionType()));
        int mainQuestionIndex = slot.mainQuestionIndex();
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        questions.add(InterviewQuestionDTO.create(
            mainQuestionIndex,
            questionDTO.question().trim(),
            slot.questionType(),
            category,
            false,
            null
        ));

        List<String> followUps = fillFollowUps(questionDTO.question().trim(), slot.followUpCount(), questionDTO.followUps());
        for (int i = 0; i < followUps.size(); i++) {
            questions.add(InterviewQuestionDTO.create(
                mainQuestionIndex + i + 1,
                followUps.get(i),
                slot.questionType(),
                buildFollowUpCategory(category, i + 1),
                true,
                mainQuestionIndex
            ));
        }

        return new GeneratedQuestionSlot(slot, questions);
    }

    private GeneratedQuestionSlot buildDefaultSlot(QuestionSlot slot) {
        DefaultQuestion defaultQuestion = defaultQuestion(slot);
        int mainQuestionIndex = slot.mainQuestionIndex();
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        questions.add(InterviewQuestionDTO.create(
            mainQuestionIndex,
            defaultQuestion.question(),
            slot.questionType(),
            defaultQuestion.category(),
            false,
            null
        ));
        List<String> followUps = fillFollowUps(defaultQuestion.question(), slot.followUpCount(), List.of());
        for (int i = 0; i < followUps.size(); i++) {
            questions.add(InterviewQuestionDTO.create(
                mainQuestionIndex + i + 1,
                followUps.get(i),
                slot.questionType(),
                buildFollowUpCategory(defaultQuestion.category(), i + 1),
                true,
                mainQuestionIndex
            ));
        }
        return new GeneratedQuestionSlot(slot, questions);
    }

    private List<String> fillFollowUps(String mainQuestion, int slotFollowUpCount, List<String> aiFollowUps) {
        List<String> followUps = sanitizeFollowUps(aiFollowUps, slotFollowUpCount);
        if (followUps.size() >= slotFollowUpCount) {
            return followUps;
        }

        List<String> filled = new ArrayList<>(followUps);
        for (int i = followUps.size(); i < slotFollowUpCount; i++) {
            filled.add(buildDefaultFollowUp(mainQuestion, i + 1));
        }
        return filled;
    }

    private QuestionDistribution distributionForSingleSlot(QuestionType type) {
        Map<QuestionType, Integer> counts = new EnumMap<>(QuestionType.class);
        counts.put(type, 1);
        return new QuestionDistribution(
            counts.getOrDefault(QuestionType.PROJECT, 0),
            counts.getOrDefault(QuestionType.MYSQL, 0),
            counts.getOrDefault(QuestionType.REDIS, 0),
            counts.getOrDefault(QuestionType.JAVA_BASIC, 0),
            counts.getOrDefault(QuestionType.JAVA_COLLECTION, 0),
            counts.getOrDefault(QuestionType.JAVA_CONCURRENT, 0),
            counts.getOrDefault(QuestionType.SPRING, 0) + counts.getOrDefault(QuestionType.SPRING_BOOT, 0)
        );
    }

    private QuestionDistribution calculateDistribution(int total) {
        if (total <= 0) {
            return new QuestionDistribution(0, 0, 0, 0, 0, 0, 0);
        }

        Map<QuestionType, Integer> counts = new EnumMap<>(QuestionType.class);
        Map<QuestionType, Double> remainders = new EnumMap<>(QuestionType.class);
        List<QuestionType> distributionOrder = List.of(
            QuestionType.PROJECT,
            QuestionType.MYSQL,
            QuestionType.REDIS,
            QuestionType.JAVA_BASIC,
            QuestionType.JAVA_COLLECTION,
            QuestionType.JAVA_CONCURRENT,
            QuestionType.SPRING
        );

        int assigned = 0;
        for (QuestionType questionType : distributionOrder) {
            double expected = total * distributionWeight(questionType);
            int baseCount = (int) Math.floor(expected);
            counts.put(questionType, baseCount);
            remainders.put(questionType, expected - baseCount);
            assigned += baseCount;
        }

        int remaining = total - assigned;
        List<QuestionType> remainderOrder = new ArrayList<>(distributionOrder);
        remainderOrder.sort(
            java.util.Comparator
                .comparingDouble((QuestionType questionType) -> remainders.getOrDefault(questionType, 0D))
                .reversed()
                .thenComparingInt(distributionOrder::indexOf)
        );

        for (int i = 0; i < remaining; i++) {
            QuestionType questionType = remainderOrder.get(i);
            counts.merge(questionType, 1, Integer::sum);
        }

        return new QuestionDistribution(
            counts.getOrDefault(QuestionType.PROJECT, 0),
            counts.getOrDefault(QuestionType.MYSQL, 0),
            counts.getOrDefault(QuestionType.REDIS, 0),
            counts.getOrDefault(QuestionType.JAVA_BASIC, 0),
            counts.getOrDefault(QuestionType.JAVA_COLLECTION, 0),
            counts.getOrDefault(QuestionType.JAVA_CONCURRENT, 0),
            counts.getOrDefault(QuestionType.SPRING, 0)
        );
    }

    private List<String> sanitizeFollowUps(List<String> followUps, int maxFollowUpCount) {
        if (maxFollowUpCount == 0 || followUps == null || followUps.isEmpty()) {
            return List.of();
        }
        return followUps.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .limit(maxFollowUpCount)
            .collect(Collectors.toList());
    }

    private void appendSlotTypes(List<QuestionType> slotTypes, int count, QuestionType type) {
        for (int i = 0; i < count; i++) {
            slotTypes.add(type);
        }
    }

    private double distributionWeight(QuestionType questionType) {
        return switch (questionType) {
            case PROJECT -> PROJECT_RATIO;
            case MYSQL -> MYSQL_RATIO;
            case REDIS -> REDIS_RATIO;
            case JAVA_BASIC -> JAVA_BASIC_RATIO;
            case JAVA_COLLECTION -> JAVA_COLLECTION_RATIO;
            case JAVA_CONCURRENT -> JAVA_CONCURRENT_RATIO;
            case SPRING, SPRING_BOOT -> SPRING_RATIO;
        };
    }

    private String buildFollowUpCategory(String category, int order) {
        String baseCategory = defaultIfBlank(category, "追问");
        return baseCategory + "（追问" + order + "）";
    }

    private String buildDefaultFollowUp(String mainQuestion, int order) {
        if (order == 1) {
            return "基于“" + mainQuestion + "”，请结合你亲自做过的一个真实场景展开说明。";
        }
        return "基于“" + mainQuestion + "”，如果线上出现异常，你会如何定位并给出修复方案？";
    }

    private String defaultCategory(QuestionType questionType) {
        return switch (questionType) {
            case PROJECT -> "项目经历";
            case JAVA_BASIC -> "Java基础";
            case JAVA_COLLECTION -> "Java集合";
            case JAVA_CONCURRENT -> "Java并发";
            case MYSQL -> "MySQL";
            case REDIS -> "Redis";
            case SPRING -> "Spring";
            case SPRING_BOOT -> "Spring Boot";
        };
    }

    private String normalizeQuestionKey(String question) {
        if (question == null) {
            return "";
        }
        return question.replaceAll("\\s+", "").toLowerCase();
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private DefaultQuestion defaultQuestion(QuestionSlot slot) {
        return switch (slot.questionType()) {
            case PROJECT -> projectDefaultQuestion(slot.slotOrder());
            case MYSQL -> mysqlDefaultQuestion(slot.slotOrder());
            case REDIS -> redisDefaultQuestion(slot.slotOrder());
            case JAVA_BASIC -> javaBasicDefaultQuestion(slot.slotOrder());
            case JAVA_COLLECTION -> javaCollectionDefaultQuestion(slot.slotOrder());
            case JAVA_CONCURRENT -> concurrentDefaultQuestion(slot.slotOrder());
            case SPRING, SPRING_BOOT -> springDefaultQuestion(slot.slotOrder());
        };
    }

    private DefaultQuestion projectDefaultQuestion(int slotOrder) {
        if (slotOrder % 2 == 0) {
            return new DefaultQuestion("请介绍一下你在简历中提到的最重要的项目，你在其中承担了什么角色？", "项目经历");
        }
        return new DefaultQuestion("请结合一个你主导或深度参与的项目，说明核心业务目标、技术方案与最终收益。", "项目经历");
    }

    private DefaultQuestion javaBasicDefaultQuestion(int slotOrder) {
        if (slotOrder % 2 == 0) {
            return new DefaultQuestion("Java的垃圾回收机制是怎样的？常见的GC算法有哪些？", "Java基础");
        }
        return new DefaultQuestion("Java中的异常体系是如何设计的？受检异常和非受检异常分别适合什么场景？", "Java基础");
    }

    private DefaultQuestion javaCollectionDefaultQuestion(int slotOrder) {
        if (slotOrder % 2 == 0) {
            return new DefaultQuestion("Java中HashMap的底层实现原理是什么？JDK8做了哪些优化？", "Java集合");
        }
        return new DefaultQuestion("ArrayList和LinkedList的底层差异是什么？在不同读写场景下你会如何选择？", "Java集合");
    }

    private DefaultQuestion mysqlDefaultQuestion(int slotOrder) {
        if (slotOrder % 2 == 0) {
            return new DefaultQuestion("MySQL的索引有哪些类型？B+树索引的原理是什么？", "MySQL");
        }
        return new DefaultQuestion("MySQL事务的ACID特性是什么？隔离级别有哪些？", "MySQL");
    }

    private DefaultQuestion redisDefaultQuestion(int slotOrder) {
        if (slotOrder % 2 == 0) {
            return new DefaultQuestion("Redis支持哪些数据结构？各自的使用场景是什么？", "Redis");
        }
        return new DefaultQuestion("Redis的持久化机制有哪些？RDB和AOF的区别？", "Redis");
    }

    private DefaultQuestion concurrentDefaultQuestion(int slotOrder) {
        if (slotOrder % 2 == 0) {
            return new DefaultQuestion("synchronized和ReentrantLock有什么区别？", "Java并发");
        }
        return new DefaultQuestion("线程池的核心参数有哪些？如何合理配置？", "Java并发");
    }

    private DefaultQuestion springDefaultQuestion(int slotOrder) {
        if (slotOrder % 2 == 0) {
            return new DefaultQuestion("Spring的IoC和AOP原理是什么？", "Spring");
        }
        return new DefaultQuestion("Spring Boot的自动配置机制是如何工作的？你在项目里做过哪些自定义扩展？", "Spring");
    }

    private record DefaultQuestion(String question, String category) {
    }
}
