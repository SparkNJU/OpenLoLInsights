<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'

const router = useRouter()

// 当前视图模式：赛事总览/战队数据/选手信息
const viewMode = ref<'overview' | 'teams' | 'players'>('overview')

// 通用加载状态（不同视图可复用）
const loading = ref(false)

// 日期筛选（用于比赛与概览）
const dateRange = ref('')

// 比赛数据
const matches = ref<any[]>([])

// 战队数据
const teams = ref<any[]>([])

// 概览数据（示例：总场次、总战队、总选手）
const overview = reactive<{ totalMatches: number; totalTeams: number; totalPlayers: number }>({
  totalMatches: 0,
  totalTeams: 0,
  totalPlayers: 0,
})

// 获取比赛列表（数据来源：后端 /matches/search；若后端不可用，使用模拟数据）
const fetchMatches = async () => {
  loading.value = true
  try {
    const payload = {
      filter: {
        ...(dateRange.value ? {
          dateRange: {
            from: (dateRange as any)[0],
            to: (dateRange as any)[1],
          },
        } : {}),
      },
      page: 1,
      pageSize: 20,
      sort: [{ field: 'date', direction: 'desc' }],
    }

    // 模拟数据（无后端时）
    const mockMatch = {
      id: 'mock-123',
      time: '2024-07-20',
      teamA: 'BLG',
      teamB: 'TES',
      scoreA: 2,
      scoreB: 1,
      status: 'Finished',
      tournament: 'LPL 2024 Summer',
    }
    matches.value = [mockMatch]

    // 后端请求
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
          tournament: item.tournamentId,
        }))
        matches.value = [...matches.value, ...apiMatches]
      }
    } catch (e) {
      console.log('Backend not available, using mock data only')
    }
  } catch (error) {
    console.error('Failed to fetch matches:', error)
  } finally {
    loading.value = false
  }
}

// 当日期改变时刷新当前视图的数据（数据来源：用户选择 + 后端接口）
const handleDateChange = () => {
  if (viewMode.value === 'overview') { fetchOverview(); fetchMatches() }
  if (viewMode.value === 'teams') fetchTeams()
  if (viewMode.value === 'players') handlePlayerSearch()
}

// 跳转到比赛详情页（数据来源：路由参数传入）
const goToMatch = (id: string) => {
  router.push({ name: 'match-detail', params: { id } })
}

// 选手搜索关键字
const playerSearchKeyword = ref('')
// 选手列表数据
const players = ref<any[]>([])
// 选手加载状态
const playerLoading = ref(false)

