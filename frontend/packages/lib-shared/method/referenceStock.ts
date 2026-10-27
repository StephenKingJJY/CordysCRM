export const REFERENCE_STOCK_KEY = 'lingxingReferenceStock';

interface StockField {
  id: string;
  internalKey?: string;
  readable?: boolean;
  show?: boolean;
}

export function referenceStockField(fields: StockField[] = []): StockField | undefined {
  return fields.find(
    (field) => field.internalKey === REFERENCE_STOCK_KEY && field.readable !== false && field.show !== false
  );
}

export function referenceStockValue(row: Record<string, unknown>, fieldId?: string): number | undefined {
  if (!fieldId) return undefined;
  const fields = Array.isArray(row.moduleFields)
    ? (row.moduleFields as { fieldId: string; fieldValue?: unknown }[])
    : undefined;
  const raw = fields?.find((field) => field.fieldId === fieldId)?.fieldValue ?? row[fieldId];
  if (!['number', 'string'].includes(typeof raw) || (typeof raw === 'string' && !raw.trim())) return undefined;
  const quantity = Number(raw);
  return Number.isSafeInteger(quantity) ? quantity : undefined;
}
