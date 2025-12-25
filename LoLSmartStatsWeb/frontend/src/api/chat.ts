import request from '@/utils/request'

export const chatApi = {
  // 2.1 创建会话
  createSession(title?: string) {
    return request.post('/chat/sessions', { title })
  },

  // 2.4 获取历史消息
  getHistory(sessionId: string, page: number = 1, pageSize: number = 50) {
    return request.post('/chat/history', {
      sessionId,
      page,
      pageSize
    })
  }
}