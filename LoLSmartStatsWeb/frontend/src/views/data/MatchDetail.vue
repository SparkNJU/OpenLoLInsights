<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Trophy, Medal } from '@element-plus/icons-vue'
//引入接口定义
import type { Team, MatchDetail, Game, PlayerGameStat } from '@/types/models'
import { dataApi } from '@/api/data'

const route = useRoute()
const router = useRouter()
const matchId = route.params.id
const loading = ref(false)

// --- 2. 状态管理 ---
const matchData = ref<MatchDetail | null>(null)
const activeGameTab = ref<string>('1')

// --- 3. 数据加载 ---
const fetchMatchDetail = async () => {
  loading.value = true
  try {
      const numericId = typeof matchId === 'string' ? parseInt(matchId as string, 10) : (matchId as number)
      const res: any = await dataApi.getMatchDetail(numericId)
      if (!res) {
        matchData.value = null
        return
      }

      const match = res.match || {}
      const teams = res.teams || {}
      const games = Array.isArray(res.games) ? res.games : []

      matchData.value = {
        id: match.id,
        date: match.matchDate,
        tournamentName: match.tournamentName,
        stage: match.stage,
        winnerId: match.winnerTeamId,
        team1: teams.team1 as Team,
        team2: teams.team2 as Team,
        games: games.map((g: any) => {
          const participants = Array.isArray(g.participants) ? g.participants : []
          const stats: PlayerGameStat[] = participants.map((p: any, idx: number) => {
            const st = p.stats || {}
            return {
              id: p.playerId ?? idx,
              gameId: g.gameId,
              playerId: p.playerId,
              teamId: p.teamId,
              playerName: p.playerName || '',
              position: p.position || '',
              championName: p.championName || '',
              championNameEn: p.championNameEn || '',
              playerLevel: st.playerLevel ?? 0,
              kills: st.kills ?? 0,
              deaths: st.deaths ?? 0,
              assists: st.assists ?? 0,
              kda: st.kda ?? 0,
              killParticipation: st.killParticipation ?? 0,
              totalDamageDealt: st.totalDamageDealt ?? 0,
              damageDealtPercentage: st.damageDealtPercentage ?? 0,
              totalDamageTaken: st.totalDamageTaken ?? 0,
              damageTakenPercentage: st.damageTakenPercentage ?? 0,
              goldEarned: st.goldEarned ?? 0,
              minionsKilled: st.minionsKilled ?? 0,
              isMvp: !!st.isMvp
            }
          })

          return {
            id: g.gameId,
            matchId: match.id,
            gameNumber: g.gameNumber,
            duration: g.duration,
            blueTeamId: g.blueTeamId,
            redTeamId: g.redTeamId,
            winnerId: g.winnerTeamId,
            stats
          } as Game
        })
      }

      if (matchData.value.games.length > 0) {
        activeGameTab.value = String(matchData.value.games[0].gameNumber || 1)
      }
  } catch (e) {
      console.error(e)
  } finally {
      loading.value = false // 移动到 finally 确保执行
  }
}

// --- 4. 聚合计算逻辑 ---
const seriesScore = computed(() => {
  if (!matchData.value) return { t1: 0, t2: 0 }
  let t1Wins = 0, t2Wins = 0
  matchData.value.games.forEach(g => {
    if (g.winnerId === matchData.value?.team1.id) t1Wins++
    else t2Wins++
  })
  return { t1: t1Wins, t2: t2Wins }
})

const currentGame = computed(() => {
  if (!matchData.value) return undefined
  const active = Number(activeGameTab.value)
  return matchData.value.games.find(g => g.gameNumber === active)
})

const currentGameTeamStats = computed(() => {
  if (!currentGame.value || !matchData.value) return null

  const calc = (teamId: number) => {
    const teamStats = currentGame.value!.stats.filter(s => s.teamId === teamId)
    return {
      totalKills: teamStats.reduce((sum, p) => sum + p.kills, 0),
      players: teamStats
    }
  }

  const blueId = currentGame.value.blueTeamId
  const redId = currentGame.value.redTeamId

  const blueTeamInfo = matchData.value.team1.id === blueId ? matchData.value.team1 : matchData.value.team2
  const redTeamInfo = matchData.value.team1.id === redId ? matchData.value.team1 : matchData.value.team2

  return {
    blue: { ...calc(blueId), info: blueTeamInfo },
    red: { ...calc(redId), info: redTeamInfo }
  }
})

