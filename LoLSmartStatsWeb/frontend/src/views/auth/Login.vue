<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)

const form = reactive({
  email: '',
  password: ''
})
const handleLoginTemp = async () => {
  // 临时测试账号
  if (form.email == 'test' && form.password == 'test') {
    // 伪造 token 以通过路由守卫
    localStorage.setItem('accessToken', 'temp-mock-token');
    ElMessage.success('登录成功')//提示
    router.push('/')
    return
  }
  ElMessage.error('账号或密码错误')
}
// 后端实现后使用下面这个函数
const handleLogin = async () => {
  if (!form.email || !form.password) {
    ElMessage.warning('请输入邮箱和密码')
    return
  }

  loading.value = true
  try {
    // 根据 API 文档: POST /api/v1/auth/login
    const res: any = await request.post('/auth/login', form)
    // res 结构: { user: {...}, tokens: { accessToken, refreshToken } }

    if (res.tokens) {
      localStorage.setItem('accessToken', res.tokens.accessToken)
      localStorage.setItem('refreshToken', res.tokens.refreshToken)
      localStorage.setItem('user', JSON.stringify(res.user))
      ElMessage.success('登录成功')//提示
      router.push('/')
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

        <el-form :model="form" @keyup.enter="handleLoginTemp" size="large">
          <!-- 后端实现之后修改成handleLogin -->
          <div class="mb-4 p-3 bg-blue-50 border border-blue-200 rounded-lg">
            <p class="text-sm text-blue-600">测试账号：test / test</p>
          </div>
          <el-form-item>
            <el-input v-model="form.email" placeholder="请输入测试账号: test" :prefix-icon="User" />
          </el-form-item>

          <el-form-item>
            <el-input v-model="form.password" type="password" placeholder="请输入测试密码: test" :prefix-icon="Lock"
              show-password />
          </el-form-item>

          <el-button type="primary" class="w-full !rounded-lg !h-12 !text-lg !font-medium mt-4" :loading="loading"
            @click="handleLoginTemp">
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
