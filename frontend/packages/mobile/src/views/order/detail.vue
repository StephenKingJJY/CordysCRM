<template>
  <CrmPageWrapper :title="t('mobileOrder.detail')" :back-route-name="OrderRouteEnum.ORDER_INDEX">
    <div class="flex justify-end p-[12px]">
      <van-button size="small" :loading="busy" @click="load">{{ t('mobileOrder.refresh') }}</van-button>
    </div>
    <van-loading v-if="busy" class="p-[24px]" />
    <van-empty v-else-if="failed" :description="t('mobileOrder.failed')">
      <van-button type="primary" @click="load">{{ t('mobileOrder.retry') }}</van-button>
    </van-empty>
    <div v-else class="order-detail pb-[24px]">
      <van-cell :title="t('mobileOrder.number')" :value="detail.number" />
      <van-cell :title="t('mobileOrder.status')" :value="detail.stageName || detail.stage" />
      <van-cell :title="t('mobileOrder.approvalStatus')" :value="approvalLabel" />
      <div v-if="canSubmit" class="p-[12px]">
        <van-button block type="primary" :loading="submitting" :disabled="busy" @click="submitApproval">
          {{ t('mobileOrder.submitApproval') }}
        </van-button>
        <p class="mt-[8px] text-[12px] text-[var(--text-n4)]">{{ t('mobileOrder.approvalHint') }}</p>
      </div>
      <CrmDescription :description="visibleDescriptions">
        <template #tracking="{ item }">
          <div class="select-text break-all">{{ item.value || '-' }}</div>
          <van-button v-if="item.value" size="small" plain type="primary" @click="copyTracking(String(item.value))">
            {{ t('mobileOrder.copy') }}
          </van-button>
        </template>
      </CrmDescription>
      <OrderPayments :order-id="sourceId" :editable="hasAnyPermission(['ORDER:UPDATE'])" :api="paymentApi" />
    </div>
  </CrmPageWrapper>
</template>

<script setup lang="ts">
  import { useRoute } from 'vue-router';
  import { useClipboard } from '@vueuse/core';
  import { showToast } from 'vant';

  import OrderPayments from '@lib/shared/components/order-payments.vue';
  import { FormDesignKeyEnum } from '@lib/shared/enums/formDesignEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';

  import CrmDescription from '@/components/pure/crm-description/index.vue';
  import CrmPageWrapper from '@/components/pure/crm-page-wrapper/index.vue';

  import {
    addOrderPayment,
    downloadOrderPaymentReceipt,
    getOrderPayments,
    reviewResource,
    voidOrderPayment,
  } from '@/api/modules';
  import useFormCreateApi from '@/hooks/useFormCreateApi';
  import { hasAnyPermission } from '@/utils/permission';

  import { OrderRouteEnum } from '@/enums/routeEnum';

  defineOptions({ name: OrderRouteEnum.ORDER_DETAIL });
  const paymentApi = {
    get: getOrderPayments,
    add: addOrderPayment,
    void: voidOrderPayment,
    receipt: downloadOrderPaymentReceipt,
  };
  const route = useRoute();
  const { t } = useI18n();
  const sourceId = computed(() => route.query.id?.toString() || '');
  const { descriptions, detail, initFormConfig, initFormDescription } = useFormCreateApi({
    formKey: FormDesignKeyEnum.ORDER,
    sourceId,
    needInitDetail: true,
  });
  const busy = ref(false);
  const failed = ref(false);
  const submitting = ref(false);
  const approvalLabel = computed(() => t(`mobileOrder.approval.${detail.value.approvalStatus || 'NONE'}`));
  const canSubmit = computed(
    () =>
      ['PENDING', 'UNAPPROVED', 'REVOKED'].includes(detail.value.approvalStatus) && hasAnyPermission(['ORDER:UPDATE'])
  );

  const { copy } = useClipboard({ legacy: true });
  const visibleDescriptions = computed(() =>
    descriptions.value
      .filter((item) => item.fieldInfo?.mobile !== false)
      .map((item) => ({
        ...item,
        valueSlotName: item.fieldInfo?.internalKey === 'lingxingTrackingNumber' ? 'tracking' : undefined,
      }))
  );

  async function copyTracking(value: string) {
    try {
      await copy(value);
      showToast(t('mobileOrder.copied'));
    } catch {
      showToast(t('mobileOrder.copyFailed'));
    }
  }

  async function load() {
    if (busy.value) return;
    busy.value = true;
    failed.value = false;
    detail.value = {};
    descriptions.value = [];
    try {
      await initFormConfig();
      await initFormDescription();
      failed.value = !detail.value.id;
    } catch {
      failed.value = true;
    } finally {
      busy.value = false;
    }
  }

  async function submitApproval() {
    if (submitting.value || busy.value || !canSubmit.value) return;
    submitting.value = true;
    try {
      await reviewResource({ resourceId: sourceId.value, formKey: FormDesignKeyEnum.ORDER });
      showToast(t('mobileOrder.approvalSubmitted'));
      await load();
    } catch {
      showToast(t('mobileOrder.approvalSubmitFailed'));
      await load();
    } finally {
      submitting.value = false;
    }
  }
  onMounted(load);
</script>

<style lang="less" scoped>
  .order-detail {
    word-break: break-word;
    :deep(.crm-description-value) {
      min-width: 0;
      overflow-wrap: anywhere;
    }
  }
</style>
