export interface OrderPayment {
  id: string;
  amount: string;
  receivedDate: string;
  remark: string;
  createUserName: string;
  createTime: number;
  voided: boolean;
  voidUserName?: string;
  voidTime?: number;
  voidReason?: string;
  receipts: { id: string; name: string }[];
}
export interface OrderPaymentSummary {
  orderAmount: string;
  receivedAmount: string;
  remainingAmount: string;
  overpaidAmount: string;
  status: 'UNPAID' | 'PARTIAL' | 'PAID' | 'OVERPAID';
  records: OrderPayment[];
}
export interface OrderPaymentInput {
  id: string;
  amount: string;
  receivedDate: string;
  remark: string;
}
export interface OrderPaymentApi {
  get: (orderId: string) => Promise<OrderPaymentSummary>;
  add: (orderId: string, data: OrderPaymentInput, files: File[]) => Promise<unknown>;
  void: (orderId: string, id: string, reason: string) => Promise<unknown>;
  receipt: (orderId: string, id: string, fileId: string) => Promise<Blob>;
}
