<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ChatDotRound, DataAnalysis, PieChart, SwitchButton, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
// 用于高亮当前菜单
const activeMenu = computed(() => route.path)

const user = ref<any>({})
const isLoggedIn = ref(false)

// 组件挂载：只负责获取用户信息，不再处理会话列表
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
})

// 退出登录
const handleLogout = () => {
  localStorage.clear()
  router.push('/login')
}
</script>

<template>
  <div class="flex h-screen w-full bg-gray-50">
    
    <!-- 1. 全局左侧导航栏 (Left Sidebar) -->
    <!-- 也就是原先的 Right Sidebar 移到了左边，并去掉了会话列表逻辑 -->
    <aside class="w-64 bg-white border-r border-gray-200 flex flex-col shadow-sm z-20 shrink-0">
      
      <!-- Logo 区域 -->
      <div class="h-16 flex items-center px-6 border-b border-gray-100">
        <div class="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center mr-3 text-white font-bold text-xl">
          L
        </div>
        <span class="text-lg font-bold text-gray-800 tracking-tight">LoL Smart Stats</span>
      </div>

      <!-- 核心菜单区域 -->
      <div class="flex-1 py-6 px-3 space-y-2 overflow-y-auto">
        
        <!-- 菜单 1: AI 问答 -->
        <router-link to="/chat"
          class="flex items-center px-3 py-3 rounded-lg text-gray-600 hover:bg-blue-50 hover:text-blue-600 transition-colors"
          :class="{ 'bg-blue-50 text-blue-600 font-medium shadow-sm': activeMenu.includes('/chat') }">
          <el-icon :size="20" class="mr-3">
            <ChatDotRound />
          </el-icon>
          AI 问答助手
        </router-link>

        <!-- 菜单 2: 赛事数据 -->
        <router-link to="/data"
          class="flex items-center px-3 py-3 rounded-lg text-gray-600 hover:bg-blue-50 hover:text-blue-600 transition-colors"
          :class="{ 'bg-blue-50 text-blue-600 font-medium shadow-sm': activeMenu.includes('/data') }">
          <el-icon :size="20" class="mr-3">
            <DataAnalysis />
          </el-icon>
          赛事数据中心
        </router-link>

        <!-- 菜单 3: 统计图表 -->
        <router-link to="/stats"
          class="flex items-center px-3 py-3 rounded-lg text-gray-600 hover:bg-blue-50 hover:text-blue-600 transition-colors"
          :class="{ 'bg-blue-50 text-blue-600 font-medium shadow-sm': activeMenu.includes('/stats') }">
          <el-icon :size="20" class="mr-3">
            <PieChart />
          </el-icon>
          统计图表
        </router-link>
      </div>

      <!-- 底部用户信息区域 -->
      <div class="p-4 border-t border-gray-100">
        <div class="flex items-center p-3 rounded-lg bg-gray-50 mb-3">
          <div class="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 flex-shrink-0">
            <el-icon>
              <UserFilled />
            </el-icon>
          </div>
          <div class="ml-3 overflow-hidden">
            <p class="text-sm font-medium text-gray-900 truncate">
              {{ isLoggedIn ? (user.nickname || 'User') : '访客' }}
            </p>
            <p class="text-xs text-gray-500 truncate">
              {{ isLoggedIn ? user.email : '未登录' }}
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

    <!-- 2. 主内容区域 -->
    <main class="flex-1 flex flex-col overflow-hidden relative bg-gray-50">
      <!-- Router View 容器 -->
      <div class="flex-1 overflow-hidden relative">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>

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