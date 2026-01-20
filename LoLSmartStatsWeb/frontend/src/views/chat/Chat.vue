<script setup lang="ts">
import { ref, reactive, nextTick, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import {
  Position, Plus, Document, ChatDotRound, UserFilled, PieChart,
  Edit, Delete, Search, Message as MessageIcon, Clock
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { chatApi } from '@/api/chat'
import { dataApi } from '@/api/data'

// --- 类型定义 ---
interface Message {
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  dataRefs?: any[]
  ts?: string
}

interface ChatSession {
  id: string
  name: string
}

const route = useRoute()
const router = useRouter()

// --- 状态管理 ---
// 1. 聊天相关
const messages = ref<Message[]>([])
const userInput = ref('')
const loading = ref(false)
const showContext = ref(false)
const chatContainer = ref<HTMLElement | null>(null)
const isSidebarOpen = ref(false)

// 2. 会话管理相关 (从 Layout 移入)
const sessions = ref<ChatSession[]>([])
const currentSessionId = ref<string | null>(null)
const editingSessionId = ref<string | null>(null)
const editingSessionName = ref('')
const originalEditingSessionName = ref('')
const editContainer = ref<HTMLElement | null>(null)
const hasInteractedInside = ref(false)

// 3. 用户/权限相关
const isLoggedIn = ref(false)
const guestLimit = 5
const guestCount = ref(0)
const isSendDisabled = computed(() => {
  if (loading.value) return true
  if (!userInput.value.trim()) return true
  if (!isLoggedIn.value && guestCount.value >= guestLimit) return true
  return false
})

// 4. 上下文筛选
const context = reactive({
  tournamentId: '',
  dateRange: [] as string[],
  teamIds: [] as string[],
  patch: ''
})
const options = reactive({
  tournaments: [] as { label: string; value: string }[],
  teams: [] as { label: string; value: string }[],
  patches: ['14.18', '14.19']
})

// 5.切换侧边栏状态
const toggleSidebar = () => {
  isSidebarOpen.value = !isSidebarOpen.value
}

const closeSidebar = () => {
  if (isSidebarOpen.value) {
    isSidebarOpen.value = false
  }
}

// --- 工具函数 ---
// 生成临时sessionId
const generateTempSessionId = () => {
  return 'temp_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
}

// 获取或创建sessionId
const getOrCreateSessionId = () => {
  if (!currentSessionId.value) {
    const tempId = generateTempSessionId()
    currentSessionId.value = tempId
    console.log('Generated temporary sessionId:', tempId)
  }
  return currentSessionId.value
}

// 辅助函数：只提取搜索结果的output
// 辅助函数：只提取搜索结果的output（修正版）
const filterAndExtractContent = (rawData: string): string => {
  if (!rawData || typeof rawData !== 'string') return ''

  // 检查是否是完整的事件行：event: xxx\ndata: {...}
  if (rawData.includes('event:') && rawData.includes('data:')) {
    // 尝试解析为JSON
    try {
      // 提取 data: 后面的部分
      const dataMatch = rawData.match(/data:\s*({.*})/)
      if (dataMatch && dataMatch[1]) {
        const parsed = JSON.parse(dataMatch[1])

        // 只处理 search 步骤的结果
        if (parsed.type === 'step' &&
          parsed.detail &&
          parsed.detail.step === 'search' &&
          parsed.detail.output) {

          // 返回output内容
          const output = parsed.detail.output
          if (typeof output === 'string') {
            // 简单清理：移除多余的空格和空行
            return output
              .replace(/\n+/g, '\n')
              .replace(/\s+/g, ' ')
              .replace(/\n\s*\n/g, '\n\n')
              .trim()
          }
        }
      }
    } catch (e) {
      console.log('解析错误:', e, '原始数据:', rawData)
    }
  }

  // 单独处理 data: 开头的行
  if (rawData.startsWith('data:')) {
    const content = rawData.substring(5).trim()
    if (!content) return ''

    try {
      const parsed = JSON.parse(content)

      // 只处理 search 步骤的结果
      if (parsed.type === 'step' &&
        parsed.detail &&
        parsed.detail.step === 'search' &&
        parsed.detail.output) {

        // 返回output内容
        const output = parsed.detail.output
        if (typeof output === 'string') {
          return output
            .replace(/\n+/g, '\n')
            .replace(/\s+/g, ' ')
            .replace(/\n\s*\n/g, '\n\n')
            .trim()
        }
      }
    } catch (e) {
      // 不是JSON，不显示
    }
  }

  return ''
}
// 登出函数
const handleLogout = () => {
  ElMessageBox.confirm(
    '确定要退出登录吗？',
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    localStorage.removeItem('accessToken')
    isLoggedIn.value = false
    sessions.value = []
    currentSessionId.value = null
    messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。' }]
    router.replace({ query: {} })
    ElMessage.success('已退出登录')
  })
}

// --- 逻辑方法：会话管理 (CRUD) ---

// 加载会话列表
const loadSessions = async () => {
  if (isLoggedIn.value) {
    try {
      const res: any = await chatApi.listSessions({ page: 1, pageSize: 50, status: 'active' })
      const items = Array.isArray(res?.items) ? res.items : []
      sessions.value = items.map((it: any) => ({
        id: it.sessionId,
        name: it.title || '新对话'
      }))
      localStorage.setItem('chat_sessions', JSON.stringify(sessions.value))
      return
    } catch (e) {
      console.error('加载会话列表失败，使用本地缓存', e)
    }
  }

  const saved = localStorage.getItem('chat_sessions')
  if (saved) {
    try {
      sessions.value = JSON.parse(saved)
    } catch (e) {
      sessions.value = []
    }
  }
}

// 保存会话列表
const saveSessions = () => {
  localStorage.setItem('chat_sessions', JSON.stringify(sessions.value))
}

// 开启重命名
const startEditingSession = (sessionId: string, currentName: string) => {
  editingSessionId.value = sessionId
  editingSessionName.value = currentName
  originalEditingSessionName.value = currentName
  hasInteractedInside.value = false
}

// 保存重命名
const saveSessionName = () => {
  if (!editingSessionId.value || !editingSessionName.value.trim()) {
    editingSessionId.value = null
    return
  }
  const newName = editingSessionName.value.trim()
  const duplicate = sessions.value.some(s => s.name === newName && s.id !== editingSessionId.value)
  if (duplicate) {
    ElMessage.error('名称已存在')
    editingSessionName.value = originalEditingSessionName.value
    return
  }
  const session = sessions.value.find(s => s.id === editingSessionId.value)
  if (session) {
    session.name = newName
    saveSessions()
    ElMessage.success('重命名成功')
  }
  cancelEditing()
}

// 取消编辑
const cancelEditing = () => {
  editingSessionId.value = null
  editingSessionName.value = ''
  hasInteractedInside.value = false
}

// 删除会话
const deleteSession = async (sessionId: string) => {
  try {
    await ElMessageBox.confirm('确定要删除这个会话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    const index = sessions.value.findIndex(s => s.id === sessionId)
    if (index !== -1) {
      sessions.value.splice(index, 1)
      saveSessions()
      if (currentSessionId.value === sessionId) {
        if (sessions.value.length > 0) {
          router.push({ query: { sessionId: sessions.value[0].id } })
        } else {
          currentSessionId.value = null
          messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。' }]
          router.replace({ query: {} })
        }
      }
      ElMessage.success('删除成功')
    }
  } catch { }
}

// 全局点击处理
const handleDocumentClick = (e: MouseEvent) => {
  if (!editingSessionId.value) return
  const elVal = editContainer.value as unknown as HTMLElement | null
  if (!elVal) return
  const path = (e as any).composedPath?.() as EventTarget[] | undefined
  const isInside = path ? path.includes(elVal) : elVal.contains(e.target as Node)

  if (isInside) {
    hasInteractedInside.value = true
    return
  }
  cancelEditing()
}

// --- 逻辑方法：聊天交互 ---

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

// 加载特定会话的历史消息
const fetchHistory = async () => {
  if (!currentSessionId.value) return

  messages.value = [{ role: 'assistant', content: '正在加载历史记录...' }]
  try {
    const res: any = await chatApi.getHistory(currentSessionId.value, 1, 50)
    const items = Array.isArray(res?.items) ? res.items : []
    if (items.length > 0) {
      messages.value = items
        .slice()
        .reverse()
        .map((item: any) => ({
          role: item.role,
          content: item.content,
          ts: item.createdAt,
          dataRefs: item.reportFileId
            ? [
              {
                type: 'report',
                fileId: item.reportFileId,
                downloadUrl: `/api/v1/chat/files/${item.reportFileId}${currentSessionId.value ? `?sessionId=${currentSessionId.value}` : ''
                  }`
              }
            ]
            : []
        }))
    } else {
      messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。' }]
    }
    scrollToBottom()
  } catch (err) {
    console.error(err)
    messages.value = [{ role: 'assistant', content: '加载历史记录失败。' }]
  }
}

// 创建新会话
const handleNewSession = async () => {
  if (!isLoggedIn.value) {
    messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。' }]
    currentSessionId.value = generateTempSessionId()
    router.replace({ query: {} })
    return
  }

  try {
    const res: any = await chatApi.createSession()
    const newId = res?.sessionId
    const newSessionName = res?.title || `新的对话 ${sessions.value.length + 1}`
    if (newId) {
      sessions.value.unshift({ id: newId, name: newSessionName })
      saveSessions()
      router.push({ query: { sessionId: newId } })
    }
  } catch (e) {
    ElMessage.error('创建会话失败')
  }
}

// 发送消息 - 修正版本
const handleSend = async () => {
  if (!userInput.value.trim() || loading.value) return

  // 未登录限制
  if (!isLoggedIn.value) {
    if (guestCount.value >= guestLimit) {
      ElMessage.warning('请登录以继续使用完整功能。')
      return
    }
    guestCount.value++
    localStorage.setItem('guest_chat_count', guestCount.value.toString())
  }

  const content = userInput.value
  userInput.value = ''

  messages.value.push({ role: 'user', content })
  scrollToBottom()

  const aiMsgIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', loading: true })
  loading.value = true

  const ctrl = new AbortController()
  const sessionId = getOrCreateSessionId()

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept': 'text/event-stream'
  }

  console.log('=== SSE Request Details ===')
  console.log('URL:', '/api/v1/chat/stream')
  console.log('Method: POST')
  console.log('Headers:', headers)
  console.log('isLoggedIn:', isLoggedIn.value)
  console.log('Session ID:', sessionId)
  console.log('Request Body:', {
    sessionId: sessionId,
    message: content,
    mode: 'data+analysis',
    context: {
      tournamentId: context.tournamentId,
      dateRange: context.dateRange.length > 0 ? { from: context.dateRange[0], to: context.dateRange[1] } : undefined,
      teamIds: context.teamIds.length > 0 ? context.teamIds : undefined,
      patch: context.patch || undefined
    }
  })

  try {
    await fetchEventSource('/api/v1/chat/stream', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        sessionId: sessionId,
        message: content,
        mode: 'data+analysis',
        context: {
          tournamentId: context.tournamentId,
          dateRange: context.dateRange.length > 0 ? { from: context.dateRange[0], to: context.dateRange[1] } : undefined,
          teamIds: context.teamIds.length > 0 ? context.teamIds : undefined,
          patch: context.patch || undefined
        }
      }),
      signal: ctrl.signal,
      onopen(response) {
        console.log('=== SSE Connection Opened ===')
        console.log('Status:', response.status)
        console.log('Status Text:', response.statusText)

        console.log('Response Headers:')
        response.headers.forEach((value, key) => {
          console.log(`  ${key}: ${value}`)
        })

        if (response.ok) {
          console.log('Connection successful')
          return Promise.resolve()
        } else {
          console.error('Connection failed:', response.status, response.statusText)
          return response.text().then(text => {
            console.error('Error response body:', text || '(empty)')
            throw new Error(`HTTP ${response.status}: ${response.statusText} - ${text}`)
          })
        }
      },
      onmessage(msg) {
        console.log('SSE Message event:', msg.event)
        console.log('SSE Message data:', msg.data)

        if (msg.data) {
          // 使用新的过滤函数提取search步骤的output
          const extractedContent = filterAndExtractContent(msg.data)
          if (extractedContent && extractedContent.trim().length > 0) {
            // 直接替换内容（只显示search结果）
            messages.value[aiMsgIndex].content = extractedContent
            scrollToBottom()
          }
        }
      },
      onerror(err) {
        console.error('SSE onerror:', err)
        throw err
      },
      onclose() {
        console.log('SSE Connection closed')
        loading.value = false
        messages.value[aiMsgIndex].loading = false

        // 如果没有提取到任何内容，显示简洁提示
        if (messages.value[aiMsgIndex].content.trim().length === 0) {
          messages.value[aiMsgIndex].content = '本次查询没有获取到相关比赛数据。'
          scrollToBottom()
        } else {
          // 清理内容，移除多余空行
          messages.value[aiMsgIndex].content = messages.value[aiMsgIndex].content
            .replace(/\n{3,}/g, '\n\n')
            .trim()
        }
      }
    })
  } catch (err) {
    console.error('SSE请求失败:', err)
    messages.value[aiMsgIndex].content += '\n[请求出错: ' + (err instanceof Error ? err.message : '未知错误') + ']'
    loading.value = false
    messages.value[aiMsgIndex].loading = false

    if (err instanceof Error) {
      if (err.message.includes('403')) {
        ElMessage.error('访问被拒绝 (403)。如果已登录，请尝试退出重新登录。')
        if (isLoggedIn.value) {
          setTimeout(() => {
            ElMessageBox.confirm(
              '认证可能已过期，是否退出登录？',
              '提示',
              {
                confirmButtonText: '退出',
                cancelButtonText: '取消',
                type: 'warning',
              }
            ).then(() => {
              localStorage.removeItem('accessToken')
              isLoggedIn.value = false
              window.location.reload()
            })
          }, 1000)
        }
      } else if (err.message.includes('400')) {
        ElMessage.error('请求参数错误，请刷新页面重试')
      } else {
        ElMessage.error('请求失败: ' + err.message)
      }
    } else {
      ElMessage.error('请求失败，请稍后重试')
    }
  }
}

