<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const matchId = route.params.id
const loading = ref(false)
// const matchData = ref<any>(null)

// Hardcoded mock data for UI testing
const matchData = ref<any>({
  matchId: matchId || 'mock-123',
  tournamentId: 'LPL 2024 Summer Playoffs',
  date: '2024-07-20',
  teams: [
    { teamId: 'BLG' },
    { teamId: 'TES' }
  ],
  games: [
    { gameNo: 1, winnerTeamId: 'BLG', durationSec: 1856 },
    { gameNo: 2, winnerTeamId: 'TES', durationSec: 2140 },
    { gameNo: 3, winnerTeamId: 'BLG', durationSec: 1980 }
  ],
  pickBan: {
    picks: ["Aatrox", "Sejuani", "Ahri", "Kai'sa", "Rell", "Renekton", "Viego", "Azir", "Ezreal", "Braum"],
    bans: ["Rumble", "Tristana", "Ashe", "Leona", "Nautilus", "Corki", "Ivern", "Varus"]
  },
  teamStats: {
    "BLG": { "kills": 45, "turrets": 24, "dragons": 8, "barons": 3, "gold": "185.5k" },
    "TES": { "kills": 32, "turrets": 11, "dragons": 4, "barons": 1, "gold": "162.3k" }
  }
})

const fetchMatchDetail = async () => {
  // loading.value = true
  // try {
  //   const res: any = await request.post('/matches/detail', { matchId: matchId })
  //   matchData.value = res
  // } catch (error) {
  //   console.error('Failed to fetch match detail:', error)
  //   // ElMessage.error('获取比赛详情失败')
  // } finally {
  //   loading.value = false
  // }
  
  // Simulate loading for mock data
  loading.value = true
  setTimeout(() => {
    loading.value = false
  }, 500)
}

const formatDuration = (seconds: number) => {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

onMounted(() => {
  if (matchId) {
    fetchMatchDetail()
  }
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center space-x-4">
      <el-button :icon="ArrowLeft" circle @click="router.back()" />
      <h2 class="text-2xl font-bold text-gray-800">比赛详情</h2>
    </div>

    <div v-loading="loading" class="space-y-6">
      <!-- 没数据时的展示 -->
      <el-empty v-if="!loading && !matchData" description="未找到比赛数据" />

      <template v-else-if="matchData">
        <!-- 基本信息卡片 -->
        <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100">
          <div class="flex justify-between items-center mb-6">
            <span class="text-gray-500">{{ matchData.date }} · {{ matchData.tournamentId }}</span>
            <span class="px-3 py-1 bg-green-50 text-green-600 rounded-full text-sm font-medium">Finished</span>
          </div>

          <div class="flex items-center justify-around">
            <!-- Team A -->
            <div class="text-center">
              <div class="text-3xl font-bold text-gray-900 mb-2">
                {{ matchData.teams?.[0]?.teamId || 'Team A' }}
              </div>
              <div class="text-gray-500">Blue Side</div>
            </div>

            <!-- Score -->
            <div class="text-4xl font-bold text-gray-800 bg-gray-50 px-8 py-4 rounded-xl">
              VS
            </div>

            <!-- Team B -->
            <div class="text-center">
              <div class="text-3xl font-bold text-gray-900 mb-2">
                {{ matchData.teams?.[1]?.teamId || 'Team B' }}
              </div>
              <div class="text-gray-500">Red Side</div>
            </div>
          </div>
        </div>

        <!-- 详细数据 Tabs -->
        <el-tabs type="border-card" class="rounded-xl shadow-sm border-gray-100">
          <el-tab-pane label="小局列表 (Games)">
            <div class="space-y-4">
              <div v-for="game in matchData.games" :key="game.gameNo" 
                   class="flex items-center justify-between p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors">
                <div class="flex items-center space-x-4">
                  <span class="w-8 h-8 flex items-center justify-center bg-blue-600 text-white rounded-full font-bold">
                    {{ game.gameNo }}
                  </span>
                  <span class="text-gray-700">Duration: {{ formatDuration(game.durationSec) }}</span>
                </div>
                <div class="font-medium">
                  Winner: <span class="text-blue-600">{{ game.winnerTeamId }}</span>
                </div>
              </div>
              <div v-if="!matchData.games?.length" class="text-center text-gray-400 py-4">
                暂无小局数据
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="BP 数据 (Pick/Ban)">
            <div class="p-4">
              <h3 class="font-bold mb-4">Picks</h3>
              <div class="flex flex-wrap gap-2 mb-6">
                <span v-for="(pick, index) in matchData.pickBan?.picks" :key="index"
                      class="px-3 py-1 bg-blue-50 text-blue-700 rounded border border-blue-100">
                  {{ pick }}
                </span>
                <span v-if="!matchData.pickBan?.picks?.length" class="text-gray-400">暂无 Pick 数据</span>
              </div>
              
              <h3 class="font-bold mb-4">Bans</h3>
              <div class="flex flex-wrap gap-2">
                <span v-for="(ban, index) in matchData.pickBan?.bans" :key="index"
                      class="px-3 py-1 bg-red-50 text-red-700 rounded border border-red-100">
                  {{ ban }}
                </span>
                <span v-if="!matchData.pickBan?.bans?.length" class="text-gray-400">暂无 Ban 数据</span>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="战队数据对比">
            <div class="p-4 grid grid-cols-2 gap-8" v-if="matchData.teamStats">
              <div v-for="(stats, teamId) in matchData.teamStats" :key="teamId" class="space-y-2">
                <h3 class="font-bold text-lg text-center mb-4">{{ teamId }}</h3>
                <div v-for="(value, key) in stats" :key="key" class="flex justify-between border-b border-gray-100 py-2">
                  <span class="text-gray-600 capitalize">{{ key }}</span>
                  <span class="font-mono font-medium">{{ value }}</span>
                </div>
              </div>
            </div>
            <div v-else class="text-center text-gray-400 py-10">暂无战队统计数据</div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>
  </div>
</template>