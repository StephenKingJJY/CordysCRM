# Order approval before Lingxing synchronization

Configure the native Cordys order approval flow with two sequential manual nodes: domestic B2B owner, then Finance. Enable both creation and update execution. Use explicit members, `sameSubmitterAction: ALLOW`, and `duplicateApproverRule: EACH`; never configure an automatic-pass branch for new orders. A saved new order remains pending submission until its owner submits it for approval.

The mobile order detail now displays approval state and offers submission for pending, rejected, or revoked orders to users with order-update permission. Approvers use the existing approval workbench on desktop or mobile.

The bridge creates upstream orders only when the current order is in `CREATE` and its approval status is `APPROVED`. It rechecks approval and the business-data fingerprint after recording the synchronization claim. Previously synced orders continue logistics reconciliation and cancellation without requiring retrospective approval.

`POST /order/{orderId}/lingxing-sync` replaces whole-order writes from the bridge. The request supplies `expectedUpdateTime` and a `values` map keyed by one of seven explicitly allowed synchronization/logistics internal keys. It checks order-update permission and order data scope, locks the organization-scoped order row, rejects stale business versions, and requires `APPROVED` + `CREATE` for a new synchronization claim. Only the requested metadata fields and corresponding current snapshot fields are patched; business fields, approval state, and business timestamps remain unchanged. This endpoint does not trigger the ordinary order-edit approval hook. It cannot update prices, products, address, approval state, or arbitrary fields.

Deployment order: back up CRM; deploy the CRM endpoint and mobile UI; deploy the gated bridge; configure/enable the native approval flow. Verify any existing unsynced new order before submitting it to the new flow. Do not approve a real order on behalf of either approver during verification.

No database migration is required. Tests cover permission denial, stale revisions, organization isolation, repeated metadata writes, preservation of business fields and approval state, disallowed approval states, changes while preparing synchronization, and legacy logistics reconciliation.
