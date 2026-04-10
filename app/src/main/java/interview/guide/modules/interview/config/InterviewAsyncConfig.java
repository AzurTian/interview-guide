package interview.guide.modules.interview.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 面试模块异步线程池配置
 */
@Configuration
public class InterviewAsyncConfig {

    public static final String QUESTION_GENERATION_EXECUTOR = "interviewQuestionGenerationExecutor";
    public static final String ANSWER_EVALUATION_EXECUTOR = "interviewAnswerEvaluationExecutor";

    @Bean(name = QUESTION_GENERATION_EXECUTOR)
    public Executor interviewQuestionGenerationExecutor(
        @Value("${app.interview.async.question-generation.pool-size:4}") int poolSize,
        @Value("${app.interview.async.question-generation.queue-capacity:32}") int queueCapacity
    ) {
        return buildExecutor("interview-question-generation-", poolSize, queueCapacity);
    }

    @Bean(name = ANSWER_EVALUATION_EXECUTOR)
    public Executor interviewAnswerEvaluationExecutor(
        @Value("${app.interview.async.answer-evaluation.pool-size:4}") int poolSize,
        @Value("${app.interview.async.answer-evaluation.queue-capacity:64}") int queueCapacity
    ) {
        return buildExecutor("interview-answer-evaluation-", poolSize, queueCapacity);
    }

    private Executor buildExecutor(String threadNamePrefix, int poolSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setCorePoolSize(Math.max(1, poolSize));
        executor.setMaxPoolSize(Math.max(1, poolSize));
        executor.setQueueCapacity(Math.max(1, queueCapacity));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
