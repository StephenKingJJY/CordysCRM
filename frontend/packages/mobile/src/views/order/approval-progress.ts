import type { ApprovalDetail, ApprovalNode, ApprovalNodeTaskNode } from '@lib/shared/models/system/process';

export type ProgressState = 'approved' | 'rejected' | 'waiting' | 'upcoming' | 'stopped' | 'skipped' | 'unknown';

function completedState(status?: string): ProgressState | undefined {
  if (['APPROVED', 'AUTO_APPROVED'].includes(status || '')) return 'approved';
  if (['UNAPPROVED', 'AUTO_UNAPPROVED'].includes(status || '')) return 'rejected';
  if (status === 'NONE') return 'skipped';
  return undefined;
}

export function taskProgressState(task: ApprovalNodeTaskNode, node: ApprovalNode, info: ApprovalDetail): ProgressState {
  const completed = completedState(task.approvalStatus);
  if (completed) return completed;
  if (info.approvalStatus !== 'APPROVING' || completedState(node.approvalStatus)) return 'stopped';
  if (task.approvalStatus === 'APPROVING' && node.approvalStatus === 'APPROVING') return 'waiting';
  if (task.approvalStatus === 'PENDING' || node.approvalStatus === 'PENDING') return 'upcoming';
  if (task.approvalStatus === 'REVOKED') return 'stopped';
  return 'unknown';
}

export function nodeProgressState(node: ApprovalNode, info: ApprovalDetail): ProgressState {
  if (node.endNode) {
    if (['APPROVED', 'AUTO_APPROVED'].includes(info.approvalStatus)) return 'approved';
    return info.approvalStatus === 'APPROVING' ? 'upcoming' : 'stopped';
  }
  const completed = completedState(node.approvalStatus);
  if (completed) return completed;
  if (info.approvalStatus !== 'APPROVING') return 'stopped';
  if (
    node.approvalStatus === 'APPROVING' ||
    node.taskNodes?.some((task) => taskProgressState(task, node, info) === 'waiting')
  ) {
    return 'waiting';
  }
  if (node.approvalStatus === 'PENDING') return 'upcoming';
  return 'unknown';
}

export function waitingApprovers(info: ApprovalDetail): string[] {
  if (info.approvalStatus !== 'APPROVING') return [];
  return [
    ...new Set(
      (info.nodes || []).flatMap((node) =>
        (node.taskNodes || [])
          .filter((task) => taskProgressState(task, node, info) === 'waiting')
          .map((task) => task.approver)
          .filter(Boolean)
      )
    ),
  ];
}
