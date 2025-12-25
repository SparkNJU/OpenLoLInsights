import request from '@/utils/request'

export const authApi = {
  // 1.1 注册
  register(data: any) {
    return request.post('/auth/register', data)
  },

  // 1.2 登录
  login(data: { email: string; password: string }) {
    return request.post('/auth/login', data)
  },

  // 1.3 刷新 Token (通常在拦截器中自动处理，但也暴露出来)
  refreshToken(token: string) {
    return request.post('/auth/refresh', { refreshToken: token })
  },

  // 1.4 登出
  logout(refreshToken: string) {
    return request.post('/auth/logout', { refreshToken })
  },

  // 1.5 获取当前用户信息
  getUserInfo() {
    return request.get('/users/me')
  }
}