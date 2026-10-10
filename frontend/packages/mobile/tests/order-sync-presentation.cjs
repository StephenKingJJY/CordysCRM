const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const ts = require('typescript');
const ctx = { exports: {} };
vm.runInNewContext(
  ts.transpileModule(fs.readFileSync('../lib-shared/method/orderSyncPresentation.ts', 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText,
  ctx
);
const { orderSyncApprovalState, orderSyncHistoryDescription } = ctx.exports;
for (const [status, state] of Object.entries({
  NONE: 'pending',
  PENDING: 'pending',
  APPROVING: 'approving',
  UNAPPROVED: 'rejected',
  AUTO_UNAPPROVED: 'rejected',
  REVOKED: 'revoked',
  AUTO_APPROVED: 'manual',
})) {
  assert.equal(orderSyncApprovalState('CREATE', status), state);
  for (const stage of ['PENDING_SHIPMENT', 'SHIPPED', 'VOIDED', undefined]) {
    assert.equal(orderSyncApprovalState(stage, status), undefined);
  }
}
assert.equal(orderSyncApprovalState('CREATE', 'APPROVED'), undefined);
assert.equal(orderSyncApprovalState('CREATE', undefined), undefined);
assert.equal(orderSyncApprovalState('CREATE', 'UNKNOWN'), undefined);
const translate = (key) => key;
for (const [key, label] of Object.entries({
  lingxingOrderSyncStatus: 'orderSync.previousStatus',
  lingxingOrderSyncTime: 'orderSync.previousTime',
  lingxingOrderSyncError: 'orderSync.previousError',
  lingxingOrderNumber: 'orderSync.previousNumber',
})) {
  for (const value of ['失败', '成功', '待人工确认', '', null, '2026-10-09 16:28:52']) {
    const item = { label: 'Original label', value, fieldInfo: { internalKey: key }, other: true };
    const copy = JSON.stringify(item);
    const presented = orderSyncHistoryDescription(item, 'approving', translate);
    assert.equal(presented.label, label);
    assert.equal(presented.value, value);
    assert.equal(presented.fieldInfo, item.fieldInfo);
    assert.equal(JSON.stringify(item), copy);
    assert.equal(orderSyncHistoryDescription(item, undefined, translate), item);
  }
}
for (const key of ['lingxingTrackingNumber', 'lingxingLogisticsCompany', 'lingxingOrderSyncFingerprint', 'customer']) {
  const item = { label: 'Original', value: 'keep', fieldInfo: { internalKey: key } };
  assert.equal(orderSyncHistoryDescription(item, 'approving', translate), item);
}
assert.equal(orderSyncHistoryDescription({ label: 'Missing config' }, 'approving', translate).label, 'Missing config');
const web = fs.readFileSync('../web/src/views/order/order/components/detail.vue', 'utf8');
const mobile = fs.readFileSync('src/views/order/detail.vue', 'utf8');
assert.ok(web.includes('order-sync-approval-notice'));
assert.ok(mobile.includes('<OrderSyncApprovalNotice'));
assert.ok(mobile.includes('orderSyncHistoryDescription'));
console.log('PASS: approval gates, legacy orders, history labels, immutable metadata and both detail integrations');
