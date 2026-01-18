<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Message } from '@element-plus/icons-vue'
import { authApi } from '@/api/auth'

const router = useRouter()
const loading = ref(false)

const form = reactive({
  email: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

const handleRegister = async () => {
  if (!form.email || !form.password || !form.nickname) {
    ElMessage.warning('请填写完整信息')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.error('两次密码输入不一致')
    return
  }
  
  loading.value = true
  try {
    const res: any = await authApi.register({
      email: form.email,
      password: form.password,
      nickname: form.nickname
    })

    if (res && res.tokens && res.user) {
      localStorage.setItem('accessToken', res.tokens.accessToken)
      localStorage.setItem('refreshToken', res.tokens.refreshToken)
      localStorage.setItem('user', JSON.stringify(res.user))
      ElMessage.success('注册成功')
      router.push('/')
    }
  } catch (err) {
    console.error(err)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex h-screen w-full overflow-hidden bg-gray-50">
    <!-- Left Side -->
    <div class="hidden w-1/2 bg-indigo-600 lg:flex flex-col justify-center items-center text-white relative">
      <div class="z-10 text-center px-10">
        <h1 class="text-5xl font-bold mb-6">加入社区</h1>
        <p class="text-xl opacity-90">探索海量赛事数据，体验智能问答</p>
      </div>
      <div class="absolute inset-0 bg-gradient-to-tr from-purple-600 to-indigo-600 opacity-90"></div>
    </div>

    <!-- Right Side -->
    <div class="w-full lg:w-1/2 flex items-center justify-center p-8">
      <div class="w-full max-w-md bg-white p-8 rounded-2xl shadow-xl border border-gray-100">
        <h2 class="text-3xl font-bold text-gray-800 mb-2">创建账号</h2>
        <p class="text-gray-500 mb-8">填写以下信息完成注册</p>

        <el-form :model="form" @keyup.enter="handleRegister" size="large">
          <el-form-item>
            <el-input 
              v-model="form.email" 
              placeholder="邮箱地址" 
              :prefix-icon="Message" 
            />
          </el-form-item>

          <el-form-item>
            <el-input 
              v-model="form.nickname" 
              placeholder="昵称" 
              :prefix-icon="User" 
            />
          </el-form-item>
          
          <el-form-item>
            <el-input 
              v-model="form.password" 
              type="password" 
              placeholder="密码" 
              :prefix-icon="Lock" 
              show-password
            />
          </el-form-item>

          <el-form-item>
            <el-input 
              v-model="form.confirmPassword" 
              type="password" 
              placeholder="确认密码" 
              :prefix-icon="Lock" 
              show-password
            />
          </el-form-item>

          <el-button 
            type="primary" 
            class="w-full !rounded-lg !h-12 !text-lg !font-medium mt-4" 
            :loading="loading"
            @click="handleRegister"
          >
            注 册
          </el-button>
          
          <div class="mt-6 text-center text-gray-600">
            已有账号？ 
            <router-link to="/login" class="text-blue-600 hover:underline font-medium">去登录</router-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>
