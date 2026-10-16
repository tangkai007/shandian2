<script setup lang="ts">
import { computed, ref } from 'vue';
import { RouterLink, RouterView, useRouter } from 'vue-router';
import { navFor, ROLE_LABELS } from '@/domain/rbac';
import { useSessionStore } from '@/stores/session';

/**
 * 后台框架与角色导航（D-ADM-000）。
 *
 * 口径：侧栏 186px（可折叠至 56px）；主区 padding 16px；最小宽度 1280px、
 * 内容 max-width 1440px 居中；顶栏固定展示运营主体与资质编号（OP-20）；
 * 非生产环境常驻红色角标。
 */
const session = useSessionStore();
const router = useRouter();

const collapsed = ref(false);
const nav = computed(() => (session.role === null ? [] : navFor(session.role)));
const roleLabel = computed(() => (session.role === null ? '' : ROLE_LABELS[session.role]));
const isNonProduction = (import.meta.env.VITE_ENV ?? 'local') !== 'production';

function onLogout(): void {
  session.logout();
  void router.push({ name: 'login' });
}
</script>

<template>
  <el-container class="adm-shell">
    <el-aside :width="collapsed ? 'var(--adm-sidebar-collapsed)' : 'var(--adm-sidebar-width)'" class="adm-aside">
      <div class="adm-brand">
        <span class="adm-brand__mark">瞬</span>
        <span v-if="!collapsed" class="adm-brand__name">管理后台</span>
      </div>

      <el-menu :collapse="collapsed" :default-active="$route.path" router class="adm-menu">
        <template v-for="group in nav" :key="group.group">
          <el-menu-item-group :title="collapsed ? '' : group.groupLabel">
            <el-menu-item v-for="item in group.items" :key="item.key" :index="item.path">
              <span class="adm-menu__icon">{{ item.icon }}</span>
              <template #title>{{ item.label }}</template>
            </el-menu-item>
          </el-menu-item-group>
        </template>
      </el-menu>

      <div class="adm-aside__foot">
        <el-button text size="small" @click="collapsed = !collapsed">
          {{ collapsed ? '»' : '« 折叠' }}
        </el-button>
        <RouterLink v-if="!collapsed" class="text-min" to="/403">无权限页说明</RouterLink>
      </div>
    </el-aside>

    <el-container>
      <el-header class="adm-header">
        <div class="adm-header__left">
          <span class="adm-header__title">瞬间直播 · 管理后台</span>
          <span v-if="isNonProduction" class="adm-env-badge">非生产环境</span>
        </div>
        <div class="adm-header__right">
          <span class="adm-operator">
            {{ roleLabel }} · {{ session.operatorName }}
          </span>
          <el-divider direction="vertical" />
          <el-tooltip :content="session.session?.license_no ?? ''" placement="bottom">
            <span class="text-min">运营主体：{{ session.operatorEntity }}</span>
          </el-tooltip>
          <el-divider direction="vertical" />
          <el-button text size="small" @click="onLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="adm-main">
        <div class="adm-main__inner">
          <RouterView />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.adm-shell {
  min-width: var(--adm-min-width);
  min-height: 100vh;
}

.adm-aside {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--n-200);
  background: #ffffff;
  transition: width 0.15s ease;
}

.adm-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 52px;
  padding: 0 16px;
  border-bottom: 1px solid var(--n-200);
}

.adm-brand__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: var(--brand-700);
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

.adm-brand__name {
  font-size: 13px;
  font-weight: 600;
}

.adm-menu {
  flex: 1;
  border-right: none;
}

.adm-menu__icon {
  margin-right: 8px;
}

.adm-aside__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid var(--n-200);
}

.adm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 16px;
  border-bottom: 1px solid var(--n-200);
  background: #ffffff;
}

.adm-header__left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.adm-header__title {
  font-size: 14px;
  font-weight: 600;
}

.adm-header__right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.adm-operator {
  font-size: 12px;
  color: var(--n-600);
}

.adm-main {
  padding: 0;
}

.adm-main__inner {
  box-sizing: border-box;
  max-width: var(--adm-content-max);
  margin: 0 auto;
  padding: var(--adm-main-padding);
}

/* 1280px 及以下：审核三栏降为「房间列表 + 预览/处置」两栏（S1b §8.4） */
@media (max-width: 1440px) {
  .adm-main__inner {
    max-width: 100%;
  }
}
</style>
