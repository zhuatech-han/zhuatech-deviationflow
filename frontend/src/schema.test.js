// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { actions, errors, states } from "./schema.js";
const p = {
  authorId: 1,
  reviewerId: 2,
  approverId: 3,
  executorId: 4,
  status: "REVIEW",
};
const me = (id, permissions, scope = "DEPARTMENT") => ({
  id,
  permissions,
  scope,
});
test("only designated technical reviewer sees review", () => {
  assert.deepEqual(actions(p, me(2, ["permit.review"])), [
    "review",
    "return-review",
  ]);
  assert.deepEqual(actions(p, me(1, ["permit.review"])), []);
});
test("administrator permission does not substitute assigned approver", () => {
  assert.deepEqual(
    actions({ ...p, status: "APPROVAL" }, me(99, ["permit.approve"], "ALL")),
    [],
  );
});
test("SELF extra approval privilege is not a write route", () => {
  assert.deepEqual(
    actions({ ...p, status: "APPROVAL" }, me(3, ["permit.approve"], "SELF")),
    [],
  );
});
test("only assigned executor can record effective permit", () => {
  assert.ok(
    actions({ ...p, status: "ACTIVE" }, me(4, ["permit.use"], "SELF")).includes(
      "use",
    ),
  );
  for (const status of [
    "SCHEDULED",
    "EXPIRED",
    "EXHAUSTED",
    "REVOKED",
    "CLOSED",
  ])
    assert.ok(
      !actions({ ...p, status }, me(4, ["permit.use"], "SELF")).includes("use"),
    );
});
test("expired records remain revocable and closable for designated approver", () => {
  assert.deepEqual(
    actions({ ...p, status: "EXPIRED" }, me(3, ["permit.approve"])),
    ["revoke", "close"],
  );
});
test("domain conflicts have clear messages and terminal statuses", () => {
  assert.ok(errors.QUANTITY_EXCEEDED);
  assert.ok(errors.BOUNDARY_MISMATCH);
  assert.ok(states.REVOKED);
});
