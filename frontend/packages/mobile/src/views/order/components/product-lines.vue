<template>
  <div class="order-product-lines">
    <van-field
      :label="fieldConfig.name"
      :name="fieldConfig.id"
      :model-value="rows.length ? String(rows.length) : ''"
      :rules="[{ required: true, message: t('mobileOrder.productRequired') }]"
      readonly
    />
    <div v-for="(row, index) in rows" :key="row._mobileKey" class="order-product-line">
      <div class="flex items-center justify-between px-[12px] py-[8px]">
        <span>{{ t('mobileOrder.productLine', { number: index + 1 }) }}</span>
        <van-button v-if="fieldConfig.editable !== false" size="small" plain type="danger" @click="removeLine(index)">
          {{ t('common.delete') }}
        </van-button>
      </div>
      <template v-for="field in visibleFields" :key="field.id">
        <CrmDataSource
          v-if="field.type === FieldTypeEnum.DATA_SOURCE"
          :id="`${fieldConfig.id}[${index}].${field.id}`"
          :value="String(row[key(field)] || '')"
          :label="field.name"
          :data-source-type="field.dataSourceType as FieldDataSourceTypeEnum"
          :disabled="field.editable === false || fieldConfig.editable === false"
          :rules="[{ required: true, message: t('mobileOrder.productRequired') }]"
          @update:value="row[key(field)] = Array.isArray($event) ? $event[0] : $event"
        />
        <van-field
          v-else-if="field.type === FieldTypeEnum.INPUT_NUMBER"
          v-model="row[key(field)]"
          :name="`${fieldConfig.id}[${index}].${field.id}`"
          :label="field.name"
          type="number"
          :disabled="field.editable === false || fieldConfig.editable === false"
          :rules="[{ validator: (v) => validateNumber(field, v), message: t('mobileOrder.invalidNumber') }]"
          @blur="normalizeNumber(row, field)"
        />
        <FormulaValue
          v-else-if="field.type === FieldTypeEnum.FORMULA"
          v-model:value="row[key(field)]"
          :field-config="field"
          :fields="fields"
          :form-detail="formDetail"
          :path="`${fieldConfig.id}[${index}].${key(field)}`"
        />
        <van-field
          v-else
          :label="field.name"
          :model-value="String(row[key(field)] ?? '')"
          :rules="[{ validator: () => false, message: t('mobileOrder.unsupportedField') }]"
          :error-message="t('mobileOrder.unsupportedField')"
          readonly
        />
      </template>
    </div>
    <van-button v-if="fieldConfig.editable !== false" plain type="primary" icon="plus" block @click="addLine">
      {{ t('mobileOrder.addProduct') }}
    </van-button>
  </div>
</template>

<script setup lang="ts">
  import { FieldDataSourceTypeEnum, FieldTypeEnum } from '@lib/shared/enums/formDesignEnum';
  import { useI18n } from '@lib/shared/hooks/useI18n';
  import { getGenerateId } from '@lib/shared/method';

  import CrmDataSource from '@/components/business/crm-datasource/index.vue';
  import FormulaValue from './formula-value.vue';

  import type { FormCreateField } from '@cordys/web/src/components/business/crm-form-create/types';

  const props = defineProps<{
    fieldConfig: FormCreateField;
    fields: FormCreateField[];
    formDetail: Record<string, any>;
  }>();
  const rows = defineModel<Record<string, any>[]>('value', { default: () => [] });
  const { t } = useI18n();
  const visibleFields = computed(() => props.fieldConfig.subFields?.filter((f) => f.readable !== false) || []);
  const key = (field: FormCreateField) => field.businessKey || field.id;

  function addLine() {
    const row: Record<string, any> = { _mobileKey: getGenerateId() };
    props.fieldConfig.subFields?.forEach((field) => {
      row[key(field)] = field.defaultValue ?? (field.internalKey === 'orderProductNumber' ? 1 : '');
    });
    rows.value = [...rows.value, row];
  }

  function removeLine(index: number) {
    rows.value = rows.value.filter((_, i) => i !== index);
  }

  function validateNumber(field: FormCreateField, value: string | number) {
    if (value === '' || value == null) return false;
    const number = Number(value);
    if (!Number.isFinite(number) || number < 0) return false;
    if (field.internalKey === 'orderProductNumber' && (!Number.isInteger(number) || number <= 0)) return false;
    return (field.min == null || number >= field.min) && (field.max == null || number <= field.max);
  }

  function normalizeNumber(row: Record<string, any>, field: FormCreateField) {
    if (validateNumber(field, row[key(field)])) {
      row[key(field)] = Number(Number(row[key(field)]).toFixed(field.precision ?? 0));
    }
  }
</script>

<style lang="less" scoped>
  .order-product-lines {
    padding: 0 12px 16px;
    .order-product-line {
      overflow: hidden;
      margin-bottom: 12px;
      border: 1px solid var(--text-n8);
      border-radius: 8px;
    }
  }
</style>
