package interview.guide.modules.interview.model;

/**
 * 面试评估状态响应 DTO。
 */
public record InterviewEvaluationStatusDTO(
    String evaluateStatus,
    String evaluateError
) {
}
