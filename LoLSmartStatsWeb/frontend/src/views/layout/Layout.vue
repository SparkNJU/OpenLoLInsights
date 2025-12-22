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

const saveSessions = () => {
  localStorage.setItem('chat_sessions', JSON.stringify(sessions.value))
  window.dispatchEvent(new Event('session-updated'))
}

const startEditingSession = (sessionId: string, currentName: string) => {
  editingSessionId.value = sessionId
  editingSessionName.value = currentName
}

const saveSessionName = () => {
  if (!editingSessionId.value || !editingSessionName.value.trim()) {
    editingSessionId.value = null
    return
  }

  const session = sessions.value.find(s => s.id === editingSessionId.value)
  if (session) {
    session.name = editingSessionName.value.trim()
    saveSessions()
    ElMessage.success('重命名成功')
  }

  editingSessionId.value = null
  editingSessionName.value = ''
}

const cancelEditing = () => {
  editingSessionId.value = null
  editingSessionName.value = ''
}

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

const handleSessionUpdate = () => {
  loadSessions()
}

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
})

onUnmounted(() => {
  window.removeEventListener('session-updated', handleSessionUpdate)
})

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
            <div v-else class="flex items-center px-3 py-2 rounded-lg bg-blue-50">
              <el-icon :size="20" class="mr-3 text-blue-600">
                <ChatDotRound />
              </el-icon>
              <el-input v-model="editingSessionName" size="small" class="flex-1" @keyup.enter="saveSessionName"
                @keyup.esc="cancelEditing" @blur="saveSessionName" />
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
