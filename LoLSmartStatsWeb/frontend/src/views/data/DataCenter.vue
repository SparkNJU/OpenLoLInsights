<script setup lang="ts">
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Filter, TrophyBase } from '@element-plus/icons-vue'

//引入接口定义
import type { Team, MatchListItem, Player } from '@/types/models'
const router = useRouter()

// --- 2. 状态管理 ---

const viewMode = ref<'overview' | 'teams' | 'players'>('overview')
const loading = ref(false)

// 筛选状态
const selectedYear = ref('24') // 默认选中 2024 (S14)
const selectedTournamentType = ref('') // 默认全部赛事

const matches = ref<MatchListItem[]>([])
const teams = ref<Team[]>([])
const players = ref<Player[]>([])
const playerLoading = ref(false)
const playerSearchKeyword = ref('')

// --- 3. 筛选选项配置 ---

// 生成 S4 (14) 到 S25 (25) 的年份选项
const yearOptions = computed(() => {
  const options = []
  const startYear = 14 // 2014 (S4)
  const endYear = 25   // 2025 (S15)
  
  for (let i = endYear; i >= startYear; i--) {
    const fullYear = 2000 + i
    const season = i - 10 // 14 -> S4
    options.push({
      label: `${fullYear} 赛季 (S${season})`,
      value: String(i)
    })
  }
  return options
})

// 赛事类型映射 (根据你的描述)
const tournamentOptions = [
  { label: '全部赛事', value: '' },
  { label: '全球总决赛 (S赛)', value: 's' },
  { label: '季中冠军赛 (MSI)', value: 'msi' },
  { label: 'LPL 职业联赛', value: 'lpl' },
  { label: 'LCK 职业联赛', value: 'lck' },
  { label: '亚洲对抗赛', value: 'rr' }, // 假设 Rift Rivals 简写为 rr
  { label: '全球先锋赛', value: 'msc' } // 假设 Mid-Season Cup 或其他简写
]

// --- 4. 数据获取逻辑 ---

