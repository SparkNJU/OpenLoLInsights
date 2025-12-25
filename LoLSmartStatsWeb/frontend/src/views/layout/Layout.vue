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
            <div class="h-16 flex items-center px-6 border-b border-gray-100 overflow-hidden">
              <!-- SVG Logo: 设置高度固定，宽度自适应 -->
              <svg 
                xmlns="http://www.w3.org/2000/svg" 
                viewBox="0 0 600 229.42" 
                class="h-8 w-auto mr-3"
              >
                <g fill="#c79b3b">
                  <path d="m566.8 164.82c-10.2-2.6-19-4.8-19-13.2 0-6.6 5-10.8 13-10.8 7.4-0.4 14.2 4.6 15.6 12l20-9.6c-5.4-13.2-18.4-21.2-35-21.2-21.6 0-36.6 13.2-36.6 32 0 23 16.6 27.4 31.4 31.2 10.4 2.6 19.2 5 19.2 13.6 0 7.2-5.8 11.8-14.6 11.8s-16.2-4.8-18.2-12l-20 9.6c5.6 13.2 19.8 21.2 37.4 21.2 23.4 0 38.4-13 38.4-33.4 0-23.2-17.6-27.6-31.6-31.2z"/>
                  <path d="m600 29.019v10.6h-17v10h14.8l-2.8 10.2h-12v15.8h-11.6v-46.6z"/>
                  <path d="m542.2 39.819c7.2 0 13 5.8 13 13s-5.8 13-13 13-13-5.8-13-13 5.8-13 13-13zm0 37.2c13.4 0 24.2-11 24.2-24.4s-11-24.2-24.4-24.2-24.2 11-24.2 24.2c0 13.6 11 24.6 24.4 24.4z"/>
                  <path d="m329 226.42h21.6v-68.2l49 68.2h22.6v-103.8h-21.8v69.2l-48.4-69.2h-28l5 10.2z"/>
                  <path d="m495.4 186.82c-3 7.4-9 13.2-16.2 16.4-3.8 1.6-7.8 2.4-12 2.4h-12.4v-62h12.4c4.2 0 8.2 0.8 12 2.4 7.4 3 13.2 9 16.2 16.4 3.2 8 3.2 16.6 0 24.4zm-7.2-60c-6.6-2.6-13.6-4-20.6-4.4h-35.4v103.8h35.4c14 0 27.4-5.2 37.6-14.8 4.8-4.6 8.8-10.2 11.4-16.4 8.6-19.6 4-42.4-11.4-57.2-5-4.6-10.6-8.4-17-11z"/>
                  <path d="m138.4 226.42h-61.8v-103.8h61.8v20.2h-39.2v21h35.8l-5.4 19.6h-30.4v22.8l39.2 0.2z"/>
                  <path d="m279.6 142.82h39.2v-20.2h-61.6v103.8h61.6v-20l-39.2-0.2v-22.8h30.4l5.4-19.6h-35.8z"/>
                  <path d="m358 82.019c-2-5.8-3-11.8-2.8-17.8l0.40002-64h22.4l-0.1999 63c0 7 1.6 12.6 4.8 16.6 7.6 8 20.4 8.2 28.2 0.4l0.4-0.4c3.4-4 5-9.6 5-16.6l0.2001-63.2h22.8l-0.40002 64.6c0 6-0.79999 12-2.8 17.8-1.8 5-4.8 9.6-8.4 13.6-3.8 3.8-8.2 6.8-13.2 8.6-11.4 4-23.8 4-35.2-0.2001-5-2-9.4-4.8-13-8.8-3.6-3.8-6.4-8.4-8.2-13.6z"/>
                  <path d="m245.6 74.019c-2.8-6.6-4.2-13.8-4.2-20.8 0-7.2 1.6-14.2 4.4-20.8 2.8-6.4 6.8-12.2 11.8-17 5-5 11-8.8 17.4-11.4 6.6-2.8 13.8-4.2 21.2-4 9.8-0.20011 19.6 2.4 28.2 7.2 8 4.6 14.6 11.4 18.8 19.8l-20.4 9.6c-2.4-4.8-6.2-8.8-10.8-11.6-4.8-2.8-10.4-4-16-4-4.2 0-8.6 0.80001-12.4 2.4-3.8 1.6-7.2 4-10 7-3 3-5.2 6.6-6.8 10.4-3.4 8.2-3.4 17.2-0.2 25.4 3 7.8 9.2 14.2 16.8 17.6 4 1.8 8.2 2.8 12.6 2.6 6.4 0.2001 12.8-1.8 18-5.4 4.8-3.4 8.2-8.6 9.6-14.4h-37l9.2-18.8h51.6c0.2001 4 0 12.6-0.2001 14.4-0.6 4.8-1.8 9.6-3.6 14.2-5 12.4-14.8 22-27.2 27-20.4 8.2-43.6 3.4-59.2-12.2-5-5-8.8-10.8-11.6-17.2z"/>
                  <path d="m5.2 93.619v-82.6l-5.2-10.8h27.6v83.8h41.4l-5.8 20.4h-63.2z"/>
                  <path d="m5.2 215.62v-82.6l-5.2-10.8h27.6v83.8h41.4l-5.8 20.4h-63.2z"/>
                  <path d="m138.4 104.42h-61.8v-103.8h61.8v20.2h-39.2v21h35.8l-5.4 19.6h-30.4v22.6l39.2 0.2001z"/>
                  <path d="m510.8 104.42h-61.6v-103.8h61.8v20.2h-39.2v21h35.8l-5.4 19.6h-30.4v22.6l39.2 0.2001v20.2z"/>
                  <path d="m197.6 29.419 13 35h-26.6zm-29 75 7.8-20.2h41.6l8 20.2h23.4l-41.8-104.2h-32l6.8 13.6-36.8 90.6z"/>
                  <path d="m185.2 228.02c3.8 0.8 7.6 1.2 11.6 1.2 7 0.2 14-1.2 20.6-3.8 12.4-5 22.2-14.6 27.2-27 1.8-4.6 3-9.4 3.6-14.2 0.4-4.8 0.4-9.6 0.2-14.6h-51.6v0.2l-9.2 18.8h36.8c-1.4 5.8-4.8 11-9.6 14.4-5.2 3.8-11.4 5.6-17.8 5.4-2 0-3.8-0.2-5.8-0.6l-4.6-1.2c-0.8-0.19999-1.4-0.6-2.2-0.8-7.6-3.4-13.8-9.6-16.8-17.6-3.2-8.2-3.2-17.4 0.19999-25.6 1.6-3.8 4-7.4 6.8-10.4s6.4-5.2 10.2-6.8c4-1.6 8.2-2.4 12.4-2.4 5.6-0.2 11.2 1.2 16 4 4.6 2.8 8.4 6.8 10.8 11.6l20.4-9.6c-4.2-8.4-10.6-15.2-18.8-19.8-8.6-4.8-18.2-7.2-28.2-7.2-14.4-0.2-28.2 5.4-38.6 15.6-5 4.8-9 10.6-11.8 17-5.8 13.2-5.8 28.4-0.2 41.6 2.8 6.4 6.6 12.2 11.4 17.2 4.4 4.4 9.6 8 15.2 10.8 3.9333 1.7718 7.8667 3.2094 11.8 3.8z"/>
                </g>
              </svg>
              <span class="text-lg font-bold text-gray-800 tracking-tight">Smart Stats</span>
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
            <component :is="Component" :key="route.fullPath"/>
        </router-view>
      </div>
    </main>

  </div>
</template>

<style scoped>

</style>