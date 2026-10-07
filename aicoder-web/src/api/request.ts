import axios from 'axios'
import { useUserStore } from '@/stores/userStore'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default request

export interface StreamCallbacks {
  onEvent: (eventType: string, data: string) => void
  onError?: (error: Error) => void
  onComplete?: () => void
}

export async function streamRequest(url: string, body: Record<string, any>, callbacks: StreamCallbacks): Promise<void> {
  const token = localStorage.getItem('token')
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    },
    body: JSON.stringify(body)
  })

  if (!response.ok) {
    const err = new Error(`请求失败: ${response.status}`)
    callbacks.onError?.(err)
    return
  }

  if (!response.body) {
    const err = new Error('不支持流式输出')
    callbacks.onError?.(err)
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = ''
  let dataLines: string[] = []

  const flushEvent = () => {
    if (dataLines.length === 0) return
    const fullData = dataLines.join('\n')
    dataLines = []
    if (fullData && fullData !== '[DONE]') {
      callbacks.onEvent(currentEvent, fullData)
    }
    currentEvent = ''
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('event:')) {
          flushEvent()
          currentEvent = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5))
        } else if (line === '') {
          flushEvent()
        }
      }
    }

    if (buffer.startsWith('data:')) {
      dataLines.push(buffer.slice(5))
    }
    flushEvent()
    callbacks.onComplete?.()
  } catch (e) {
    callbacks.onError?.(e instanceof Error ? e : new Error(String(e)))
  }
}
