// Run from frontend/packages/mobile: node tests/list-search.cjs
// Exercise real component functions with isolated fake APIs; no business data.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const ts = require('typescript');
const source = fs.readFileSync('src/components/pure/crm-list/index.vue', 'utf8');
const code = source.slice(source.indexOf('  const currentPage'), source.indexOf('  defineExpose('));
const ref = (value) => ({ value });
function harness(props, initial = []) {
  const ctx = {
    props,
    ref,
    list: ref(initial),
    loading: ref(false),
    refreshing: ref(false),
    finished: ref(false),
    error: ref(false),
    cloneDeep: structuredClone,
    showLoadingToast() {},
    showSuccessToast() {},
    closeToast() {},
    t: (k) => k,
    emit() {},
    console: { log() {} },
  };
  vm.runInNewContext(
    ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText +
      '\nthis.api={loadList,filterListByKeyword};',
    ctx
  );
  return ctx;
}
const rows = Array.from({ length: 43 }, (_, i) => ({
  id: String(i + 1),
  name: i === 42 ? 'OTMF-LG-01-TB-CN 国内奶泡棒' : `Product ${i + 1}`,
}));
(async () => {
  const calls = [];
  let fail = false;
  const props = {
    keyword: '',
    listParams: { combineSearch: { searchMode: 'AND' } },
    loadListApi: async (p) => {
      calls.push(p);
      if (fail) {
        fail = false;
        throw Error('network');
      }
      const data = rows.filter((r) => r.name.includes(p.keyword));
      return { list: data.slice((p.current - 1) * 20, p.current * 20), total: data.length };
    },
  };
  const h = harness(props);
  await h.api.loadList(true);
  await h.api.loadList();
  assert.equal(h.list.value.length, 40, 'Pagination keeps earlier products');
  assert.equal(h.list.value[0].id, '1');
  props.keyword = '国内奶泡棒';
  await h.api.filterListByKeyword('name');
  assert.equal(calls.at(-1).current, 1);
  assert.equal(calls.at(-1).keyword, '国内奶泡棒');
  assert.equal(calls.at(-1).combineSearch.searchMode, 'AND');
  assert.equal(h.list.value[0].id, '43', 'Search finds a product beyond loaded pages');
  props.keyword = '';
  await h.api.filterListByKeyword('name');
  assert.equal(h.list.value.length, 20, 'Clearing search reloads page one');
  fail = true;
  await h.api.loadList();
  assert.equal(h.list.value.length, 20, 'Failed page retains visible products');
  await h.api.loadList();
  assert.equal(calls.at(-1).current, 2, 'Retry repeats failed page');
  assert.equal(h.list.value.length, 40);
  fail = true;
  await h.api.loadList(true);
  await h.api.loadList();
  assert.equal(calls.at(-1).current, 1, 'Failed refresh retries page one');
  props.keyword = 'not submitted';
  await h.api.loadList();
  assert.equal(calls.at(-1).keyword, '', 'Scrolling keeps committed search');
  props.listParams.keyword = 'Product 1';
  await h.api.loadList(true);
  assert.equal(calls.at(-1).keyword, 'Product 1', 'listParams keyword remains supported');
  const localProps = { keyword: '' };
  const local = harness(localProps, rows.slice(0, 2));
  await local.api.loadList(true);
  assert.equal(local.list.value.length, 2, 'Static list survives initialization');
  localProps.keyword = '2';
  local.api.filterListByKeyword('name');
  assert.equal(local.list.value.length, 1);
  localProps.keyword = '';
  local.api.filterListByKeyword('name');
  assert.equal(local.list.value.length, 2);
  let optionCalls = 0;
  const optionProps = {
    noPageNation: true,
    keyword: '',
    loadListApi: async () => {
      optionCalls++;
      return rows;
    },
  };
  const options = harness(optionProps);
  await options.api.loadList(true);
  optionProps.keyword = '国内';
  options.api.filterListByKeyword('name');
  assert.equal(optionCalls, 1, 'Complete option lists retain local search');
  assert.equal(options.list.value[0].id, '43');
  const deferred = [];
  const raceProps = { keyword: '', loadListApi: (p) => new Promise((resolve) => deferred.push({ p, resolve })) };
  const race = harness(raceProps);
  const old = race.api.loadList(true);
  await race.api.loadList();
  assert.equal(deferred.length, 1, 'Duplicate page load ignored');
  raceProps.keyword = '国内';
  const latest = race.api.filterListByKeyword('name');
  deferred[1].resolve({ list: [rows[42]], total: 1 });
  await latest;
  deferred[0].resolve({ list: rows.slice(0, 20), total: 43 });
  await old;
  assert.equal(race.list.value.length, 1);
  assert.equal(race.list.value[0].id, '43', 'Late response cannot overwrite search');
  assert.equal(race.loading.value, false);
  console.log(
    'PASS: pagination, full-dataset search, reset, retry, committed keywords, static lists, complete options, concurrent searches'
  );
})().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
