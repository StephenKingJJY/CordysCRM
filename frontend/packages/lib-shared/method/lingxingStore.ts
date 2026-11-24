import type { ComputedRef, InjectionKey } from 'vue';

export const LINGXING_STORE_KEY = 'lingxingOrderStore';
export const orderStoreContext: InjectionKey<ComputedRef<string | undefined>> = Symbol('orderLingxingStore');

interface StoreField {
  id: string;
  internalKey?: string;
  defaultValue?: unknown;
}

export function orderStoreId(fields: StoreField[], values: Record<string, unknown>): string | undefined {
  const field = fields.find((item) => item.internalKey === LINGXING_STORE_KEY);
  if (!field) return undefined;
  const value = values[field.id] || field.defaultValue;
  return typeof value === 'string' && value ? value : undefined;
}

// Native edit initialization clears field defaults. Materialize the legacy store before that happens.
export function initializeOrderStore(
  fields: StoreField[],
  values: Record<string, unknown>,
  original?: Record<string, unknown>
): void {
  const field = fields.find((item) => item.internalKey === LINGXING_STORE_KEY);
  const selected = orderStoreId(fields, values);
  const missing = (value: unknown) => value === undefined || value === null || value === '';
  if (!field || !selected || !missing(values[field.id])) return;
  values[field.id] = selected;
  if (original && missing(original[field.id])) {
    original[field.id] = selected;
  }
}

export function orderStoreLocked(detail: Record<string, unknown>, fields: StoreField[]): boolean {
  if (['PENDING_SHIPMENT', 'SHIPPED', 'VOIDED'].includes(String(detail.stage || ''))) return true;
  const values = Array.isArray(detail.moduleFields)
    ? (detail.moduleFields as { fieldId: string; fieldValue?: unknown }[])
    : [];
  const read = (key: string) => {
    const field = fields.find((item) => item.internalKey === key);
    return field ? values.find((value) => value.fieldId === field.id)?.fieldValue ?? detail[field.id] : undefined;
  };
  const number = read('lingxingOrderNumber');
  return (
    Boolean(number && String(number).trim()) ||
    ['同步中', '待人工确认', '成功', '取消中', '已取消', '取消失败'].includes(
      String(read('lingxingOrderSyncStatus') || '')
    )
  );
}
