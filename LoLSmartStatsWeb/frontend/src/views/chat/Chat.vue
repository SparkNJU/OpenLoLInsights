<script setup lang="ts">
import { ref, reactive, nextTick, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { Position, VideoPlay, RefreshRight, Document, ChatDotRound, UserFilled, PieChart } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
// 这里需要引入 ECharts 组件（稍后封装）

interface Message {
  role: 'user' | 'assistant' // 消息角色，用户或助手
  content: string // 消息内容
  loading?: boolean // AI是否在加载
  dataRefs?: any[] // 关联的数据图表 ID
  ts?: string // 时间戳
}

const route = useRoute()
const router = useRouter()

const messages = ref<Message[]>([{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。你可以问我关于比赛胜负、选手数据或战队对比的问题。' }])
const userInput = ref('')
const loading = ref(false)
const showContext = ref(false)
const isLoggedIn = ref(false)
const guestLimit = 5
const guestCount = ref(0)
const currentSessionId = ref<string | null>(null)

// 计算是否禁用发送按钮
const isSendDisabled = computed(() => {
  // AI正在回复，禁止使用发送按钮
  if (loading.value) return true
  // 输入为空
  if (!userInput.value.trim()) return true
  // 未登录且达到上限
  if (!isLoggedIn.value && guestCount.value >= guestLimit) return true
  return false
})

// 筛选上下文
const context = reactive({
  tournamentId: '2024-worlds',
  dateRange: [] as string[],
  teamIds: [] as string[],
  patch: ''
})

// 模拟选项数据
const options = reactive({
  tournaments: [{ label: 'Worlds 2024', value: '2024-worlds' }],
  teams: [{ label: 'T1', value: 'T1' }, { label: 'BLG', value: 'BLG' }],
  patches: ['14.18', '14.19']
})

const chatContainer = ref<HTMLElement | null>(null)

// 加载历史消息
const fetchHistory = async () => {
  if (!currentSessionId.value) return
  
  // 重置消息
  messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。你可以问我关于比赛胜负、选手数据或战队对比的问题。' }]

  try {
    const res: any = await request.post('/chat/history', {
      sessionId: currentSessionId.value,
      page: 1,
      pageSize: 50
    })
    
    if (res && res.items && Array.isArray(res.items) && res.items.length > 0) {
      // 转换历史消息格式
      const history = res.items.reverse().map((item: any) => ({
        role: item.role,
        content: item.content,
        ts: item.ts,
        dataRefs: item.dataRefs
      }))
      messages.value = history
      scrollToBottom()
    }
  } catch (err) {
    console.error('Failed to fetch history:', err)
  }
}

// 监听路由参数变化，切换会话
watch(() => route.query.sessionId, (newId) => {
  if (newId) {
    currentSessionId.value = newId as string
    localStorage.setItem('currentSessionId', newId as string)
    if (isLoggedIn.value) {
      fetchHistory()
    } else {
       // 未登录用户，如果是切换到不同会话，也重置消息（虽然未登录通常不持久化多会话，但为了UI一致性）
       messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。你可以问我关于比赛胜负、选手数据或战队对比的问题。' }]
    }
  }
}, { immediate: true })

onMounted(() => {
  const token = localStorage.getItem('accessToken')
  isLoggedIn.value = !!token
  
  if (!isLoggedIn.value) {
    const count = localStorage.getItem('guest_chat_count')
    guestCount.value = count ? parseInt(count) : 0
  }
  
  // 初始化 sessionId
  if (!route.query.sessionId) {
    // 如果没有 sessionId，检查 localStorage 或使用默认
    const saved = localStorage.getItem('chat_sessions')
    if (saved) {
        try {
            const sessions = JSON.parse(saved)
            if (sessions.length > 0) {
                router.replace({ query: { sessionId: sessions[0].id } })
            } else {
                // Should be handled by Layout, but just in case
                router.replace({ query: { sessionId: 'default' } })
            }
        } catch(e) {
            router.replace({ query: { sessionId: 'default' } })
        }
    } else {
        router.replace({ query: { sessionId: 'default' } })
    }
  }
})

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

// 开启新会话（并在左侧列表添加）
const handleNewSession = () => {
  // 如果是未登录用户，仅清空当前屏幕
  if (!isLoggedIn.value) {
    messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。你可以问我关于比赛胜负、选手数据或战队对比的问题。' }]
    currentSessionId.value = null
    // 移除 URL 中的 sessionId 参数（如果有）
    router.replace({ query: {} })
    return
  }

  const newId = Date.now().toString()
  
  // 更新 localStorage 中的会话列表
  const saved = localStorage.getItem('chat_sessions')
  let sessions = []
  try {
    sessions = saved ? JSON.parse(saved) : []
  } catch (e) {
    sessions = []
  }
  
  const newSessionName = `AI 问答助手 ${sessions.length + 1}`
  sessions.push({ id: newId, name: newSessionName })
  localStorage.setItem('chat_sessions', JSON.stringify(sessions))
  
  // 触发事件通知 Layout 更新
  window.dispatchEvent(new Event('session-updated'))
  
  // 跳转到新会话
  router.push({ query: { sessionId: newId } })
}

const handleSend = async () => {
  if (!userInput.value.trim() || loading.value) return
  
  // 检查未登录用户的限制
  if (!isLoggedIn.value) {
    if (guestCount.value >= guestLimit) {
        ElMessage.warning('您已达到未登录用户的提问次数上限。请登录以继续使用完整功能。')
        return
    }
    guestCount.value++
    localStorage.setItem('guest_chat_count', guestCount.value.toString())
  }
  
  const content = userInput.value
  userInput.value = ''
  
  // 1. 添加用户消息
  messages.value.push({ role: 'user', content })
  scrollToBottom()
  
  // 2. 准备 AI 消息占位
  const aiMsgIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', loading: true })
  loading.value = true

  // 3. 发起 SSE 请求
  const token = localStorage.getItem('accessToken')
  const ctrl = new AbortController()
  
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  try {
    await fetchEventSource('/api/v1/chat/stream', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        sessionId: currentSessionId.value, // 如果为空，后端会创建新会话并在 meta 中返回
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
        if (response.ok) return Promise.resolve()
        throw new Error(`Failed to send message: ${response.status}`)
      },
      onmessage(msg) {
        if (msg.event === 'meta') {
            const data = JSON.parse(msg.data)
            if (data.sessionId) {
                currentSessionId.value = data.sessionId
                localStorage.setItem('currentSessionId', data.sessionId)
            }
        } else if (msg.event === 'token') {
          const data = JSON.parse(msg.data)
          messages.value[aiMsgIndex].content += (data.delta || '')
          scrollToBottom()
        } else if (msg.event === 'data') {
          const data = JSON.parse(msg.data)
          // 处理图表数据，挂载到当前消息或全局状态
          if (!messages.value[aiMsgIndex].dataRefs) messages.value[aiMsgIndex].dataRefs = []
          messages.value[aiMsgIndex].dataRefs?.push(data)
        } else if (msg.event === 'done') {
          loading.value = false
          messages.value[aiMsgIndex].loading = false
        } else if (msg.event === 'error') {
            throw new Error(msg.data)
        }
      },
      onerror(err) {
        throw err
      }
    })
  } catch (err) {
    console.error(err)
    messages.value[aiMsgIndex].content += '\n[网络错误或请求失败]'
    loading.value = false
    messages.value[aiMsgIndex].loading = false
  }
}
</script>

<template>
  <div class="flex h-full flex-col relative bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
    <!-- Header -->
    <div class="h-14 border-b border-gray-100 flex items-center justify-between px-6 bg-white z-10">
      <div class="flex items-center space-x-2">
        <span class="font-bold text-gray-800">AI 分析会话</span>
        <el-tag size="small" type="info" effect="plain">DeepSeek V3</el-tag>
      </div>
      <el-button link :icon="RefreshRight" @click="handleNewSession">
        {{ isLoggedIn ? '新会话' : '清空对话' }}
      </el-button>
    </div>

    <!-- Chat Area -->
    <div ref="chatContainer" class="flex-1 overflow-y-auto p-6 space-y-6 bg-gray-50/50">
      <div v-for="(msg, idx) in messages" :key="idx" 
        class="flex w-full" 
        :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
      >
        <div class="flex max-w-[80%] md:max-w-[70%]">
          <!-- Avatar (Assistant) -->
          <div v-if="msg.role === 'assistant'" class="w-8 h-8 rounded-full bg-blue-100 flex items-center justify-center mr-3 flex-shrink-0 text-blue-600 mt-1">
            <el-icon><ChatDotRound /></el-icon>
          </div>

          <!-- Bubble -->
          <div 
            class="p-4 rounded-2xl shadow-sm text-sm leading-relaxed whitespace-pre-wrap break-words"
            :class="[
              msg.role === 'user' 
                ? 'bg-blue-600 text-white rounded-br-none' 
                : 'bg-white border border-gray-100 text-gray-800 rounded-bl-none'
            ]"
          >
            {{ msg.content }}
            <span v-if="msg.loading" class="inline-block w-2 h-4 ml-1 bg-blue-400 animate-pulse align-middle"></span>
            
            <!-- Data Visualization Attachments -->
            <div v-if="msg.dataRefs && msg.dataRefs.length" class="mt-4 space-y-3">
              <div v-for="(data, dIdx) in msg.dataRefs" :key="dIdx" class="bg-gray-50 rounded p-3 border border-gray-100">
                <div class="text-xs text-gray-500 mb-2 flex items-center">
                  <el-icon class="mr-1"><PieChart /></el-icon> 数据图表: {{ data.title || '未命名' }}
                </div>
                <!-- Placeholder for Chart Component -->
                <div class="h-40 bg-gray-200 rounded flex items-center justify-center text-gray-400 text-xs">
                  [图表组件待实现: {{ data.chartId }}]
                </div>
              </div>
            </div>
          </div>

          <!-- Avatar (User) -->
          <div v-if="msg.role === 'user'" class="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center ml-3 flex-shrink-0 text-indigo-600 mt-1">
            <el-icon><UserFilled /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- Input Area -->
    <div class="p-4 bg-white border-t border-gray-100">
      <!-- Context Filters Toggle -->
      <div class="flex items-center mb-3 space-x-2">
         <el-button 
            size="small" 
            :type="showContext ? 'primary' : 'default'" 
            text 
            bg
            @click="showContext = !showContext"
          >
            <el-icon class="mr-1"><Document /></el-icon>
            上下文筛选 {{ showContext ? '开启' : '关闭' }}
         </el-button>
         
         <div v-if="showContext" class="flex items-center space-x-2 animate-fade-in">
           <el-select v-model="context.tournamentId" placeholder="赛事" size="small" class="w-32">
             <el-option v-for="opt in options.tournaments" :key="opt.value" :label="opt.label" :value="opt.value" />
           </el-select>
           <el-select v-model="context.teamIds" multiple collapse-tags placeholder="战队" size="small" class="w-40">
             <el-option v-for="opt in options.teams" :key="opt.value" :label="opt.label" :value="opt.value" />
           </el-select>
         </div>
      </div>

      <!-- Input Box -->
      <div class="relative">
        <el-input
          v-model="userInput"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="问问我这场比赛的关键数据..."
          resize="none"
          class="!text-base"
          @keydown.enter.prevent="handleSend"
        />
        <el-button 
          type="primary" 
          circle 
          class="absolute bottom-2 right-2 !w-8 !h-8"
          :disabled="isSendDisabled"
          @click="handleSend"
        >
          <el-icon><Position /></el-icon>
        </el-button>
      </div>
      <div class="text-center mt-2 text-xs text-gray-400">
        <span v-if="!isLoggedIn">
            未登录用户剩余提问次数: {{ guestLimit - guestCount }} / {{ guestLimit }} | 
            <router-link to="/login" class="text-blue-500 hover:underline">去登录</router-link>
        </span>
        <span v-else>
            AI 可能产生错误信息，请以官方数据为准
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
:deep(.el-textarea__inner) {
  padding-right: 3rem;
  border-radius: 0.75rem;
  box-shadow: none !important;
  background-color: #f9fafb;
  border-color: #e5e7eb;
}
:deep(.el-textarea__inner:focus) {
  background-color: #fff;
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1) !important;
}
</style>





