const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const ts = require('typescript');

function compile(source, context) {
  vm.runInNewContext(
    ts.transpileModule(source, {
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
    }).outputText,
    context
  );
}
const context = { exports: {} };
compile(fs.readFileSync(path.resolve(__dirname, '../../lib-shared/method/lingxingStore.ts'), 'utf8'), context);
const { orderStoreId, orderStoreLocked } = context.exports;
const fields = [
  { id: 'shop', internalKey: 'lingxingOrderStore', defaultValue: 'A' },
  { id: 'products', internalKey: 'orderProducts' },
  { id: 'number', internalKey: 'lingxingOrderNumber' },
  { id: 'sync', internalKey: 'lingxingOrderSyncStatus' },
];
assert.equal(orderStoreId(fields, {}), 'A');
assert.equal(orderStoreId(fields, { shop: 'B' }), 'B');
assert.equal(orderStoreId([], {}), undefined);
for (const status of ['同步中', '待人工确认', '成功', '取消中', '已取消', '取消失败']) {
  assert.equal(orderStoreLocked({ moduleFields: [{ fieldId: 'sync', fieldValue: status }] }, fields), true);
}
assert.equal(orderStoreLocked({ number: 'LX' }, fields), true);
assert.equal(orderStoreLocked({ sync: '失败' }, fields), false);
assert.equal(orderStoreLocked({}, fields), false);
assert.equal(orderStoreLocked({ stage: 'SHIPPED' }, fields), true);

// Exercise the actual desktop query builder so a store switch cannot fall back to all products.
const desktopText = fs.readFileSync(
  path.resolve(__dirname, '../../web/src/components/business/crm-data-source-select/dataSourceTable.vue'),
  'utf8'
);
const desktopScript = desktopText.match(/<script[^>]*>([\s\S]*?)<\/script>/)[1];
const desktopAst = ts.createSourceFile('table.ts', desktopScript, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
const queryBuilder = desktopAst.statements.find(
  (node) => ts.isFunctionDeclaration(node) && node.name?.text === 'searchData'
);
assert.ok(queryBuilder);
let query;
const queryContext = {
  exports: {},
  props: { fieldConfig: { internalKey: 'orderProduct' } },
  selectedStore: { value: 'A' },
  keyword: { value: '' },
  isCustomForm: { value: false },
  setLoadListParams: (value) => {
    query = value;
  },
  loadList: () => true,
  crmTableRef: { value: null },
};
compile(`exports.search = ${queryBuilder.getText(desktopAst)};`, queryContext);
queryContext.exports.search();
assert.equal(query.lingxingStoreId, 'A');
queryContext.selectedStore.value = 'B';
queryContext.exports.search('SKU');
assert.equal(query.lingxingStoreId, 'B');
assert.equal(query.keyword, 'SKU');
queryContext.props.fieldConfig.internalKey = 'otherProduct';
queryContext.exports.search();
assert.equal(query.lingxingStoreId, undefined);

function watcher(file, confirm) {
  const text = fs.readFileSync(path.resolve(__dirname, file), 'utf8');
  const source = text.match(/<script[^>]*>([\s\S]*?)<\/script>/)[1];
  const ast = ts.createSourceFile('form.ts', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  let callback;
  ast.forEachChild((node) => {
    if (
      ts.isExpressionStatement(node) &&
      ts.isCallExpression(node.expression) &&
      node.expression.expression.getText(ast) === 'watch' &&
      node.expression.arguments[0]?.getText(ast) === 'selectedStore'
    )
      callback = node.expression.arguments[1].getText(ast);
  });
  assert.ok(callback, 'The actual form must watch store changes');
  let modal;
  const ctx = {
    exports: {},
    storeReady: true,
    storeChangePending: false,
    LINGXING_STORE_KEY: 'lingxingOrderStore',
    fieldList: { value: fields },
    formDetail: { value: { shop: 'B', products: [{ product: 'p', quantity: 2, price: 3 }] } },
    t: (key) => key,
    nextTick: async (fn) => fn?.(),
    openModal: (options) => {
      modal = options;
    },
    showConfirmDialog: confirm,
  };
  compile(`exports.run = ${callback};`, ctx);
  return { ctx, run: ctx.exports.run, modal: () => modal };
}

(async () => {
  const desktop = watcher('../../web/src/components/business/crm-form-create/index.vue');
  desktop.run('B', 'A');
  assert.ok(desktop.modal());
  assert.equal(desktop.ctx.formDetail.value.products.length, 1, 'Do not clear product lines before confirmation');
  desktop.modal().onNegativeClick();
  assert.equal(desktop.ctx.formDetail.value.shop, 'A');
  assert.equal(desktop.ctx.formDetail.value.products.length, 1);
  desktop.ctx.formDetail.value.shop = 'B';
  desktop.run('B', 'A');
  desktop.modal().onPositiveClick();
  assert.equal(desktop.ctx.formDetail.value.products.length, 0);
  const initializing = watcher('../../web/src/components/business/crm-form-create/index.vue');
  initializing.ctx.storeReady = false;
  initializing.run('B', 'A');
  assert.equal(initializing.modal(), undefined);
  assert.equal(initializing.ctx.formDetail.value.products.length, 1);
  const mobileCancel = watcher('../src/components/business/crm-form-create/index.vue', async () => {
    throw new Error('cancel');
  });
  await mobileCancel.run('B', 'A');
  assert.equal(mobileCancel.ctx.formDetail.value.shop, 'A');
  assert.equal(mobileCancel.ctx.formDetail.value.products.length, 1);
  const mobileConfirm = watcher('../src/components/business/crm-form-create/index.vue', async () => true);
  await mobileConfirm.run('B', 'A');
  assert.equal(mobileConfirm.ctx.formDetail.value.products.length, 0);
  console.log(
    'PASS: store default, sync lock, initial-load preservation, and confirmed/cancelled switching on both forms'
  );
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
