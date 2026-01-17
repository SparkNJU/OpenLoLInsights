<script setup lang="ts">
import { ref, reactive, onMounted, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router' // [新增] 引入 useRoute
import { Search, Filter, ArrowLeft, Calendar } from '@element-plus/icons-vue'
import type { Team, MatchListItem, Player } from '@/types/models'
import { dataApi } from '@/api/data'

const router = useRouter()
const route = useRoute() // [新增] 获取当前路由参数

const getLogoUrl = (name: string) => {
  return new URL(`../../assets/logos/${name}`, import.meta.url).href
}

// --- 1. 状态管理 ---
const viewMode = ref<'overview' | 'teams' | 'players'>('overview')
const browsingLevel = ref<'gallery' | 'matches'>('gallery')
const activeTournament = ref<any>(null)
const loading = ref(false)
const selectedYear = ref('25')

const matches = ref<MatchListItem[]>([])
const teams = ref<Team[]>([])
const players = ref<Player[]>([])
const playerLoading = ref(false)
const playerSearchKeyword = ref('')

// --- 2. 赛事卡片数据 ---
const tournaments = computed(() => {
  const year = selectedYear.value
  const fullYear = `20${year}`
  return [
    { id: 'lpl_spring', code: 'lpl', name: `${fullYear} LPL 春季赛`, period: `${fullYear}-01-15 ~ ${fullYear}-04-20`, logo: 'lpl.png' },
    { id: 'lck_spring', code: 'lck', name: `${fullYear} LCK 春季赛`, period: `${fullYear}-01-17 ~ ${fullYear}-04-14`, logo: 'lck.png' },
    { id: 'msi', code: 'msi', name: `${fullYear} 季中冠军赛`, period: `${fullYear}-05-01 ~ ${fullYear}-05-19`, logo: '季中冠军赛.png' },
    { id: 'lpl_summer', code: 'lpl', name: `${fullYear} LPL 夏季赛`, period: `${fullYear}-06-01 ~ ${fullYear}-08-30`, logo: 'lpl.png' },
    { id: 's_world', code: 's', name: `${fullYear} 全球总决赛`, period: `${fullYear}-10-01 ~ ${fullYear}-11-02`, logo: '全球冠军赛.png' },
    { id: 'rr', code: 'rr', name: `${fullYear} 亚洲对抗赛`, period: `${fullYear}-07-05 ~ ${fullYear}-07-08`, logo: '亚洲对抗赛.png' },
    { id: 'first_stand', code: 'msc', name: `${fullYear} 全球先锋赛`, period: `${fullYear}-03-10 ~ ${fullYear}-03-16`, logo: '全球先锋赛.png' }
  ]
})

const yearOptions = computed(() => {
  const options = []
  for (let i = 25; i >= 14; i--) {
    options.push({ label: `20${i} 赛季`, value: String(i) })
  }
  return options
})

// --- 3. 核心逻辑 (修改了路由同步) ---

// 进入赛事详情 (Level 1 -> Level 2)
const handleSelectTournament = (tournament: any) => {
  activeTournament.value = tournament
  browsingLevel.value = 'matches'
  // [关键修改] 将状态写入 URL，这样刷新或返回时状态还在
  router.replace({ query: { ...route.query, year: selectedYear.value, tournament: tournament.code } })
  fetchMatches(tournament.code)
}

// 返回赛事列表 (Level 2 -> Level 1)
const handleBackToGallery = () => {
  browsingLevel.value = 'gallery'
  activeTournament.value = null
  matches.value = []
  // [关键修改] 清除 URL 中的 tournament 参数
  router.replace({ query: { ...route.query, tournament: undefined } })
}

const goToMatch = (id: number) => {
  // 跳转时，当前的 URL query (year, tournament) 会保留在历史记录里
  // 当用户点浏览器返回时，这些参数会恢复
  router.push({ name: 'match-detail', params: { id: id.toString() } })
}

// [新增] 初始化状态：检查 URL 参数
const initFromUrl = () => {
  const { year, tournament } = route.query
  
  // 1. 恢复年份
  if (year) {
    selectedYear.value = year as string
  }

  // 2. 恢复赛事详情页
  if (tournament) {
    // 必须等待 tournaments 计算属性更新 (虽然 computed 是同步的，但稳妥起见直接查找)
    const target = tournaments.value.find(t => t.code === tournament)
    if (target) {
      activeTournament.value = target
      browsingLevel.value = 'matches'
      fetchMatches(tournament as string)
    }
  }
}

const fetchMatches = async (tournamentType?: string) => {
  loading.value = true
  try {
    const year = `20${selectedYear.value}`
    const tournament = activeTournament.value
    const filter: any = {
      tournamentName: tournament?.name,
      dateRange: {
        from: `${year}-01-01`,
        to: `${year}-12-31`
      }
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

const fetchTeams = async () => {
  const year = `20${selectedYear.value}`
  const scope = {
    dateRange: {
      from: `${year}-01-01`,
      to: `${year}-12-31`
    }
  }
  const res: any = await dataApi.getOptions(scope, ['teams'])
  const list = Array.isArray(res?.teams) ? res.teams : []
  teams.value = list.map((t: any) => ({
    id: t.id,
    name: t.name,
    shortName: t.shortName
  }))
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
  
  

watch(viewMode, (newVal) => {
  if (newVal === 'teams') fetchTeams()
  if (newVal === 'players') handlePlayerSearch()
})

watch(selectedYear, () => {
  // 如果在详情页切换年份，退回列表并更新 URL
  if (browsingLevel.value === 'matches') {
    handleBackToGallery()
    // handleBackToGallery 里面已经处理了 router.replace
  }
  // 如果是在外面切换年份，也更新一下 URL 保持同步
  else {
    router.replace({ query: { ...route.query, year: selectedYear.value } })
  }
})

onMounted(() => {
  // [关键] 组件加载时，根据 URL 恢复状态
  initFromUrl()
  
  if (viewMode.value === 'teams') fetchTeams()
  if (viewMode.value === 'players') handlePlayerSearch()
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
        <!-- A: 选手/战队 -->
        <template v-if="viewMode !== 'overview'">
           <div class="text-sm text-gray-400">{{ viewMode === 'teams' ? '浏览所有注册战队' : '搜索职业选手生涯数据' }}</div>
        </template>

        <!-- B: 赛事总览 - 列表模式 -->
        <template v-else-if="browsingLevel === 'matches'">
           <el-button link @click="handleBackToGallery" class="!px-0 mr-2 hover:text-blue-600 transition-colors">
              <el-icon class="mr-1 text-lg"><ArrowLeft /></el-icon>
              <span class="text-base font-bold text-gray-700">返回赛事列表</span>
           </el-button>
           <div class="h-4 w-px bg-gray-300 mx-2"></div>
           <span class="font-bold text-gray-800">{{ activeTournament?.name }}</span>
           <el-tag size="small" type="info" class="ml-2">{{ activeTournament?.period }}</el-tag>
        </template>

        <!-- C: 赛事总览 - 卡片墙模式 -->
        <template v-else>
           <el-select v-model="selectedYear" placeholder="年份" size="small" class="!w-36">
              <template #prefix><el-icon><Calendar /></el-icon></template>
              <el-option v-for="item in yearOptions" :key="item.value" :label="item.label" :value="item.value" />
           </el-select>
        </template>
      </div>

      <!-- 搜索框 -->
      <div v-if="viewMode === 'players'" class="flex items-center gap-2">
        <el-input v-model="playerSearchKeyword" placeholder="Search Player..." size="small" class="!w-64" clearable>
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
    </div>

    <!-- 3. 主内容区 -->
    <div class="flex-1 overflow-auto p-6 scroll-smooth">
      
      <!-- MODE: OVERVIEW -->
      <div v-if="viewMode === 'overview'" class="h-full">
        
        <!-- Level 1: 赛事卡片墙 -->
        <div v-if="browsingLevel === 'gallery'" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-3 gap-6 animate-fade-in">
          <div 
            v-for="t in tournaments" 
            :key="t.id"
            @click="handleSelectTournament(t)"
            class="group bg-white rounded-xl border border-gray-100 p-5 cursor-pointer shadow-sm hover:shadow-lg hover:border-blue-200 transition-all duration-300 flex items-center gap-5 relative overflow-hidden"
          >
            <!-- 装饰背景 -->
            <div class="absolute right-0 top-0 w-24 h-24 bg-gray-50 rounded-bl-full -mr-4 -mt-4 transition-transform group-hover:scale-110"></div>
            
            <div class="w-16 h-16 shrink-0 relative z-10">
               <img :src="getLogoUrl(t.logo)" class="w-full h-full object-contain drop-shadow-sm group-hover:scale-105 transition-transform duration-300" alt="logo" />
            </div>
            <div class="flex-1 z-10">
               <h3 class="font-bold text-gray-800 text-lg mb-1 group-hover:text-blue-600 transition-colors">{{ t.name }}</h3>
               <div class="text-xs text-gray-400 font-mono">{{ t.period }}</div>
            </div>
          </div>
        </div>

        <!-- Level 2: 比赛列表 -->
        <div v-else class="animate-fade-in-up space-y-4 max-w-5xl mx-auto" v-loading="loading">
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
                  <span class="text-sm font-bold text-gray-800">{{ match.tournamentName.split('/')[1].toUpperCase() }}</span>
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
