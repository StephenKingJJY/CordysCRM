<template>
  <van-field
    :model-value="value == null ? '' : String(value)"
    :name="path || fieldConfig.id"
    :label="fieldConfig.name"
    :rules="[{ validator: () => valid, message: t('mobileOrder.formulaError') }]"
    :error-message="valid ? '' : t('mobileOrder.formulaError')"
    readonly
  />
</template>

<script setup lang="ts">
  import { useI18n } from '@lib/shared/hooks/useI18n';

  import type { FormCreateField } from '@cordys/web/src/components/business/crm-form-create/types';
  import { executeFormFormula } from '@cordys/web/src/components/business/crm-formula/formula-runtime/formula-executor';
  import { getFormulaResultFormatOptions } from '@cordys/web/src/components/business/crm-formula/utils';

  const props = defineProps<{
    fieldConfig: FormCreateField;
    fields: FormCreateField[];
    formDetail: Record<string, any>;
    path?: string;
  }>();
  const value = defineModel<string | number | null>('value');
  const { t } = useI18n();
  const valid = ref(false);

  watch(
    () => props.formDetail,
    () => {
      try {
        const result = executeFormFormula({
          formula: props.fieldConfig.formula,
          path: props.path || props.fieldConfig.id,
          formDetail: props.formDetail,
          fields: props.fields,
          formulaDataSource: {},
          ...getFormulaResultFormatOptions(props.fieldConfig),
        });
        valid.value = !!result && result.normalizedResult !== '' && Number.isFinite(Number(result.normalizedResult));
        const next = valid.value ? result!.normalizedResult : '';
        if (!Object.is(next, value.value)) value.value = next;
      } catch {
        valid.value = false;
        value.value = '';
      }
    },
    { deep: true, immediate: true, flush: 'post' }
  );
</script>
