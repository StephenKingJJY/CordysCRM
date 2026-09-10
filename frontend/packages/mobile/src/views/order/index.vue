<template>
  <CrmPageWrapper :title="t('mobileOrder.title')" :back-route-name="WorkbenchRouteEnum.WORKBENCH_INDEX">
    <van-search v-model="searchText" :placeholder="t('mobileOrder.search')" @search="search" @clear="search" />
    <div class="flex-1 overflow-auto p-[16px]">
      <van-pull-refresh v-model="refreshing" @refresh="refresh">
        <van-list
          v-model:loading="loading"
          v-model:error="error"
          :finished="finished"
          :error-text="t('common.listLoadErrorTip')"
          :finished-text="orders.length ? t('common.listFinishedTip') : ''"
          @load="load"
        >
          <van-cell
            v-for="order in orders"
            :key="order.id"
            is-link
            class="mb-[12px] rounded-[8px]"
            @click="router.push({ name: OrderRouteEnum.ORDER_DETAIL, query: { id: order.id } })"
          >
            <template #title>
              <div class="break-all font-semibold">{{ order.name }}</div>
              <div class="break-all text-[12px] text-[var(--text-n4)]">{{ order.number }}</div>
            </template>
            <template #label>
              <div class="break-all">{{ t('mobileOrder.customer') }}：{{ order.customerName || '-' }}</div>
              <div>{{ t('mobileOrder.amount') }}：{{ order.amount ?? '-' }}</div>
              <div>{{ t('mobileOrder.status') }}：{{ order.stageName || order.stage || '-' }}</div>
            </template>
          </van-cell>
        </van-list>
        <van-empty v-if="!orders.length && !loading && !error" :description="t('common.noData')" />
      </van-pull-refresh>
    </div>
  </CrmPageWrapper>
</template>

<script setup lang="ts">
  import { useRouter } from 'vue-router';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { OrderItem } from '@lib/shared/models/order';

  import CrmPageWrapper from '@/components/pure/crm-page-wrapper/index.vue';

  import { getOrderList } from '@/api/modules';

  import { OrderRouteEnum, WorkbenchRouteEnum } from '@/enums/routeEnum';

  defineOptions({ name: OrderRouteEnum.ORDER_INDEX });
  const { t } = useI18n();
  const router = useRouter();
  const orders = ref<OrderItem[]>([]);
  const searchText = ref('');
  const keyword = ref('');
  const loading = ref(false);
  const refreshing = ref(false);
  const error = ref(false);
  const finished = ref(false);
  let current = 1;
  let generation = 0;

  async function load() {
    const requestGeneration = generation;
    loading.value = true;
    try {
      const response = await getOrderList({ current, pageSize: 20, keyword: keyword.value });
      if (requestGeneration !== generation) return;
      orders.value.push(...response.list);
      current += 1;
      finished.value = orders.value.length >= response.total || response.list.length === 0;
      error.value = false;
    } catch {
      if (requestGeneration === generation) error.value = true;
    } finally {
      if (requestGeneration === generation) {
        loading.value = false;
        refreshing.value = false;
      }
    }
  }

  function refresh() {
    generation += 1;
    orders.value = [];
    current = 1;
    finished.value = false;
    error.value = false;
    load();
  }

  function search() {
    keyword.value = searchText.value.trim();
    refresh();
  }
</script>