const formatDuration = (seconds: number) => {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

onMounted(() => {
  if (matchId) fetchMatchDetail()
})
</script>

<template>
  <!-- [关键修改] 外层使用 h-full flex flex-col 确保占满 Layout 分配的高度 -->
  <div class="h-full flex flex-col bg-slate-50">
    
    <!-- [关键修改] 头部固定高度 flex-none，不随内容滚动 -->
    <div class="flex-none h-16 bg-white border-b border-gray-100 px-6 flex items-center shadow-sm z-10">
      <el-button :icon="ArrowLeft" circle class="mr-4" @click="router.back()" />
      <div>
        <h2 class="text-xl font-bold text-gray-800 flex items-center gap-2">
          比赛详情
        </h2>
        <p v-if="matchData" class="text-xs text-gray-500 mt-0.5">
          {{ matchData.tournamentName }} · {{ matchData.stage }} · {{ matchData.date }}
        </p>
      </div>
    </div>

    <!-- [关键修改] 内容区域 flex-1 overflow-y-auto，这是实现滚动的关键 -->
    <div class="flex-1 overflow-y-auto p-6 scroll-smooth">
      <div v-loading="loading" class="space-y-6 max-w-7xl mx-auto">
        <el-empty v-if="!loading && !matchData" description="未找到比赛数据" />

        <template v-else-if="matchData">
          
          <!-- 1. 系列赛大比分卡片 -->
          <div class="bg-white p-6 rounded-xl shadow-sm border border-gray-100 relative overflow-hidden">
            <div class="absolute inset-0 bg-gradient-to-r from-blue-50 via-white to-red-50 opacity-40"></div>
            
            <div class="relative flex items-center justify-around z-10">
              <!-- Team 1 -->
              <div class="text-center w-1/3">
                <div class="text-3xl font-bold text-gray-900 mb-1 flex items-center justify-center gap-2">
                   {{ matchData.team1.shortName }}
                   <el-icon v-if="matchData.winnerId === matchData.team1.id" class="text-yellow-500"><Trophy /></el-icon>
                </div>
                <div class="text-gray-400 text-sm">{{ matchData.team1.name }}</div>
              </div>

              <!-- Big Score -->
              <div class="flex flex-col items-center">
                 <div class="text-5xl font-black text-gray-800 tracking-wider">
                    {{ seriesScore.t1 }} : {{ seriesScore.t2 }}
                 </div>
                 <div class="mt-2 px-3 py-1 bg-gray-100 text-gray-500 text-xs rounded-full">BO{{ matchData.games.length > 3 ? 5 : 3 }}</div>
              </div>

              <!-- Team 2 -->
              <div class="text-center w-1/3">
                <div class="text-3xl font-bold text-gray-900 mb-1 flex items-center justify-center gap-2">
                   {{ matchData.team2.shortName }}
                   <el-icon v-if="matchData.winnerId === matchData.team2.id" class="text-yellow-500"><Trophy /></el-icon>
                </div>
                <div class="text-gray-400 text-sm">{{ matchData.team2.name }}</div>
              </div>
            </div>
          </div>

          <!-- 2. 小局详情 -->
          <el-tabs v-model="activeGameTab" type="border-card" class="rounded-xl shadow-sm border-gray-100 custom-tabs">
            <el-tab-pane 
              v-for="game in matchData.games" 
              :key="game.gameNumber" 
              :label="`Game ${game.gameNumber}`" 
              :name="String(game.gameNumber)"
            >
              <!-- Tab 头部信息 -->
              <div class="flex justify-between items-center mb-6 pb-4 border-b border-gray-100">
                 <div class="flex items-center gap-4">
                    <span class="font-bold text-gray-700">时长: {{ formatDuration(game.duration) }}</span>
                    <span class="px-2 py-0.5 rounded text-xs" :class="game.winnerId === game.blueTeamId ? 'bg-blue-100 text-blue-700' : 'bg-red-100 text-red-700'">
                      胜方: {{ game.winnerId === currentGameTeamStats?.blue.info.id ? 'Blue Side' : 'Red Side' }}
                    </span>
                 </div>
              </div>

              <!-- 3. 队伍数据对比（按击杀数） -->
              <div v-if="currentGameTeamStats" class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
                <div class="bg-blue-50 rounded-lg p-4 text-center border border-blue-100">
                  <div class="text-blue-800 font-bold text-xl mb-1">{{ currentGameTeamStats.blue.info.shortName }}</div>
                  <div class="text-blue-400 text-xs uppercase mb-3">Blue Side</div>
                  <div class="flex justify-center text-sm">
                    <div>
                      <div class="font-bold text-gray-700">{{ currentGameTeamStats.blue.totalKills }}</div>
                      <div class="text-gray-400 text-xs">Total Kills</div>
                    </div>
                  </div>
                </div>

                <div class="flex items-center justify-center font-bold text-gray-300 italic text-2xl">VS</div>

                <div class="bg-red-50 rounded-lg p-4 text-center border border-red-100">
                  <div class="text-red-800 font-bold text-xl mb-1">{{ currentGameTeamStats.red.info.shortName }}</div>
                  <div class="text-red-400 text-xs uppercase mb-3">Red Side</div>
                  <div class="flex justify-center text-sm">
                    <div>
                      <div class="font-bold text-gray-700">{{ currentGameTeamStats.red.totalKills }}</div>
                      <div class="text-gray-400 text-xs">Total Kills</div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 4. 选手详细数据表（基于 kills/deaths/assists） -->
              <div v-if="currentGameTeamStats" class="space-y-8 pb-4">
                
                <!-- Blue Team Table -->
                <div>
                  <h3 class="text-blue-700 font-bold mb-3 border-l-4 border-blue-500 pl-3">
                    {{ currentGameTeamStats.blue.info.shortName }} (Blue)
                  </h3>
                  <el-table :data="currentGameTeamStats.blue.players" style="width: 100%" size="small" stripe>
                    <el-table-column prop="position" label="Pos" width="60" />
                    <el-table-column label="Player" min-width="120">
                      <template #default="{ row }">
                        <div class="font-bold text-gray-800 flex items-center gap-1">
                          {{ row.playerName }}
                          <el-icon v-if="row.isMvp" class="text-yellow-500"><Medal /></el-icon>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column label="Hero" min-width="120">
                      <template #default="{ row }">
                        {{ row.championName }} 
                      </template>
                    </el-table-column>
                    <el-table-column label="K / D / A" min-width="110" align="center">
                      <template #default="{ row }">
                         <span class="font-mono">{{ row.kills }}/{{ row.deaths }}/{{ row.assists }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>

                <!-- Red Team Table -->
                <div>
                  <h3 class="text-red-700 font-bold mb-3 border-l-4 border-red-500 pl-3">
                     {{ currentGameTeamStats.red.info.shortName }} (Red)
                  </h3>
                  <el-table :data="currentGameTeamStats.red.players" style="width: 100%" size="small" stripe>
                    <el-table-column prop="position" label="Pos" width="60" />
                    <el-table-column label="Player" min-width="120">
                      <template #default="{ row }">
                        <div class="font-bold text-gray-800 flex items-center gap-1">
                          {{ row.playerName }}
                          <el-icon v-if="row.isMvp" class="text-yellow-500"><Medal /></el-icon>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column label="Hero" min-width="120">
                      <template #default="{ row }">
                        {{ row.championName }} 
                      </template>
                    </el-table-column>
                    <el-table-column label="K / D / A" min-width="110" align="center">
                      <template #default="{ row }">
                         <span class="font-mono">{{ row.kills }}/{{ row.deaths }}/{{ row.assists }}</span>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>

              </div>
            </el-tab-pane>
          </el-tabs>

        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
:deep(.custom-tabs .el-tabs__header) {
  margin-bottom: 0;
  border-bottom: 1px solid #f3f4f6;
  background-color: #f9fafb;
}
/* 修复表格头部可能的溢出 */
:deep(.el-table) {
  width: 100%;
  overflow-x: auto;
}
</style>
