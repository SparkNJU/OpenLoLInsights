<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'

const router = useRouter()
const activeTab = ref('matches')
const loading = ref(false)
const dateRange = ref('')

const matches = ref<any[]>([])

// 获取比赛列表
const fetchMatches = async () => {
  loading.value = true
  try {
    const payload = {
      filter: {
        // 如果有日期筛选
        ...(dateRange.value ? { 
            dateRange: { 
              from: dateRange.value[0], 
              to: dateRange.value[1] 
            } 
          } : {}),
      },
      page: 1,
      pageSize: 20,
      sort: [{ field: "date", direction: "desc" }]
    }
    // /api/v1/matches/search
    // const res: any = await request.post('/matches/search', payload)
    
    // 模拟数据
    const mockMatch = {
        id: 'mock-123',
        time: '2024-07-20',
        teamA: 'BLG',
        teamB: 'TES',
        scoreA: 2,
        scoreB: 1,
        status: 'Finished',
        tournament: 'LPL 2024 Summer'
    }
    matches.value = [mockMatch]

    // if (res && res.items) {
    //   matches.value = res.items.map((item: any) => ({
    //     id: item.matchId,
    //     time: item.date,
    //     teamA: item.blueTeamId, // 暂时使用ID，理想情况后端返回名称
    //     teamB: item.redTeamId,
    //     scoreA: item.score ? item.score.split('-')[0] : 0,
    //     scoreB: item.score ? item.score.split('-')[1] : 0,
    //     status: 'Finished', // 接口暂未返回状态，默认为 Finished
    //     tournament: item.tournamentId
    //   }))
    // }

    try {
        const res: any = await request.post('/matches/search', payload)
        if (res && res.items && res.items.length > 0) {
            const apiMatches = res.items.map((item: any) => ({
                id: item.matchId,
                time: item.date,
                teamA: item.blueTeamId,
                teamB: item.redTeamId,
                scoreA: item.score ? item.score.split('-')[0] : 0,
                scoreB: item.score ? item.score.split('-')[1] : 0,
                status: 'Finished',
                tournament: item.tournamentId
            }))
            matches.value = [...matches.value, ...apiMatches]
        }
    } catch (e) {
        console.log('Backend not available, using mock data only')
    }

  } catch (error) {
    console.error('Failed to fetch matches:', error)
    // ElMessage.error('获取比赛数据失败') // 暂时注释，以免后端没起reportedror too many')
  } finally {
    loading.value = false
  }
}

// 监听日期变化重新请求
const handleDateChange = () => {
  fetchMatches()
}

// 跳转到详情页
const goToMatch = (id: string) => {
  router.push({ name: 'match-detail', params: { id } })
}

// 选手搜索逻辑
const playerSearchKeyword = ref('')
const players = ref<any[]>([])
const playerLoading = ref(false)

const handlePlayerSearch = async () => {
  playerLoading.value = true
  try {
    // 构造请求体，假设后端支持模糊搜索
    const payload = {
      filter: {
        playerId: playerSearchKeyword.value ? playerSearchKeyword.value : undefined
      },
      page: 1,
      pageSize: 20
    }
    const res: any = await request.post('/players/search', payload)
    if (res && res.items) {
      players.value = res.items
    } else {
      players.value = []
    }
  } catch (error) {
    console.error('Failed to search players:', error)
    players.value = []
  } finally {
    playerLoading.value = false
  }
}

onMounted(() => {
  fetchMatches()
  // 默认加载一次选手列表
  handlePlayerSearch()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h2 class="text-2xl font-bold text-gray-800">赛事数据中心</h2>
      <el-date-picker 
        v-if="activeTab === 'matches'"
        v-model="dateRange"
        type="daterange" 
        range-separator="To" 
        start-placeholder="Start date" 
        end-placeholder="End date"
        value-format="YYYY-MM-DD"
        @change="handleDateChange"
      />
    </div>

    <el-tabs v-model="activeTab" class="demo-tabs">
      <el-tab-pane label="近期比赛" name="matches">
        <div class="grid gap-4" v-loading="loading">
          <div v-if="matches.length === 0 && !loading" class="text-center py-10 text-gray-500">
            暂无比赛数据
          </div>
          <div v-for="match in matches" :key="match.id" 
               class="bg-white p-4 rounded-lg shadow-sm border border-gray-100 flex items-center justify-between hover:shadow-md transition-shadow cursor-pointer"
               @click="goToMatch(match.id)">
            <div class="flex items-center space-x-4 w-1/3">
              <span class="text-sm text-gray-500">{{ match.time }}</span>
              <span class="text-xs px-2 py-1 bg-blue-50 text-blue-600 rounded">{{ match.tournament }}</span>
            </div>
            
            <div class="flex items-center justify-center space-x-6 w-1/3 font-bold text-lg">
              <div class="text-right w-20">{{ match.teamA }}</div>
              <div class="px-3 py-1 bg-gray-100 rounded text-gray-800">
                {{ match.scoreA }} - {{ match.scoreB }}
              </div>
              <div class="text-left w-20">{{ match.teamB }}</div>
            </div>

            <div class="w-1/3 text-right">
              <el-button type="primary" size="small" plain @click.stop="goToMatch(match.id)">查看详情</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
      
      <el-tab-pane label="选手榜单" name="players">
        <div class="space-y-4">
          <!-- 搜索框 -->
          <div class="flex space-x-2">
            <el-input
              v-model="playerSearchKeyword"
              placeholder="搜索选手 ID..."
              class="w-64"
              @keyup.enter="handlePlayerSearch"
              clearable
              @clear="handlePlayerSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" @click="handlePlayerSearch">搜索</el-button>
          </div>

          <!-- 选手列表 -->
          <div v-loading="playerLoading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div v-if="players.length === 0 && !playerLoading" class="col-span-full text-center py-10 text-gray-500">
              未找到选手数据
            </div>
            
            <div v-for="player in players" :key="player.playerId" 
                 class="bg-white p-4 rounded-lg shadow-sm border border-gray-100 flex items-center space-x-4 hover:shadow-md transition-shadow">
              <div class="w-12 h-12 bg-gray-200 rounded-full flex items-center justify-center text-gray-500 font-bold">
                {{ player.playerId.substring(0, 1).toUpperCase() }}
              </div>
              <div>
                <div class="font-bold text-lg text-gray-800">{{ player.playerId }}</div>
                <div class="text-sm text-gray-500">
                  位置: {{ player.role || 'Unknown' }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>