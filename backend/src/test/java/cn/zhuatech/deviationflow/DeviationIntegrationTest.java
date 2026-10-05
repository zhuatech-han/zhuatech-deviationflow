// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.deviationflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 验证独立授权、冻结边界、限额、留痕与真实接口隔离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DeviationIntegrationTest.TimeConfig.class)
class DeviationIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("deviationflow.admin-password", () -> password);
  }

  static class MutableClock extends Clock {
    volatile Instant value = Instant.parse("2026-10-05T02:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return value;
    }
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired MutableClock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, author, reviewer, approver, executor, other, outsider;
  long dept, otherDept, authorId, reviewerId, approverId, executorId, permit, executorRole;
  String suffix;

  @BeforeEach
  void setup() throws Exception {
    clock.value = Instant.parse("2026-10-05T02:00:00Z");
    admin = login("admin", password);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    dept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST 偏差-" + suffix))
            .path("id")
            .asLong();
    otherDept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "TEST 隔离-" + suffix))
            .path("id")
            .asLong();
    var roles = new HashMap<String, Long>();
    for (var x : ok(admin, "GET", "/admin/roles", null))
      roles.put(x.path("name").asString(), x.path("id").asLong());
    authorId = user("author", roles.get("申请人员"), dept);
    reviewerId = user("review", roles.get("技术复核员"), dept);
    approverId = user("approve", roles.get("批准人员"), dept);
    executorRole = roles.get("执行人员");
    executorId = user("execute", executorRole, dept);
    user("other", executorRole, dept);
    user("outside", roles.get("申请人员"), otherDept);
    author = login("author-" + suffix, password);
    reviewer = login("review-" + suffix, password);
    approver = login("approve-" + suffix, password);
    executor = login("execute-" + suffix, password);
    other = login("other-" + suffix, password);
    outsider = login("outside-" + suffix, password);
    permit = ok(author, "POST", "/permits", input()).path("id").asLong();
  }

  long user(String n, long role, long d) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/users",
            Map.of(
                "username",
                n + "-" + suffix,
                "displayName",
                "TEST " + n,
                "password",
                password,
                "roleId",
                role,
                "departmentId",
                d,
                "enabled",
                true))
        .path("id")
        .asLong();
  }

  Map<String, Object> input() {
    var m = new HashMap<String, Object>();
    m.put("code", "TEST-" + suffix);
    m.put("title", "TEST 夹具替代授权");
    m.put("category", "FIXTURE");
    m.put("departmentId", dept);
    m.put("reviewerId", reviewerId);
    m.put("approverId", approverId);
    m.put("executorId", executorId);
    m.put("itemCode", "TEST-PART");
    m.put("itemRevision", "A");
    m.put("scopeTag", "TEST-WO-01");
    m.put("baseline", "TEST 原夹具定位");
    m.put("deviation", "TEST 临时替代夹具");
    m.put("controls", "TEST 逐件检查尺寸并登记");
    m.put("externalAuthorization", "TEST 不涉及外部审批，仅用于验证");
    m.put("quantityLimit", 10);
    m.put("unit", "件");
    m.put("validFrom", "2026-10-05");
    m.put("validUntil", "2026-10-06");
    return m;
  }

  JsonNode detail() throws Exception {
    return ok(author, "GET", "/permits/" + permit, null);
  }

  JsonNode p() throws Exception {
    return detail().path("record");
  }

  Map<String, Object> command(JsonNode p) {
    var m = new HashMap<String, Object>();
    m.put("version", p.path("version").asLong());
    m.put("requestKey", UUID.randomUUID().toString());
    m.put("note", "TEST 独立核对依据与结论");
    return m;
  }

  JsonNode act(MockHttpSession s, String a) throws Exception {
    return ok(s, "POST", "/permits/" + permit + "/commands/" + a, command(p()));
  }

  void approve() throws Exception {
    act(author, "submit");
    act(reviewer, "review");
    act(approver, "approve");
  }

  Map<String, Object> usage(long q) throws Exception {
    var m = command(p());
    m.put("reference", "TEST-USE-" + UUID.randomUUID());
    m.put("quantity", q);
    m.put("evidence", "TEST 逐件检查记录已核对一致");
    m.put("itemCode", "TEST-PART");
    m.put("itemRevision", "A");
    m.put("scopeTag", "TEST-WO-01");
    return m;
  }

  JsonNode use(long q) throws Exception {
    return ok(executor, "POST", "/permits/" + permit + "/usages", usage(q));
  }

  MockHttpSession login(String n, String pass) throws Exception {
    var res =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", n, "password", pass))))
            .andReturn();
    assertEquals(200, res.getResponse().getStatus());
    return (MockHttpSession) res.getRequest().getSession(false);
  }

  JsonNode request(MockHttpSession s, String method, String path, Object body, int expected)
      throws Exception {
    var b =
        switch (method) {
          case "GET" -> get("/api" + path);
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          default -> delete("/api" + path);
        };
    if (s != null) b.session(s);
    if (!method.equals("GET")) b.with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    var res = mvc.perform(b).andReturn().getResponse();
    assertEquals(expected, res.getStatus(), path + " " + res.getContentAsString());
    return json.readTree(res.getContentAsString());
  }

  JsonNode ok(MockHttpSession s, String m, String p, Object b) throws Exception {
    return request(s, m, p, b, 200);
  }

  @Test
  void completeFlowKeepsOriginalUsageAndIndependentVoid() throws Exception {
    approve();
    var u = use(6);
    assertEquals(6, p().path("usedQuantity").asLong());
    ok(
        approver,
        "POST",
        "/permits/" + permit + "/usages/" + u.path("id").asLong() + "/void",
        command(p()));
    assertEquals(0, p().path("usedQuantity").asLong());
    assertEquals(6, detail().path("usages").get(0).path("quantity").asLong());
    assertTrue(detail().path("usages").get(0).path("voided").asBoolean());
    use(10);
    assertEquals("EXHAUSTED", p().path("status").asString());
    act(approver, "close");
    assertEquals("CLOSED", p().path("status").asString());
    assertEquals(8, detail().path("events").size());
  }

  @Test
  void independentAuthorReviewerApproverAndAssignedActors() throws Exception {
    var m = input();
    m.put("reviewerId", authorId);
    request(author, "POST", "/permits", m, 403);
    act(author, "submit");
    request(admin, "POST", "/permits/" + permit + "/commands/review", command(p()), 403);
    act(reviewer, "review");
    request(reviewer, "POST", "/permits/" + permit + "/commands/approve", command(p()), 403);
    act(approver, "approve");
    request(admin, "POST", "/permits/" + permit + "/usages", usage(1), 403);
  }

  @Test
  void approvalFreezesBoundaryAndReturnPreservesHistory() throws Exception {
    act(author, "submit");
    act(reviewer, "review");
    act(approver, "return-approval");
    assertEquals("", p().path("reviewNote").asString());
    assertEquals("DRAFT", p().path("status").asString());
    request(
        author,
        "DELETE",
        "/permits/" + permit + "?version=" + p().path("version").asLong(),
        null,
        409);
    approve();
    var m = input();
    m.put("version", p().path("version").asLong());
    m.put("quantityLimit", 100);
    request(author, "PUT", "/permits/" + permit, m, 409);
  }

  @Test
  void draftEditAdvancesVersionAndIdentityCannotChange() throws Exception {
    var before = p();
    var m = input();
    m.put("version", before.path("version").asLong());
    ok(author, "PUT", "/permits/" + permit, m);
    assertTrue(p().path("version").asLong() > before.path("version").asLong());
    request(author, "PUT", "/permits/" + permit, m, 409);
    m.put("version", p().path("version").asLong());
    m.put("code", "OTHER-CODE");
    request(author, "PUT", "/permits/" + permit, m, 409);
  }

  @Test
  void departmentSelfAndExportIsolation() throws Exception {
    request(outsider, "GET", "/permits/" + permit, null, 403);
    request(other, "GET", "/permits/" + permit, null, 403);
    request(other, "GET", "/permits/" + permit + "/report.json", null, 403);
    assertEquals(0, ok(other, "GET", "/permits", null).path("total").asInt());
    assertEquals(1, ok(executor, "GET", "/options", null).path("accounts").size());
    approve();
    var export = ok(executor, "GET", "/permits/" + permit + "/report.json", null).toString();
    assertFalse(export.contains("zhuatech"));
    assertFalse(export.contains("password"));
    request(executor, "GET", "/admin/users", null, 403);
    request(author, "GET", "/permits?page=-1", null, 400);
  }

  @Test
  void quantityAndFrozenObjectValidation() throws Exception {
    approve();
    for (String field : List.of("itemCode", "itemRevision", "scopeTag")) {
      var m = usage(1);
      m.put(field, "WRONG");
      request(executor, "POST", "/permits/" + permit + "/usages", m, 409);
    }
    request(executor, "POST", "/permits/" + permit + "/usages", usage(0), 400);
    request(executor, "POST", "/permits/" + permit + "/usages", usage(11), 409);
    use(7);
    request(executor, "POST", "/permits/" + permit + "/usages", usage(4), 409);
    assertEquals(7, p().path("usedQuantity").asInt());
  }

  @Test
  void idempotencyDoesNotSpendTwiceAndConflictsOnChangedPayload() throws Exception {
    approve();
    var m = usage(3);
    var a = ok(executor, "POST", "/permits/" + permit + "/usages", m);
    assertEquals(
        a.path("id").asLong(),
        ok(executor, "POST", "/permits/" + permit + "/usages", m).path("id").asLong());
    m.put("quantity", 4);
    request(executor, "POST", "/permits/" + permit + "/usages", m, 409);
    assertEquals(3, p().path("usedQuantity").asInt());
    assertEquals(1, detail().path("usages").size());
  }

  @Test
  void duplicateReferenceRollsBackQuantityAndEvent() throws Exception {
    approve();
    var m = usage(2);
    m.put("reference", "lower-ref");
    ok(executor, "POST", "/permits/" + permit + "/usages", m);
    var duplicate = usage(2);
    duplicate.put("reference", "LOWER-REF");
    request(executor, "POST", "/permits/" + permit + "/usages", duplicate, 409);
    assertEquals(2, p().path("usedQuantity").asLong());
    assertEquals(1, detail().path("usages").size());
  }

  @Test
  void futureDateExpiryAnd365DayInclusiveBoundary() throws Exception {
    var m = input();
    m.put("version", p().path("version").asLong());
    m.put("validFrom", "2026-10-06");
    m.put("validUntil", "2027-10-06");
    request(author, "PUT", "/permits/" + permit, m, 400);
    m.put("validUntil", "2027-10-05");
    ok(author, "PUT", "/permits/" + permit, m);
    approve();
    assertEquals("SCHEDULED", p().path("status").asString());
    request(executor, "POST", "/permits/" + permit + "/usages", usage(1), 409);
    clock.value = Instant.parse("2027-10-05T15:59:59Z");
    use(1);
    clock.value = Instant.parse("2027-10-05T16:00:00Z");
    assertEquals("EXPIRED", p().path("status").asString());
    request(executor, "POST", "/permits/" + permit + "/usages", usage(1), 409);
    assertEquals(1, ok(author, "GET", "/permits?status=EXPIRED", null).path("total").asInt());
  }

  @Test
  void terminalRevocationCannotReopenOrVoid() throws Exception {
    approve();
    var u = use(1);
    act(approver, "revoke");
    request(executor, "POST", "/permits/" + permit + "/usages", usage(1), 409);
    request(
        approver,
        "POST",
        "/permits/" + permit + "/usages/" + u.path("id").asLong() + "/void",
        command(p()),
        409);
    assertEquals(1, p().path("usedQuantity").asInt());
  }

  @Test
  void transferAndDisableTakeEffectAfterApproval() throws Exception {
    approve();
    ok(
        admin,
        "PUT",
        "/admin/users/" + executorId,
        Map.of(
            "username",
            "execute-" + suffix,
            "displayName",
            "TEST execute",
            "roleId",
            executorRole,
            "departmentId",
            otherDept,
            "enabled",
            true));
    request(executor, "POST", "/permits/" + permit + "/usages", usage(1), 403);
    ok(
        admin,
        "PUT",
        "/admin/users/" + executorId,
        Map.of(
            "username",
            "execute-" + suffix,
            "displayName",
            "TEST execute",
            "roleId",
            executorRole,
            "departmentId",
            dept,
            "enabled",
            false));
    request(executor, "GET", "/permits", null, 401);
  }

  @Test
  void concurrentRequestsNeverExceedLimit() throws Exception {
    approve();
    var a = usage(7);
    var b = usage(7);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var start = new CountDownLatch(1);
      var jobs = new ArrayList<Future<Integer>>();
      for (var body : List.of(a, b)) {
        jobs.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          post("/api/permits/" + permit + "/usages")
                              .session(executor)
                              .with(csrf())
                              .contentType("application/json")
                              .content(json.writeValueAsString(body)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      start.countDown();
      var statuses = new ArrayList<Integer>();
      for (var f : jobs) statuses.add(f.get(15, TimeUnit.SECONDS));
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
      assertEquals(7, p().path("usedQuantity").asInt());
      assertEquals(1, detail().path("usages").size());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void selfScopedReviewerCannotBeAssigned() throws Exception {
    var role =
        ok(
                admin,
                "POST",
                "/admin/roles",
                Map.of(
                    "name",
                    "TEST 自范围复核 " + suffix,
                    "scope",
                    "SELF",
                    "permissions",
                    Set.of("permit.read", "permit.review")))
            .path("id")
            .asLong();
    var id = user("self-review", role, dept);
    var m = input();
    m.put("reviewerId", id);
    request(author, "POST", "/permits", m, 400);
    m = input();
    m.put("validFrom", "+999999999-01-01");
    m.put("validUntil", "+999999999-12-31");
    request(author, "POST", "/permits", m, 400);
  }

  @Test
  void csrfAndLastAdminProtection() throws Exception {
    assertEquals(
        403,
        mvc.perform(
                post("/api/permits")
                    .session(author)
                    .contentType("application/json")
                    .content(json.writeValueAsString(input())))
            .andReturn()
            .getResponse()
            .getStatus());
    var admins = ok(admin, "GET", "/admin/users", null);
    for (var u : admins)
      if (u.path("username").asString().equals("admin"))
        request(admin, "DELETE", "/admin/users/" + u.path("id").asLong(), null, 409);
    request(author, "GET", "/audit", null, 200);
  }
}