// 加载上下文选项
const loadContextOptions = async () => {
  try {
    const res: any = await dataApi.getOptions({}, ['tournaments', 'teams'])
    const tournaments = Array.isArray(res?.tournaments) ? res.tournaments : []
    const teams = Array.isArray(res?.teams) ? res.teams : []

    options.tournaments = tournaments.map((t: any) => ({
      label: t,
      value: t
    }))
    options.teams = teams.map((t: any) => ({
      label: t.shortName || t.name,
      value: String(t.id)
    }))

    if (!context.tournamentId && options.tournaments.length > 0) {
      context.tournamentId = options.tournaments[0].value
    }
  } catch (e) {
    console.error('加载上下文筛选项失败', e)
    options.tournaments = [
      { label: 'S14 全球总决赛', value: 's14_worlds' },
      { label: 'LPL 夏季赛', value: 'lpl_summer_2024' }
    ]
    options.teams = [
      { label: 'T1', value: '1' },
      { label: 'GEN', value: '2' },
      { label: 'JDG', value: '3' },
      { label: 'BLG', value: '4' }
    ]
    context.tournamentId = 's14_worlds'
  }
}

// --- 生命周期与监听 ---

watch(() => route.query.sessionId, (newId) => {
  currentSessionId.value = (newId as string) || null
  if (isLoggedIn.value && currentSessionId.value) {
    fetchHistory()
  } else if (!currentSessionId.value) {
    messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。' }]
  }
}, { immediate: true })

