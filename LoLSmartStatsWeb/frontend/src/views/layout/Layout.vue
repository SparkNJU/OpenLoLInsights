<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ChatDotRound, DataAnalysis, PieChart, SwitchButton, UserFilled, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const activeMenu = computed(() => route.path)
const currentSessionId = computed(() => route.query.sessionId as string)

const user = ref<any>({})
const isLoggedIn = ref(false)
const sessions = ref<any[]>([])
const editingSessionId = ref<string | null>(null)
const editingSessionName = ref('')
const originalEditingSessionName = ref('')
const editContainer = ref<HTMLElement | null>(null)
const hasInteractedInside = ref(false)
// 加载会话
const loadSessions = () => {
  const saved = localStorage.getItem('chat_sessions')
  if (saved) {
    try {
      sessions.value = JSON.parse(saved)
    } catch (e) {
      sessions.value = []
    }
  }

  // If no sessions, create default one
  if (sessions.value.length === 0) {
    const defaultSession = { id: 'default', name: 'AI 问答助手 1' }
    sessions.value = [defaultSession]
    localStorage.setItem('chat_sessions', JSON.stringify(sessions.value))
  }
}
// 保存会话修改
const saveSessions = () => {
  localStorage.setItem('chat_sessions', JSON.stringify(sessions.value))
  window.dispatchEvent(new Event('session-updated'))
}

// 进入编辑模式：设置当前会话与名称，并标记是否在容器内交互
const startEditingSession = (sessionId: string, currentName: string) => {
  editingSessionId.value = sessionId
  editingSessionName.value = currentName
  originalEditingSessionName.value = currentName
  hasInteractedInside.value = false
}

// 保存重命名：增加校验空值与重名的逻辑，成功后写入本地并提示
const saveSessionName = () => {
  if (!editingSessionId.value || !editingSessionName.value.trim()) {
    editingSessionId.value = null
    return
  }

  const newName = editingSessionName.value.trim()
  const duplicate = sessions.value.some(s => s.name === newName && s.id !== editingSessionId.value)
  if (duplicate) {
    ElMessage.error('名称已存在，不能重复命名')
    editingSessionName.value = originalEditingSessionName.value
    return
  }

  const session = sessions.value.find(s => s.id === editingSessionId.value)
  if (session) {
    session.name = newName
    saveSessions()
    ElMessage.success('重命名成功')
  }

  editingSessionId.value = null
  editingSessionName.value = ''
  originalEditingSessionName.value = ''
  hasInteractedInside.value = false
}

// 取消编辑：退出编辑并重置相关状态
const cancelEditing = () => {
  editingSessionId.value = null
  editingSessionName.value = ''
  hasInteractedInside.value = false
}

// 删除会话：至少保留一个；若删除当前会话则跳转首个剩余会话
const deleteSession = async (sessionId: string) => {
  if (sessions.value.length <= 1) {
    ElMessage.warning('至少需要保留一个会话')
    return
  }

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

      // If deleting current session, navigate to the first remaining session
      if (currentSessionId.value === sessionId) {
        const newSessionId = sessions.value[0].id
        router.push({ path: '/chat', query: { sessionId: newSessionId } })
      }

      ElMessage.success('删除成功')
    }
  } catch {
    // User cancelled
  }
}

// 会话更新事件处理：重新从本地存储加载会话列表
const handleSessionUpdate = () => {
  loadSessions()
}

// 全局点击捕获：判断点击是否在编辑容器内，决定取消或保存
const handleDocumentClick = (e: MouseEvent) => {
  if (!editingSessionId.value) return
  const elVal = editContainer.value as unknown as HTMLElement | HTMLElement[] | null
  const el = Array.isArray(elVal) ? elVal[0] : elVal
  if (!el) return
  const path = (e as any).composedPath?.() as EventTarget[] | undefined
  const isInside = path ? path.includes(el) : el.contains(e.target as Node)
  if (isInside) {
    hasInteractedInside.value = true
    return
  }
  if (hasInteractedInside.value) {
    saveSessionName()
  } else {
    cancelEditing()
  }
}

// 组件挂载：初始化用户与会话，并注册事件监听
onMounted(() => {
  const userData = localStorage.getItem('user')
  const token = localStorage.getItem('accessToken')
  isLoggedIn.value = !!token

  if (userData) {
    try {
      user.value = JSON.parse(userData)
    } catch (e) {
      user.value = {}
    }
  }

  loadSessions()
  window.addEventListener('session-updated', handleSessionUpdate)
  document.addEventListener('pointerdown', handleDocumentClick, true)
  document.addEventListener('click', handleDocumentClick, true)
})

// 组件卸载：移除事件监听
onUnmounted(() => {
  window.removeEventListener('session-updated', handleSessionUpdate)
  document.removeEventListener('pointerdown', handleDocumentClick, true)
  document.removeEventListener('click', handleDocumentClick, true)
})

// 退出登录：清空本地存储并跳转至登录页
const handleLogout = () => {
  localStorage.clear()
  router.push('/login')
}
</script>

