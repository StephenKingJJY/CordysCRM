<template>
  <section class="approval-progress" :aria-label="t('mobileOrder.progress.title')">
    <h2>{{ t('mobileOrder.progress.title') }}</h2>
    <van-loading v-if="loading" size="18">{{ t('mobileOrder.progress.loading') }}</van-loading>
    <div v-else-if="failed" role="alert">
      <p>{{ t('mobileOrder.progress.failed') }}</p>
      <van-button size="small" @click="load">{{ t('mobileOrder.retry') }}</van-button>
    </div>
    <p v-else-if="!info || !info.nodes?.length" class="muted">{{ t('mobileOrder.progress.empty') }}</p>
    <template v-else>
      <p class="progress-summary" :class="{ active: info.approvalStatus === 'APPROVING' }" role="status">
        {{ summary }}
      </p>
      <ol class="progress-timeline">
        <li class="progress-node approved">
          <span class="progress-dot" aria-hidden="true">✓</span>
          <div class="node-heading">{{ t('mobileOrder.progress.submitted') }}</div>
          <p>{{ info.submitter || t('mobileOrder.progress.unknownPerson') }}</p>
          <time v-if="info.submitTime" class="muted">{{ formatTime(info.submitTime) }}</time>
          <p v-if="info.comment" class="comment">{{ info.comment }}</p>
        </li>
        <li
          v-for="(node, index) in info.nodes"
          :key="node.nodeId + ':' + node.nodeRound + ':' + index"
          class="progress-node"
          :class="nodeProgressState(node, info)"
        >
          <span class="progress-dot" aria-hidden="true">{{
            nodeProgressState(node, info) === 'approved' ? '✓' : index + 1
          }}</span>
          <div class="node-heading">
            <span>{{ node.nodeName || t('mobileOrder.progress.unnamedNode') }}</span>
            <span class="state-label">{{ stateLabel(nodeProgressState(node, info)) }}</span>
          </div>
          <div v-for="(task, taskIndex) in node.taskNodes || []" :key="task.taskId || taskIndex" class="approver">
            <div class="approver-heading">
              <span>{{ task.approver || t('mobileOrder.progress.unknownPerson') }}</span>
              <span :class="taskProgressState(task, node, info)">{{
                stateLabel(taskProgressState(task, node, info))
              }}</span>
            </div>
            <time v-if="task.approvalTime" class="muted">{{ formatTime(task.approvalTime) }}</time>
            <p v-if="task.comment" class="comment">{{ t('mobileOrder.progress.opinion') }}{{ task.comment }}</p>
            <p v-if="task.signComment" class="comment"
              >{{ t('mobileOrder.progress.signOpinion') }}{{ task.signComment }}</p
            >
          </div>
          <p v-if="node.backNode && node.backReason" class="comment">
            {{ t('mobileOrder.progress.backReason') }}{{ node.backReason }}
          </p>
        </li>
      </ol>
    </template>
  </section>
</template>

<script setup lang="ts">
  import dayjs from 'dayjs';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type { ApprovalDetail } from '@lib/shared/models/system/process';

  import { getApprovalResourceDetail } from '@/api/modules';

  import { nodeProgressState, type ProgressState, taskProgressState, waitingApprovers } from './approval-progress';

  const props = defineProps<{ orderId: string }>();
  const { t } = useI18n();
  const info = ref<ApprovalDetail>();
  const loading = ref(false);
  const failed = ref(false);
  let generation = 0;
  const summary = computed(() => {
    if (!info.value) return '';
    if (info.value.approvalStatus === 'APPROVING') {
      const names = waitingApprovers(info.value);
      return names.length
        ? t('mobileOrder.progress.waitingFor', { names: names.join('、') })
        : t('mobileOrder.progress.processing');
    }
    return t(`mobileOrder.approval.${info.value.approvalStatus}`);
  });
  const stateLabel = (state: ProgressState) => t(`mobileOrder.progress.${state}`);
  const formatTime = (value: number) => dayjs(value).format('YYYY-MM-DD HH:mm');

  async function load() {
    const request = ++generation;
    info.value = undefined;
    failed.value = false;
    if (!props.orderId) {
      loading.value = false;
      return;
    }
    loading.value = true;
    try {
      const result = await getApprovalResourceDetail(props.orderId);
      if (request === generation) info.value = result;
    } catch {
      if (request === generation) failed.value = true;
    } finally {
      if (request === generation) loading.value = false;
    }
  }

  watch(() => props.orderId, load, { immediate: true });
  onBeforeUnmount(() => {
    generation += 1;
  });
</script>

<style lang="less" scoped>
  .approval-progress {
    margin: 12px;
    padding: 16px;
    font-size: 14px;
    border: 1px solid var(--text-n8);
    border-radius: 12px;
    background: var(--text-n10);
    overflow-wrap: anywhere;
    h2 {
      margin: 0 0 12px;
      font-size: 16px;
      font-weight: 600;
    }
    p {
      margin: 6px 0;
    }
    .muted {
      font-size: 12px;
      color: var(--text-n4);
    }
    .progress-summary {
      margin: 0 0 16px;
      padding: 10px 12px;
      font-weight: 600;
      border-radius: 8px;
      background: var(--text-n9);
    }
    .active {
      color: var(--primary-8);
      background: var(--primary-1);
    }
    .progress-timeline {
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .progress-node {
      position: relative;
      margin-left: 11px;
      padding: 0 0 20px 24px;
      border-left: 2px solid var(--text-n8);
      color: var(--text-n1);
      &:last-child {
        padding-bottom: 0;
        border-left-color: transparent;
      }
    }
    .progress-dot {
      position: absolute;
      top: 0;
      left: -12px;
      display: flex;
      justify-content: center;
      align-items: center;
      width: 22px;
      height: 22px;
      font-size: 12px;
      border-radius: 50%;
      color: var(--text-n1);
      background: var(--text-n7);
    }
    .approved > .progress-dot {
      color: var(--text-n10);
      background: var(--success-green);
    }
    .waiting > .progress-dot {
      color: var(--text-n10);
      background: var(--primary-8);
    }
    .rejected > .progress-dot {
      color: var(--text-n10);
      background: var(--error-red);
    }
    .node-heading,
    .approver-heading {
      display: flex;
      justify-content: space-between;
      gap: 8px;
      line-height: 22px;
    }
    .node-heading {
      font-weight: 600;
    }
    .state-label {
      font-size: 12px;
      font-weight: 400;
      color: var(--text-n4);
      flex-shrink: 0;
    }
    .waiting .state-label,
    .approver-heading .waiting {
      color: var(--primary-8);
    }
    .approved .state-label,
    .approver-heading .approved {
      color: var(--success-green);
    }
    .rejected .state-label,
    .approver-heading .rejected {
      color: var(--error-red);
    }
    .approver {
      margin-top: 8px;
      padding: 8px;
      border-radius: 6px;
      background: var(--text-n9);
    }
    .comment {
      white-space: pre-wrap;
      color: var(--text-n3);
    }
  }
</style>
