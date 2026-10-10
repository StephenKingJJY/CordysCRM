export type OrderSyncApprovalState = 'pending' | 'approving' | 'rejected' | 'revoked' | 'manual';

// Only unsynced orders need a current-round notice. Historical synced orders
// may have NONE approval status and must retain their existing presentation.
export function orderSyncApprovalState(stage?: string, approvalStatus?: string): OrderSyncApprovalState | undefined {
  if (stage !== 'CREATE') return undefined;
  const states: Record<string, OrderSyncApprovalState> = {
    NONE: 'pending',
    PENDING: 'pending',
    APPROVING: 'approving',
    UNAPPROVED: 'rejected',
    AUTO_UNAPPROVED: 'rejected',
    REVOKED: 'revoked',
    AUTO_APPROVED: 'manual',
  };
  return states[approvalStatus || ''];
}

const historyLabels: Record<string, string> = {
  lingxingOrderSyncStatus: 'orderSync.previousStatus',
  lingxingOrderSyncTime: 'orderSync.previousTime',
  lingxingOrderSyncError: 'orderSync.previousError',
  lingxingOrderNumber: 'orderSync.previousNumber',
};

export function orderSyncHistoryDescription<T extends { label: string; fieldInfo?: { internalKey?: string } }>(
  item: T,
  state: OrderSyncApprovalState | undefined,
  translate: (key: string) => string
): T {
  const key = historyLabels[item.fieldInfo?.internalKey || ''];
  // Do not mutate source fields or stored metadata; this is presentation only.
  return state && key ? { ...item, label: translate(key) } : item;
}
