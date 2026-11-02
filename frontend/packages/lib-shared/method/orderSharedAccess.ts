export interface SharedOrderAccess {
  sharedAccessOnly?: boolean;
  sharedEditable?: boolean;
}

export function canUseSharedOrderAction(order: SharedOrderAccess | undefined, action: string): boolean {
  return !order?.sharedAccessOnly || action === 'read' || (action === 'edit' && order.sharedEditable === true);
}
