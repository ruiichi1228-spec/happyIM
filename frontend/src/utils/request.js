import axios from 'axios'
import router from '@/router'
import { API_BASE_URL } from '@/config/index'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000
})

// 请求拦截器：自动带 access_token
request.interceptors.request.use(
  (config) => {
    const url = config.url || ''

    // 这些接口不需要 token
    if (url.includes('/auth/send-code') ||
        url.includes('/auth/register') ||
        url.includes('/auth/login') ||
        url.includes('/auth/refresh-token') ||
        url.includes('/auth/reset-password')) {
      return config
    }

    const token = localStorage.getItem('access_token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

let refreshPromise = null

// 响应拦截器：自动刷新 token
request.interceptors.response.use(
  (response) => {
    return response.data
  },
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = localStorage.getItem('refresh_token')
      if (refreshToken) {
        originalRequest._retry = true
        try {
          // 防并发刷新：多个 401 共享同一个 refresh 请求
          if (!refreshPromise) {
            refreshPromise = axios.post(
              `${API_BASE_URL}/auth/refresh-token`,
              { refreshToken },
              { timeout: 5000 }
            ).finally(() => { refreshPromise = null })
          }
          const res = await refreshPromise
          const data = res.data?.data || res.data
          if (data?.accessToken) {
            localStorage.setItem('access_token', data.accessToken)
            localStorage.setItem('refresh_token', data.refreshToken)
            originalRequest.headers['Authorization'] = `Bearer ${data.accessToken}`
            return request(originalRequest)
          }
        } catch (refreshError) {
          refreshPromise = null
          // 只有 refresh token 真的过期(401)才登出，503等临时错误不踢
          if (refreshError.response?.status === 401) {
            clearAuth()
            router.push('/login')
          }
          return Promise.reject(refreshError)
        }
      }
    }

    if (error.response?.status === 401) {
      clearAuth()
      router.push('/login')
    }

    return Promise.reject(error)
  }
)

export function clearAuth() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem('user_info')
}

export function saveAuth(data) {
  localStorage.setItem('access_token', data.accessToken)
  localStorage.setItem('refresh_token', data.refreshToken)
  localStorage.setItem('user_info', JSON.stringify({
    userId: data.userId,
    username: data.username,
    email: data.email,
    nickname: data.nickname,
    avatarUrl: data.avatarUrl
  }))
}

export default request
