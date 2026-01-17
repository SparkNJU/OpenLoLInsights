import request from '@/utils/request'

export const dataApi = {
  // 3.1 获取筛选项 (下拉菜单数据)
  // scope: { tournamentId: '...' }, need: ['teams', 'players']
  getOptions(scope: any = {}, need: string[] = ['tournaments', 'teams']) {
    return request.post('/data/options', { scope, need })
  },

  // 3.2 比赛列表搜索
  searchMatches(filter: any, page: number = 1, pageSize: number = 20) {
    return request.post('/matches/search', {
      page,
      pageSize,
      filter,
      sort: { field: 'matchDate', order: 'desc' }
    })
  },

  // 3.3 比赛详情 (包含小局 & 选手数据)
  getMatchDetail(matchId: number | string) {
    // 注意：matchId 即使是数字，有些后端也可能兼容字符串，视具体情况而定
    return request.post('/matches/detail', { matchId })
  },

  // 3.4 选手搜索
  searchPlayers(q: string, page: number = 1, pageSize: number = 20) {
    return request.post('/players/search', {
      q,
      page,
      pageSize
    })
  },

  // 3.5 统计图表数据
  getMetrics(metric: string, filter: any, groupBy: string[] = []) {
    return request.post('/metrics/query', {
      metric,
      filter,
      groupBy,
      limit: 50
    })
  }
}
