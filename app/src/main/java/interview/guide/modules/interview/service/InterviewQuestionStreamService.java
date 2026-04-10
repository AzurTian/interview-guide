package interview.guide.modules.interview.service;

import interview.guide.common.model.AsyncTaskStatus;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewQuestionStreamEvent;
import interview.guide.modules.interview.model.InterviewSessionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 面试题目 SSE 事件服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewQuestionStreamService {

    private final ObjectMapper objectMapper;

    private final Map<String, Sinks.Many<String>> streamSinks = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<String>> subscribe(String sessionId, Supplier<InterviewSessionDTO> snapshotSupplier) {
        InterviewSessionDTO snapshot = snapshotSupplier.get();
        Flux<String> snapshotFlux = Flux.just(serialize(InterviewQuestionStreamEvent.snapshot(snapshot)));

        boolean terminal = snapshot.questionGenerationStatus() == AsyncTaskStatus.COMPLETED
            || snapshot.questionGenerationStatus() == AsyncTaskStatus.FAILED;

        Flux<String> liveFlux = terminal
            ? Flux.empty()
            : sink(sessionId).asFlux();

        return Flux.concat(snapshotFlux, liveFlux)
            .map(payload -> ServerSentEvent.<String>builder().data(payload).build())
            .doOnSubscribe(subscription -> log.debug("订阅题目流: sessionId={}", sessionId))
            .doFinally(signalType -> log.debug("题目流结束: sessionId={}, signal={}", sessionId, signalType));
    }

    public void publishQuestion(
        String sessionId,
        InterviewQuestionDTO question,
        int generatedQuestionCount,
        int totalQuestions,
        InterviewSessionDTO sessionSnapshot
    ) {
        emit(sessionId, InterviewQuestionStreamEvent.questionGenerated(
            question,
            generatedQuestionCount,
            totalQuestions,
            sessionSnapshot.questionGenerationStatus()
        ));
    }

    public void publishCompleted(String sessionId, int generatedQuestionCount, int totalQuestions) {
        emit(sessionId, InterviewQuestionStreamEvent.completed(generatedQuestionCount, totalQuestions));
        complete(sessionId);
    }

    public void publishError(String sessionId, String error, int generatedQuestionCount, int totalQuestions) {
        emit(sessionId, InterviewQuestionStreamEvent.failed(error, generatedQuestionCount, totalQuestions));
        complete(sessionId);
    }

    private void emit(String sessionId, InterviewQuestionStreamEvent event) {
        Sinks.Many<String> sink = sink(sessionId);
        Sinks.EmitResult result = sink.tryEmitNext(serialize(event));
        if (result.isFailure()) {
            log.debug("推送题目流事件失败: sessionId={}, result={}", sessionId, result);
        }
    }

    private void complete(String sessionId) {
        Sinks.Many<String> sink = streamSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    private Sinks.Many<String> sink(String sessionId) {
        return streamSinks.computeIfAbsent(sessionId, ignored ->
            Sinks.many().multicast().directBestEffort()
        );
    }

    private String serialize(InterviewQuestionStreamEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            log.error("序列化题目流事件失败", exception);
            throw new IllegalStateException("序列化题目流事件失败", exception);
        }
    }
}
