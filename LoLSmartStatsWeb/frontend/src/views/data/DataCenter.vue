<script setup lang="ts">
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Filter, Calendar } from '@element-plus/icons-vue'
import type { Team, MatchListItem, Player } from '@/types/models'
import { dataApi } from '@/api/data'

const router = useRouter()

const viewMode = ref<'overview' | 'teams' | 'players'>('overview')
const loading = ref(false)
const selectedYear = ref('25')

const matches = ref<MatchListItem[]>([])
const teams = ref<Team[]>([])
const players = ref<Player[]>([])
const playerLoading = ref(false)
const playerSearchKeyword = ref('')

const tournamentOptions = ref<string[]>([])
const stageOptions = ref<string[]>([])

const matchFilters = reactive({
  tournamentName: '',
  stage: '',
  teamId: null as number | null
})

const yearOptions = computed(() => {
  const options = []
  for (let i = 25; i >= 14; i--) {
    options.push({ label: `20${i} 赛季`, value: String(i) })
  }
  return options
})

const fetchMatches = async () => {
  loading.value = true
  try {
    const year = `20${selectedYear.value}`
    const filter: any = {
      dateRange: {
        from: `${year}-01-01`,
        to: `${year}-12-31`
      }
    }
    if (matchFilters.tournamentName) {
      filter.tournamentName = matchFilters.tournamentName
    }
    if (matchFilters.stage) {
      filter.stage = matchFilters.stage
    }
    if (matchFilters.teamId) {
      filter.teamIds = [matchFilters.teamId]
    }
    const res: any = await dataApi.searchMatches(filter, 1, 20)
    const items = Array.isArray(res?.items) ? res.items : []
    matches.value = items.map((m: any) => {
      const gamesCount = m.gamesCount || 0
      const winnerTeamId = m.winnerTeamId
      let scoreTeam1 = 0
      let scoreTeam2 = 0
      if (gamesCount > 0 && winnerTeamId && m.team1 && m.team2) {
        const winnerScore = Math.floor(gamesCount / 2) + 1
        const loserScore = gamesCount - winnerScore
        if (winnerTeamId === m.team1.id) {
          scoreTeam1 = winnerScore
          scoreTeam2 = loserScore
        } else {
          scoreTeam1 = loserScore
          scoreTeam2 = winnerScore
        }
      }
      return {
        id: m.matchId,
        date: m.matchDate,
        tournamentName: m.tournamentName,
        stage: m.stage,
        team1Id: m.team1?.id,
        team2Id: m.team2?.id,
        winnerId: m.winnerTeamId,
        team1Name: m.team1?.shortName || m.team1?.name,
        team2Name: m.team2?.shortName || m.team2?.name,
        scoreTeam1,
        scoreTeam2
      } as MatchListItem
    })
  } catch (error) {
    matches.value = []
  } finally {
    loading.value = false
  }
}

const fetchOptions = async () => {
  const year = `20${selectedYear.value}`
  const scope = {
    dateRange: {
      from: `${year}-01-01`,
      to: `${year}-12-31`
    }
  }
  const res: any = await dataApi.getOptions(scope, ['tournaments', 'stages', 'teams'])
  const listTeams = Array.isArray(res?.teams) ? res.teams : []
  teams.value = listTeams.map((t: any) => ({
    id: t.id,
    name: t.name,
    shortName: t.shortName
  }))
  tournamentOptions.value = Array.isArray(res?.tournaments) ? res.tournaments : []
  stageOptions.value = Array.isArray(res?.stages) ? res.stages : []
}

const handlePlayerSearch = async () => {
  playerLoading.value = true
  try {
    if (!playerSearchKeyword.value.trim()) {
      players.value = []
      return
    }
    const res: any = await dataApi.searchPlayers(playerSearchKeyword.value.trim(), 1, 50)
    const items = Array.isArray(res?.items) ? res.items : []
    players.value = items.map((p: any) => ({
      id: p.id,
      name: p.name
    }))
  } finally {
    playerLoading.value = false
  }
}

const applyMatchFilters = () => {
  fetchMatches()
}

const goToMatch = (id: number) => {
  router.push({ name: 'match-detail', params: { id: id.toString() } })
}

watch(viewMode, (newVal) => {
  if (newVal === 'overview') {
    fetchOptions()
    fetchMatches()
  }
  if (newVal === 'teams') {
    fetchOptions()
  }
  if (newVal === 'players') {
    handlePlayerSearch()
  }
})

watch(selectedYear, () => {
  fetchOptions()
  if (viewMode.value === 'overview') {
    fetchMatches()
  }
})

onMounted(() => {
  fetchOptions()
  fetchMatches()
})
</script>

