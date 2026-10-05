<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  ShieldCheck,
  LogOut,
  Plus,
  RefreshCw,
  X,
  ChevronLeft,
  ChevronRight,
  ArrowUpRight,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import { states, commands, labels, errors, actions, date } from "./schema.js";
const me = ref(null),
  login = ref({ username: "", password: "" }),
  page = ref("workbench"),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  notice = ref(""),
  rows = ref([]),
  total = ref(0),
  offset = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  dialog = ref(null),
  options = ref({
    accounts: [],
    departments: [],
    dictionaries: [],
    settings: [],
  }),
  roles = ref([]),
  permissions = ref([]),
  stats = ref({ counts: {}, quantities: {} }),
  work = ref({ review: [], approval: [], use: [], draft: [] });
const record = computed(() => detail.value?.record),
  can = (p) => me.value?.permissions.includes(p),
  title = computed(
    () =>
      me.value?.menus.find((m) => m.code === page.value)?.name || "授权工作台",
  );
const department = (id) =>
  options.value.departments.find((d) => d.id === id)?.name || "#" + id;
const filtered = computed(() =>
    rows.value.filter((r) =>
      JSON.stringify(r).toLowerCase().includes(search.value.toLowerCase()),
    ),
  ),
  visibleRows = computed(() =>
    page.value === "permits"
      ? rows.value
      : filtered.value.slice(offset.value * 20, offset.value * 20 + 20),
  ),
  pageTotal = computed(() =>
    page.value === "permits" ? total.value : filtered.value.length,
  );
const f = (key, type = "text", extra = {}) => ({ key, type, ...extra }),
  choices = (list, label = "name", value = "id") =>
    list.map((a) => ({ label: a[label], value: a[value] }));
const candidates = (permission) =>
  options.value.accounts.filter(
    (a) => a.enabled && a.permissions.includes(permission),
  );
