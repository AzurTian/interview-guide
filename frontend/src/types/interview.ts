// 面试相关类型定义
export type QuestionGenerationStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface InterviewSession {
  sessionId: string;
  resumeText: string;
  totalQuestions: number;
  generatedQuestionCount: number;
  currentQuestionIndex: number;
  questions: InterviewQuestion[];
  status: 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'EVALUATED';
  questionGenerationStatus: QuestionGenerationStatus;
  questionGenerationError: string | null;
}

export interface InterviewQuestion {
  questionIndex: number;
  question: string;
  type: QuestionType;
  category: string;
  userAnswer: string | null;
  score: number | null;
  feedback: string | null;
  referenceAnswer?: string | null;
  keyPoints?: string[] | null;
  isFollowUp?: boolean;
  parentQuestionIndex?: number | null;
}

export type QuestionType = 
  | 'PROJECT' 
  | 'JAVA_BASIC' 
  | 'JAVA_COLLECTION' 
  | 'JAVA_CONCURRENT' 
  | 'MYSQL' 
  | 'REDIS' 
  | 'SPRING' 
  | 'SPRING_BOOT';

export interface CreateInterviewRequest {
  resumeText: string;
  questionCount: number;
  resumeId?: number;
  forceCreate?: boolean;  // 是否强制创建新会话（忽略未完成的会话）
}

export interface SubmitAnswerRequest {
  sessionId: string;
  questionIndex: number;
  answer: string;
}

export interface SubmitAnswerResponse {
  hasNextQuestion: boolean;
  nextQuestion: InterviewQuestion | null;
  waitingForNextQuestion: boolean;
  completed: boolean;
  currentIndex: number;
  totalQuestions: number;
}

export interface CurrentQuestionResponse {
  completed: boolean;
  waitingForNextQuestion?: boolean;
  question?: InterviewQuestion;
  message?: string;
}

export interface InterviewQuestionStreamEvent {
  type: 'snapshot' | 'question' | 'completed' | 'error';
  session?: InterviewSession | null;
  question?: InterviewQuestion | null;
  generatedQuestionCount?: number | null;
  totalQuestions?: number | null;
  questionGenerationStatus?: QuestionGenerationStatus | null;
  error?: string | null;
}

export interface InterviewReport {
  sessionId: string;
  totalQuestions: number;
  overallScore: number;
  categoryScores: CategoryScore[];
  questionDetails: QuestionEvaluation[];
  overallFeedback: string;
  strengths: string[];
  improvements: string[];
  referenceAnswers: ReferenceAnswer[];
}

export interface CategoryScore {
  category: string;
  score: number;
  questionCount: number;
}

export interface QuestionEvaluation {
  questionIndex: number;
  question: string;
  category: string;
  userAnswer: string;
  score: number;
  feedback: string;
}

export interface ReferenceAnswer {
  questionIndex: number;
  question: string;
  referenceAnswer: string;
  keyPoints: string[];
}