<template>
  <div class="h-full flex flex-col bg-slate-50">
    
    <!-- 1. 顶部 Header -->
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

    <!-- 2. 二级工具栏 -->
    <div class="flex-none bg-white border-b border-gray-100 px-6 py-3 flex items-center justify-between z-20 shadow-sm min-h-[60px]">
      <div class="flex items-center gap-3 w-full">
        <template v-if="viewMode === 'overview'">
          <el-select v-model="selectedYear" placeholder="年份" size="small" class="!w-32">
            <template #prefix>
              <el-icon>
                <Calendar />
              </el-icon>
            </template>
            <el-option v-for="item in yearOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="h-4 w-px bg-gray-200 mx-1.5"></div>
          <el-select
            v-model="matchFilters.tournamentName"
            placeholder="赛事"
            size="small"
            clearable
            class="!w-40"
          >
            <template #prefix>
              <el-icon>
                <Filter />
              </el-icon>
            </template>
            <el-option v-for="item in tournamentOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select
            v-model="matchFilters.stage"
            placeholder="阶段"
            size="small"
            clearable
            class="!w-32"
          >
            <el-option v-for="item in stageOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select
            v-model="matchFilters.teamId"
            placeholder="战队"
            size="small"
            clearable
            class="!w-40"
          >
            <el-option
              v-for="team in teams"
              :key="team.id"
              :label="team.shortName || team.name"
              :value="team.id"
            />
          </el-select>
          <el-button type="primary" size="small" class="ml-2" @click="applyMatchFilters">筛选比赛</el-button>
        </template>
        <template v-else>
          <div class="text-sm text-gray-400">
            {{ viewMode === 'teams' ? '浏览所有注册战队' : '搜索职业选手生涯数据' }}
          </div>
        </template>
      </div>
      <div v-if="viewMode === 'players'" class="flex items-center gap-2">
        <el-input v-model="playerSearchKeyword" placeholder="Search Player..." size="small" class="!w-64" clearable>
          <template #prefix>
            <el-icon>
              <Search />
            </el-icon>
          </template>
        </el-input>
        <el-button size="small" @click="handlePlayerSearch">搜索</el-button>
      </div>
    </div>

    <!-- 3. 主内容区 -->
    <div class="flex-1 overflow-auto p-6 scroll-smooth">
      <!-- MODE: OVERVIEW -->
      <div v-if="viewMode === 'overview'" class="h-full">
        <div class="animate-fade-in-up space-y-4 max-w-5xl mx-auto" v-loading="loading">
            <div v-if="!loading && matches.length === 0" class="text-center py-20 text-gray-400 bg-white rounded-xl border border-gray-100">
               暂无比赛记录
            </div>
            <div 
              v-for="match in matches" 
              :key="match.id"
              class="bg-white p-5 rounded-xl border border-gray-100 shadow-sm hover:shadow-md hover:border-blue-300 transition-all cursor-pointer flex items-center justify-between group"
              @click="goToMatch(match.id)"
            >
                <div class="flex flex-col w-32 border-r border-gray-100 pr-4">
                  <span class="text-sm font-bold text-gray-800">{{ match.tournamentName }}</span>
                  <span class="text-xs text-gray-400 mt-0.5">{{ match.stage }}</span>
                  <span class="text-xs text-gray-300 font-mono mt-1">{{ match.date }}</span>
                </div>
                <div class="flex-1 flex items-center justify-center px-8">
                   <div class="flex-1 text-right font-bold text-lg text-gray-800" :class="{'text-blue-600': match.winnerId === match.team1Id}">
                      {{ match.team1Name }}
                   </div>
                   <div class="mx-6 px-4 py-1 bg-slate-50 border border-slate-200 rounded-lg text-xl font-bold font-mono text-slate-700">
                      {{ match.scoreTeam1 }} : {{ match.scoreTeam2 }}
                   </div>
                   <div class="flex-1 text-left font-bold text-lg text-gray-800" :class="{'text-blue-600': match.winnerId === match.team2Id}">
                      {{ match.team2Name }}
                   </div>
                </div>
                <div class="w-32 text-right text-xs text-gray-400">
                  <div>获胜方</div>
                  <div class="font-bold text-gray-700">
                    {{ match.winnerId === match.team1Id ? match.team1Name : match.team2Name }}
                  </div>
                </div>
                <div class="text-gray-300 group-hover:text-blue-500 transition-colors">
                   <span class="text-sm mr-2 opacity-0 group-hover:opacity-100 transition-opacity">详情</span>
                   <span class="text-lg">→</span>
                </div>
            </div>
        </div>

      </div>

      <!-- MODE: TEAMS -->
      <div v-else-if="viewMode === 'teams'" class="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-4 gap-4 animate-fade-in">
         <div v-for="team in teams" :key="team.id" class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm hover:shadow-md transition-all group cursor-pointer flex flex-col items-center">
             <div class="w-12 h-12 bg-gray-50 rounded-lg flex items-center justify-center font-bold text-gray-400 mb-3 group-hover:bg-blue-50 group-hover:text-blue-600">{{ team.shortName[0] }}</div>
             <div class="text-lg font-bold text-gray-800">{{ team.shortName }}</div>
             <div class="text-xs text-gray-400">{{ team.name }}</div>
        </div>
      </div>

      <!-- MODE: PLAYERS -->
      <div v-else class="grid grid-cols-1 md:grid-cols-4 gap-4 animate-fade-in">
         <div v-for="player in players" :key="player.id" class="bg-white p-4 rounded-xl border border-gray-100 shadow-sm hover:border-blue-300 transition-all flex items-center space-x-3">
            <div class="w-10 h-10 rounded-full bg-gray-100 flex items-center justify-center text-gray-500 text-sm font-bold">{{ player.name[0] }}</div>
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
.animate-fade-in { animation: fadeIn 0.4s ease-out; }
.animate-fade-in-up { animation: fadeInUp 0.4s ease-out; }
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes fadeInUp { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }

:deep(.el-input__wrapper), :deep(.el-select__wrapper) {
  box-shadow: none !important;
  border: 1px solid #e5e7eb;
  background-color: #fff;
  border-radius: 6px;
}
:deep(.el-input__wrapper:hover), :deep(.el-select__wrapper:hover) {
  border-color: #3b82f6;
}
:deep(.el-input__wrapper.is-focus), :deep(.el-select__wrapper.is-focused) {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1) !important;
}
</style>
