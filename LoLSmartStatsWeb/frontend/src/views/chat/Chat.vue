<script setup lang="ts">
import { ref, reactive, nextTick, onMounted, onUnmounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { 
  Position, Plus, Document, ChatDotRound, UserFilled, PieChart, 
  Edit, Delete, Search, Message as MessageIcon , Clock
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

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
  tournamentId: '2024-worlds',
  dateRange: [] as string[],
  teamIds: [] as string[],
  patch: ''
})
const options = reactive({
  tournaments: [{ label: 'Worlds 2024', value: '2024-worlds' }],
  teams: [{ label: 'T1', value: 'T1' }, { label: 'BLG', value: 'BLG' }],
  patches: ['14.18', '14.19']
})

// 5.切换侧边栏状态
// 切换侧边栏状态
const toggleSidebar = () => {
  isSidebarOpen.value = !isSidebarOpen.value
}

// 点击外部区域关闭侧边栏
const closeSidebar = () => {
  if (isSidebarOpen.value) {
    isSidebarOpen.value = false
  }
}


// --- 逻辑方法：会话管理 (CRUD) ---

// 加载本地会话列表
const loadSessions = () => {
  const saved = localStorage.getItem('chat_sessions')
  if (saved) {
    try {
      sessions.value = JSON.parse(saved)
    } catch (e) {
      sessions.value = []
    }
  }
  // 如果没有会话且已登录，创建一个默认的
  if (sessions.value.length === 0 && isLoggedIn.value) {
    // 可以在这里静默创建，或者等用户发第一条消息时创建
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
      // 如果删除的是当前选中的
      if (currentSessionId.value === sessionId) {
        if (sessions.value.length > 0) {
          router.push({ query: { sessionId: sessions.value[0].id } })
        } else {
          // 清空当前界面并移除ID
          currentSessionId.value = null
          messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。' }]
          router.replace({ query: {} })
        }
      }
      ElMessage.success('删除成功')
    }
  } catch { }
}

// 全局点击处理（用于点击外部取消编辑）
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
  // 点击外部：如果没有交互过直接取消，交互过可以尝试保存(或者也取消，看需求，这里选取消更安全)
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
    const res: any = await request.post('/chat/history', {
      sessionId: currentSessionId.value,
      page: 1,
      pageSize: 50
    })
    
    if (res && res.items && Array.isArray(res.items)) {
      if(res.items.length > 0) {
         const history = res.items.reverse().map((item: any) => ({
          role: item.role,
          content: item.content,
          ts: item.ts,
          dataRefs: item.dataRefs
        }))
        messages.value = history
      } else {
        // 新会话
        messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。' }]
      }
      scrollToBottom()
    }
  } catch (err) {
    console.error(err)
    messages.value = [{ role: 'assistant', content: '加载历史记录失败。' }]
  }
}

// 创建新会话
const handleNewSession = () => {
  if (!isLoggedIn.value) {
    messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。' }]
    currentSessionId.value = null
    router.replace({ query: {} })
    return
  }

  const newId = Date.now().toString()
  const newSessionName = `新的对话 ${sessions.value.length + 1}`
  sessions.value.unshift({ id: newId, name: newSessionName }) // 加到最前
  saveSessions()
  
  router.push({ query: { sessionId: newId } })
}