const fields = {
  permits: () => [
    f("code", "text", { readonly: !!dialog.value?.id, max: 60 }),
    f("title", "text", { max: 160 }),
    f("departmentId", "select", {
      options: choices(options.value.departments),
      readonly: !!dialog.value?.id,
    }),
    f("category", "select", {
      options: choices(
        options.value.dictionaries.filter((d) => d.type === "permit"),
        "name",
        "code",
      ),
    }),
    f("itemCode", "text", { max: 100 }),
    f("itemRevision", "text", { max: 60 }),
    f("scopeTag", "text", { max: 120 }),
    f("quantityLimit", "number", { min: 1, max: 1000000000 }),
    f("unit", "select", {
      options: ["件", "批", "套"].map((v) => ({ value: v, label: v })),
    }),
    f("validFrom", "date"),
    f("validUntil", "date"),
    f("reviewerId", "select", {
      options: choices(
        candidates("permit.review").filter((a) => a.id !== me.value.id),
      ),
    }),
    f("approverId", "select", {
      options: choices(
        candidates("permit.approve").filter(
          (a) =>
            a.id !== me.value.id && a.id !== dialog.value?.values.reviewerId,
        ),
      ),
    }),
    f("executorId", "select", { options: choices(candidates("permit.use")) }),
    f("baseline", "textarea"),
    f("deviation", "textarea"),
    f("controls", "textarea"),
    f("externalAuthorization", "textarea", { max: 1000 }),
  ],
  users: () => [
    f("username"),
    f("displayName"),
    f("password", "password", { required: !dialog.value?.id }),
    f("roleId", "select", { options: choices(roles.value) }),
    f("departmentId", "select", {
      options: choices(options.value.departments),
    }),
    f("enabled", "checkbox"),
  ],
  roles: () => [
    f("name"),
    f("scope", "select", {
      options: [
        { value: "ALL", label: "全部部门" },
        { value: "DEPARTMENT", label: "本部门" },
        { value: "SELF", label: "本人相关" },
      ],
    }),
    f("permissions", "permissions", {
      options: choices(permissions.value, "name", "code"),
    }),
  ],
  departments: () => [f("name")],
  menus: () => [
    f("code", "text", { readonly: true }),
    f("name"),
    f("nameEn"),
    f("permissionCode", "select", {
      options: choices(permissions.value, "name", "code"),
    }),
    f("position", "number"),
    f("enabled", "checkbox"),
  ],
  permissions: () => [f("code", "text", { readonly: true }), f("name")],
  dictionaries: () => [f("type"), f("code"), f("name"), f("nameEn")],
  settings: () => [f("code", "text", { readonly: true }), f("value")],
  password: () => [f("oldPassword", "password"), f("newPassword", "password")],
};
const dialogFields = computed(() => {
  let d = dialog.value;
  if (!d) return [];
  if (d.action === "use")
    return [
      f("reference", "text", { max: 100 }),
      f("quantity", "number", { min: 1, max: record.value.remaining }),
      f("itemCode", "text", { max: 100 }),
      f("itemRevision", "text", { max: 60 }),
      f("scopeTag", "text", { max: 120 }),
      f("evidence", "textarea", { minLength: 10 }),
    ];
  if (d.action) return d.action === "delete" ? [] : [f("note", "textarea")];
  return fields[d.kind]?.() || [];
});
/** 登出或会话失效清除上个账号业务资料。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function clear() {
  me.value = null;
  detail.value = null;
  dialog.value = null;
  rows.value = [];
  roles.value = [];
  permissions.value = [];
  options.value = {
    accounts: [],
    departments: [],
    dictionaries: [],
    settings: [],
  };
  work.value = { review: [], approval: [], use: [], draft: [] };
  stats.value = { counts: {}, quantities: {} };
  search.value = "";
  notice.value = "";
  page.value = "workbench";
}
function fail(e) {
  error.value = errors[e.message] || "操作未完成，请核对输入后重试";
  if (e.message === "UNAUTHENTICATED") clear();
}
/** 刷新实时权限和数据，避免旧页面暴露前一账号记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function load() {
  if (!me.value) return;
  loading.value = true;
  error.value = "";
  try {
    me.value = await api("/auth/me");
    options.value = await api("/options");
    if (can("admin") && me.value.scope === "ALL") {
      roles.value = await api("/admin/roles");
      permissions.value = await api("/admin/permissions");
    }
    if (!me.value.menus.some((m) => m.code === page.value))
      page.value = me.value.menus[0]?.code || "workbench";
    if (detail.value) {
      detail.value = await api("/permits/" + record.value.id);
      return;
    }
    if (page.value === "permits") {
      let v = await api(
        "/permits?" +
          new URLSearchParams({
            search: search.value,
            status: status.value,
            page: offset.value,
            size: 20,
            sort: sort.value,
          }),
      );
      rows.value = v.items;
      total.value = v.total;
    } else if (page.value === "workbench") work.value = await api("/workbench");
    else if (page.value === "dashboard") stats.value = await api("/dashboard");
    else
      rows.value = await api(
        page.value === "audit" ? "/audit" : "/admin/" + page.value,
      );
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
/** 实际会话登录，不把密码保存到浏览器存储。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function signIn() {
  saving.value = true;
  error.value = "";
  try {
    resetCsrf();
    me.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function signOut() {
  try {
    await api("/auth/logout", "POST");
  } catch (e) {
    fail(e);
  } finally {
    resetCsrf();
    clear();
  }
}
async function navigate(code) {
  if (loading.value || saving.value) return;
  page.value = code;
  detail.value = null;
  rows.value = [];
  offset.value = 0;
  search.value = "";
  status.value = "";
  notice.value = "";
  await load();
}
async function show(id) {
  if (loading.value || saving.value) return;
  loading.value = true;
  error.value = "";
  try {
    detail.value = await api("/permits/" + id);
  } catch (e) {
    fail(e);
  } finally {
    loading.value = false;
  }
}
function edit(kind, row) {
  dialog.value = {
    kind,
    id: row?.id,
    title: row ? "编辑" + title.value : "新建" + title.value,
    values: row
      ? structuredClone(row)
      : {
          enabled: true,
          scope: "DEPARTMENT",
          permissions: [],
          departmentId: me.value.departmentId,
          unit: "件",
          category: "PROCESS",
          quantityLimit: 100,
          validFrom: options.value.today,
          validUntil: options.value.today,
          type: "permit",
        },
  };
}
function command(action, usage) {
  dialog.value = {
    kind: "permits",
    action,
    usageId: usage?.id,
    title: commands[action],
    requestKey: crypto.randomUUID(),
    values:
      action === "use"
        ? {
            quantity: 1,
            itemCode: record.value.itemCode,
            itemRevision: record.value.itemRevision,
            scopeTag: record.value.scopeTag,
          }
        : { note: "" },
  };
}
/** 状态命令保留原UUID供精确重试，服务端拒绝改内容的同键请求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  if (saving.value) return;
  saving.value = true;
  error.value = "";
  const d = dialog.value;
  try {
    if (d.kind === "password") await api("/auth/password", "POST", d.values);
    else if (d.action) {
      const id = record.value.id;
      const payload = {
        ...d.values,
        version: record.value.version,
        requestKey: d.requestKey,
      };
      if (d.action === "delete")
        await api(
          "/permits/" + id + "?version=" + record.value.version,
          "DELETE",
        );
      else
        await api(
          "/permits/" +
            id +
            (d.action === "use"
              ? "/usages"
              : d.action === "void"
                ? "/usages/" + d.usageId + "/void"
                : "/commands/" + d.action),
          "POST",
          payload,
        );
      if (d.action === "delete") detail.value = null;
    } else if (d.action === undefined) {
      const prefix = d.kind === "permits" ? "/permits" : "/admin/" + d.kind;
      await api(
        prefix + (d.id ? "/" + d.id : ""),
        d.id ? "PUT" : "POST",
        d.values,
      );
    }
    dialog.value = null;
    notice.value = "已保存";
    if (d.kind === "password") {
      clear();
      resetCsrf();
      notice.value = "密码已更新，请重新登录";
    } else await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
async function adminDelete(row) {
  dialog.value = {
    kind: page.value,
    id: row.id,
    action: "admin-delete",
    title: "删除" + (row.name || row.username),
    values: {},
  };
}
async function submitDialog() {
  if (dialog.value.action !== "admin-delete") return save();
  saving.value = true;
  try {
    await api("/admin/" + dialog.value.kind + "/" + dialog.value.id, "DELETE");
    dialog.value = null;
    notice.value = "已删除";
    await load();
  } catch (e) {
    fail(e);
  } finally {
    saving.value = false;
  }
}
/** 授权JSON仅含业务资料，继承详情数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function exportReport() {
  try {
    const data = await api("/permits/" + record.value.id + "/report.json");
    const url = URL.createObjectURL(
      new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "permit-" + record.value.code + ".json";
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    fail(e);
  }
}
async function move(n) {
  offset.value += n;
  await load();
}
onMounted(async () => {
  try {
    me.value = await api("/auth/me");
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>
<template>
  <div v-if="!me" class="login-shell">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" />
      <p class="eyebrow">DEVIATIONFLOW / 0.1.0</p>
      <h1>临时偏差<br />有期限、有边界</h1>
      <p>事先授权 · 独立复核 · 限量使用 · 到期停止</p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >知华科技官网 <ArrowUpRight :size="15"
      /></a>
    </section>
    <section class="login-form">
      <p class="eyebrow">CONTROLLED PROCESS DEVIATIONS</p>
      <h2>登录偏差授权工作台</h2>
      <form @submit.prevent="signIn">
        <label
          >账号<input
            v-model="login.username"
            autocomplete="username"
            required /></label
        ><label
          >密码<input
            v-model="login.password"
            type="password"
            autocomplete="current-password"
            required
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <p v-if="notice" class="success">{{ notice }}</p>
        <button class="primary" :disabled="saving">登录</button>
      </form>
      <p class="subtle">公开源码学习版 · 未经书面授权不得商用</p>
      <small
        >上海如静知华信息科技有限公司<br />商业咨询微信 zhuatech /
        zhuatech2</small
      >
    </section>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><strong
          >DeviationFlow</strong
        >
      </div>
      <p class="sidebar-caption">临时工艺偏差协作</p>
      <nav>
        <button
          v-for="menu in me.menus"
          :key="menu.code"
          :class="{ active: page === menu.code }"
          @click="navigate(menu.code)"
          :disabled="loading || saving"
        >
          <ShieldCheck :size="17" />{{ menu.name }}
        </button>
      </nav>
      <footer class="sidebar-footer">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技官网 <ArrowUpRight :size="13" /></a
        ><small>公开源码学习版 0.1.0</small>
      </footer>
    </aside>
    <div class="content-shell">
      <header class="topbar">
        <span>{{ department(me.departmentId) }}</span>
        <div>
          <button
            :disabled="loading || saving"
            @click="
              dialog = { kind: 'password', title: '修改密码', values: {} }
            "
          >
            修改密码</button
          ><span>{{ me.displayName }} · {{ me.role }}</span
          ><button
            aria-label="退出登录"
            :disabled="loading || saving"
            @click="signOut"
          >
            <LogOut :size="18" />
          </button>
        </div>
      </header>
      <main>
        <div class="page-heading">
          <div>
            <p class="eyebrow">DEVIATION AUTHORIZATION</p>
            <h1>{{ detail ? "授权详情" : title }}</h1>
          </div>
          <button @click="load" :disabled="loading || saving">
            <RefreshCw :size="16" />刷新
          </button>
        </div>
        <div v-if="error" role="alert" class="error">{{ error }}</div>
        <div v-if="notice" role="status" class="success">{{ notice }}</div>
        <p v-if="loading" class="subtle">正在读取记录…</p>
        <template v-if="detail"
          ><button
            :disabled="loading || saving"
            @click="
              detail = null;
              load();
            "
          >
            <ChevronLeft :size="16" />返回列表
          </button>
          <section class="panel detail-head">
            <div>
              <h2>{{ record.code }} · {{ record.title }}</h2>
              <span class="badge" :class="record.status">{{
                states[record.status]
              }}</span>
            </div>
            <div class="toolbar">
              <button
                v-if="
                  record.status === 'DRAFT' &&
                  record.authorId === me.id &&
                  can('permit.write') &&
                  me.scope !== 'SELF'
                "
                @click="edit('permits', record)"
              >
                编辑申请</button
              ><button
                v-if="
                  record.status === 'DRAFT' &&
                  record.authorId === me.id &&
                  can('permit.write') &&
                  me.scope !== 'SELF'
                "
                @click="command('delete')"
              >
                删除草稿</button
              ><button
                v-for="action in actions(record, me)"
                :key="action"
                :class="{ primary: action === 'use' }"
                @click="command(action)"
              >
                {{ commands[action] }}</button
              ><button v-if="can('export')" @click="exportReport">
                导出 JSON
              </button>
            </div>
          </section>
          <section class="panel">
            <div class="permit-capacity">
              <div>
                <span>已登记 / 批准上限</span
                ><strong
                  >{{ record.usedQuantity }} / {{ record.quantityLimit }}
                  <small>{{ record.unit }}</small></strong
                >
              </div>
              <div>
                <span>剩余可用量</span
                ><strong
                  >{{ record.remaining }}
                  <small>{{ record.unit }}</small></strong
                >
              </div>
              <div>
                <span>限定日期</span
                ><strong class="date-range"
                  >{{ record.validFrom }} — {{ record.validUntil }}</strong
                >
              </div>
            </div>
            <dl class="facts">
              <dt>对象编号 / 修订</dt>
              <dd>{{ record.itemCode }} / {{ record.itemRevision }}</dd>
              <dt>限定批次或范围</dt>
              <dd>{{ record.scopeTag }}</dd>
              <dt>归属部门</dt>
              <dd>{{ department(record.departmentId) }}</dd>
              <dt>申请人</dt>
              <dd>{{ record.authorName }}</dd>
              <dt>技术复核人</dt>
              <dd>{{ record.reviewerName }}</dd>
              <dt>批准人</dt>
              <dd>{{ record.approverName }}</dd>
              <dt>执行人</dt>
              <dd>{{ record.executorName }}</dd>
              <dt>批准时间</dt>
              <dd>{{ date(record.approvedAt) }}</dd>
            </dl>
          </section>
          <section class="panel permit-specs">
            <article
              v-for="key in [
                'baseline',
                'deviation',
                'controls',
                'externalAuthorization',
                'reviewNote',
                'approvalNote',
              ]"
              :key="key"
            >
              <h3>
                {{
                  { reviewNote: "技术复核结论", approvalNote: "批准结论" }[
                    key
                  ] || labels[key]
                }}
              </h3>
              <p class="course-content">{{ record[key] || "尚无记录" }}</p>
            </article>
          </section>
          <section class="panel">
            <h3>使用与冲正台账</h3>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>凭证编号</th>
                    <th>数量</th>
                    <th>现场核查与证据</th>
                    <th>登记人 / 时间</th>
                    <th>冲正记录</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="u in detail.usages" :key="u.id">
                    <td>{{ u.reference }}</td>
                    <td>{{ u.quantity }} {{ record.unit }}</td>
                    <td class="evidence-cell">{{ u.evidence }}</td>
                    <td>
                      {{ u.actorName }}<small>{{ date(u.createdAt) }}</small>
                    </td>
                    <td v-if="u.voided">
                      已冲正 · {{ u.voidActorName
                      }}<small>{{ u.voidReason }}</small>
                    </td>
                    <td v-else>
                      <button
                        v-if="
                          [
                            'ACTIVE',
                            'EXPIRED',
                            'EXHAUSTED',
                            'SCHEDULED',
                          ].includes(record.status) &&
                          record.approverId === me.id &&
                          u.actorId !== me.id &&
                          can('permit.approve') &&
                          me.scope !== 'SELF'
                        "
                        @click="command('void', u)"
                      >
                        冲正</button
                      ><span v-else>有效登记</span>
                    </td>
                  </tr>
                  <tr v-if="!detail.usages.length">
                    <td colspan="5" class="empty">尚无使用登记</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
          <section class="panel">
            <h3>操作历史</h3>
            <ol class="timeline">
              <li v-for="e in detail.events" :key="e.id">
                <strong>{{
                  commands[e.action.toLowerCase()] || e.action
                }}</strong
                ><span>{{ e.actor }} · {{ date(e.createdAt) }}</span>
                <p>{{ e.note }}</p>
              </li>
            </ol>
          </section></template
        >
        <template v-else-if="page === 'workbench'"
          ><div class="work-grid">
            <section v-for="(items, key) in work" :key="key" class="panel">
              <h3>
                {{
                  {
                    review: "我的技术复核",
                    approval: "我的批准",
                    use: "我的可用授权",
                    draft: "我的草稿",
                  }[key]
                }}
                {{ items.length }}
              </h3>
              <button
                v-for="p in items"
                :key="p.id"
                class="task-row"
                :disabled="loading || saving"
                @click="show(p.id)"
              >
                <span
                  >{{ p.code }} · {{ p.title
                  }}<small
                    >{{ p.validUntil }} · 余{{ p.remaining }}{{ p.unit }}</small
                  ></span
                ><span class="badge" :class="p.status">{{
                  states[p.status]
                }}</span>
              </button>
              <p v-if="!items.length" class="empty">暂无待办</p>
            </section>
          </div></template
        >
        <template v-else-if="page === 'dashboard'"
          ><div class="metrics">
            <article class="metric">
              <span>授权记录</span><strong>{{ stats.total || 0 }}</strong>
            </article>
            <article class="metric">
              <span>可使用</span><strong>{{ stats.counts.ACTIVE || 0 }}</strong>
            </article>
            <article class="metric">
              <span>临近到期</span><strong>{{ stats.dueSoon || 0 }}</strong>
            </article>
            <article class="metric">
              <span>已到期</span
              ><strong>{{ stats.counts.EXPIRED || 0 }}</strong>
            </article>
          </div>
          <section class="panel">
            <h3>授权状态</h3>
            <div class="state-counts">
              <p v-for="(n, s) in stats.counts" :key="s">
                <span class="badge" :class="s">{{ states[s] }}</span
                ><strong>{{ n }}</strong>
              </p>
              <p v-if="!stats.total" class="empty">尚无授权记录</p>
            </div>
          </section>
          <section class="panel">
            <h3>按单位核对累计数量</h3>
            <table>
              <thead>
                <tr>
                  <th>单位</th>
                  <th>批准上限合计</th>
                  <th>净登记用量</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(q, unit) in stats.quantities" :key="unit">
                  <td>{{ unit }}</td>
                  <td>{{ q.limit }}</td>
                  <td>{{ q.used }}</td>
                </tr>
              </tbody>
            </table>
          </section></template
        >
        <template v-else
          ><section class="panel">
            <form
              class="filters"
              @submit.prevent="
                offset = 0;
                load();
              "
            >
              <input
                v-model="search"
                placeholder="搜索名称或编号"
                aria-label="搜索名称或编号"
              /><select
                v-if="page === 'permits'"
                v-model="status"
                aria-label="状态"
              >
                <option value="">全部状态</option>
                <option v-for="(label, key) in states" :key="key" :value="key">
                  {{ label }}
                </option></select
              ><select
                v-if="page === 'permits'"
                v-model="sort"
                aria-label="排序"
              >
                <option value="newest">最新在前</option>
                <option value="oldest">最早在前</option></select
              ><button type="submit">查询</button
              ><button
                v-if="
                  (page === 'permits' &&
                    can('permit.write') &&
                    me.scope !== 'SELF') ||
                  (['users', 'roles', 'departments', 'dictionaries'].includes(
                    page,
                  ) &&
                    can('admin'))
                "
                type="button"
                class="primary"
                @click="edit(page)"
              >
                <Plus :size="16" />新建
              </button>
            </form>
            <div class="table-wrap">
              <table v-if="page === 'permits'">
                <thead>
                  <tr>
                    <th>编号 / 标题</th>
                    <th>对象 / 修订</th>
                    <th>限定范围</th>
                    <th>状态</th>
                    <th>已用 / 上限</th>
                    <th>截止日</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in visibleRows" :key="p.id">
                    <td>
                      {{ p.code }}<small>{{ p.title }}</small>
                    </td>
                    <td>{{ p.itemCode }} / {{ p.itemRevision }}</td>
                    <td>{{ p.scopeTag }}</td>
                    <td>
                      <span class="badge" :class="p.status">{{
                        states[p.status]
                      }}</span>
                    </td>
                    <td>
                      {{ p.usedQuantity }} / {{ p.quantityLimit }} {{ p.unit }}
                    </td>
                    <td>{{ p.validUntil }}</td>
                    <td>
                      <button :disabled="loading || saving" @click="show(p.id)">
                        查看
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <table v-else-if="page === 'audit'">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>账号</th>
                    <th>操作</th>
                    <th>记录</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in visibleRows" :key="r.id">
                    <td>{{ date(r.createdAt) }}</td>
                    <td>{{ r.actor }}</td>
                    <td>{{ r.action }}</td>
                    <td>{{ r.objectId }}</td>
                  </tr>
                </tbody>
              </table>
              <table v-else>
                <thead>
                  <tr>
                    <th>代码 / 账号</th>
                    <th>名称</th>
                    <th>配置</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in visibleRows" :key="r.id">
                    <td>{{ r.code || r.username || r.id }}</td>
                    <td>{{ r.displayName || r.name || r.nameEn }}</td>
                    <td>
                      {{
                        r.scope ||
                        r.value ||
                        (r.enabled === false ? "停用" : "")
                      }}<small v-if="r.permissions">{{
                        r.permissions.join(" · ")
                      }}</small>
                    </td>
                    <td>
                      <button @click="edit(page, r)">编辑</button
                      ><button
                        v-if="
                          [
                            'users',
                            'roles',
                            'departments',
                            'dictionaries',
                          ].includes(page)
                        "
                        @click="adminDelete(r)"
                      >
                        删除
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!visibleRows.length" class="empty">暂无记录</p>
            </div>
            <div class="pagination">
              <span>共 {{ pageTotal }} 条 · 第 {{ offset + 1 }} 页</span
              ><button
                aria-label="上一页"
                :disabled="offset === 0 || loading"
                @click="move(-1)"
              >
                <ChevronLeft :size="16" /></button
              ><button
                aria-label="下一页"
                :disabled="(offset + 1) * 20 >= pageTotal || loading"
                @click="move(1)"
              >
                <ChevronRight :size="16" />
              </button>
            </div></section
        ></template>
      </main>
    </div>
  </div>
  <div v-if="dialog" class="overlay" @click.self="!saving && (dialog = null)">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="dialog.title"
    >
      <header>
        <h2>{{ dialog.title }}</h2>
        <button aria-label="关闭" @click="dialog = null" :disabled="saving">
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="submitDialog">
        <p
          v-if="dialog.action === 'delete' || dialog.action === 'admin-delete'"
          class="subtle"
        >
          确认删除这条记录？已引用或留有提交历史的记录将被拒绝。
        </p>
        <div class="form-grid">
          <component
            :is="field.type === 'permissions' ? 'div' : 'label'"
            v-for="field in dialog.action === 'admin-delete'
              ? []
              : dialogFields"
            :key="field.key"
            :class="{
              full: field.type === 'textarea' || field.type === 'permissions',
            }"
            ><span>{{ labels[field.key] || field.key }}</span
            ><select
              v-if="field.type === 'select'"
              v-model="dialog.values[field.key]"
              :disabled="field.readonly"
              required
            >
              <option :value="undefined">请选择</option>
              <option
                v-for="o in field.options"
                :key="o.value"
                :value="o.value"
              >
                {{ o.label }}
              </option></select
            ><textarea
              v-else-if="field.type === 'textarea'"
              v-model="dialog.values[field.key]"
              :maxlength="field.max || 2000"
              :minlength="field.minLength || 1"
              required
              rows="3"
            ></textarea>
            <div v-else-if="field.type === 'permissions'" class="checks">
              <label v-for="o in field.options" :key="o.value"
                ><input
                  v-model="dialog.values.permissions"
                  type="checkbox"
                  :value="o.value"
                />{{ o.label }}</label
              >
            </div>
            <input
              v-else-if="field.type === 'checkbox'"
              v-model="dialog.values[field.key]"
              type="checkbox" /><input
              v-else
              v-model="dialog.values[field.key]"
              :type="field.type"
              :min="field.min"
              :max="field.max"
              :maxlength="field.max || 200"
              :readonly="field.readonly"
              :required="field.required !== false"
              :autocomplete="field.type === 'password' ? 'new-password' : 'off'"
              :step="field.type === 'number' ? 1 : undefined"
          /></component>
        </div>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <footer>
          <button type="button" @click="dialog = null" :disabled="saving">
            取消</button
          ><button class="primary" :disabled="saving || loading">
            {{ saving ? "正在保存…" : "确认保存" }}
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>
