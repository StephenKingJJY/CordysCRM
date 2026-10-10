<template>
  <section v-if="state" class="order-sync-approval-notice" role="status">
    <strong>{{ t('orderSync.currentStatus') }}：{{ t(`orderSync.${state}`) }}</strong>
    <p>{{ t('orderSync.historyHint') }}</p>
  </section>
</template>

<script setup lang="ts">
  import { computed } from 'vue';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { orderSyncApprovalState } from '@lib/shared/method/orderSyncPresentation';

  const props = defineProps<{ stage?: string; approvalStatus?: string }>();
  const { t } = useI18n();
  const state = computed(() => orderSyncApprovalState(props.stage, props.approvalStatus));
</script>

<style scoped>
  .order-sync-approval-notice {
    margin: 12px;
    padding: 12px;
    font-size: 14px;
    border: 1px solid var(--primary-4, #b3d5ff);
    border-radius: 6px;
    color: var(--text-n1, #1f2329);
    background: var(--primary-1, #f0f7ff);
    overflow-wrap: anywhere;
  }
  .order-sync-approval-notice p {
    margin: 6px 0 0;
    font-size: 12px;
    color: var(--text-n3, #646a73);
    line-height: 1.6;
  }
</style>
