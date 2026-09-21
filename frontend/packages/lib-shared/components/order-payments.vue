<template>
  <section class="order-payments" :aria-label="t('orderPayment.title')">
    <header class="payment-toolbar">
      <h3>{{ t('orderPayment.title') }}</h3>
      <div class="payment-actions">
        <button type="button" :disabled="loading || saving" @click="load">{{ t('orderPayment.refresh') }}</button>
        <button v-if="editable && summary && !failed" type="button" :disabled="saving" @click="openAdd">
          {{ t('orderPayment.add') }}
        </button>
      </div>
    </header>
    <p v-if="loading" role="status">{{ t('orderPayment.loading') }}</p>
    <p v-if="error" role="alert" class="payment-error">{{ error }}</p>
    <template v-if="summary && !failed">
      <dl class="payment-summary">
        <div
          ><dt>{{ t('orderPayment.total') }}</dt
          ><dd>{{ summary.orderAmount }}</dd></div
        >
        <div
          ><dt>{{ t('orderPayment.received') }}</dt
          ><dd>{{ summary.receivedAmount }}</dd></div
        >
        <div
          ><dt>{{ t('orderPayment.remaining') }}</dt
          ><dd>{{ summary.remainingAmount }}</dd></div
        >
        <div
          ><dt>{{ t('orderPayment.status') }}</dt
          ><dd>{{ t(`orderPayment.${summary.status}`) }}</dd></div
        >
      </dl>
      <p v-if="summary.status === 'OVERPAID'" class="payment-error">
        {{ t('orderPayment.overpaid', { amount: summary.overpaidAmount }) }}
      </p>
      <p class="payment-hint">{{ t('orderPayment.hint') }}</p>
      <form v-if="adding" class="payment-form" @submit.prevent="save">
        <label
          >{{ t('orderPayment.date')
          }}<input v-model="form.receivedDate" type="date" :max="today()" required :disabled="saving"
        /></label>
        <label
          >{{ t('orderPayment.amount')
          }}<input
            v-model="form.amount"
            type="text"
            inputmode="decimal"
            pattern="[0-9]+([.][0-9]{1,2})?"
            required
            :disabled="saving"
        /></label>
        <label
          >{{ t('orderPayment.remark') }}<textarea v-model="form.remark" maxlength="2000" rows="3" :disabled="saving" />
        </label>
        <label
          >{{ t('orderPayment.receipt')
          }}<input type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.webp" :disabled="saving" @change="selectFiles"
        /></label>
        <small>{{ t('orderPayment.fileHint') }}</small>
        <div class="payment-actions">
          <button type="submit" :disabled="saving">{{
            t(saving ? 'orderPayment.saving' : 'orderPayment.save')
          }}</button>
          <button type="button" :disabled="saving" @click="adding = false">{{ t('orderPayment.cancel') }}</button>
        </div>
      </form>
      <p v-if="!summary.records.length">{{ t('orderPayment.empty') }}</p>
      <article
        v-for="record in summary.records"
        :key="record.id"
        class="payment-record"
        :class="{ 'payment-voided': record.voided }"
      >
        <div class="payment-toolbar">
          <strong>{{ record.receivedDate }} · {{ record.amount }}</strong>
          <span v-if="record.voided">{{ t('orderPayment.voided') }}</span>
          <button v-else-if="editable" type="button" :disabled="saving" @click="startVoid(record)">{{
            t('orderPayment.void')
          }}</button>
        </div>
        <p v-if="record.remark" class="payment-remark">{{ record.remark }}</p>
        <p class="payment-hint">{{
          t('orderPayment.created', { name: record.createUserName, time: formatTime(record.createTime) })
        }}</p>
        <div class="payment-actions">
          <button
            v-for="receipt in record.receipts"
            :key="receipt.id"
            type="button"
            @click="download(record.id, receipt)"
            >{{ receipt.name }}</button
          >
        </div>
        <p v-if="record.voided" class="payment-remark">
          {{
            t('orderPayment.voidInfo', {
              name: record.voidUserName,
              time: formatTime(record.voidTime),
              reason: record.voidReason,
            })
          }}
        </p>
        <form v-if="voidId === record.id" class="payment-form" @submit.prevent="confirmVoid">
          <p>{{ t('orderPayment.voidHint') }}</p>
          <label
            >{{ t('orderPayment.voidReason')
            }}<textarea v-model="voidReason" required maxlength="500" :disabled="saving" />
          </label>
          <div class="payment-actions">
            <button type="submit" :disabled="saving || !voidReason.trim()">{{ t('orderPayment.confirmVoid') }}</button>
            <button type="button" :disabled="saving" @click="voidId = ''">{{ t('orderPayment.cancel') }}</button>
          </div>
        </form>
      </article>
    </template>
  </section>
</template>

