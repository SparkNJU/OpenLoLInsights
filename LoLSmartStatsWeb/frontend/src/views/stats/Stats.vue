<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dataApi } from '@/api/data'

const loading = ref(false)
const query = ref('')
const players = ref<{ id: number; name: string }[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const handleSearch = async (resetPage: boolean = false) => {
  if (!query.value.trim()) {
    ElMessage.warning('请输入选手名字关键字')
    return
  }

  if (resetPage) {
    page.value = 1
  }

  loading.value = true
  try {
    const res: any = await dataApi.searchPlayers(query.value.trim(), page.value, pageSize.value)
    const data = res || {}
    console.log('players/search raw response', res)
    console.log('players/search data', data)
    const items = Array.isArray(data.items) ? data.items : []
    players.value = items.map((p: any) => ({
      id: p.id,
      name: p.name
    }))
    total.value = typeof data.total === 'number' ? data.total : 0
  } catch (error) {
    players.value = []
    total.value = 0
    ElMessage.error('选手查询失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (newPage: number) => {
  page.value = newPage
  handleSearch(false)
}
</script>

<template>
  <div class="space-y-6 h-full flex flex-col">
    <h2 class="text-2xl font-bold text-gray-800">选手查询</h2>

    <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
      <div class="flex flex-col md:flex-row md:items-center gap-4">
        <el-input
          v-model="query"
          placeholder="请输入选手名称关键字，例如 Faker"
          clearable
          class="md:flex-1"
          @keyup.enter="handleSearch(true)"
        />
        <el-button type="primary" @click="handleSearch(true)">
          查询选手
        </el-button>
      </div>
    </div>

    <div class="flex-1 overflow-hidden">
      <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 h-full flex flex-col" v-loading="loading">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-gray-800">查询结果</h3>
          <span class="text-xs text-gray-400">
            共 {{ total }} 个选手
          </span>
        </div>

        <div class="flex-1 overflow-auto">
          <el-table
            :data="players"
            style="width: 100%"
            size="small"
            stripe
            v-if="players.length > 0"
          >
            <el-table-column prop="id" label="ID" width="120" />
            <el-table-column prop="name" label="选手名称" min-width="200" />
          </el-table>

          <el-empty v-else description="请先输入关键字并点击查询，或当前关键字无结果" />
        </div>

        <div class="mt-4 flex justify-end" v-if="total > pageSize">
          <el-pagination
            background
            layout="prev, pager, next"
            :page-size="pageSize"
            :total="total"
            :current-page="page"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>
