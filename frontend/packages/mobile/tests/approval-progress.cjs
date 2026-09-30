const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const ts = require('typescript');
const ctx = { exports: {} };
vm.runInNewContext(
  ts.transpileModule(fs.readFileSync('src/views/order/approval-progress.ts', 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText,
  ctx
);
const { nodeProgressState, taskProgressState, waitingApprovers } = ctx.exports;
const task = (name, state) => ({ approver: name, approvalStatus: state });
const node = (id, status, tasks = []) => ({ nodeId: id, approvalStatus: status, taskNodes: tasks });
const first = node('a', 'APPROVING', [task('B2B负责人', 'APPROVING')]);
const future = node('b', 'PENDING', [task('财务', 'PENDING')]);
const info = { approvalStatus: 'APPROVING', currentNodeId: 'a', nodes: [first, future] };
assert.equal(waitingApprovers(info).join(','), 'B2B负责人');
assert.equal(nodeProgressState(first, info), 'waiting');
assert.equal(taskProgressState(future.taskNodes[0], future, info), 'upcoming');
first.approvalStatus = first.taskNodes[0].approvalStatus = 'APPROVED';
future.approvalStatus = future.taskNodes[0].approvalStatus = 'APPROVING';
info.currentNodeId = 'b';
assert.equal(waitingApprovers(info).join(','), '财务');
assert.equal(nodeProgressState(first, info), 'approved');
for (const status of ['UNAPPROVED', 'REVOKED', 'APPROVED']) {
  const ended = { ...info, approvalStatus: status };
  assert.equal(waitingApprovers(ended).length, 0);
  assert.equal(taskProgressState(future.taskNodes[0], future, ended), 'stopped');
}
const rejected = node('b', 'UNAPPROVED', [task('财务', 'UNAPPROVED')]);
assert.equal(nodeProgressState(rejected, { ...info, approvalStatus: 'UNAPPROVED' }), 'rejected');
const sequential = node('c', 'APPROVING', [task('甲', 'APPROVED'), task('乙', 'APPROVING'), task('丙', 'PENDING')]);
const seqInfo = { ...info, nodes: [sequential] };
assert.equal(waitingApprovers(seqInfo).join(','), '乙');
assert.equal(taskProgressState(sequential.taskNodes[2], sequential, seqInfo), 'upcoming');
const parallel = node('d', 'APPROVING', [task('甲', 'APPROVING'), task('乙', 'APPROVING')]);
assert.equal(waitingApprovers({ ...info, nodes: [parallel] }).join(','), '甲,乙');
assert.equal(nodeProgressState({ ...first, approvalStatus: 'AUTO_APPROVED' }, info), 'approved');
assert.equal(nodeProgressState({ ...first, approvalStatus: 'AUTO_UNAPPROVED' }, info), 'rejected');
assert.equal(nodeProgressState({ endNode: true }, info), 'upcoming');
assert.equal(nodeProgressState({ endNode: true }, { ...info, approvalStatus: 'APPROVED' }), 'approved');
assert.equal(nodeProgressState({ endNode: true }, { ...info, approvalStatus: 'REVOKED' }), 'stopped');
assert.equal(nodeProgressState(node('x', null), info), 'unknown');
assert.equal(waitingApprovers({ ...info, nodes: null }).length, 0);
console.log('PASS: current/future, sequential/parallel, rejected/revoked/completed, automatic and missing data');

const anyDone = node('any', 'APPROVED', [task('甲', 'APPROVED'), task('乙', 'APPROVING')]);
assert.equal(taskProgressState(anyDone.taskNodes[1], anyDone, info), 'stopped');