<script setup lang="ts">
  import { ref, watch } from 'vue';

  import { useI18n } from '@lib/shared/hooks/useI18n';
  import type {
    OrderPayment,
    OrderPaymentApi,
    OrderPaymentInput,
    OrderPaymentSummary,
  } from '@lib/shared/models/orderPayment';

  const props = defineProps<{ orderId: string; editable: boolean; api: OrderPaymentApi }>();
  const emit = defineEmits<{ (e: 'changed'): void }>();
  const { t } = useI18n();
  const summary = ref<OrderPaymentSummary>();
  const loading = ref(false);
  const saving = ref(false);
  const failed = ref(false);
  const error = ref('');
  const adding = ref(false);
  const files = ref<File[]>([]);
  const voidId = ref('');
  const voidReason = ref('');
  function today() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
  function blank(): OrderPaymentInput {
    return { id: crypto.randomUUID().replaceAll('-', ''), amount: '', receivedDate: today(), remark: '' };
  }
  const form = ref<OrderPaymentInput>(blank());
  function formatTime(time?: number) {
    return time ? new Date(time).toLocaleString() : '';
  }
  let generation = 0;
  async function load() {
    const { orderId } = props;
    const current = ++generation;
    if (!orderId) return;
    loading.value = true;
    error.value = '';
    try {
      const result = await props.api.get(orderId);
      if (current === generation) {
        summary.value = result;
        failed.value = false;
      }
    } catch {
      if (current === generation) {
        failed.value = true;
        error.value = t('orderPayment.loadFailed');
      }
    } finally {
      if (current === generation) loading.value = false;
    }
  }
  watch(
    () => props.orderId,
    () => {
      summary.value = undefined;
      adding.value = false;
      voidId.value = '';
      load();
    },
    { immediate: true }
  );
  function openAdd() {
    if (adding.value) return;
    form.value = blank();
    files.value = [];
    error.value = '';
    adding.value = true;
    voidId.value = '';
  }
  function selectFiles(event: Event) {
    const input = event.target as HTMLInputElement;
    const selected = Array.from(input.files || []);
    if (
      selected.length > 5 ||
      selected.some((f) => !f.size || f.size > 10 * 1024 * 1024 || !/\.(pdf|png|jpe?g|webp)$/i.test(f.name))
    ) {
      error.value = t('orderPayment.fileHint');
      input.value = '';
      files.value = [];
      return;
    }
    files.value = selected;
    error.value = '';
  }
  async function save() {
    if (saving.value || !props.editable) return;
    if (
      !/^\d{1,10}(\.\d{1,2})?$/.test(form.value.amount) ||
      Number(form.value.amount) <= 0 ||
      !form.value.receivedDate ||
      form.value.receivedDate > today()
    ) {
      error.value = t('orderPayment.invalid');
      return;
    }
    saving.value = true;
    error.value = '';
    const { orderId } = props;
    try {
      await props.api.add(orderId, { ...form.value }, files.value);
      if (props.orderId === orderId) {
        adding.value = false;
        await load();
        emit('changed');
      }
    } catch {
      if (props.orderId === orderId) error.value = t('orderPayment.saveFailed');
    } finally {
      saving.value = false;
    }
  }
  function startVoid(record: OrderPayment) {
    voidId.value = record.id;
    voidReason.value = '';
    adding.value = false;
    error.value = '';
  }
  async function confirmVoid() {
    if (saving.value || !props.editable || !voidReason.value.trim()) return;
    saving.value = true;
    error.value = '';
    const { orderId } = props;
    try {
      await props.api.void(orderId, voidId.value, voidReason.value.trim());
      if (props.orderId === orderId) {
        voidId.value = '';
        await load();
        emit('changed');
      }
    } catch {
      if (props.orderId === orderId) error.value = t('orderPayment.saveFailed');
    } finally {
      saving.value = false;
    }
  }
  async function download(id: string, receipt: { id: string; name: string }) {
    try {
      const blob = await props.api.receipt(props.orderId, id, receipt.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = receipt.name;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch {
      error.value = t('orderPayment.downloadFailed');
    }
  }
</script>

<style scoped>
  .order-payments {
    padding: 16px;
    color: var(--text-n1, #222222);
    background: var(--text-n10, #ffffff);
  }
  .payment-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
  }
  h3 {
    margin: 0;
    font-size: 17px;
    font-weight: 600;
  }
  .payment-summary {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    margin: 20px 0;
  }
  .payment-summary > div {
    padding: 12px;
    border-radius: 8px;
    background: #f4f8f8;
  }
  dt {
    font-size: 12px;
    color: #666666;
  }
  dd {
    margin: 6px 0 0;
    font-size: 19px;
    font-weight: 600;
    overflow-wrap: anywhere;
  }
  .payment-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  button {
    padding: 7px 12px;
    border: 1px solid #73aaa9;
    border-radius: 6px;
    color: #246865;
    background: #ffffff;
    cursor: pointer;
    overflow-wrap: anywhere;
  }
  button:disabled {
    opacity: 0.5;
    cursor: default;
  }
  button:focus-visible,
  input:focus-visible,
  textarea:focus-visible {
    outline: 2px solid #287e79;
    outline-offset: 2px;
  }
  .payment-hint,
  small {
    font-size: 12px;
    color: #687278;
  }
  .payment-error {
    color: #b42318;
  }
  .payment-form {
    display: grid;
    margin: 16px 0;
    padding: 16px;
    border-radius: 8px;
    background: #f7fafa;
    gap: 12px;
  }
  label {
    display: grid;
    gap: 6px;
    font-size: 14px;
  }
  input,
  textarea {
    padding: 9px;
    width: 100%;
    min-width: 0;
    border: 1px solid #ccd5d5;
    border-radius: 5px;
    background: #ffffff;
    box-sizing: border-box;
    font: inherit;
  }
  .payment-record {
    padding: 16px 0;
    border-top: 1px solid #e3e8e8;
  }
  .payment-voided {
    color: #757575;
  }
  .payment-remark {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
  p {
    margin: 10px 0;
  }
  @media (min-width: 640px) {
    .payment-summary {
      grid-template-columns: repeat(4, minmax(0, 1fr));
    }
  }
</style>
