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
const activeGameTab = ref<number>(1)

// --- 3. 模拟数据 ---
const fetchMatchDetail = async () => {
  loading.value = true
  try {
      // --- [TODO: 对接后端时取消注释] ---
      /*
      // 注意：这里强烈依赖后端在 /matches/detail 的 games 数组中返回 stats 字段
      const res: any = await dataApi.getMatchDetail(matchId)
      
      // 数据映射 (Backend camelCase -> Frontend Interface)
      matchData.value = {
          id: res.matchId,
          date: res.date,
          tournamentName: res.tournamentId,
          stage: 'TBD', // 接口文档里好像没写 stage，可能需要补充
          team1: { id: res.teams[0].teamId, name: '...', shortName: '...' }, // 需确认后端是否返回完整 Team 对象
          team2: { id: res.teams[1].teamId, name: '...', shortName: '...' },
          winnerId: 0, // 文档没直接返回大场 winnerId，可能需要前端算
          games: res.games.map((g: any) => ({
              id: g.gameNo, // 或 g.gameId
              matchId: res.matchId,
              gameNumber: g.gameNo,
              duration: g.durationSec,
              blueTeamId: 0, // 文档里没明确写 games 下的红蓝方 ID，需确认
              redTeamId: 0,
              winnerId: g.winnerTeamId,
              stats: g.stats // [关键] 依赖后端返回这个数组
          }))
      }
      */

      // --- [Mock Logic] ---
      setTimeout(() => {
      matchData.value = {
        id: 1001,
        date: '2024-07-20',
        tournamentName: '14/lpl',
        stage: '季后',
        winnerId: 101,
        team1: { id: 101, name: 'Bilibili Gaming', shortName: 'BLG' },
        team2: { id: 102, name: 'Top Esports', shortName: 'TES' },
        games: [
          {
            id: 5001, matchId: 1001, gameNumber: 1, duration: 1856,
            blueTeamId: 101, redTeamId: 102, winnerId: 101,
            stats: generateMockStats(5001, 101, 102, 101)
          },
          {
            id: 5002, matchId: 1001, gameNumber: 2, duration: 2140,
            blueTeamId: 102, redTeamId: 101, winnerId: 102,
            stats: generateMockStats(5002, 102, 101, 102)
          },
          {
            id: 5003, matchId: 1001, gameNumber: 3, duration: 1980,
            blueTeamId: 101, redTeamId: 102, winnerId: 101,
            stats: generateMockStats(5003, 101, 102, 101)
          }
        ]
      }
      loading.value = false
    }, 500)

  } catch (e) {
      console.error(e)
  } finally {
      loading.value = false // 移动到 finally 确保执行
  }


}

const generateMockStats = (gameId: number, blueId: number, redId: number, winnerId: number): PlayerGameStat[] => {
  const positions = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT']
  const stats: PlayerGameStat[] = []
  
  // Blue Team
  positions.forEach((pos, i) => {
    stats.push({
      id: gameId * 10 + i, gameId: gameId, playerId: i, teamId: blueId,
      playerName: `BluePlayer_${pos}`, position: pos,
      championName: ['剑魔', '猪妹', '阿狸', '卡莎', '牛头'][i], championNameEn: 'Mock',
      playerLevel: 16, kills: Math.floor(Math.random() * 10), deaths: Math.floor(Math.random() * 5), assists: Math.floor(Math.random() * 15),
      kda: 4.5, killParticipation: 0.65, totalDamageDealt: 20000 + Math.random() * 10000, damageDealtPercentage: 0.2,
      totalDamageTaken: 15000, damageTakenPercentage: 0.2, goldEarned: 12000 + Math.random() * 3000, minionsKilled: 200,
      isMvp: winnerId === blueId && pos === 'MID'
    })
  })
  // Red Team
  positions.forEach((pos, i) => {
    stats.push({
      id: gameId * 20 + i, gameId: gameId, playerId: i + 5, teamId: redId,
      playerName: `RedPlayer_${pos}`, position: pos,
      championName: ['鳄鱼', '破败王', '沙皇', 'EZ', '布隆'][i], championNameEn: 'Mock',
      playerLevel: 15, kills: Math.floor(Math.random() * 8), deaths: Math.floor(Math.random() * 8), assists: Math.floor(Math.random() * 10),
      kda: 2.1, killParticipation: 0.5, totalDamageDealt: 18000 + Math.random() * 8000, damageDealtPercentage: 0.2,
      totalDamageTaken: 18000, damageTakenPercentage: 0.2, goldEarned: 10000 + Math.random() * 2000, minionsKilled: 180,
      isMvp: false
    })
  })
  return stats
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
  return matchData.value?.games.find(g => g.gameNumber === activeGameTab.value)
})

