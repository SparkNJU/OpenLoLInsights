<script setup lang="ts">
import { ref, onMounted } from 'vue'
import BaseChart from '@/components/BaseChart.vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'

const loading = ref(false)

const pieOptions = ref<any>({
  title: { text: '英雄选择率 Top 5', left: 'center' },
  tooltip: { trigger: 'item' },
  legend: { orient: 'vertical', left: 'left' },
  series: [] // 初始为空，等待数据加载
})

const barOptions = ref<any>({
  title: { text: '战队场均击杀', left: 'center' },
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: [] },
  yAxis: { type: 'value' },
  series: []
})

const fetchChartData = async () => {
  loading.value = true
  try {
    // 1. 获取英雄选择率 (Pick Ban)
    // POST /api/v1/metrics/query
    const pieRes: any = await request.post('/metrics/query', {
      metric: 'pickban',
      groupBy: ['champion'],
      limit: 5
    })

    if (pieRes && pieRes.table) {
      const data = pieRes.table.map((item: any) => ({
        value: typeof item.pickRate === 'string' ? parseFloat(item.pickRate) : (item.pickRate || 0),
        name: item.champion || 'Unknown'
      }))

      pieOptions.value = {
        ...pieOptions.value,
        series: [
          {
            name: 'Pick Rate',
            type: 'pie',
            radius: '50%',
            data: data,
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)'
              }
            }
          }
        ]
      }
    }

    // 2. 获取战队场均击杀
    // 假设 metric 为 'team_stats'，需后端支持
    const barRes: any = await request.post('/metrics/query', {
      metric: 'team_stats',
      groupBy: ['teamId'],
      limit: 5
    })

    if (barRes && barRes.table) {
      const teams = barRes.table.map((item: any) => item.teamId || 'Unknown')
      const kills = barRes.table.map((item: any) => {
         const val = item.avgKills ?? item.kills ?? 0;
         return typeof val === 'string' ? parseFloat(val) : val;
      })

      barOptions.value = {
        ...barOptions.value,
        xAxis: { type: 'category', data: teams },
        series: [
          {
            data: kills,
            type: 'bar',
            itemStyle: { color: '#409EFF' }
          }
        ]
      }
    }

  } catch (error) {
    console.error('Failed to fetch chart data:', error)
    // ElMessage.warning('加载图表数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchChartData()
})
</script>

<template>
  <div class="space-y-6">
    <h2 class="text-2xl font-bold text-gray-800">统计分析大盘</h2>
    
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6" v-loading="loading">
      <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
        <BaseChart :options="pieOptions" height="350px" />
      </div>
      <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
        <BaseChart :options="barOptions" height="350px" />
      </div>
    </div>
  </div>
</template>