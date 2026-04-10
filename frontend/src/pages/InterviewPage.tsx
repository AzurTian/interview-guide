import { useCallback, useEffect, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { historyApi } from '../api/history';
import { interviewApi } from '../api/interview';
import ConfirmDialog from '../components/ConfirmDialog';
import InterviewConfigPanel from '../components/InterviewConfigPanel';
import InterviewChatPanel from '../components/InterviewChatPanel';
import type {
  InterviewQuestion,
  InterviewSession,
} from '../types/interview';

type InterviewStage = 'config' | 'interview';

interface Message {
  type: 'interviewer' | 'user';
  content: string;
  category?: string;
  questionIndex?: number;
}

interface InterviewProps {
  resumeText: string;
  resumeId?: number;
  onBack: () => void;
  onInterviewComplete: () => void;
}

function sortQuestions(questions: InterviewQuestion[]): InterviewQuestion[] {
  return [...questions].sort((left, right) => left.questionIndex - right.questionIndex);
}

function mergeQuestions(existing: InterviewQuestion[], incoming: InterviewQuestion[]): InterviewQuestion[] {
  const questionMap = new Map<number, InterviewQuestion>();
  for (const question of existing) {
    questionMap.set(question.questionIndex, question);
  }
  for (const question of incoming) {
    questionMap.set(question.questionIndex, {
      ...questionMap.get(question.questionIndex),
      ...question,
    });
  }
  return sortQuestions(Array.from(questionMap.values()));
}

function shouldSubscribeQuestionStream(session: InterviewSession | null): boolean {
  if (!session) {
    return false;
  }
  return session.questionGenerationStatus === 'PENDING' || session.questionGenerationStatus === 'PROCESSING';
}

export default function Interview({ resumeText, resumeId, onBack, onInterviewComplete }: InterviewProps) {
  const [stage, setStage] = useState<InterviewStage>('config');
  const [questionCount, setQuestionCount] = useState(8);
  const [session, setSession] = useState<InterviewSession | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState<InterviewQuestion | null>(null);
  const [currentQuestionIndex, setCurrentQuestionIndex] = useState(0);
  const [messages, setMessages] = useState<Message[]>([]);
  const [answer, setAnswer] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [checkingUnfinished, setCheckingUnfinished] = useState(false);
  const [unfinishedSession, setUnfinishedSession] = useState<InterviewSession | null>(null);
  const [showCompleteConfirm, setShowCompleteConfirm] = useState(false);
  const [forceCreateNew, setForceCreateNew] = useState(false);
  const [waitingForNextQuestion, setWaitingForNextQuestion] = useState(false);
  const [isEvaluating, setIsEvaluating] = useState(false);

  const sessionRef = useRef<InterviewSession | null>(null);
  const currentQuestionRef = useRef<InterviewQuestion | null>(null);
  const displayedQuestionIndicesRef = useRef<Set<number>>(new Set());
  const streamCleanupRef = useRef<(() => void) | null>(null);
  const evaluationPollingRef = useRef<number | null>(null);

  const stopQuestionStream = useCallback(() => {
    streamCleanupRef.current?.();
    streamCleanupRef.current = null;
  }, []);

  const stopEvaluationPolling = useCallback(() => {
    if (evaluationPollingRef.current !== null) {
      window.clearInterval(evaluationPollingRef.current);
      evaluationPollingRef.current = null;
    }
  }, []);

  const beginEvaluation = useCallback((sessionId: string) => {
    stopQuestionStream();
    stopEvaluationPolling();
    setWaitingForNextQuestion(false);
    setIsEvaluating(true);

    const checkEvaluationStatus = async () => {
      try {
        const detail = await historyApi.getInterviewDetail(sessionId);
        if (detail.evaluateStatus === 'COMPLETED' || detail.evaluateStatus === 'FAILED') {
          stopEvaluationPolling();
          onInterviewComplete();
        }
      } catch (requestError) {
        console.error('查询面试评估状态失败', requestError);
      }
    };

    evaluationPollingRef.current = window.setInterval(() => {
      void checkEvaluationStatus();
    }, 3000);
    void checkEvaluationStatus();
  }, [onInterviewComplete, stopEvaluationPolling, stopQuestionStream]);

  const appendQuestionMessage = useCallback((question: InterviewQuestion) => {
    if (displayedQuestionIndicesRef.current.has(question.questionIndex)) {
      return;
    }
    displayedQuestionIndicesRef.current.add(question.questionIndex);
    setMessages((prev) => [
      ...prev,
      {
        type: 'interviewer',
        content: question.question,
        category: question.category,
        questionIndex: question.questionIndex,
      },
    ]);
  }, []);

  const syncCurrentQuestion = useCallback((nextSession: InterviewSession | null) => {
    if (!nextSession) {
      currentQuestionRef.current = null;
      setCurrentQuestion(null);
      setCurrentQuestionIndex(0);
      setWaitingForNextQuestion(false);
      return;
    }

    const nextQuestion = nextSession.questions.find(
      (question) => question.questionIndex === nextSession.currentQuestionIndex
    ) ?? null;

    currentQuestionRef.current = nextQuestion;
    setCurrentQuestion(nextQuestion);
    setCurrentQuestionIndex(nextSession.currentQuestionIndex);

    const waiting = !nextQuestion
      && nextSession.currentQuestionIndex < nextSession.totalQuestions
      && nextSession.questionGenerationStatus !== 'FAILED';
    setWaitingForNextQuestion(waiting);

    if (nextQuestion) {
      appendQuestionMessage(nextQuestion);
    }
  }, [appendQuestionMessage]);

  const commitSession = useCallback((nextSession: InterviewSession | null) => {
    sessionRef.current = nextSession;
    setSession(nextSession);
    syncCurrentQuestion(nextSession);
  }, [syncCurrentQuestion]);

  const subscribeQuestionStream = useCallback((sessionId: string) => {
    stopQuestionStream();
    streamCleanupRef.current = interviewApi.subscribeQuestionStream(
      sessionId,
      (event) => {
        const currentSession = sessionRef.current;
        if (event.type === 'snapshot' && event.session) {
          const snapshotSession: InterviewSession = {
            ...event.session,
            questions: mergeQuestions(currentSession?.questions ?? [], event.session.questions ?? []),
          };
          commitSession(snapshotSession);
          return;
        }

        if (!currentSession) {
          return;
        }

        if (event.type === 'question' && event.question) {
          commitSession({
            ...currentSession,
            questions: mergeQuestions(currentSession.questions, [event.question]),
            generatedQuestionCount: event.generatedQuestionCount ?? currentSession.generatedQuestionCount,
            totalQuestions: event.totalQuestions ?? currentSession.totalQuestions,
            questionGenerationStatus: event.questionGenerationStatus ?? currentSession.questionGenerationStatus,
          });
          return;
        }

        if (event.type === 'completed') {
          commitSession({
            ...currentSession,
            generatedQuestionCount: event.generatedQuestionCount ?? currentSession.generatedQuestionCount,
            totalQuestions: event.totalQuestions ?? currentSession.totalQuestions,
            questionGenerationStatus: 'COMPLETED',
            questionGenerationError: null,
          });
          return;
        }

        if (event.type === 'error') {
          const generationError = event.error ?? '题目生成失败，请重试。';
          commitSession({
            ...currentSession,
            questionGenerationStatus: 'FAILED',
            questionGenerationError: generationError,
          });
          setError(generationError);
        }
      },
      () => {
        streamCleanupRef.current = null;
      },
      (streamError) => {
        const currentSession = sessionRef.current;
        if (currentSession && currentSession.questionGenerationStatus !== 'COMPLETED') {
          commitSession({
            ...currentSession,
            questionGenerationStatus: 'FAILED',
            questionGenerationError: streamError.message,
          });
          setError(streamError.message);
        }
      }
    );
  }, [commitSession, stopQuestionStream]);

  const resetConversationState = useCallback(() => {
    displayedQuestionIndicesRef.current = new Set();
    setMessages([]);
    setAnswer('');
    setError('');
    setWaitingForNextQuestion(false);
    setIsEvaluating(false);
    setCurrentQuestion(null);
    setCurrentQuestionIndex(0);
  }, []);

  const restoreSession = useCallback((sessionToRestore: InterviewSession) => {
    stopQuestionStream();
    stopEvaluationPolling();

    const restoredSession: InterviewSession = {
      ...sessionToRestore,
      questions: sortQuestions(sessionToRestore.questions),
    };

    const restoredMessages: Message[] = [];
    const displayedIndices = new Set<number>();
    for (const question of restoredSession.questions) {
      if (question.questionIndex > restoredSession.currentQuestionIndex) {
        continue;
      }
      displayedIndices.add(question.questionIndex);
      restoredMessages.push({
        type: 'interviewer',
        content: question.question,
        category: question.category,
        questionIndex: question.questionIndex,
      });
      if (question.userAnswer) {
        restoredMessages.push({
          type: 'user',
          content: question.userAnswer,
        });
      }
    }

    displayedQuestionIndicesRef.current = displayedIndices;
    setMessages(restoredMessages);
    const currentDraft = restoredSession.questions.find(
      (question) => question.questionIndex === restoredSession.currentQuestionIndex
    );
    setAnswer(currentDraft?.userAnswer ?? '');
    setIsEvaluating(false);
    commitSession(restoredSession);
    setStage('interview');

    if (shouldSubscribeQuestionStream(restoredSession)) {
      subscribeQuestionStream(restoredSession.sessionId);
    }
  }, [commitSession, stopEvaluationPolling, stopQuestionStream, subscribeQuestionStream]);

  useEffect(() => {
    if (!resumeId) {
      return;
    }

    const checkUnfinishedSession = async () => {
      setCheckingUnfinished(true);
      try {
        const foundSession = await interviewApi.findUnfinishedSession(resumeId);
        if (foundSession) {
          setUnfinishedSession(foundSession);
        }
      } catch (requestError) {
        console.error('检查未完成面试失败', requestError);
      } finally {
        setCheckingUnfinished(false);
      }
    };

    void checkUnfinishedSession();
  }, [resumeId]);

  useEffect(() => {
    return () => {
      stopQuestionStream();
      stopEvaluationPolling();
    };
  }, [stopEvaluationPolling, stopQuestionStream]);

  const handleContinueUnfinished = () => {
    if (!unfinishedSession) {
      return;
    }
    setForceCreateNew(false);
    setUnfinishedSession(null);
    restoreSession(unfinishedSession);
  };

  const handleStartNew = () => {
    setUnfinishedSession(null);
    setForceCreateNew(true);
  };

  const startInterview = async () => {
    setIsCreating(true);
    setError('');
    stopQuestionStream();
    stopEvaluationPolling();
    resetConversationState();

    try {
      const newSession = await interviewApi.createSession({
        resumeText,
        questionCount,
        resumeId,
        forceCreate: forceCreateNew,
      });

      setForceCreateNew(false);

      const hasProgress = newSession.currentQuestionIndex > 0
        || newSession.questions.some((question) => !!question.userAnswer)
        || newSession.status === 'IN_PROGRESS';

      if (hasProgress) {
        restoreSession(newSession);
        return;
      }

      const initialSession: InterviewSession = {
        ...newSession,
        questions: sortQuestions(newSession.questions),
      };

      displayedQuestionIndicesRef.current = new Set();
      commitSession(initialSession);
      setStage('interview');

      if (shouldSubscribeQuestionStream(initialSession)) {
        subscribeQuestionStream(initialSession.sessionId);
      }
    } catch (requestError) {
      setError('创建面试失败，请重试');
      console.error(requestError);
      setForceCreateNew(false);
    } finally {
      setIsCreating(false);
    }
  };

  const handleSubmitAnswer = async () => {
    const activeSession = sessionRef.current;
    const activeQuestion = currentQuestionRef.current;
    if (!answer.trim() || !activeSession || !activeQuestion) {
      return;
    }

    setIsSubmitting(true);
    setError('');

    const submittedAnswer = answer.trim();
    setMessages((prev) => [
      ...prev,
      {
        type: 'user',
        content: submittedAnswer,
      },
    ]);

    try {
      const response = await interviewApi.submitAnswer({
        sessionId: activeSession.sessionId,
        questionIndex: activeQuestion.questionIndex,
        answer: submittedAnswer,
      });

      setAnswer('');

      const latestSession = sessionRef.current ?? activeSession;
      const updatedQuestions = mergeQuestions(
        latestSession.questions.map((question) => (
          question.questionIndex === activeQuestion.questionIndex
            ? { ...question, userAnswer: submittedAnswer }
            : question
        )),
        response.nextQuestion ? [response.nextQuestion] : []
      );

      const updatedSession: InterviewSession = {
        ...latestSession,
        questions: updatedQuestions,
        currentQuestionIndex: response.currentIndex,
        status: response.completed ? 'COMPLETED' : 'IN_PROGRESS',
      };

      commitSession(updatedSession);

      if (response.completed) {
        beginEvaluation(activeSession.sessionId);
      } else {
        setWaitingForNextQuestion(response.waitingForNextQuestion);
      }
    } catch (requestError) {
      setError('提交答案失败，请重试');
      console.error(requestError);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCompleteEarly = async () => {
    const activeSession = sessionRef.current;
    if (!activeSession) {
      return;
    }

    setIsSubmitting(true);
    try {
      await interviewApi.completeInterview(activeSession.sessionId);
      setShowCompleteConfirm(false);
      commitSession({
        ...activeSession,
        currentQuestionIndex: activeSession.totalQuestions,
        status: 'COMPLETED',
      });
      beginEvaluation(activeSession.sessionId);
    } catch (requestError) {
      setError('提前交卷失败，请重试');
      console.error(requestError);
    } finally {
      setIsSubmitting(false);
    }
  };

  const renderConfig = () => (
    <InterviewConfigPanel
      questionCount={questionCount}
      onQuestionCountChange={setQuestionCount}
      onStart={startInterview}
      isCreating={isCreating}
      checkingUnfinished={checkingUnfinished}
      unfinishedSession={unfinishedSession}
      onContinueUnfinished={handleContinueUnfinished}
      onStartNew={handleStartNew}
      resumeText={resumeText}
      onBack={onBack}
      error={error}
    />
  );

  const renderInterview = () => {
    if (!session) {
      return null;
    }

    return (
      <InterviewChatPanel
        session={session}
        currentQuestion={currentQuestion}
        currentQuestionIndex={currentQuestionIndex}
        waitingForNextQuestion={waitingForNextQuestion}
        isEvaluating={isEvaluating}
        questionGenerationStatus={session.questionGenerationStatus}
        questionGenerationError={session.questionGenerationError}
        messages={messages}
        answer={answer}
        onAnswerChange={setAnswer}
        onSubmit={handleSubmitAnswer}
        onCompleteEarly={handleCompleteEarly}
        isSubmitting={isSubmitting}
        showCompleteConfirm={showCompleteConfirm}
        onShowCompleteConfirm={setShowCompleteConfirm}
      />
    );
  };

  const stageSubtitles = {
    config: '配置您的面试参数',
    interview: '认真回答每个问题，展示您的实力',
  };

  return (
    <div className="pb-10">
      <motion.div
        className="text-center mb-10"
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <h1 className="text-3xl font-bold text-slate-900 dark:text-white mb-2 flex items-center justify-center gap-3">
          <div className="w-12 h-12 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center">
            <svg className="w-6 h-6 text-white" viewBox="0 0 24 24" fill="none">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              <line x1="12" y1="19" x2="12" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              <line x1="8" y1="23" x2="16" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
          模拟面试
        </h1>
        <p className="text-slate-500 dark:text-slate-400">{stageSubtitles[stage]}</p>
      </motion.div>

      <AnimatePresence mode="wait" initial={false}>
        {stage === 'config' && (
          <motion.div
            key="config"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            transition={{ duration: 0.3 }}
          >
            {renderConfig()}
          </motion.div>
        )}
        {stage === 'interview' && (
          <motion.div
            key="interview"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
          >
            {renderInterview()}
          </motion.div>
        )}
      </AnimatePresence>

      <ConfirmDialog
        open={showCompleteConfirm}
        title="提前交卷"
        message="确定要提前交卷吗？未回答的问题将按0分计算。"
        confirmText="确定交卷"
        cancelText="取消"
        confirmVariant="warning"
        loading={isSubmitting}
        onConfirm={handleCompleteEarly}
        onCancel={() => setShowCompleteConfirm(false)}
      />
    </div>
  );
}