// 获取比赛列表
const fetchMatches = async () => {
  loading.value = true
  try {
    // 构造筛选条件
    // 如果选了具体赛事，组合成 "24/lpl"；如果没选，只用 "24" 做模糊匹配
    const tournamentFilter = selectedTournamentType.value 
      ? `${selectedYear.value}/${selectedTournamentType.value}`
      : `${selectedYear.value}/` // 后端可以用 startWith 匹配

    console.log('Fetching matches with filter:', tournamentFilter)

    // 模拟数据：根据筛选条件生成 Mock 数据
    const mockMatches: MatchListItem[] = []
    
    // 模拟生成几条数据用于展示效果
    if (selectedTournamentType.value === '' || selectedTournamentType.value === 'lpl') {
        mockMatches.push({
            id: 1001, date: `20${selectedYear.value}-07-20`, 
            tournamentName: `${selectedYear.value}/lpl`, stage: '常规赛',
            team1Id: 101, team1Name: 'BLG', team2Id: 102, team2Name: 'TES',
            winnerId: 101, scoreTeam1: 2, scoreTeam2: 1
        })
    }
    if (selectedTournamentType.value === '' || selectedTournamentType.value === 's') {
         mockMatches.push({
            id: 1002, date: `20${selectedYear.value}-10-15`, 
            tournamentName: `${selectedYear.value}/s`, stage: '小组赛',
            team1Id: 103, team1Name: 'JDG', team2Id: 104, team2Name: 'T1',
            winnerId: 104, scoreTeam1: 1, scoreTeam2: 3
        })
    }

    matches.value = mockMatches

    // 真实请求示例:
    /*
    const payload = {
      filter: { tournament_name_pattern: tournamentFilter }, 
      page: 1, pageSize: 20
    }
    const res = await request.post('/matches/search', payload)
    matches.value = res.items
    */

  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

// 获取战队
const fetchTeams = async () => {
  loading.value = true
  try {
    const mockTeams: Team[] = [
      { id: 101, name: 'Bilibili Gaming', shortName: 'BLG' },
      { id: 102, name: 'Top Esports', shortName: 'TES' },
      { id: 103, name: 'JD Gaming', shortName: 'JDG' },
    ]
    teams.value = mockTeams
  } finally {
    loading.value = false
  }
}

// 获取选手
const handlePlayerSearch = async () => {
  playerLoading.value = true
  try {
    const mockPlayers: Player[] = [
      { id: 2001, name: 'Bin' },
      { id: 2002, name: 'Knight' },
      { id: 2003, name: 'Elk' },
    ]
    if (playerSearchKeyword.value) {
      players.value = mockPlayers.filter(p => p.name.toLowerCase().includes(playerSearchKeyword.value.toLowerCase()))
    } else {
      players.value = mockPlayers
    }
  } finally {
    playerLoading.value = false
  }
}

const handleFilterChange = () => {
  if (viewMode.value === 'overview') fetchMatches()
}

const goToMatch = (id: number) => {
  router.push({ name: 'match-detail', params: { id: id.toString() } })
}

watch(viewMode, (mode) => {
  if (mode === 'overview') fetchMatches()
  if (mode === 'teams') fetchTeams()
  if (mode === 'players') handlePlayerSearch()
})

onMounted(() => {
  fetchMatches()
  handlePlayerSearch()
})
</script>

<template>
  <div class="h-full flex flex-col bg-slate-50">
    <!-- 1. Header (保持样式一致) -->
    <div class="h-16 flex-none bg-white border-b border-gray-100 px-6 flex items-center justify-between sticky top-0 z-30">
      <div class="flex items-center space-x-3">
        <span class="w-1.5 h-6 bg-blue-600 rounded-full inline-block shadow-sm shadow-blue-200"></span>
        <span class="font-bold text-lg text-gray-800">赛事数据中心</span>
        <el-tag type="primary" effect="light" round class="font-medium">Beta</el-tag>
      </div>

      <div class="flex bg-gray-100/80 p-1 rounded-lg">
        <button 
          v-for="mode in ['overview', 'teams', 'players']" 
          :key="mode"
          @click="viewMode = mode as any"
          class="px-3 py-1.5 rounded-md text-xs font-medium transition-all duration-200"
          :class="[viewMode === mode ? 'bg-white text-blue-600 shadow-sm' : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200/50']"
        >
          {{ mode === 'overview' ? '赛事总览' : mode === 'teams' ? '战队列表' : '选手名录' }}
        </button>
      </div>
    </div>

    <!-- 2. Sub-Header (筛选区域) -->
    <div class="flex-none bg-white border-b border-gray-100 px-6 py-3 flex items-center justify-between z-20 shadow-sm">
      
      <!-- [重要修改] 左侧：年份 + 赛事类型 双重筛选 -->
      <div class="flex items-center gap-3">
        <template v-if="viewMode === 'overview'">
           <!-- 年份选择 -->
           <el-select 
             v-model="selectedYear" 
             placeholder="选择年份" 
             size="small" 
             class="!w-40"
             @change="handleFilterChange"
           >
              <template #prefix><el-icon><Filter /></el-icon></template>
              <el-option 
                v-for="item in yearOptions" 
                :key="item.value" 
                :label="item.label" 
                :value="item.value" 
              />
           </el-select>

           <!-- 赛事类型选择 -->
           <el-select 
             v-model="selectedTournamentType" 
             placeholder="赛事类型" 
             size="small" 
             class="!w-48"
             @change="handleFilterChange"
           >
              <template #prefix><el-icon><TrophyBase /></el-icon></template>
              <el-option 
                v-for="item in tournamentOptions" 
                :key="item.value" 
                :label="item.label" 
                :value="item.value" 
              />
           </el-select>
        </template>

        <template v-else-if="viewMode === 'teams'">
           <div class="text-sm text-gray-400">显示所有注册战队历史数据</div>
        </template>
        
        <template v-else>
           <div class="text-sm text-gray-400">搜索已注册选手信息</div>
        </template>
      </div>

      <!-- 右侧：搜索框 (仅选手模式显示) -->
      <div v-if="viewMode === 'players'" class="flex items-center gap-2">
        <el-input
          v-model="playerSearchKeyword"
          placeholder="Search Player ID..."
          size="small"
          class="!w-64"
          @keyup.enter="handlePlayerSearch"
          clearable
        >
          <template #prefix><el-icon class="text-gray-400"><Search /></el-icon></template>
        </el-input>
      </div>
    </div>

    <!-- 3. 内容展示 -->
    <div class="flex-1 overflow-auto p-6 scroll-smooth">
      
      <!-- VIEW: Overview (去掉了顶部统计卡片，直接展示列表) -->
      <div v-if="viewMode === 'overview'" class="space-y-4" v-loading="loading">
        
        <!-- 列表头部标题 (可选) -->
        <div class="flex items-center justify-between mb-2">
            <h3 class="text-lg font-bold text-gray-800 flex items-center gap-2">
                比赛列表 
                <span class="text-xs font-normal text-gray-400 bg-gray-100 px-2 py-0.5 rounded-full">
                    20{{selectedYear}} Season
                </span>
            </h3>
            <span class="text-xs text-gray-400">共找到 {{ matches.length }} 场比赛</span>
        </div>

        <!-- 比赛列表卡片 -->
        <div class="grid gap-4">
          <div v-if="matches.length === 0" class="text-center py-20 text-gray-400 bg-white rounded-xl border border-gray-100">
             暂无该赛事的比赛记录
          </div>

          <div v-for="match in matches" :key="match.id"
               class="group bg-white p-5 rounded-xl border border-gray-100 shadow-sm hover:shadow-md hover:border-blue-300 transition-all cursor-pointer flex items-center justify-between"
               @click="goToMatch(match.id)">
             
             <div class="flex items-center gap-8 w-full">
                <!-- 赛事信息列 -->
                <div class="flex flex-col w-32 border-r border-gray-100 pr-4">
                  <span class="text-sm font-bold text-gray-800 uppercase tracking-tight">
                      {{ match.tournamentName.split('/')[1].toUpperCase() }}
                  </span>
                  <span class="text-xs text-gray-400">{{ match.stage }}</span>
                  <span class="text-xs text-gray-400 font-mono mt-1">{{ match.date }}</span>
                </div>

                <!-- 比分核心区域 -->
                <div class="flex-1 flex items-center justify-center gap-6">
                  <div class="flex items-center gap-3 justify-end flex-1">
                      <span class="font-bold text-gray-800 text-lg" :class="{'text-blue-600': match.winnerId === match.team1Id}">
                          {{ match.team1Name }}
                      </span>
                      <!-- 这里可以放战队Logo -->
                  </div>

                  <div class="bg-slate-50 border border-slate-200 px-4 py-1 rounded-lg text-xl font-bold text-slate-800 font-mono">
                    {{ match.scoreTeam1 }} : {{ match.scoreTeam2 }}
                  </div>

                  <div class="flex items-center gap-3 justify-start flex-1">
                      <!-- 这里可以放战队Logo -->
                      <span class="font-bold text-gray-800 text-lg" :class="{'text-blue-600': match.winnerId === match.team2Id}">
                          {{ match.team2Name }}
                      </span>
                  </div>
                </div>
                
                <!-- 详情指引 -->
                <div class="w-10 flex justify-end">
                    <span class="text-gray-300 group-hover:text-blue-500 transition-colors text-xl">→</span>
                </div>
             </div>
          </div>
        </div>
      </div>

      <!-- VIEW: Teams (保持不变) -->
      <div v-else-if="viewMode === 'teams'" class="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-4" v-loading="loading">
        <div v-for="team in teams" :key="team.id" class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm hover:shadow-md transition-all group cursor-pointer flex flex-col items-center">
             <div class="w-12 h-12 bg-gray-50 rounded-lg flex items-center justify-center font-bold text-gray-400 mb-3 group-hover:bg-blue-50 group-hover:text-blue-600">
                {{ team.shortName[0] }}
             </div>
             <div class="text-lg font-bold text-gray-800">{{ team.shortName }}</div>
             <div class="text-xs text-gray-400">{{ team.name }}</div>
        </div>
      </div>

      <!-- VIEW: Players (保持不变) -->
      <div v-else class="grid grid-cols-1 md:grid-cols-4 gap-4" v-loading="playerLoading">
         <div v-for="player in players" :key="player.id" class="bg-white p-4 rounded-xl border border-gray-100 shadow-sm hover:border-blue-300 transition-all flex items-center space-x-3">
            <div class="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center text-gray-500 text-sm font-bold">
               {{ player.name[0] }}
            </div>
            <div>
               <div class="font-bold text-gray-800 text-sm">{{ player.name }}</div>
               <div class="text-xs text-gray-400">ID: {{ player.id }}</div>
            </div>
         </div>
      </div>

    </div>
  </div>
</template>

<style scoped>
/* 覆盖 Element UI Select 样式，使其更扁平 */
:deep(.el-input__wrapper) {
  box-shadow: none !important;
  border: 1px solid #e5e7eb;
  background-color: #fff;
  border-radius: 6px;
}
:deep(.el-input__wrapper:hover) {
  border-color: #3b82f6;
}
:deep(.el-input__wrapper.is-focus) {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
}
</style>