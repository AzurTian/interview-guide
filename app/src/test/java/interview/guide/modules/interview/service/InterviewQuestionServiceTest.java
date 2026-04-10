package interview.guide.modules.interview.service;

import interview.guide.common.ai.AiTextClient;
import interview.guide.common.ai.StructuredOutputInvoker;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class InterviewQuestionServiceTest {

    @Test
    void calculateTotalQuestionCountUsesRequestedDisplayedQuestionCount() throws IOException {
        InterviewQuestionService questionService = createQuestionService(1);

        assertEquals(6, questionService.calculateTotalQuestionCount(6));
    }

    @Test
    void buildQuestionSlotsKeepsExactDisplayedQuestionCount() throws IOException {
        InterviewQuestionService questionService = createQuestionService(1);

        List<InterviewQuestionService.QuestionSlot> slots = questionService.buildQuestionSlots(6);

        assertEquals(3, slots.size());
        assertEquals(List.of(0, 2, 4), slots.stream().map(InterviewQuestionService.QuestionSlot::mainQuestionIndex).toList());
        assertEquals(List.of(1, 1, 1), slots.stream().map(InterviewQuestionService.QuestionSlot::followUpCount).toList());
        assertEquals(6, slots.stream().mapToInt(slot -> slot.followUpCount() + 1).sum());
    }

    @Test
    void buildQuestionSlotsAssignsRemainderToLastMainQuestion() throws IOException {
        InterviewQuestionService questionService = createQuestionService(1);

        List<InterviewQuestionService.QuestionSlot> slots = questionService.buildQuestionSlots(5);

        assertEquals(3, slots.size());
        assertEquals(List.of(0, 2, 4), slots.stream().map(InterviewQuestionService.QuestionSlot::mainQuestionIndex).toList());
        assertEquals(List.of(1, 1, 0), slots.stream().map(InterviewQuestionService.QuestionSlot::followUpCount).toList());
        assertEquals(5, slots.stream().mapToInt(slot -> slot.followUpCount() + 1).sum());
    }

    private InterviewQuestionService createQuestionService(int followUpCount) throws IOException {
        return new InterviewQuestionService(
            mock(AiTextClient.class),
            mock(StructuredOutputInvoker.class),
            new ClassPathResource("prompts/interview-question-system.st"),
            new ClassPathResource("prompts/interview-question-user.st"),
            followUpCount
        );
    }
}
