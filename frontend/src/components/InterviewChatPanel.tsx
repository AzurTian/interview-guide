import {useMemo, useRef} from 'react';
import {motion} from 'framer-motion';
import {Virtuoso, type VirtuosoHandle} from 'react-virtuoso';
import type {InterviewQuestion, InterviewSession, QuestionGenerationStatus} from '../types/interview';
import {Send, User} from 'lucide-react';

interface Message {
  type: 'interviewer' | 'user';
  content: string;
  category?: string;
  questionIndex?: number;
}

interface InterviewChatPanelProps {
  session: InterviewSession;
  currentQuestion: InterviewQuestion | null;
  currentQuestionIndex: number;
  waitingForNextQuestion: boolean;
  isEvaluating: boolean;
  questionGenerationStatus: QuestionGenerationStatus;
  questionGenerationError: string | null;
  messages: Message[];
  answer: string;
  onAnswerChange: (answer: string) => void;
  onSubmit: () => void;
  onCompleteEarly: () => void;
  isSubmitting: boolean;
  showCompleteConfirm: boolean;
  onShowCompleteConfirm: (show: boolean) => void;
}

/**
 * 面试聊天面板组件
 */
export default function InterviewChatPanel({
  session,
  currentQuestion,
  currentQuestionIndex,
  waitingForNextQuestion,
  isEvaluating,
  questionGenerationStatus,
  questionGenerationError,
  messages,
  answer,
  onAnswerChange,
  onSubmit,
  // onCompleteEarly, // 暂时未使用
  isSubmitting,
  // showCompleteConfirm, // 暂时未使用
  onShowCompleteConfirm
}: InterviewChatPanelProps) {
  const virtuosoRef = useRef<VirtuosoHandle>(null);
  const showQuestionGenerationNotice = !isEvaluating
    && (waitingForNextQuestion
      || (!currentQuestion
        && questionGenerationStatus !== 'FAILED'
        && currentQuestionIndex < session.totalQuestions));

  const progress = useMemo(() => {
    if (!session || session.totalQuestions === 0) return 0;
    const progressIndex = currentQuestion ? currentQuestion.questionIndex + 1 : Math.min(currentQuestionIndex, session.totalQuestions);
    return (progressIndex / session.totalQuestions) * 100;
  }, [session, currentQuestion, currentQuestionIndex]);

  const activeQuestionNumber = currentQuestion
    ? currentQuestion.questionIndex + 1
    : Math.min(currentQuestionIndex + 1, session.totalQuestions);

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey) && currentQuestion && !waitingForNextQuestion && !isEvaluating) {
      onSubmit();
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-200px)] max-w-4xl mx-auto">
      {/* 进度条 */}
        <div
            className="bg-white dark:bg-slate-800 rounded-2xl p-6 mb-4 shadow-sm dark:shadow-slate-900/50 border border-slate-100 dark:border-slate-700">
        <div className="flex items-center justify-between mb-3">
          <span className="text-sm font-semibold text-slate-700 dark:text-slate-300">
            题目 {session.totalQuestions > 0 ? activeQuestionNumber : 0} / {session.totalQuestions}
          </span>
            <span className="text-sm text-slate-500 dark:text-slate-400">
            {Math.round(progress)}%
          </span>
        </div>
            <div className="h-2 bg-slate-200 dark:bg-slate-700 rounded-full overflow-hidden">
          <motion.div
            className="h-full bg-gradient-to-r from-primary-500 to-primary-600 rounded-full"
            initial={{ width: 0 }}
            animate={{ width: `${progress}%` }}
            transition={{ duration: 0.3 }}
          />
        </div>
      </div>

      {/* 聊天区域 */}
        <div
            className="flex-1 bg-white dark:bg-slate-800 rounded-2xl shadow-sm dark:shadow-slate-900/50 overflow-hidden flex flex-col min-h-0 border border-slate-100 dark:border-slate-700">
        <Virtuoso
          ref={virtuosoRef}
          data={messages}
          initialTopMostItemIndex={messages.length - 1}
          followOutput="smooth"
          className="flex-1"
          itemContent={(_index, msg) => (
            <div className="pb-4 px-6 first:pt-6">
              <MessageBubble message={msg} />
            </div>
          )}
        />

        {isEvaluating && (
          <div className="px-6 py-4 border-t border-slate-200 dark:border-slate-600 bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-300 flex items-center gap-3">
            <motion.div
              className="w-4 h-4 border-2 border-amber-500 border-t-transparent rounded-full"
              animate={{ rotate: 360 }}
              transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
            />
            <span className="text-sm">正在评估面试结果中，请稍后。</span>
          </div>
        )}

        {showQuestionGenerationNotice && (
          <div className="px-6 py-4 border-t border-slate-200 dark:border-slate-600 bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300 flex items-center gap-3">
            <motion.div
              className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full"
              animate={{ rotate: 360 }}
              transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
            />
            <span className="text-sm">
              {currentQuestionIndex === 0 ? '正在生成首题...' : '下一题生成中，请稍候。'}
            </span>
          </div>
        )}

        {questionGenerationStatus === 'FAILED' && !currentQuestion && (
          <div className="px-6 py-4 border-t border-slate-200 dark:border-slate-600 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-300 text-sm">
            {questionGenerationError || '题目生成失败，请返回重试。'}
          </div>
        )}

        {/* 输入区域 */}
            <div className="border-t border-slate-200 dark:border-slate-600 p-4 bg-slate-50 dark:bg-slate-700/50">
          <div className="flex gap-3">
            <textarea
              value={answer}
              onChange={(e) => onAnswerChange(e.target.value)}
              onKeyDown={handleKeyPress}
              placeholder={
                currentQuestion
                  ? '输入你的回答... (Ctrl/Cmd + Enter 提交)'
                  : isEvaluating
                    ? '正在评估面试结果中，请稍后...'
                  : questionGenerationStatus === 'FAILED'
                    ? '题目生成失败'
                    : '请等待题目生成...'
              }
              className="flex-1 px-4 py-3 border border-slate-300 dark:border-slate-500 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none bg-white dark:bg-slate-800 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500"
              rows={3}
              disabled={isSubmitting || isEvaluating || waitingForNextQuestion || !currentQuestion}
            />
            <div className="flex flex-col gap-2">
              <motion.button
                onClick={onSubmit}
                disabled={!answer.trim() || isSubmitting || isEvaluating || waitingForNextQuestion || !currentQuestion}
                className="px-6 py-3 bg-primary-500 text-white rounded-xl font-medium hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                whileHover={{ scale: isSubmitting || isEvaluating || !answer.trim() || waitingForNextQuestion || !currentQuestion ? 1 : 1.02 }}
                whileTap={{ scale: isSubmitting || isEvaluating || !answer.trim() || waitingForNextQuestion || !currentQuestion ? 1 : 0.98 }}
              >
                {isSubmitting || isEvaluating ? (
                  <>
                    <motion.div
                      className="w-4 h-4 border-2 border-white border-t-transparent rounded-full"
                      animate={{ rotate: 360 }}
                      transition={{ duration: 1, repeat: Infinity, ease: "linear" }}
                    />
                    {isEvaluating ? '评估中' : '提交中'}
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    提交
                  </>
                )}
              </motion.button>
              {!isEvaluating && (
                <motion.button
                  onClick={() => onShowCompleteConfirm(true)}
                  disabled={isSubmitting}
                  className="px-6 py-3 bg-slate-200 dark:bg-slate-600 text-slate-700 dark:text-slate-200 rounded-xl font-medium hover:bg-slate-300 dark:hover:bg-slate-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm"
                  whileHover={{ scale: isSubmitting ? 1 : 1.02 }}
                  whileTap={{ scale: isSubmitting ? 1 : 0.98 }}
                >
                  提前交卷
                </motion.button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// 消息气泡组件
function MessageBubble({ message }: { message: Message }) {
  if (message.type === 'interviewer') {
    return (
      <motion.div
        initial={{ opacity: 0, x: -20 }}
        animate={{ opacity: 1, x: 0 }}
        className="flex items-start gap-3"
      >
          <div
              className="w-8 h-8 bg-primary-100 dark:bg-primary-900/50 rounded-full flex items-center justify-center flex-shrink-0">
              <User className="w-4 h-4 text-primary-600 dark:text-primary-400"/>
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-1">
              <span className="text-sm font-semibold text-slate-700 dark:text-slate-300">面试官</span>
            {message.category && (
                <span
                    className="px-2 py-0.5 bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 text-xs rounded-full">
                {message.category}
              </span>
            )}
          </div>
            <div
                className="bg-slate-100 dark:bg-slate-700 rounded-2xl rounded-tl-none p-4 text-slate-800 dark:text-slate-200 leading-relaxed">
            {message.content}
          </div>
        </div>
      </motion.div>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
      className="flex items-start gap-3 justify-end"
    >
      <div className="flex-1 max-w-[80%]">
        <div className="bg-primary-500 text-white rounded-2xl rounded-tr-none p-4 leading-relaxed">
          {message.content}
        </div>
      </div>
        <div
            className="w-8 h-8 bg-slate-200 dark:bg-slate-600 rounded-full flex items-center justify-center flex-shrink-0">
            <svg className="w-4 h-4 text-slate-600 dark:text-slate-300" viewBox="0 0 24 24" fill="none">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          <circle cx="12" cy="7" r="4" stroke="currentColor" strokeWidth="2" />
        </svg>
      </div>
    </motion.div>
  );
}