onMounted(async () => {
  // 检查登录状态
  const token = localStorage.getItem('accessToken')
  if (token && token.trim()) {
    try {
      const response = await fetch('/api/v1/users/me', {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Accept': 'application/json'
        }
      })
      isLoggedIn.value = response.ok
      if (!isLoggedIn.value) {
        localStorage.removeItem('accessToken')
        ElMessage.warning('登录状态已过期，请重新登录')
      }
    } catch (e) {
      isLoggedIn.value = false
      localStorage.removeItem('accessToken')
    }
  } else {
    isLoggedIn.value = false
  }

  if (!isLoggedIn.value) {
    const count = localStorage.getItem('guest_chat_count')
    guestCount.value = count ? parseInt(count) : 0
  }

  await loadSessions()
  await loadContextOptions()
  document.addEventListener('pointerdown', handleDocumentClick, true)

  if (!currentSessionId.value) {
    currentSessionId.value = generateTempSessionId()
    console.log('Initial temporary sessionId:', currentSessionId.value)
  }

  console.log('=== Initializing Chat Component ===')
  console.log('Login status:', isLoggedIn.value)
  console.log('Current session ID:', currentSessionId.value)
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', handleDocumentClick, true)
})
</script>

<template>
  <div class="flex h-full w-full bg-white">
    <!-- 1. 左侧：聊天主界面 -->
    <div class="flex-1 flex flex-col h-full relative overflow-hidden transition-all duration-300" @click="closeSidebar">
      <!-- Header -->
      <div
        class="flex-none bg-white border-b border-gray-100 px-8 py-5 flex items-center justify-between shrink-0 z-10">
        <div>
          <h2 class="text-2xl font-bold text-gray-800 tracking-tight flex items-center gap-3">
            <span class="w-1.5 h-7 bg-blue-600 rounded-full inline-block shadow-sm shadow-blue-200"></span>
            AI 分析会话
          </h2>
          <p class="text-gray-500 text-sm mt-1.5 ml-5 flex items-center gap-2">
            DeepSeek V3 驱动 · 沉浸式赛事数据智能问答
            <span class="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>
          </p>
        </div>

        <!-- 右侧按钮区域 -->
        <div class="flex items-center gap-4">
          <span class="hidden lg:block text-xs font-mono text-gray-300 mr-2">Model: deepseek-chat-v3</span>

          <!-- 登出按钮（如果已登录） -->
          <el-button v-if="isLoggedIn" type="warning" size="small" @click="handleLogout" class="!rounded-lg">
            退出登录
          </el-button>

          <el-button :type="isSidebarOpen ? 'primary' : 'default'" :icon="Clock" size="large"
            class="!rounded-xl !px-5 transition-all duration-300 shadow-sm hover:shadow-md border-gray-200"
            :class="{ '!bg-blue-600 !border-blue-600': isSidebarOpen }" @click.stop="toggleSidebar">
            {{ isSidebarOpen ? '收起会话' : '历史会话' }}
          </el-button>
        </div>
      </div>

      <!-- Messages Area -->
      <div ref="chatContainer" class="flex-1 overflow-y-auto p-4 md:p-8 space-y-6 bg-white scroll-smooth">
        <div v-for="(msg, idx) in messages" :key="idx" class="flex w-full animate-fade-in-up"
          :class="msg.role === 'user' ? 'justify-end' : 'justify-start'">
          <div class="flex max-w-[90%] md:max-w-[75%] gap-3">
            <div v-if="msg.role === 'assistant'"
              class="w-8 h-8 rounded-full bg-blue-50 border border-blue-100 flex items-center justify-center shrink-0 text-blue-600 mt-1">
              <el-icon>
                <ChatDotRound />
              </el-icon>
            </div>
            <div class="flex flex-col">
              <div class="p-4 rounded-2xl text-sm leading-7 shadow-sm border"
                :class="[msg.role === 'user' ? 'bg-blue-600 border-blue-600 text-white rounded-br-none' : 'bg-gray-50 border-gray-100 text-gray-800 rounded-bl-none']">
                <div class="whitespace-pre-wrap break-words">{{ msg.content }}</div>
                <div v-if="msg.loading" class="mt-2 flex space-x-1">
                  <div class="w-2 h-2 bg-current rounded-full animate-bounce" style="animation-delay: 0s"></div>
                  <div class="w-2 h-2 bg-current rounded-full animate-bounce" style="animation-delay: 0.2s"></div>
                  <div class="w-2 h-2 bg-current rounded-full animate-bounce" style="animation-delay: 0.4s"></div>
                </div>
              </div>
              <div v-if="msg.dataRefs && msg.dataRefs.length" class="mt-3 grid grid-cols-1 md:grid-cols-2 gap-3">
                <div v-for="(data, dIdx) in msg.dataRefs" :key="dIdx"
                  class="bg-white rounded-lg border border-gray-200 p-3 shadow-sm hover:shadow-md transition-shadow cursor-pointer"
                  @click="data.type === 'report' && data.downloadUrl ? window.open(data.downloadUrl, '_blank') : null">
                  <div class="text-xs font-bold text-gray-600 mb-2 flex items-center">
                    <el-icon class="mr-1 text-blue-500">
                      <PieChart />
                    </el-icon>
                    {{ data.title || (data.type === 'report' ? '分析报告' : '数据图表') }}
                  </div>
                  <div
                    class="h-24 bg-gray-50 rounded flex items-center justify-center text-gray-400 text-xs border border-dashed border-gray-200">
                    {{ data.type === 'report' ? '点击下载报告文件' : '图表预览' }}
                  </div>
                </div>
              </div>
            </div>
            <div v-if="msg.role === 'user'"
              class="w-8 h-8 rounded-full bg-gray-100 border border-gray-200 flex items-center justify-center shrink-0 text-gray-600 mt-1">
              <el-icon>
                <UserFilled />
              </el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- Input Area -->
      <div class="p-6 bg-white shrink-0">
        <div class="max-w-4xl mx-auto">
          <div class="flex items-center justify-between mb-3 px-1">
            <el-button size="small" :type="showContext ? 'primary' : 'info'" :plain="!showContext" round
              @click.stop="showContext = !showContext">
              <el-icon class="mr-1">
                <Document />
              </el-icon> 上下文控制
            </el-button>
            <div v-if="showContext" class="flex items-center space-x-2 animate-fade-in" @click.stop>
              <el-select v-model="context.tournamentId" placeholder="赛事" size="small" class="!w-32">
                <el-option v-for="opt in options.tournaments" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-select v-model="context.teamIds" multiple collapse-tags placeholder="战队" size="small" class="!w-40">
                <el-option v-for="opt in options.teams" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </div>
          </div>
          <div class="relative group" @click.stop>
            <el-input v-model="userInput" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }"
              placeholder="问问我关于 S14 决赛 T1 的表现..." class="!text-base custom-textarea"
              @keydown.enter.exact.prevent="handleSend" />
            <el-button type="primary" circle
              class="absolute bottom-3 right-3 shadow-lg transition-transform hover:scale-105"
              :disabled="isSendDisabled" @click="handleSend">
              <el-icon>
                <Position />
              </el-icon>
            </el-button>
          </div>
          <div class="text-center mt-2 text-xs text-gray-400 select-none">DeepSeek V3 Generated Content. Check important
            info.</div>
        </div>
      </div>
    </div>

    <!-- 2. 右侧：会话列表侧边栏 -->
    <aside
      class="border-gray-200 bg-gray-50 flex flex-col shrink-0 transition-all duration-300 ease-in-out overflow-hidden h-full border-l"
      :class="isSidebarOpen ? 'w-[300px] opacity-100' : 'w-0 opacity-0 border-l-0'" @click.stop>
      <div class="w-[300px] flex flex-col h-full">
        <div class="h-16 flex items-center justify-between px-4 border-b border-gray-200/50">
          <span class="font-medium text-gray-600">历史会话</span>
          <el-tooltip content="新建会话" placement="bottom">
            <el-button :icon="Plus" circle size="small" @click="handleNewSession" />
          </el-tooltip>
        </div>

        <div class="p-3">
          <el-input prefix-icon="Search" placeholder="搜索历史..." size="small" class="w-full" />
        </div>

        <div class="flex-1 overflow-y-auto px-2 space-y-1 py-2">
          <template v-if="isLoggedIn && sessions.length">
            <div v-for="session in sessions" :key="session.id" class="group relative">
              <div v-if="editingSessionId !== session.id" @click="router.push({ query: { sessionId: session.id } })"
                class="flex items-center px-3 py-3 rounded-lg cursor-pointer transition-all duration-200 border border-transparent"
                :class="currentSessionId === session.id
                  ? 'bg-white border-gray-200 shadow-sm text-blue-600'
                  : 'text-gray-600 hover:bg-gray-200/50'">
                <el-icon class="mr-3 text-lg"
                  :class="currentSessionId === session.id ? 'text-blue-500' : 'text-gray-400'">
                  <MessageIcon />
                </el-icon>
                <span class="truncate text-sm font-medium flex-1">{{ session.name }}</span>
                <div class="hidden group-hover:flex items-center bg-inherit pl-2"
                  :class="currentSessionId === session.id ? 'bg-white' : 'bg-gray-200/50'">
                  <el-icon class="text-gray-400 hover:text-blue-500 cursor-pointer mr-2" size="14"
                    @click.stop="startEditingSession(session.id, session.name)">
                    <Edit />
                  </el-icon>
                  <el-icon class="text-gray-400 hover:text-red-500 cursor-pointer" size="14"
                    @click.stop="deleteSession(session.id)">
                    <Delete />
                  </el-icon>
                </div>
              </div>
              <div v-else ref="editContainer"
                class="flex items-center px-2 py-2 bg-white rounded-lg border border-blue-200 shadow-sm ring-2 ring-blue-50">
                <el-input v-model="editingSessionName" size="small" ref="nameInput" @keyup.enter="saveSessionName"
                  @keyup.esc="cancelEditing" />
                <el-button type="primary" link size="small" class="ml-1" @click="saveSessionName">OK</el-button>
              </div>
            </div>
          </template>

          <div v-else-if="isLoggedIn" class="text-center py-10 text-gray-400 text-sm">暂无历史会话</div>

          <div v-else class="p-4 bg-blue-50 rounded-lg border border-blue-100 m-2">
            <div class="text-blue-800 font-medium text-sm mb-1">访客模式</div>
            <p class="text-xs text-blue-600">登录后可保存多条对话历史。</p>
            <el-button type="primary" size="small" class="w-full mt-3" @click="router.push('/login')">去登录</el-button>
          </div>
        </div>
      </div>
    </aside>
  </div>
</template>

<style scoped>
/* 针对 Element Plus Textarea 的深度定制 */
:deep(.custom-textarea .el-textarea__inner) {
  padding: 12px 16px;
  border-radius: 12px;
  background-color: #f9fafb;
  border: 1px solid #e5e7eb;
  box-shadow: none !important;
  transition: all 0.2s;
}

:deep(.custom-textarea .el-textarea__inner:focus) {
  background-color: #ffffff;
  border-color: #3b82f6;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.1) !important;
}

/* 简单的淡入动画 */
.animate-fade-in-up {
  animation: fadeInUp 0.3s ease-out forwards;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>