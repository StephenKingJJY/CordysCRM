// Component regression tests; all API calls are mocked, no production data is accessed.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const { JSDOM } = require('jsdom');
const { parse, compileScript } = require('vue/compiler-sfc');
const esbuild = require(require.resolve('esbuild', { paths: [path.dirname(require.resolve('vite'))] }));
const dom = new JSDOM('<div id="app"></div>', { url: 'http://localhost' });
for (const k of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'Event']) global[k] = dom.window[k];
const { createApp, nextTick } = require('vue');
const tick = async () => { await new Promise(r => setTimeout(r, 0)); await nextTick(); };
async function main() {
  const filename = path.resolve('../lib-shared/components/order-payments.vue');
  const descriptor = parse(fs.readFileSync(filename, 'utf8'), { filename }).descriptor;
  const compiled = compileScript(descriptor, { id: 'payment-test', inlineTemplate: true });
  const bundle = await esbuild.build({
    stdin: { contents: compiled.content, loader: 'ts', resolveDir: process.cwd() },
    bundle: true, platform: 'node', format: 'cjs', write: false, external: ['vue'],
    plugins: [{ name: 'i18n', setup(b) {
      b.onResolve({ filter: /^@lib\/shared\/hooks\/useI18n$/ }, () => ({ path: 'i18n', namespace: 'stub' }));
      b.onLoad({ filter: /.*/, namespace: 'stub' }, () => ({ contents: 'export const useI18n=()=>({t:(k,p)=>k+ (p ? JSON.stringify(p) : "")});' }));
    } }],
  });
  const m = { exports: {} };
  vm.runInNewContext(bundle.outputFiles[0].text, { module: m, exports: m.exports, require, console, crypto: require('node:crypto').webcrypto, setTimeout, URL, File: dom.window.File, document });
  const Component = m.exports.default;
  let added = [], voided = [], rejectNext = false;
  const summary = { orderAmount: '10000.00', receivedAmount: '8000.00', remainingAmount: '2000.00', overpaidAmount: '0.00', status: 'PARTIAL', records: [] };
  const api = {
    get: async () => structuredClone(summary),
    add: async (id, data, files) => { added.push({ id, data, files }); if (rejectNext) { rejectNext = false; throw Error('network'); } },
    void: async (...args) => voided.push(args), receipt: async () => new Blob(),
  };
  const app = createApp(Component, { orderId: 'order-a', editable: true, api }); app.mount('#app'); await tick();
  const button = name => [...document.querySelectorAll('button')].find(b => b.textContent === `orderPayment.${name}`);
  assert.match(document.body.textContent, /8000.00/); assert.match(document.body.textContent, /2000.00/);
  button('add').click(); await tick();
  let amount = document.querySelector('input[inputmode="decimal"]'); amount.value = '6000.25'; amount.dispatchEvent(new Event('input', { bubbles: true }));
  let remark = document.querySelector('textarea'); remark.value = 'Part of same bank transfer'; remark.dispatchEvent(new Event('input', { bubbles: true }));
  rejectNext = true;
  document.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await tick();
  assert.match(document.body.textContent, /saveFailed/);
  document.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await tick();
  assert.equal(added.length, 2); assert.equal(added[0].data.id, added[1].data.id, 'Retry must retain its idempotency key');
  assert.equal(added[0].id, 'order-a'); assert.equal(added[0].data.amount, '6000.25'); assert.equal(document.querySelectorAll('form').length, 0);
  summary.records.push({ id: 'p1', amount: '6000.25', receivedDate: '2026-09-21', remark: '<script>bad</script>', createUserName: 'Finance', createTime: 1, voided: false, receipts: [] });
  button('refresh').click(); await tick();
  assert.equal(document.querySelectorAll('script').length, 0, 'Remarks must render as text');
  button('void').click(); await tick();
  assert.equal(button('confirmVoid').disabled, true);
  remark = document.querySelector('textarea'); remark.value = 'Wrong allocation'; remark.dispatchEvent(new Event('input', { bubbles: true })); await tick();
  document.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await tick();
  assert.deepEqual(voided[0], ['order-a', 'p1', 'Wrong allocation']);
  app.unmount();
  const readonly = createApp(Component, { orderId: 'order-a', editable: false, api }); readonly.mount('#app'); await tick();
  assert.equal(button('add'), undefined); assert.equal(button('void'), undefined); readonly.unmount();
  console.log('PASS: summary, manual allocation, idempotent retry, escaped remarks, reasoned void, read-only UI');
}
main().catch(e => { console.error(e); process.exit(1); });
