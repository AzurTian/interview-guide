import { getErrorMessage, request } from './request';
import type {
  CreateInterviewRequest,
  CurrentQuestionResponse,
  InterviewReport,
  InterviewQuestionStreamEvent,
  InterviewSession,
  SubmitAnswerRequest,
  SubmitAnswerResponse
} from '../types/interview';

const API_BASE_URL = import.meta.env.PROD ? '' : 'http://localhost:8080';

export const interviewApi = {
  /**
   * 创建面试会话
   */
  async createSession(req: CreateInterviewRequest): Promise<InterviewSession> {
    return request.post<InterviewSession>('/api/interview/sessions', req);
  },

  /**
   * 获取会话信息
   */
  async getSession(sessionId: string): Promise<InterviewSession> {
    return request.get<InterviewSession>(`/api/interview/sessions/${sessionId}`);
  },

  /**
   * 获取当前问题
   */
  async getCurrentQuestion(sessionId: string): Promise<CurrentQuestionResponse> {
    return request.get<CurrentQuestionResponse>(`/api/interview/sessions/${sessionId}/question`);
  },

  /**
   * 提交答案
   */
  async submitAnswer(req: SubmitAnswerRequest): Promise<SubmitAnswerResponse> {
    return request.post<SubmitAnswerResponse>(
      `/api/interview/sessions/${req.sessionId}/answers`,
      { questionIndex: req.questionIndex, answer: req.answer },
    );
  },

  /**
   * 获取面试报告
   */
  async getReport(sessionId: string): Promise<InterviewReport> {
    return request.get<InterviewReport>(`/api/interview/sessions/${sessionId}/report`, {
      timeout: 180000, // 3分钟超时，AI评估需要时间
    });
  },

  /**
   * 查找未完成的面试会话
   */
  async findUnfinishedSession(resumeId: number): Promise<InterviewSession | null> {
    try {
      return await request.get<InterviewSession>(`/api/interview/sessions/unfinished/${resumeId}`);
    } catch {
      // 如果没有未完成的会话，返回null
      return null;
    }
  },

  /**
   * 暂存答案（不进入下一题）
   */
  async saveAnswer(req: SubmitAnswerRequest): Promise<void> {
    return request.put<void>(
      `/api/interview/sessions/${req.sessionId}/answers`,
      { questionIndex: req.questionIndex, answer: req.answer }
    );
  },

  /**
   * 提前交卷
   */
  async completeInterview(sessionId: string): Promise<void> {
    return request.post<void>(`/api/interview/sessions/${sessionId}/complete`);
  },

  /**
   * 订阅题目流
   */
  subscribeQuestionStream(
    sessionId: string,
    onEvent: (event: InterviewQuestionStreamEvent) => void,
    onComplete: () => void,
    onError: (error: Error) => void
  ): () => void {
    const controller = new AbortController();

    void (async () => {
      try {
        const response = await fetch(
          `${API_BASE_URL}/api/interview/sessions/${sessionId}/questions/stream`,
          {
            method: 'GET',
            headers: { Accept: 'text/event-stream' },
            signal: controller.signal,
          }
        );

        if (!response.ok) {
          throw new Error(`请求失败 (${response.status})`);
        }

        const reader = response.body?.getReader();
        if (!reader) {
          throw new Error('无法获取响应流');
        }

        const decoder = new TextDecoder();
        let buffer = '';

        const extractEventData = (eventBlock: string): string | null => {
          if (!eventBlock.trim()) {
            return null;
          }

          const dataLines = eventBlock
            .split('\n')
            .filter((line) => line.startsWith('data:'))
            .map((line) => line.substring(5));

          if (dataLines.length === 0) {
            return null;
          }

          return dataLines.join('');
        };

        while (true) {
          const { done, value } = await reader.read();
          if (done) {
            if (buffer.trim()) {
              const data = extractEventData(buffer);
              if (data) {
                onEvent(JSON.parse(data) as InterviewQuestionStreamEvent);
              }
            }
            onComplete();
            break;
          }

          buffer += decoder.decode(value, { stream: true });
          let separatorIndex = buffer.indexOf('\n\n');
          while (separatorIndex !== -1) {
            const eventBlock = buffer.substring(0, separatorIndex);
            buffer = buffer.substring(separatorIndex + 2);

            const data = extractEventData(eventBlock);
            if (data) {
              onEvent(JSON.parse(data) as InterviewQuestionStreamEvent);
            }

            separatorIndex = buffer.indexOf('\n\n');
          }
        }
      } catch (error) {
        if (controller.signal.aborted) {
          return;
        }
        onError(new Error(getErrorMessage(error)));
      }
    })();

    return () => controller.abort();
  },
};
