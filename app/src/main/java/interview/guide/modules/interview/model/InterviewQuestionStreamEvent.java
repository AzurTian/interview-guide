package interview.guide.modules.interview.model;

import interview.guide.common.model.AsyncTaskStatus;

/**
 * 面试题目流式事件
 */
public record InterviewQuestionStreamEvent(
    String type,
    InterviewSessionDTO session,
    InterviewQuestionDTO question,
    Integer generatedQuestionCount,
    Integer totalQuestions,
    AsyncTaskStatus questionGenerationStatus,
    String error
) {
    public static InterviewQuestionStreamEvent snapshot(InterviewSessionDTO session) {
        return new InterviewQuestionStreamEvent(
            "snapshot",
            session,
            null,
            session.generatedQuestionCount(),
            session.totalQuestions(),
            session.questionGenerationStatus(),
            session.questionGenerationError()
        );
    }

    public static InterviewQuestionStreamEvent questionGenerated(
        InterviewQuestionDTO question,
        int generatedQuestionCount,
        int totalQuestions,
        AsyncTaskStatus questionGenerationStatus
    ) {
        return new InterviewQuestionStreamEvent(
            "question",
            null,
            question,
            generatedQuestionCount,
            totalQuestions,
            questionGenerationStatus,
            null
        );
    }

    public static InterviewQuestionStreamEvent completed(int generatedQuestionCount, int totalQuestions) {
        return new InterviewQuestionStreamEvent(
            "completed",
            null,
            null,
            generatedQuestionCount,
            totalQuestions,
            AsyncTaskStatus.COMPLETED,
            null
        );
    }

    public static InterviewQuestionStreamEvent failed(String error, int generatedQuestionCount, int totalQuestions) {
        return new InterviewQuestionStreamEvent(
            "error",
            null,
            null,
            generatedQuestionCount,
            totalQuestions,
            AsyncTaskStatus.FAILED,
            error
        );
    }
}
