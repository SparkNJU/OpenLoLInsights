import axios from 'axios';
import { ElMessage } from 'element-plus';

// 创建 axios 实例
const service = axios.create({
  baseURL: '/api/v1', // 根据 API 文档约定
  timeout: 15000,     // 请求超时时间
});

// Request请求拦截器--保证用户登录才可以请求
service.interceptors.request.use(
  (config) => {
    // 如果有 token，带上
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response响应拦截器
service.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res && typeof res === 'object' && 'ok' in res && ('data' in res || 'error' in res)) {
      if (res.ok) {
        return res.data;
      }
      const msg = res.error?.message || '请求失败';
      ElMessage.error(msg);
      return Promise.reject(new Error(msg));
    }
    return res;
  },
  (error) => {
    const status = error.response?.status;
    const msg = error.response?.data?.error?.message || error.message || '请求失败';

    if (status === 401) {
      ElMessage.error('登录已过期，请重新登录');
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
    } else {
      ElMessage.error(msg);
    }
    return Promise.reject(error);
  }
);

export default service;
