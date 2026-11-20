/* eslint-disable no-await-in-loop -- Each sequence checks failure followed by recovery on the same client. */
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const ts = require('typescript');
const axios = require('axios');

function load(file, imports) {
  const context = {
    exports: {},
    require: (id) => {
      assert.ok(id in imports, `Unexpected import: ${id}`);
      return imports[id];
    },
    window: { location: { origin: 'https://crm.example' } },
    localStorage: { getItem: () => null },
    console: {
      log() {
        /* Expected request failures stay out of the test output. */
      },
    },
  };
  const source = fs.readFileSync(path.resolve(__dirname, file), 'utf8');
  vm.runInNewContext(
    ts.transpileModule(source.replaceAll('import.meta.env.VITE_API_BASE_URL', "''"), {
      compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, esModuleInterop: true },
    }).outputText,
    context
  );
  return context.exports;
}

const { CordysAxios } = load('../../lib-shared/api/http/Axios.ts', {
  'lodash-es': { cloneDeep: (value) => ({ ...value }) },
  axios,
  '../../enums/httpEnum': { ContentTypeEnum: { JSON: 'application/json' } },
  '../../method/is': { isFunction: (value) => typeof value === 'function' },
  './axiosCancel': {
    AxiosCanceler: class {
      addPending() {
        /* Cancellation is outside this error-tip regression. */
      }

      removePending() {
        /* Cancellation is outside this error-tip regression. */
      }
    },
  },
  './axiosTransform': {},
});
const createAxios = load('../../lib-shared/api/http/index.ts', {
  '../../method/local-storage': { getLocalStorage: () => ({ orgId: 'test' }) },
  './Axios': { CordysAxios },
  './helper': { joinTimestamp: () => ({}) },
  '@lib/shared/enums/httpEnum': { ContentTypeEnum: { JSON: 'application/json' }, RequestEnum: { GET: 'GET' } },
  '@lib/shared/hooks/useI18n': { useI18n: () => ({ t: (key) => key }) },
  '@lib/shared/method': {
    deepMerge: (defaults, options) => ({ ...defaults, ...options }),
    setObjToUrlParams: (url) => url,
  },
  '@lib/shared/method/auth': { getToken: () => null },
  '@lib/shared/method/is': { isString: (value) => typeof value === 'string' },
}).default;

(async () => {
  const tips = [];
  const statuses = [];
  const client = createAxios({
    showErrorMsg: (tip) => tips.push(tip),
    checkStatus: (...args) => statuses.push(args),
  });
  let attempts = 0;
  let failure;
  client.axiosInstance.defaults.adapter = async (config) => {
    attempts += 1;
    if (failure) throw new axios.AxiosError(failure.message, failure.code, config);
    return { data: { code: 100200, data: { ready: true } }, status: 200, statusText: 'OK', headers: {}, config };
  };
  for (const error of [
    { code: 'ERR_NETWORK', message: 'Network Error' },
    { code: 'ECONNABORTED', message: 'timeout of 300000ms exceeded' },
  ]) {
    failure = error;
    await assert.rejects(client.get({ url: '/is-login' }, { noErrorTip: true }), { code: error.code });
    assert.equal(tips.length, 0, 'Silent login checks must not display a network or timeout toast');
    failure = null;
    assert.equal((await client.get({ url: '/is-login' }, { noErrorTip: true })).ready, true);
  }
  for (const method of ['get', 'post']) {
    failure = { code: 'ERR_NETWORK', message: 'Network Error' };
    const before = attempts;
    await assert.rejects(client[method]({ url: method === 'get' ? '/module/form/config/customer' : '/order/add' }));
    assert.equal(attempts, before + 1, 'Business requests must not be automatically repeated');
    assert.equal(tips.at(-1).message, 'api.networkExceptionMsg');
  }
  failure = { code: 'ECONNABORTED', message: 'timeout of 300000ms exceeded' };
  await assert.rejects(client.get({ url: '/module/form/config/customer' }));
  assert.equal(tips.at(-1).message, 'api.apiTimeoutMessage');
  assert.equal(tips.length, 3, 'Each non-silent business failure must display exactly one toast');
  assert.equal(statuses.length, 0);
  console.log('PASS: silent startup failures, recovery, visible business errors, and no repeated writes');
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
