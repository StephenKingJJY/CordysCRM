# Mobile orders

The workbench provides separate Orders and New order shortcuts. The order list also
has a New order button. Viewing requires `ORDER:READ`; creation requires `ORDER:ADD`.
Creation uses the existing order form configuration and add-order API. Product lines
support product selection, editable unit prices, quantities, calculated line amounts,
and the configured order-total formula. Existing backend validations and downstream
Lingxing synchronization are unchanged. The form is not an order-editing interface.

Verify without creating a production order:

```sh
cd frontend/packages/mobile
node tests/order-create.cjs
pnpm type:check
pnpm build
```

The isolated test compiles the actual formula executor and exercises the actual save
handler with a mocked submission function. UI-only formatting and translation imports
are stubbed. It never calls the production API. Browser QA should inspect both entry
points, required-field validation, product selection, multiple lines, totals, and
virtual recipient numbers without submitting a completed production form.

## Generated icon

Asset: `frontend/packages/mobile/src/assets/images/order-shortcut.png`.
Created with the built-in image generation tool using the imagegen skill. The app
adds a frosted circular plus badge for New order; other shortcut icons are unchanged.
Original generated alpha is retained.

Final generation prompt:

> Use case: stylized-concept. Create ONE small mobile CRM shortcut icon, a lavender-violet order document/clipboard with three thick white horizontal item lines and a tiny white checkmark. Match a cohesive UI family of simple saturated colored silhouettes with subtly rounded corners, very restrained soft 3D highlights and frosted-glass accents (neighbor icons are teal briefcase, green customer card, blue person and amber coins). The icon must remain crisp and recognizable displayed at only 30x30 CSS pixels. Single object, front view with just slight depth, centered and filling 90% of square canvas, no pedestal, no letters, no numbers, no text, no border, no surrounding app mockup. Genuinely transparent background with alpha. No plus badge in this base asset; the application will add its own frosted circular plus badge for the create shortcut. Minimal soft shadow confined immediately around the object, bright and clean. Output a square PNG UI asset.
