const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const ts = require('typescript');
const ctx = { exports: {} };
vm.runInNewContext(
  ts.transpileModule(fs.readFileSync('../lib-shared/method/referenceStock.ts', 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText,
  ctx
);
const { referenceStockField, referenceStockValue } = ctx.exports;
const field = { id: 'stock', internalKey: 'lingxingReferenceStock' };
assert.equal(referenceStockField([field]).id, 'stock');
assert.equal(referenceStockField([{ ...field, readable: false }]), undefined);
assert.equal(referenceStockField([{ ...field, show: false }]), undefined);
assert.equal(referenceStockField([]), undefined);
for (const quantity of [0, 1, -1, 1234, '0', '17']) {
  assert.equal(
    referenceStockValue({ moduleFields: [{ fieldId: 'stock', fieldValue: quantity }] }, 'stock'),
    Number(quantity)
  );
  assert.equal(referenceStockValue({ stock: quantity }, 'stock'), Number(quantity));
}
for (const quantity of [undefined, null, '', ' ', '-', true, [], {}, 'oops', 1.2, Infinity]) {
  assert.equal(referenceStockValue({ stock: quantity }, 'stock'), undefined);
}
assert.equal(referenceStockValue({ stock: 10 }), undefined);
assert.equal(referenceStockValue({}, 'stock'), undefined);
const source = { id: 'p', name: 'A', moduleFields: [{ fieldId: 'stock', fieldValue: 0 }] };
const original = JSON.stringify(source);
assert.equal(referenceStockValue(source, 'stock'), 0);
assert.equal(JSON.stringify(source), original);
const mobile = fs.readFileSync('src/components/business/crm-datasource/index.vue', 'utf8');
const lines = fs.readFileSync('src/views/order/components/product-lines.vue', 'utf8');
const web = fs.readFileSync('../web/src/components/business/crm-data-source-select/dataSourceTable.vue', 'utf8');
assert.ok(mobile.includes('getProductFormConfig'));
assert.ok(mobile.includes('referenceStockValue'));
assert.ok(lines.includes(':show-reference-stock="field.internalKey === \'orderProduct\'"'));
assert.ok(web.includes("props.fieldConfig?.internalKey === 'orderProduct'"));
assert.ok(web.includes('!appendedKeys.has(stockField.id)'));
console.log(
  'PASS: reference stock zero/negative/missing values, field permissions, unchanged product payload and scoped integrations'
);
