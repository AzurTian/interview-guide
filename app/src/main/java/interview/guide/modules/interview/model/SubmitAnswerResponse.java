package interview.guide.modules.interview.model;

/**
 * 提交答案响应
 */
public record SubmitAnswerResponse(
    boolean hasNextQuestion,
    InterviewQuestionDTO nextQuestion,
    boolean waitingForNextQuestion,
    boolean completed,
    int currentIndex,
    int totalQuestions
) {}