<template>
  <div class="flex h-screen w-full bg-gray-50">
    <!-- Main Content -->
    <main class="flex-1 flex flex-col overflow-hidden relative">
      <!-- Top Header (Optional, mostly for mobile or breadcrumbs) -->
      <header class="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-8 shadow-sm lg:hidden">
        <span class="font-bold text-gray-800">LoL Smart Stats</span>
        <!-- Mobile menu button would go here -->
      </header>

      <!-- Router View -->
      <div class="flex-1 overflow-auto p-4 lg:p-8 relative">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>

    <!-- Right Sidebar -->
    <aside class="w-64 bg-white border-l border-gray-200 flex flex-col shadow-sm z-10">
      <div class="h-16 flex items-center px-6 border-b border-gray-100">
        <div class="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center mr-3 text-white font-bold text-xl">L
        </div>
        <span class="text-lg font-bold text-gray-800 tracking-tight">LoL Smart Stats</span>
      </div>

      <div class="flex-1 py-6 px-3 space-y-1 overflow-y-auto">
        <!-- Logged-in users see their session list -->
        <template v-if="isLoggedIn">
          <div v-for="session in sessions" :key="session.id" class="group">
            <!-- Session Item -->
            <div v-if="editingSessionId !== session.id"
              class="flex items-center px-3 py-2 rounded-lg text-gray-600 hover:bg-blue-50 hover:text-blue-600 transition-colors"
              :class="{ 'bg-blue-50 text-blue-600 font-medium': activeMenu === '/chat' && (currentSessionId === session.id || (!currentSessionId && session.id === 'default')) }">
              <router-link :to="{ path: '/chat', query: { sessionId: session.id } }" class="flex-1 flex items-center">
                <el-icon :size="20" class="mr-3">
                  <ChatDotRound />
                </el-icon>
                <span class="truncate">{{ session.name }}</span>
              </router-link>

              <!-- Action buttons (always visible) -->
              <div class="flex items-center space-x-1">
                <el-button size="small" text :icon="Edit" @click.stop="startEditingSession(session.id, session.name)"
                  class="!p-1" />
                <el-button size="small" text :icon="Delete" @click.stop="deleteSession(session.id)"
                  class="!p-1 text-red-500 hover:text-red-600" />
              </div>
            </div>

            <!-- Edit Mode -->
            <div v-else class="flex items-center px-3 py-2 rounded-lg bg-blue-50" ref="editContainer">
              <el-icon :size="20" class="mr-3 text-blue-600">
                <ChatDotRound />
              </el-icon>
              <el-input v-model="editingSessionName" size="small" class="flex-1" @keyup.enter="saveSessionName"
                @keyup.esc="cancelEditing" />
              <el-button size="small" type="primary" class="!ml-2" @click="saveSessionName">保存</el-button>
            </div>
          </div>
        </template>

        <!-- Guest users see a single static chat link -->
        <router-link v-else to="/chat"
          class="flex items-center px-3 py-3 rounded-lg text-gray-600 hover:bg-blue-50 hover:text-blue-600 transition-colors"
          :class="{ 'bg-blue-50 text-blue-600 font-medium': activeMenu.includes('/chat') }">
          <el-icon :size="20" class="mr-3">
            <ChatDotRound />
          </el-icon>
          AI 问答助手
        </router-link>

        <router-link to="/data"
          class="flex items-center px-3 py-3 rounded-lg text-gray-600 hover:bg-blue-50 hover:text-blue-600 transition-colors"
          :class="{ 'bg-blue-50 text-blue-600 font-medium': activeMenu.includes('/data') }">
          <el-icon :size="20" class="mr-3">
            <DataAnalysis />
          </el-icon>
          赛事数据中心
        </router-link>

        <router-link to="/stats"
          class="flex items-center px-3 py-3 rounded-lg text-gray-600 hover:bg-blue-50 hover:text-blue-600 transition-colors"
          :class="{ 'bg-blue-50 text-blue-600 font-medium': activeMenu.includes('/stats') }">
          <el-icon :size="20" class="mr-3">
            <PieChart />
          </el-icon>
          统计图表
        </router-link>
      </div>

      <div class="p-4 border-t border-gray-100">
        <div class="flex items-center p-3 rounded-lg bg-gray-50 mb-3">
          <div class="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center text-gray-500">
            <el-icon>
              <UserFilled />
            </el-icon>
          </div>
          <div class="ml-3 overflow-hidden">
            <p class="text-sm font-medium text-gray-900 truncate">
              {{ isLoggedIn ? (user.nickname || 'User') : 'Visitor' }}
            </p>
            <p class="text-xs text-gray-500 truncate">
              {{ isLoggedIn ? user.email : '未登录用户' }}
            </p>
          </div>
        </div>
        <button @click="handleLogout"
          class="flex w-full items-center justify-center px-4 py-2 text-sm text-red-600 bg-red-50 hover:bg-red-100 rounded-lg transition-colors">
          <el-icon class="mr-2">
            <SwitchButton />
          </el-icon>
          {{ isLoggedIn ? '退出登录' : '返回登录' }}
        </button>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
