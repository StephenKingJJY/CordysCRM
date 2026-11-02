const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const ts = require('typescript');
const ctx = { exports: {} };
vm.runInNewContext(
  ts.transpileModule(fs.readFileSync('../lib-shared/method/orderSharedAccess.ts', 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText,
  ctx
);
const { canUseSharedOrderAction: allows } = ctx.exports;
const readOnly = { sharedAccessOnly: true, sharedEditable: false };
const editor = { sharedAccessOnly: true, sharedEditable: true };
assert.equal(allows(readOnly, 'read'), true);
assert.equal(allows(readOnly, 'edit'), false);
assert.equal(allows(editor, 'edit'), true);
for (const action of ['delete', 'revoke', 'review', 'download', 'export', 'stage', 'payments']) {
  assert.equal(allows(readOnly, action), false);
  assert.equal(allows(editor, action), false);
  assert.equal(allows({ sharedAccessOnly: false }, action), true);
}
console.log('PASS: shared read-only/edit actions and existing order authority');