// 搜索选手（数据来源：后端 /players/search；若失败则返回空列表）
const handlePlayerSearch = async () => {
  playerLoading.value = true
  try {
    const payload = {
      filter: {
        playerId: playerSearchKeyword.value ? playerSearchKeyword.value : undefined,
      },
      page: 1,
      pageSize: 20,
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

// 获取战队数据（数据来源：后端 /teams/search；若后端不可用，使用模拟数据）
const fetchTeams = async () => {
  loading.value = true
  try {
    const payload = {
      filter: {},
      page: 1,
      pageSize: 20,
      sort: [{ field: 'name', direction: 'asc' }],
    }

    // 模拟战队数据
    const mockTeams = [
      { id: 'team-blg', name: 'BLG', wins: 12, losses: 4 },
      { id: 'team-tes', name: 'TES', wins: 10, losses: 6 },
    ]
    teams.value = mockTeams

    try {
      const res: any = await request.post('/teams/search', payload)
      if (res && res.items) {
        const apiTeams = res.items.map((t: any) => ({
          id: t.teamId || t.id,
          name: t.teamName || t.name,
          wins: t.wins ?? 0,
          losses: t.losses ?? 0,
        }))
        teams.value = apiTeams.length ? apiTeams : teams.value
      }
    } catch (e) {
      console.log('Backend not available, using mock data only')
    }
  } catch (error) {
    console.error('Failed to fetch teams:', error)
  } finally {
    loading.value = false
  }
}

// 获取赛事总览（数据来源：聚合接口 /overview；若后端不可用，使用模拟数据）
const fetchOverview = async () => {
  loading.value = true
  try {
    // 模拟概览数据
    overview.totalMatches = 128
    overview.totalTeams = 18
    overview.totalPlayers = 180

    // 真实接口（若可用）
    try {
      const payload = {
        filter: {
          ...(dateRange.value ? {
            dateRange: {
              from: (dateRange as any)[0],
              to: (dateRange as any)[1],
            },
          } : {}),
        },
      }
      const res: any = await request.post('/overview', payload)
      if (res && res.data) {
        overview.totalMatches = res.data.totalMatches ?? overview.totalMatches
        overview.totalTeams = res.data.totalTeams ?? overview.totalTeams
        overview.totalPlayers = res.data.totalPlayers ?? overview.totalPlayers
      }
    } catch (e) {
      console.log('Backend not available, using mock overview only')
    }
  } catch (error) {
    console.error('Failed to fetch overview:', error)
  } finally {
    loading.value = false
  }
}

// 视图切换：根据选择加载对应数据（数据来源：用户选择 + 后端接口）
watch(viewMode, (mode) => {
  if (mode === 'overview') { fetchOverview(); fetchMatches() }
  if (mode === 'teams') fetchTeams()
  if (mode === 'players') handlePlayerSearch()
})

// 页面挂载：默认加载概览与选手信息（数据来源：后端接口/模拟数据）
onMounted(() => {
  fetchOverview()
  fetchMatches()
  handlePlayerSearch()
})
</script>

<template>
  <div class="h-full flex flex-col bg-slate-50">
    <!-- 顶部导航栏 -->
    <div class="flex-none bg-white border-b border-gray-200 px-6 py-4 shadow-sm sticky top-0 z-20">
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 class="text-2xl font-bold text-gray-800 tracking-tight flex items-center gap-2">
            <span class="w-2 h-8 bg-blue-600 rounded-full inline-block"></span>
            赛事数据中心
          </h2>
          <p class="text-gray-500 text-sm mt-1 ml-4">LPL 2024 Summer 实时数据追踪</p>
        </div>
        
        <div class="flex items-center space-x-4 bg-gray-100 p-1 rounded-lg">
          <button 
            v-for="mode in ['overview', 'teams', 'players']" 
            :key="mode"
            @click="viewMode = mode as any"
            :class="[
              'px-4 py-2 rounded-md text-sm font-medium transition-all duration-200',
              viewMode === mode 
                ? 'bg-white text-blue-600 shadow-sm' 
                : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200/50'
            ]"
          >
            {{ mode === 'overview' ? '赛事总览' : mode === 'teams' ? '战队数据' : '选手信息' }}
          </button>
        </div>
      </div>

      <!-- 二级筛选栏 -->
      <div class="mt-4 flex flex-wrap items-center justify-between gap-4 pt-4 border-t border-gray-100">
        <!-- 左侧：日期筛选 -->
        <div class="flex items-center gap-3">
          <template v-if="viewMode !== 'players'">
            <span class="text-sm font-medium text-gray-600 bg-gray-100 px-2 py-1 rounded">日期范围</span>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              @change="handleDateChange"
              size="default"
              class="!w-64"
            />
          </template>
        </div>

        <!-- 右侧：搜索或其他操作 -->
        <div v-if="viewMode === 'players'" class="flex items-center gap-2 w-full md:w-auto">
          <el-input
            v-model="playerSearchKeyword"
            placeholder="输入选手 ID 搜索..."
            class="w-full md:w-72"
            @keyup.enter="handlePlayerSearch"
            clearable
            @clear="handlePlayerSearch"
          >
            <template #prefix>
              <el-icon class="text-gray-400"><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="handlePlayerSearch" class="!px-6">搜索</el-button>
        </div>
      </div>
    </div>

    <!-- 内容展示区域 -->
    <div class="flex-1 overflow-auto p-6 scroll-smooth">
      <!-- 概览视图 -->
      <div v-if="viewMode === 'overview'" class="space-y-6" v-loading="loading">
        <!-- 统计卡片 -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-6">
          <div class="bg-gradient-to-br from-blue-500 to-blue-600 rounded-xl p-6 text-white shadow-lg shadow-blue-200 transform hover:-translate-y-1 transition-transform duration-300">
            <div class="text-blue-100 text-sm font-medium mb-1">总场次 Matches</div>
            <div class="text-4xl font-bold tracking-tight">{{ overview.totalMatches }}</div>
          </div>
          <div class="bg-gradient-to-br from-emerald-500 to-emerald-600 rounded-xl p-6 text-white shadow-lg shadow-emerald-200 transform hover:-translate-y-1 transition-transform duration-300">
            <div class="text-emerald-100 text-sm font-medium mb-1">总战队 Teams</div>
            <div class="text-4xl font-bold tracking-tight">{{ overview.totalTeams }}</div>
          </div>
          <div class="bg-gradient-to-br from-violet-500 to-violet-600 rounded-xl p-6 text-white shadow-lg shadow-violet-200 transform hover:-translate-y-1 transition-transform duration-300">
            <div class="text-violet-100 text-sm font-medium mb-1">总选手 Players</div>
            <div class="text-4xl font-bold tracking-tight">{{ overview.totalPlayers }}</div>
          </div>
        </div>

        <!-- 比赛列表 -->
        <div>
          <h3 class="text-lg font-bold text-gray-800 mb-4 flex items-center gap-2">
            <span class="w-1 h-5 bg-gray-800 rounded-full"></span>
            近期比赛
          </h3>
          <div v-if="matches.length === 0 && !loading" class="bg-white rounded-xl p-10 text-center text-gray-400 border border-gray-100 shadow-sm">
            暂无比赛数据
          </div>
          <div class="grid gap-4">
            <div v-for="match in matches" :key="match.id"
                 class="group bg-white p-5 rounded-xl border border-gray-100 shadow-sm hover:shadow-md hover:border-blue-200 transition-all cursor-pointer flex flex-col sm:flex-row items-center justify-between gap-4"
                 @click="goToMatch(match.id)">
              
              <!-- 时间与赛事 -->
              <div class="flex flex-col sm:w-1/4 gap-1">
                <div class="text-sm font-bold text-gray-700">{{ match.tournament }}</div>
                <div class="text-xs text-gray-400 flex items-center gap-1">
                  <span class="w-2 h-2 rounded-full bg-gray-300"></span>
                  {{ match.time }}
                </div>
              </div>

              <!-- 比分核心区 -->
              <div class="flex items-center justify-center flex-1 gap-6">
                <div class="text-right w-24 font-bold text-lg text-gray-800 group-hover:text-blue-600 transition-colors">{{ match.teamA }}</div>
                <div class="px-4 py-1 bg-gray-50 border border-gray-200 rounded-full font-mono font-bold text-xl text-gray-800 group-hover:bg-blue-50 group-hover:border-blue-100 group-hover:text-blue-600 transition-colors">
                  {{ match.scoreA }} : {{ match.scoreB }}
                </div>
                <div class="text-left w-24 font-bold text-lg text-gray-800 group-hover:text-blue-600 transition-colors">{{ match.teamB }}</div>
              </div>

              <!-- 操作 -->
              <div class="sm:w-1/4 text-right">
                <span class="text-sm text-gray-400 group-hover:text-blue-500 flex items-center justify-end gap-1">
                  查看详情 <span class="text-lg">→</span>
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 战队列表 -->
      <div v-else-if="viewMode === 'teams'" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6" v-loading="loading">
        <div v-if="teams.length === 0 && !loading" class="col-span-full text-center py-20 text-gray-400">
          暂无战队数据
        </div>
        <div v-for="team in teams" :key="team.id" 
             class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm hover:shadow-lg hover:-translate-y-1 transition-all duration-300 flex flex-col items-center text-center group">
          <div class="w-16 h-16 bg-gray-50 rounded-full flex items-center justify-center text-xl font-bold text-gray-400 mb-4 group-hover:bg-blue-50 group-hover:text-blue-600 transition-colors">
            {{ team.name.substring(0, 1).toUpperCase() }}
          </div>
          <div class="font-bold text-xl text-gray-800 mb-2">{{ team.name }}</div>
          <div class="text-sm text-gray-500 bg-gray-50 px-3 py-1 rounded-full group-hover:bg-blue-50 group-hover:text-blue-600 transition-colors">
            {{ team.wins }} 胜 / {{ team.losses }} 负
          </div>
        </div>
      </div>

      <!-- 选手列表 -->
      <div v-else class="space-y-4">
        <div v-loading="playerLoading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          <div v-if="players.length === 0 && !playerLoading" class="col-span-full text-center py-20 text-gray-400">
            未找到选手数据
          </div>
          <div v-for="player in players" :key="player.playerId"
               class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm hover:shadow-lg hover:border-blue-200 transition-all duration-300 flex items-center space-x-4">
            <div class="w-14 h-14 bg-gradient-to-br from-gray-100 to-gray-200 rounded-full flex items-center justify-center text-gray-500 font-bold text-xl shadow-inner">
              {{ player.playerId.substring(0, 1).toUpperCase() }}
            </div>
            <div>
              <div class="font-bold text-lg text-gray-800">{{ player.playerId }}</div>
              <div class="text-sm text-gray-500 mt-1 flex items-center gap-2">
                <span class="w-2 h-2 rounded-full bg-green-400"></span>
                {{ player.role || 'Unknown' }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 移除旧的 hacky 样式，使用 Tailwind 类代替 */
:deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: none !important;
  border: 1px solid #e5e7eb;
  transition: all 0.2s;
}
:deep(.el-input__wrapper:hover) {
  border-color: #3b82f6;
}
:deep(.el-input__wrapper.is-focus) {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
}

:deep(.el-date-editor.el-input__wrapper) {
  width: 100%;
}
</style>
