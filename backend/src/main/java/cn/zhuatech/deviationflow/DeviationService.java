// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.deviationflow;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 临时授权独立复核、限期限量、使用和冲正的事务边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class DeviationService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final tools.jackson.databind.json.JsonMapper json =
      tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();

  public DeviationService(Store d, AccessService a, Clock c) {
    db = d;
    access = a;
    clock = c;
  }

  /** 草稿边界输入，不接收累计用量与审批结论。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String code,
      String title,
      String category,
      Long departmentId,
      Long reviewerId,
      Long approverId,
      Long executorId,
      String itemCode,
      String itemRevision,
      String scopeTag,
      String baseline,
      String deviation,
      String controls,
      String externalAuthorization,
      Long quantityLimit,
      String unit,
      LocalDate validFrom,
      LocalDate validUntil,
      Long version) {}

  /** 幂等状态命令，不允许客户端回改服务器日期。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(Long version, String note, String requestKey) {}

  /** 指定使用凭据，按冻结的对象版本和范围严格匹配。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record UsageInput(
      Long version,
      String requestKey,
      String reference,
      Long quantity,
      String evidence,
      String itemCode,
      String itemRevision,
      String scopeTag) {}

  /** 数据范围内选择目录，SELF不列出同事。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("permit.read");
    return Map.of(
        "accounts",
        db.all(Account.class).stream()
            .filter(a -> self() ? a.id.equals(access.current().id) : access.visible(a.departmentId))
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "name",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "enabled",
                        a.enabled,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList(),
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "dictionaries",
        db.all(DictionaryEntry.class),
        "settings",
        db.all(SystemSetting.class),
        "today",
        today());
  }

  /** 数据库按本人/部门限定，再搜索、筛选和分页，固定允许排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String search, String status, int page, int size, String sort) {
    access.require("permit.read");
    if (page < 0
        || page > 100000
        || size < 1
        || size > 100
        || search.length() > 120
        || !Set.of("newest", "oldest").contains(sort)) throw new Problem(400, "INVALID_INPUT");
    String where = scope();
    if (!search.isBlank())
      where +=
          " and (lower(p.code) like :search or lower(p.title) like :search or lower(p.itemCode) like :search)";
    if (!status.isBlank()) where += " and " + statusWhere(status);
    var q =
        db.jpql(
            DeviationPermit.class,
            "from DeviationPermit p where "
                + where
                + " order by p.id "
                + (sort.equals("oldest") ? "asc" : "desc"));
    var c = db.jpql(Long.class, "select count(p.id) from DeviationPermit p where " + where);
    for (var x : List.of(q, c)) {
      bindScope(x);
      if (!search.isBlank()) x.setParameter("search", "%" + search.toLowerCase(Locale.ROOT) + "%");
      if (Set.of("ACTIVE", "SCHEDULED", "EXPIRED", "EXHAUSTED").contains(status))
        x.setParameter("today", today());
    }
    return Map.of(
        "items",
        q.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
            .map(this::view)
            .toList(),
        "total",
        c.getSingleResult(),
        "page",
        page,
        "size",
        size);
  }

  /** 详情包括原授权、相关责任人、原使用和冲正、不可变操作事件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    access.require("permit.read");
    var p = permit(id);
    return Map.of(
        "record",
        view(p),
        "usages",
        usages(id).stream().map(this::usageView).toList(),
        "events",
        db
            .query(
                FlowEvent.class,
                "from FlowEvent where kind='permits' and objectId=?1 order by id",
                id)
            .stream()
            .map(
                e ->
                    Map.of(
                        "id",
                        e.id,
                        "actor",
                        name(e.actorId),
                        "action",
                        e.action,
                        "note",
                        e.note,
                        "createdAt",
                        e.createdAt))
            .toList());
  }

  /** 创建或仅由作者编辑草稿，复核人/批准人/作者三者必须独立。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(Long id, Input v) {
    gate("permit.write");
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    var p = id == null ? new DeviationPermit() : permit(id);
    if (id != null) {
      owner(p);
      state(p, "DRAFT");
      DeviationPolicy.version(p.version, v.version);
    }
    Long dept = db.get(Department.class, v.departmentId).id;
    access.department(dept);
    if (id != null && !p.departmentId.equals(dept)) throw new Problem(409, "IMMUTABLE_IDENTITY");
    var actor = access.current().id;
    if (actor.equals(v.reviewerId)
        || actor.equals(v.approverId)
        || (v.reviewerId != null && v.reviewerId.equals(v.approverId)))
      throw new Problem(403, "INDEPENDENCE_REQUIRED");
    var reviewer = eligible(v.reviewerId, dept, "permit.review");
    var approver = eligible(v.approverId, dept, "permit.approve");
    var executor = eligible(v.executorId, dept, "permit.use");
    if (reviewer.id.equals(actor) || approver.id.equals(actor) || reviewer.id.equals(approver.id))
      throw new Problem(403, "INDEPENDENCE_REQUIRED");
    String code = AdminService.text(v.code, 60).toUpperCase(Locale.ROOT);
    if (!code.matches("[A-Za-z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_CODE");
    if (id != null && !p.code.equals(code)) throw new Problem(409, "IMMUTABLE_IDENTITY");
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type='permit' and code=?1",
            v.category)
        .isEmpty()) throw new Problem(400, "INVALID_CATEGORY");
    if (v.quantityLimit == null
        || v.quantityLimit < 1
        || v.quantityLimit > 1000000000L
        || v.validFrom == null
        || v.validUntil == null
        || v.validFrom.getYear() > 9999
        || v.validUntil.getYear() > 9999
        || v.validUntil.isBefore(v.validFrom)
        || v.validUntil.isBefore(today())
        || java.time.temporal.ChronoUnit.DAYS.between(v.validFrom, v.validUntil) >= 365
        || !Set.of("件", "批", "套").contains(v.unit == null ? "" : v.unit))
      throw new Problem(400, "INVALID_BOUNDARY");
    p.code = code;
    p.title = AdminService.text(v.title, 160);
    p.category = v.category;
    p.departmentId = dept;
    p.authorId = actor;
    p.reviewerId = reviewer.id;
    p.approverId = approver.id;
    p.executorId = executor.id;
    p.itemCode = AdminService.text(v.itemCode, 100);
    p.itemRevision = AdminService.text(v.itemRevision, 60);
    p.scopeTag = AdminService.text(v.scopeTag, 120);
    p.baseline = AdminService.text(v.baseline, 2000);
    p.deviation = AdminService.text(v.deviation, 2000);
    p.controls = AdminService.text(v.controls, 2000);
    p.externalAuthorization = AdminService.text(v.externalAuthorization, 1000);
    p.quantityLimit = v.quantityLimit;
    p.unit = v.unit;
    p.validFrom = v.validFrom;
    p.validUntil = v.validUntil;
    p.reviewNote = "";
    p.approvalNote = "";
    p.editRevision++;
    if (id == null) {
      p.createdAt = clock.instant();
      db.save(p);
    }
    event(p, id == null ? "CREATE" : "EDIT", "");
    db.flush();
    return view(p);
  }

  /** 只删除作者未提交草稿，不删除已审批和使用历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id, Long version) {
    gate("permit.write");
    var p = permit(id);
    owner(p);
    state(p, "DRAFT");
    DeviationPolicy.version(p.version, version);
    if (!db.query(
            FlowEvent.class,
            "from FlowEvent where objectId=?1 and kind='permits' and action='SUBMIT'",
            id)
        .isEmpty()) throw new Problem(409, "HISTORY_PROTECTED");
    for (var e :
        db.query(FlowEvent.class, "from FlowEvent where objectId=?1 and kind='permits'", id))
      db.delete(e);
    access.audit("DELETE_DRAFT", id, p.departmentId);
    db.delete(p);
  }

  /** 提交、指定技术复核、独立批准、退回、撤销和关闭，批准后边界不能修改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object command(Long id, String action, Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    String perm =
        switch (action) {
          case "submit" -> "permit.write";
          case "review", "return-review" -> "permit.review";
          case "approve", "return-approval", "revoke", "close" -> "permit.approve";
          default -> throw new Problem(404, "NOT_FOUND");
        };
    gate(perm);
    var p = permit(id);
    String payload = "command:" + id + ":" + action + ":" + json.writeValueAsString(v);
    var replay = replay(v.requestKey, payload);
    if (replay != null) return view(p);
    DeviationPolicy.version(p.version, v.version);
    String note = AdminService.text(v.note, 2000);
    switch (action) {
      case "submit" -> {
        owner(p);
        state(p, "DRAFT");
        eligible(p.reviewerId, p.departmentId, "permit.review");
        eligible(p.approverId, p.departmentId, "permit.approve");
        eligible(p.executorId, p.departmentId, "permit.use");
        if (p.validUntil.isBefore(today())) throw new Problem(409, "PERMIT_EXPIRED");
        p.status = "REVIEW";
      }
      case "review", "return-review" -> {
        assigned(p.reviewerId);
        state(p, "REVIEW");
        if (p.authorId.equals(access.current().id)) throw new Problem(403, "INDEPENDENCE_REQUIRED");
        p.status = action.equals("review") ? "APPROVAL" : "DRAFT";
        p.reviewNote = action.equals("review") ? note : "";
        p.approvalNote = "";
      }
      case "approve", "return-approval" -> {
        assigned(p.approverId);
        state(p, "APPROVAL");
        if (Set.of(p.authorId, p.reviewerId).contains(access.current().id))
          throw new Problem(403, "INDEPENDENCE_REQUIRED");
        if (action.equals("approve")) {
          if (p.validUntil.isBefore(today())) throw new Problem(409, "PERMIT_EXPIRED");
          eligible(p.executorId, p.departmentId, "permit.use");
          p.status = "ACTIVE";
          p.approvalNote = note;
          p.approvedAt = clock.instant();
        } else {
          p.status = "DRAFT";
          p.reviewNote = "";
          p.approvalNote = "";
        }
      }
      case "revoke", "close" -> {
        assigned(p.approverId);
        state(p, "ACTIVE");
        p.status = action.equals("revoke") ? "REVOKED" : "CLOSED";
      }
    }
    event(p, action.toUpperCase(Locale.ROOT), note);
    stamp(v.requestKey, payload, p.id);
    db.flush();
    return view(p);
  }

  /** 仅指定执行人登记；日期、冻结对象/修订/范围、可用数量均由服务端核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object use(Long id, UsageInput v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    gate("permit.use");
    var p = permit(id);
    assigned(p.executorId);
    if (!access.role().scope.equals("ALL") && !access.current().departmentId.equals(p.departmentId))
      throw new Problem(403, "OUT_OF_SCOPE");
    String payload = "use:" + id + ":" + json.writeValueAsString(v);
    Long old = replay(v.requestKey, payload);
    if (old != null) return usageView(db.get(PermitUsage.class, old));
    DeviationPolicy.version(p.version, v.version);
    if (!DeviationPolicy.status(p, clock.instant()).equals("ACTIVE"))
      throw new Problem(409, "PERMIT_NOT_ACTIVE");
    if (!Objects.equals(p.itemCode, v.itemCode)
        || !Objects.equals(p.itemRevision, v.itemRevision)
        || !Objects.equals(p.scopeTag, v.scopeTag)) throw new Problem(409, "BOUNDARY_MISMATCH");
    var u = new PermitUsage();
    u.permitId = p.id;
    u.actorId = access.current().id;
    u.reference = AdminService.text(v.reference, 100).toUpperCase(Locale.ROOT);
    u.evidence = AdminService.text(v.evidence, 2000);
    if (u.evidence.length() < 10) throw new Problem(400, "EVIDENCE_REQUIRED");
    p.usedQuantity = DeviationPolicy.consume(p.usedQuantity, p.quantityLimit, v.quantity);
    u.quantity = v.quantity;
    u.createdAt = clock.instant();
    db.save(u);
    event(p, "USE", u.reference);
    stamp(v.requestKey, payload, u.id);
    db.flush();
    return usageView(u);
  }

  /** 指定批准人独立冲正误登记，保留原用量、操作者、理由；终结记录不回开。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object voidUsage(Long permitId, Long usageId, Command v) {
    if (v == null) throw new Problem(400, "INVALID_INPUT");
    gate("permit.approve");
    var p = permit(permitId);
    assigned(p.approverId);
    var u = db.get(PermitUsage.class, usageId);
    if (!u.permitId.equals(p.id)) throw new Problem(404, "NOT_FOUND");
    String payload = "void:" + permitId + ":" + usageId + ":" + json.writeValueAsString(v);
    var old = replay(v.requestKey, payload);
    if (old != null) return usageView(u);
    DeviationPolicy.version(p.version, v.version);
    state(p, "ACTIVE");
    if (u.voided) throw new Problem(409, "ALREADY_VOIDED");
    if (u.actorId.equals(access.current().id)) throw new Problem(403, "INDEPENDENCE_REQUIRED");
    u.voidReason = AdminService.text(v.note, 2000);
    u.voided = true;
    u.voidActorId = access.current().id;
    u.voidedAt = clock.instant();
    p.usedQuantity -= u.quantity;
    event(p, "VOID_USAGE", u.reference + ": " + u.voidReason);
    stamp(v.requestKey, payload, u.id);
    db.flush();
    return usageView(u);
  }

  /** 本人相关待办及有限统计，不提供任意全库记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object workbench() {
    access.require("permit.read");
    var all = scoped();
    Long me = access.current().id;
    return Map.of(
        "review",
        all.stream()
            .filter(p -> p.reviewerId.equals(me) && p.status.equals("REVIEW"))
            .map(this::view)
            .toList(),
        "approval",
        all.stream()
            .filter(p -> p.approverId.equals(me) && p.status.equals("APPROVAL"))
            .map(this::view)
            .toList(),
        "use",
        all.stream()
            .filter(
                p ->
                    p.executorId.equals(me)
                        && DeviationPolicy.status(p, clock.instant()).equals("ACTIVE"))
            .map(this::view)
            .toList(),
        "draft",
        all.stream()
            .filter(p -> p.authorId.equals(me) && p.status.equals("DRAFT"))
            .map(this::view)
            .toList());
  }

  /** 只在授权范围统计数量与到期；整数单位分别分组，禁止混加件/批/套。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var rows = scoped();
    var counts = new TreeMap<String, Long>();
    var amounts = new TreeMap<String, Map<String, Long>>();
    int due =
        Integer.parseInt(
            db.query(SystemSetting.class, "from SystemSetting where code='dueSoonDays'")
                .getFirst()
                .value);
    long soon = 0;
    for (var p : rows) {
      String status = DeviationPolicy.status(p, clock.instant());
      counts.merge(status, 1L, Long::sum);
      if (status.equals("ACTIVE") && !p.validUntil.isAfter(today().plusDays(due))) soon++;
      var m = amounts.computeIfAbsent(p.unit, k -> new TreeMap<>());
      m.merge("limit", p.quantityLimit, Long::sum);
      m.merge("used", p.usedQuantity, Long::sum);
    }
    return Map.of("total", rows.size(), "counts", counts, "quantities", amounts, "dueSoon", soon);
  }

  /** 授权JSON导出，业务载荷不添加品牌广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object report(Long id) {
    access.require("export");
    return detail(id);
  }

  /** 最近1000条授权部门审计；SELF不能扩展看到同事。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    var q =
        db.jpql(
            AuditEvent.class,
            "from AuditEvent e "
                + (access.role().scope.equals("ALL") ? "" : "where e.departmentId=:dept ")
                + "order by e.id desc");
    if (!access.role().scope.equals("ALL")) q.setParameter("dept", access.current().departmentId);
    return q.setMaxResults(1000).getResultList().stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!self() || e.actor.equals(access.current().username)))
        .toList();
  }

  private String scope() {
    if (self())
      return "(p.authorId=:actor or p.reviewerId=:actor or p.approverId=:actor or p.executorId=:actor)";
    return access.role().scope.equals("ALL") ? "1=1" : "p.departmentId=:dept";
  }

  private void bindScope(jakarta.persistence.Query q) {
    if (self()) q.setParameter("actor", access.current().id);
    else if (!access.role().scope.equals("ALL"))
      q.setParameter("dept", access.current().departmentId);
  }

  private String statusWhere(String s) {
    return switch (s) {
      case "ACTIVE" ->
          "p.status='ACTIVE' and p.validFrom<=:today and p.validUntil>=:today and p.usedQuantity<p.quantityLimit";
      case "SCHEDULED" -> "p.status='ACTIVE' and p.validFrom>:today";
      case "EXPIRED" -> "p.status='ACTIVE' and p.validFrom<=:today and p.validUntil<:today";
      case "EXHAUSTED" ->
          "p.status='ACTIVE' and p.validFrom<=:today and p.validUntil>=:today and p.usedQuantity>=p.quantityLimit";
      case "DRAFT", "REVIEW", "APPROVAL", "REVOKED", "CLOSED" -> "p.status='" + s + "'";
      default -> throw new Problem(400, "INVALID_STATUS");
    };
  }

  private List<DeviationPermit> scoped() {
    var q =
        db.jpql(
            DeviationPermit.class,
            "from DeviationPermit p where " + scope() + " order by p.id desc");
    bindScope(q);
    var rows = q.setMaxResults(10001).getResultList();
    if (rows.size() > 10000) throw new Problem(409, "REPORT_LIMIT");
    return rows;
  }

  private boolean self() {
    return access.role().scope.equals("SELF");
  }

  private LocalDate today() {
    return DeviationPolicy.today(clock.instant());
  }

  private String name(Long id) {
    return db.get(Account.class, id).displayName;
  }

  private Map<String, Object> view(DeviationPermit p) {
    var m = new LinkedHashMap<String, Object>();
    for (var f : DeviationPermit.class.getFields())
      try {
        m.put(f.getName(), f.get(p));
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    m.remove("editRevision");
    m.put("status", DeviationPolicy.status(p, clock.instant()));
    m.put("authorName", name(p.authorId));
    m.put("reviewerName", name(p.reviewerId));
    m.put("approverName", name(p.approverId));
    m.put("executorName", name(p.executorId));
    m.put("remaining", p.quantityLimit - p.usedQuantity);
    return m;
  }

  private Map<String, Object> usageView(PermitUsage u) {
    var m = new LinkedHashMap<String, Object>();
    for (var f : PermitUsage.class.getFields())
      try {
        m.put(f.getName(), f.get(u));
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
    m.put("actorName", name(u.actorId));
    m.put("voidActorName", u.voidActorId == null ? "" : name(u.voidActorId));
    return m;
  }

  private List<PermitUsage> usages(Long id) {
    return db.query(PermitUsage.class, "from PermitUsage where permitId=?1 order by id", id);
  }

  private DeviationPermit permit(Long id) {
    if (id == null) throw new Problem(400, "INVALID_INPUT");
    var p = db.get(DeviationPermit.class, id);
    if (self()) {
      if (!List.of(p.authorId, p.reviewerId, p.approverId, p.executorId)
          .contains(access.current().id)) throw new Problem(403, "OUT_OF_SCOPE");
    } else access.department(p.departmentId);
    return p;
  }

  private Account eligible(Long id, Long dept, String perm) {
    if (id == null) throw new Problem(400, "INVALID_INPUT");
    var a = db.get(Account.class, id);
    var r = db.get(AccessRole.class, a.roleId);
    if (!a.enabled
        || !r.permissions.contains(perm)
        || (r.scope.equals("SELF") && !perm.equals("permit.use"))
        || (!r.scope.equals("ALL") && !a.departmentId.equals(dept)))
      throw new Problem(400, "INELIGIBLE_ACCOUNT");
    return a;
  }

  private void gate(String permission) {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.require(permission);
    if (self() && !permission.equals("permit.use")) throw new Problem(403, "FORBIDDEN");
  }

  private void owner(DeviationPermit p) {
    assigned(p.authorId);
  }

  private void assigned(Long id) {
    if (!access.current().id.equals(id)) throw new Problem(403, "NOT_ASSIGNED");
  }

  private void state(DeviationPermit p, String status) {
    if (!p.status.equals(status)) throw new Problem(409, "INVALID_STATE");
  }

  private void event(DeviationPermit p, String action, String note) {
    var e = new FlowEvent();
    e.kind = "permits";
    e.objectId = p.id;
    e.departmentId = p.departmentId;
    e.actorId = access.current().id;
    e.action = action;
    e.note = note;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, p.id, p.departmentId);
  }

  private String fingerprint(String payload) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((access.current().id + ":" + payload).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Long replay(String key, String payload) {
    if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
      throw new Problem(400, "INVALID_REQUEST_KEY");
    var rows = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (rows.isEmpty()) return null;
    if (!rows.getFirst().fingerprint.equals(fingerprint(payload)))
      throw new Problem(409, "IDEMPOTENCY_CONFLICT");
    return rows.getFirst().resultId;
  }

  private void stamp(String key, String payload, Long id) {
    var c = new CommandRecord();
    c.requestKey = key;
    c.fingerprint = fingerprint(payload);
    c.resultId = id;
    db.save(c);
  }
}
