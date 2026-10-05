// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  DRAFT: "草稿",
  REVIEW: "待技术复核",
  APPROVAL: "待批准",
  ACTIVE: "可使用",
  SCHEDULED: "未到生效日",
  EXPIRED: "已到期",
  EXHAUSTED: "额度用尽",
  REVOKED: "已撤销",
  CLOSED: "已关闭",
};
export const commands = {
  submit: "提交技术复核",
  review: "技术复核通过",
  "return-review": "退回申请",
  approve: "独立批准",
  "return-approval": "批准退回",
  revoke: "撤销授权",
  close: "关闭授权",
  use: "登记使用",
  void: "冲正登记",
  delete: "删除草稿",
};
export const labels = {
  code: "授权编号",
  title: "标题",
  category: "偏差分类",
  departmentId: "归属部门",
  reviewerId: "技术复核人",
  approverId: "批准人",
  executorId: "执行人",
  itemCode: "对象编号",
  itemRevision: "对象修订",
  scopeTag: "限定批次或范围",
  baseline: "标准要求",
  deviation: "临时偏差内容",
  controls: "控制措施与现场核查",
  externalAuthorization: "外部授权依据或不适用理由",
  quantityLimit: "批准数量上限",
  unit: "数量单位",
  validFrom: "生效日",
  validUntil: "截止日",
  reference: "本次使用凭证编号",
  quantity: "本次使用数量",
  evidence: "本次现场核查与证据",
  note: "理由或审核记录",
  username: "账号",
  displayName: "显示名称",
  password: "初始或重置密码",
  roleId: "角色",
  enabled: "启用",
  name: "名称",
  nameEn: "英文名称",
  scope: "数据范围",
  permissions: "业务权限",
  permissionCode: "所需权限",
  position: "排序",
  type: "字典类型",
  value: "参数值",
  oldPassword: "原密码",
  newPassword: "新密码",
};
export const errors = {
  UNAUTHENTICATED: "登录已失效，请重新登录",
  FORBIDDEN: "没有这项操作权限",
  OUT_OF_SCOPE: "记录超出当前数据范围",
  NOT_ASSIGNED: "只有指定责任人可以操作",
  INDEPENDENCE_REQUIRED: "申请、技术复核、批准须为独立人员",
  STALE_VERSION: "记录已变化，请刷新后重新操作",
  IDEMPOTENCY_CONFLICT: "重试内容已改变，请关闭窗口核对记录后重新操作",
  INVALID_STATE: "当前状态不允许这项操作",
  PERMIT_NOT_ACTIVE: "授权未生效、已到期、额度用尽或已终结，不能登记",
  PERMIT_EXPIRED: "授权日期已到期",
  QUANTITY_EXCEEDED: "本次数量超过剩余额度",
  INVALID_QUANTITY: "数量须为1至10亿的整数",
  BOUNDARY_MISMATCH: "对象编号、修订或限定范围与批准内容不一致",
  EVIDENCE_REQUIRED: "请填写至少10字的现场核查与证据",
  INVALID_BOUNDARY: "检查数量、单位和日期边界（最长365天）",
  INELIGIBLE_ACCOUNT: "责任人未启用、没有所需权限或部门不符",
  IMMUTABLE_IDENTITY: "编号与归属部门不能变更",
  HISTORY_PROTECTED: "已提交的申请不能删除历史",
  ALREADY_VOIDED: "该登记已经冲正",
  INVALID_REQUEST_KEY: "请求标识无效，请关闭窗口重新操作",
  INVALID_INPUT: "请检查必填内容和长度",
  INVALID_CODE: "授权编号使用3至60位字母、数字、下划线、点或连字符",
  INVALID_CATEGORY: "请选择有效偏差分类",
  INVALID_STATUS: "状态筛选无效",
  WEAK_PASSWORD: "密码至少12位，含大小写字母与数字，最多72字节",
  LAST_ADMIN: "必须保留一个可用的全范围管理员",
  CONFLICT: "编号重复或资源已有引用",
  BUILTIN_RESOURCE: "内建资源不能删除或更换标识",
  NETWORK_ERROR: "连接未完成，请检查服务后重试",
  REPORT_LIMIT: "统计超过10000条上限",
  LOGIN_FAILED: "账号或密码不正确",
  LOGIN_THROTTLED: "登录失败次数较多，请稍后再试",
  NOT_FOUND: "记录不存在",
};
/** 按服务器状态和指定责任人展示动作；实际权限仍由接口再次检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(p, me) {
  if (!p || !me) return [];
  const can = (x) => me.permissions.includes(x);
  const out = [];
  if (
    p.status === "DRAFT" &&
    p.authorId === me.id &&
    can("permit.write") &&
    me.scope !== "SELF"
  )
    out.push("submit");
  if (
    p.status === "REVIEW" &&
    p.reviewerId === me.id &&
    can("permit.review") &&
    me.scope !== "SELF"
  )
    out.push("review", "return-review");
  if (
    p.status === "APPROVAL" &&
    p.approverId === me.id &&
    can("permit.approve") &&
    me.scope !== "SELF"
  )
    out.push("approve", "return-approval");
  if (p.status === "ACTIVE" && p.executorId === me.id && can("permit.use"))
    out.push("use");
  if (
    ["ACTIVE", "EXPIRED", "EXHAUSTED", "SCHEDULED"].includes(p.status) &&
    p.approverId === me.id &&
    can("permit.approve") &&
    me.scope !== "SELF"
  )
    out.push("revoke", "close");
  return out;
}
/** 日期使用上海业务时区，仅供显示，不回写服务端时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const date = (v) =>
  v
    ? new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "Asia/Shanghai",
      }).format(new Date(v))
    : "—";
