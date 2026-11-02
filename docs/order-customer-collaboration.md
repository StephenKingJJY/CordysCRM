# Order access through customer relationships

The order list includes orders owned by the current user and orders linked to a
customer they own or collaborate on. Existing role permissions and approval
status permissions are still required. Department views retain their department
scope. Customer and contract tabs apply their selected relationship filter.

Customer owners and `COLLABORATION` members may open an ordinary order edit form.
`READ_ONLY` members may only read the order and its approval/payment history.
Relationships are read from the live customer record; removal or reassignment
therefore takes effect on subsequent requests without rebuilding role caches.

An edit obtained solely through a customer relationship cannot change the order
owner or customer. The relationship does not grant bulk editing, stage changes,
deletion, downloading, export, payment mutation, submission or revocation of
approvals, or authority to approve a task. Existing ordinary data scopes continue
to govern those operations. No order owners or role definitions are rewritten.

List totals use the same relationship predicate as pagination. Export queries
explicitly keep the original ownership/department predicate.

Regression coverage includes customer ownership, editable and read-only
collaboration, immediate revocation, unrelated customers, organization isolation,
duplicate relationship records, list/statistic filters, retained export scope,
role/status checks, and denial of shared payment writes.