// 发送消息
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

  const token = localStorage.getItem('accessToken')
  const ctrl = new AbortController()
  
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`

  try {
    await fetchEventSource('/api/v1/chat/stream', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        sessionId: currentSessionId.value, 
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
        throw new Error(`Error: ${response.status}`)
      },
      onmessage(msg) {
        if (msg.event === 'meta') {
            const data = JSON.parse(msg.data)
            // 如果后端返回了新的 sessionId (冷启动会话)，更新本地
            if (data.sessionId && data.sessionId !== currentSessionId.value) {
                currentSessionId.value = data.sessionId
                // 如果当前列表里没有这个ID（意味着是第一次自动创建），需要加进去
                if (isLoggedIn.value && !sessions.value.find(s => s.id === data.sessionId)) {
                    sessions.value.unshift({ id: data.sessionId, name: data.title || '新对话' })
                    saveSessions()
                    // 更新 URL 但不刷新
                    router.replace({ query: { sessionId: data.sessionId } })
                }
            }
        } else if (msg.event === 'token') {
          const data = JSON.parse(msg.data)
          messages.value[aiMsgIndex].content += (data.delta || '')
          scrollToBottom()
        } else if (msg.event === 'data') {
          const data = JSON.parse(msg.data)
          if (!messages.value[aiMsgIndex].dataRefs) messages.value[aiMsgIndex].dataRefs = []
          messages.value[aiMsgIndex].dataRefs?.push(data)
        } else if (msg.event === 'done') {
          loading.value = false
          messages.value[aiMsgIndex].loading = false
        } else if (msg.event === 'error') {
            throw new Error(msg.data)
        }
      },
      onerror(err) { throw err }
    })
  } catch (err) {
    console.error(err)
    messages.value[aiMsgIndex].content += '\n[请求出错]'
    loading.value = false
    messages.value[aiMsgIndex].loading = false
  }
}

// --- 生命周期与监听 ---

watch(() => route.query.sessionId, (newId) => {
  currentSessionId.value = (newId as string) || null
  if (isLoggedIn.value && currentSessionId.value) {
    fetchHistory()
  } else if (!currentSessionId.value) {
    // 重置到初始状态
    messages.value = [{ role: 'assistant', content: '你好！我是你的 LoL 赛事数据助手。' }]
  }
}, { immediate: true })

onMounted(() => {
  const token = localStorage.getItem('accessToken')
  isLoggedIn.value = !!token
  if (!isLoggedIn.value) {
    const count = localStorage.getItem('guest_chat_count')
    guestCount.value = count ? parseInt(count) : 0
  }
  
  loadSessions()
  document.addEventListener('pointerdown', handleDocumentClick, true)
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', handleDocumentClick, true)
})
</script>

<template>
  <div class="flex h-full w-full bg-white">
    
    <!-- 1. 左侧：聊天主界面 -->
    <!-- [修改点] 添加 @click="closeSidebar"：点击主界面的任何地方都会尝试关闭侧边栏 -->
    <div 
      class="flex-1 flex flex-col h-full relative overflow-hidden transition-all duration-300"
      @click="closeSidebar"
    >
      
      <!-- Header -->
      <div class="flex-none bg-white border-b border-gray-100 px-8 py-5 flex items-center justify-between shrink-0 z-10">
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
        
        <!-- [修改点] 右侧按钮：改为更显眼的文字按钮 -->
        <!-- 使用 @click.stop 防止点击按钮本身触发外层的 closeSidebar -->
        <div class="flex items-center gap-4">
            <span class="hidden lg:block text-xs font-mono text-gray-300 mr-2">Model: deepseek-chat-v3</span>
            
            <el-button 
              :type="isSidebarOpen ? 'primary' : 'default'" 
              :icon="Clock" 
              size="large"
              class="!rounded-xl !px-5 transition-all duration-300 shadow-sm hover:shadow-md border-gray-200"
              :class="{ '!bg-blue-600 !border-blue-600': isSidebarOpen }"
              @click.stop="toggleSidebar"
            >
              {{ isSidebarOpen ? '收起会话' : '历史会话' }}
            </el-button>
        </div>
      </div>

      <!-- Messages Area (内容不变) -->
      <div ref="chatContainer" class="flex-1 overflow-y-auto p-4 md:p-8 space-y-6 bg-white scroll-smooth">
         <!-- ... (消息循环逻辑保持不变) ... -->
         <div v-for="(msg, idx) in messages" :key="idx" 
          class="flex w-full animate-fade-in-up" 
          :class="msg.role === 'user' ? 'justify-end' : 'justify-start'"
        >
          <div class="flex max-w-[90%] md:max-w-[75%] gap-3">
            <div v-if="msg.role === 'assistant'" class="w-8 h-8 rounded-full bg-blue-50 border border-blue-100 flex items-center justify-center shrink-0 text-blue-600 mt-1">
              <el-icon><ChatDotRound /></el-icon>
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
                <div v-for="(data, dIdx) in msg.dataRefs" :key="dIdx" class="bg-white rounded-lg border border-gray-200 p-3 shadow-sm hover:shadow-md transition-shadow cursor-pointer">
                  <div class="text-xs font-bold text-gray-600 mb-2 flex items-center">
                    <el-icon class="mr-1 text-blue-500"><PieChart /></el-icon> {{ data.title || '数据图表' }}
                  </div>
                  <div class="h-24 bg-gray-50 rounded flex items-center justify-center text-gray-400 text-xs border border-dashed border-gray-200">图表预览</div>
                </div>
              </div>
            </div>
            <div v-if="msg.role === 'user'" class="w-8 h-8 rounded-full bg-gray-100 border border-gray-200 flex items-center justify-center shrink-0 text-gray-600 mt-1">
              <el-icon><UserFilled /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- Input Area -->
      <!-- [注意] 输入框区域也属于"左侧主界面"，所以点击这里的空白处也会触发 closeSidebar，符合预期 -->
      <div class="p-6 bg-white shrink-0">
         <!-- ... (输入区域内容保持不变) ... -->
         <div class="max-w-4xl mx-auto">
          <div class="flex items-center justify-between mb-3 px-1">
            <el-button size="small" :type="showContext ? 'primary' : 'info'" :plain="!showContext" round @click.stop="showContext = !showContext">
              <el-icon class="mr-1"><Document /></el-icon> 上下文控制
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
          <div class="relative group" @click.stop> <!-- 阻止点击输入框本身触发关闭（虽然触发也没事，但最好保留焦点逻辑） -->
            <el-input v-model="userInput" type="textarea" :autosize="{ minRows: 3, maxRows: 8 }" placeholder="问问我关于 S14 决赛 T1 的表现..." class="!text-base custom-textarea" @keydown.enter.exact.prevent="handleSend"/>
            <el-button type="primary" circle class="absolute bottom-3 right-3 shadow-lg transition-transform hover:scale-105" :disabled="isSendDisabled" @click="handleSend">
              <el-icon><Position /></el-icon>
            </el-button>
          </div>
          <div class="text-center mt-2 text-xs text-gray-400 select-none">DeepSeek V3 Generated Content. Check important info.</div>
        </div>
      </div>
    </div>

    <!-- 2. 右侧：会话列表侧边栏 (点击这里本身不应该关闭自己，所以要加 @click.stop) -->
    <aside 
      class="border-gray-200 bg-gray-50 flex flex-col shrink-0 transition-all duration-300 ease-in-out overflow-hidden h-full border-l"
      :class="isSidebarOpen ? 'w-[300px] opacity-100' : 'w-0 opacity-0 border-l-0'"
      @click.stop
    >
      <div class="w-[300px] flex flex-col h-full">
         <!-- ... (侧边栏内容保持不变) ... -->
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
              <div 
                v-if="editingSessionId !== session.id"
                @click="router.push({ query: { sessionId: session.id } })"
                class="flex items-center px-3 py-3 rounded-lg cursor-pointer transition-all duration-200 border border-transparent"
                :class="currentSessionId === session.id 
                  ? 'bg-white border-gray-200 shadow-sm text-blue-600' 
                  : 'text-gray-600 hover:bg-gray-200/50'"
              >
                <el-icon class="mr-3 text-lg" :class="currentSessionId === session.id ? 'text-blue-500' : 'text-gray-400'"><MessageIcon /></el-icon>
                <span class="truncate text-sm font-medium flex-1">{{ session.name }}</span>
                <div class="hidden group-hover:flex items-center bg-inherit pl-2" :class="currentSessionId === session.id ? 'bg-white' : 'bg-gray-200/50'">
                   <el-icon class="text-gray-400 hover:text-blue-500 cursor-pointer mr-2" size="14" @click.stop="startEditingSession(session.id, session.name)"><Edit /></el-icon>
                   <el-icon class="text-gray-400 hover:text-red-500 cursor-pointer" size="14" @click.stop="deleteSession(session.id)"><Delete /></el-icon>
                </div>
              </div>
              <div v-else ref="editContainer" class="flex items-center px-2 py-2 bg-white rounded-lg border border-blue-200 shadow-sm ring-2 ring-blue-50">
                <el-input v-model="editingSessionName" size="small" ref="nameInput" @keyup.enter="saveSessionName" @keyup.esc="cancelEditing" />
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
  background-color: #f9fafb; /* gray-50 */
  border: 1px solid #e5e7eb; /* gray-200 */
  box-shadow: none !important;
  transition: all 0.2s;
}

:deep(.custom-textarea .el-textarea__inner:focus) {
  background-color: #ffffff;
  border-color: #3b82f6; /* blue-500 */
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