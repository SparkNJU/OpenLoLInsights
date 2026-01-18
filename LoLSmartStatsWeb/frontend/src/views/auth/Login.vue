<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { authApi } from '@/api/auth'

const router = useRouter()
const loading = ref(false)

const form = reactive({
  email: '',
  password: ''
})
const handleLogin = async () => {
  if (!form.email || !form.password) {
    ElMessage.warning('请输入邮箱和密码')
    return
  }

  loading.value = true
  try {
    // 根据 API 文档: POST /api/v1/auth/login
    const res: any = await authApi.login({
      email: form.email,
      password: form.password
    })
    // res 结构: { user: {...}, tokens: { accessToken, refreshToken } }

    if (res.tokens) {
      localStorage.setItem('accessToken', res.tokens.accessToken)
      localStorage.setItem('refreshToken', res.tokens.refreshToken)
      localStorage.setItem('user', JSON.stringify(res.user))
      ElMessage.success('登录成功')//提示
      // 先跳转到主页，然后刷新以确保状态正确更新
      window.location.href = '/'
    }
  } catch (err) {
    console.error(err)
  } finally {
    loading.value = false
  }
}

const handleGuestAccess = () => {
  // 清除可能存在的旧 token，确保以游客身份进入
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('user')
  router.push('/chat')
}
</script>

<template>
  <div class="flex h-screen w-full overflow-hidden bg-gray-50">
    <!-- Left Side: Image/Branding -->
    <div class="hidden w-1/2 bg-blue-600 lg:flex flex-col justify-center items-center text-white relative">
      <div class="z-10 text-center px-10">
        <h1 class="text-5xl font-bold mb-6">LoL Smart Stats</h1>
        <p class="text-xl opacity-90">基于 LLM 的英雄联盟赛事数据智能分析平台</p>
      </div>
      <!-- Abstract Pattern Background -->
      <div class="absolute inset-0 bg-gradient-to-br from-blue-500 to-indigo-700 opacity-90"></div>
    </div>

    <!-- Right Side: Login Form -->
    <div class="w-full lg:w-1/2 flex items-center justify-center p-8">
      <div class="w-full max-w-md bg-white p-8 rounded-2xl shadow-xl border border-gray-100">
        <h2 class="text-3xl font-bold text-gray-800 mb-2">欢迎回来</h2>
        <p class="text-gray-500 mb-8">请登录您的账户以继续</p>

        <el-form :model="form" @keyup.enter="handleLogin" size="large">
          <el-form-item>
            <el-input v-model="form.email" placeholder="邮箱地址" :prefix-icon="User" />
          </el-form-item>

          <el-form-item>
            <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
          </el-form-item>

          <el-button type="primary" class="w-full !rounded-lg !h-12 !text-lg !font-medium mt-4" :loading="loading"
            @click="handleLogin">
            登 录
          </el-button>

          <el-button class="w-full !rounded-lg !h-12 !text-lg !font-medium mt-3 !ml-0" @click="handleGuestAccess">
            游客访问
          </el-button>

          <div class="mt-6 text-center text-gray-600">
            还没有账号？
            <router-link to="/register" class="text-blue-600 hover:underline font-medium">立即注册</router-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Optional: Add custom animations or overrides */
</style>
