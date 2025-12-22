import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import Layout from '../views/layout/Layout.vue';


const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/auth/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/auth/Register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('../views/chat/Chat.vue'),
        meta: { title: 'AI 问答助手' }
      },
      {
        path: 'data',
        name: 'data',
        component: () => import('@/views/data/DataCenter.vue'),
        meta: { title: '赛事数据' }
      },
      {
        path: 'match/:id',
        name: 'match-detail',
        component: () => import('@/views/data/MatchDetail.vue'),
        meta: { title: '比赛详情' }
      },
      {
        path: 'stats',
        name: 'stats',
        component: () => import('@/views/stats/Stats.vue'),
        meta: { title: '数据统计' }
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// 路由守卫 如果跳转路径不是login和register，且没有token，就跳转到login，也就是说login和register之前的跳转不受到路由守卫的监视，其他的都受到检查
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('accessToken');
  // 允许 chat 页面不登录访问
  if (to.path !== '/login' && to.path !== '/register' && to.path !== '/chat' && !token) {
    next('/login');
  } else {
    next();
  }
});

export default router;




