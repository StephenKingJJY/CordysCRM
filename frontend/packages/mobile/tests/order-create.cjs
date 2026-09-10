// Run from frontend/packages/mobile: node tests/order-create.cjs
// UI dependencies are stubbed; order submission is mocked and never reaches a server.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const ts = require('typescript');
const esbuild = require(require.resolve('esbuild', { paths: [path.dirname(require.resolve('vite'))] }));

async function main() {
  const executor = path.resolve('../web/src/components/business/crm-formula/formula-runtime/formula-executor/index.ts');
  const built = await esbuild.build({
    stdin: { contents: `export {executeFormFormula} from ${JSON.stringify(executor)};`, resolveDir: process.cwd() },
    bundle: true,
    platform: 'node',
    format: 'cjs',
    write: false,
    plugins: [
      {
        name: 'ui-stubs',
        setup(b) {
          b.onResolve({ filter: /^@lib\/shared\/hooks\/useI18n$/ }, () => ({ path: 'i18n', namespace: 'stub' }));
          b.onResolve({ filter: /^@lib\/shared\/method\/formCreate$/ }, () => ({ path: 'format', namespace: 'stub' }));
          b.onLoad({ filter: /.*/, namespace: 'stub' }, (a) => ({
            contents:
              a.path === 'i18n'
                ? 'export const useI18n=()=>({t:k=>k});'
                : 'export const formatFormulaResultValue=v=>v;',
          }));
          b.onResolve({ filter: /^@lib\/shared\// }, (a) => ({
            path: path.resolve('../lib-shared', `${a.path.slice('@lib/shared/'.length)}.ts`),
          }));
        },
      },
    ],
  });
  const bundle = { exports: {} };
  vm.runInNewContext(built.outputFiles[0].text, {
    module: bundle,
    exports: bundle.exports,
    require,
    console,
    structuredClone,
  });
  const run = bundle.exports.executeFormFormula;
  const field = (id) => ({ type: 'field', fieldId: id, fieldType: 'INPUT_NUMBER', numberType: 'number' });
  const price = { id: 'price', type: 'INPUT_NUMBER', numberFormat: 'number', precision: 2 };
  const qty = { id: 'qty', type: 'INPUT_NUMBER', numberFormat: 'number', precision: 0 };
  const amount = { id: 'amount', type: 'FORMULA' };
  const fields = [{ id: 'lines', type: 'SUB_PRODUCT', subFields: [price, qty, amount] }];
  const form = {
    lines: [
      { price: 12.35, qty: 3 },
      { price: 0, qty: 2 },
    ],
  };
  const lineFormula = JSON.stringify({
    ir: { type: 'binary', operator: '*', left: field('price'), right: field('qty') },
  });
  for (let i = 0; i < form.lines.length; i++) {
    form.lines[i].amount = run({
      formula: lineFormula,
      path: `lines[${i}].amount`,
      formDetail: form,
      fields,
      formulaDataSource: {},
    }).normalizedResult;
  }
  assert.equal(form.lines[0].amount, 37.05);
  assert.equal(form.lines[1].amount, 0);
  const sumFormula = JSON.stringify({ ir: { type: 'function', name: 'SUM', args: [field('lines.amount')] } });
  assert.equal(run({ formula: sumFormula, formDetail: form, fields, formulaDataSource: {} }).normalizedResult, 37.05);
  form.lines[1].amount = 2.35;
  assert.equal(run({ formula: sumFormula, formDetail: form, fields, formulaDataSource: {} }).normalizedResult, 39.4);
  assert.equal(run({ formula: 'broken', formDetail: form, fields, formulaDataSource: {} }), undefined);

  const source = fs.readFileSync('src/components/business/crm-form-create/index.vue', 'utf8');
  const code = source.slice(source.indexOf('  async function handleSave()'), source.indexOf('  onBeforeMount('));
  const ref = (value) => ({ value });
  const saves = [];
  let release;
  const ctx = {
    loading: ref(false),
    saving: ref(false),
    fieldList: ref([{}]),
    mobileFieldList: ref([
      { id: 'lines', type: 'SUB_PRODUCT', subFields: [price, qty, { id: 'product', type: 'DATA_SOURCE' }] },
    ]),
    formDetail: ref({
      lines: [{ _mobileKey: 'local-only', price: '12.35', qty: '3', product: ['sku-id'], amount: 37.05 }],
    }),
    formKey: 'ORDER',
    FormDesignKeyEnum: { ORDER: 'ORDER' },
    FieldTypeEnum: {
      SUB_PRODUCT: 'SUB_PRODUCT',
      INPUT_NUMBER: 'INPUT_NUMBER',
      DATA_SOURCE: 'DATA_SOURCE',
      PHONE: 'PHONE',
    },
    nextTick: async () => {},
    formRef: ref({ validate: async () => {} }),
    cloneDeep: structuredClone,
    saveForm: async (payload) => {
      saves.push(payload);
      await new Promise((r) => {
        release = r;
      });
    },
    router: { back() {} },
    console: { log() {} },
  };
  vm.createContext(ctx);
  vm.runInContext(ts.transpile(code, { module: ts.ModuleKind.CommonJS }), ctx);
  const first = ctx.handleSave();
  await new Promise(setImmediate);
  await ctx.handleSave();
  assert.equal(saves.length, 1);
  assert.equal(saves[0].lines[0]._mobileKey, undefined);
  assert.equal(saves[0].lines[0].price, 12.35);
  assert.equal(saves[0].lines[0].qty, 3);
  assert.equal(saves[0].lines[0].product, 'sku-id');
  release();
  await first;
  assert.equal(ctx.saving.value, false);
  ctx.formRef.value.validate = async () => {
    throw Error('required');
  };
  await ctx.handleSave();
  assert.equal(saves.length, 1);
  assert.equal(ctx.saving.value, false);
  console.log(
    'PASS: decimal amounts, zero price, multi-line totals, formula errors, payload normalization, duplicate-submit guard, required validation. No production order submitted.'
  );
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