const currentGameTeamStats = computed(() => {
  if (!currentGame.value || !matchData.value) return null
  
  const calc = (teamId: number) => {
    const teamStats = currentGame.value!.stats.filter(s => s.teamId === teamId)
    return {
      totalKills: teamStats.reduce((sum, p) => sum + p.kills, 0),
      totalGold: teamStats.reduce((sum, p) => sum + p.goldEarned, 0),
      totalDamage: teamStats.reduce((sum, p) => sum + p.totalDamageDealt, 0),
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

const formatGold = (num: number) => (num / 1000).toFixed(1) + 'k'
const formatDmg = (num: number) => (num / 1000).toFixed(1) + 'k'

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
              :name="game.gameNumber"
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

              <!-- 3. 队伍数据对比 -->
              <div v-if="currentGameTeamStats" class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
                 <!-- Blue Summary -->
                 <div class="bg-blue-50 rounded-lg p-4 text-center border border-blue-100">
                    <div class="text-blue-800 font-bold text-xl mb-1">{{ currentGameTeamStats.blue.info.shortName }}</div>
                    <div class="text-blue-400 text-xs uppercase mb-3">Blue Side</div>
                    <div class="flex justify-around text-sm">
                       <div><div class="font-bold text-gray-700">{{ currentGameTeamStats.blue.totalKills }}</div><div class="text-gray-400 text-xs">Kills</div></div>
                       <div><div class="font-bold text-gray-700">{{ formatGold(currentGameTeamStats.blue.totalGold) }}</div><div class="text-gray-400 text-xs">Gold</div></div>
                       <div><div class="font-bold text-gray-700">{{ formatDmg(currentGameTeamStats.blue.totalDamage) }}</div><div class="text-gray-400 text-xs">Dmg</div></div>
                    </div>
                 </div>

                 <!-- VS Icon -->
                 <div class="flex items-center justify-center font-bold text-gray-300 italic text-2xl">VS</div>

                 <!-- Red Summary -->
                 <div class="bg-red-50 rounded-lg p-4 text-center border border-red-100">
                    <div class="text-red-800 font-bold text-xl mb-1">{{ currentGameTeamStats.red.info.shortName }}</div>
                    <div class="text-red-400 text-xs uppercase mb-3">Red Side</div>
                    <div class="flex justify-around text-sm">
                       <div><div class="font-bold text-gray-700">{{ currentGameTeamStats.red.totalKills }}</div><div class="text-gray-400 text-xs">Kills</div></div>
                       <div><div class="font-bold text-gray-700">{{ formatGold(currentGameTeamStats.red.totalGold) }}</div><div class="text-gray-400 text-xs">Gold</div></div>
                       <div><div class="font-bold text-gray-700">{{ formatDmg(currentGameTeamStats.red.totalDamage) }}</div><div class="text-gray-400 text-xs">Dmg</div></div>
                    </div>
                 </div>
              </div>

              <!-- 4. 选手详细数据表 -->
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
                    <el-table-column label="Hero" min-width="100">
                      <template #default="{ row }">
                        {{ row.championName }} 
                        <span class="text-xs text-gray-400 ml-1">Lv{{ row.playerLevel }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="KDA" min-width="100" align="center">
                      <template #default="{ row }">
                         <span class="font-mono">{{ row.kills }}/{{ row.deaths }}/{{ row.assists }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="Damage" prop="totalDamageDealt" min-width="90" align="right">
                       <template #default="{ row }">{{ (row.totalDamageDealt / 1000).toFixed(1) }}k</template>
                    </el-table-column>
                    <el-table-column label="Gold" prop="goldEarned" min-width="90" align="right">
                       <template #default="{ row }">{{ (row.goldEarned / 1000).toFixed(1) }}k</template>
                    </el-table-column>
                    <el-table-column label="CS" prop="minionsKilled" width="70" align="right" />
                    <el-table-column label="KP%" width="80" align="right">
                      <template #default="{ row }">{{ (row.killParticipation * 100).toFixed(0) }}%</template>
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
                    <el-table-column label="Hero" min-width="100">
                      <template #default="{ row }">
                        {{ row.championName }} 
                        <span class="text-xs text-gray-400 ml-1">Lv{{ row.playerLevel }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="KDA" min-width="100" align="center">
                      <template #default="{ row }">
                         <span class="font-mono">{{ row.kills }}/{{ row.deaths }}/{{ row.assists }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="Damage" prop="totalDamageDealt" min-width="90" align="right">
                       <template #default="{ row }">{{ (row.totalDamageDealt / 1000).toFixed(1) }}k</template>
                    </el-table-column>
                    <el-table-column label="Gold" prop="goldEarned" min-width="90" align="right">
                       <template #default="{ row }">{{ (row.goldEarned / 1000).toFixed(1) }}k</template>
                    </el-table-column>
                    <el-table-column label="CS" prop="minionsKilled" width="70" align="right" />
                    <el-table-column label="KP%" width="80" align="right">
                      <template #default="{ row }">{{ (row.killParticipation * 100).toFixed(0) }}%</template>
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