// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.deviationflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库仅创建管理目录、岗位及管理员，不生成批准或使用事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store d, BCryptPasswordEncoder e, @Value("${deviationflow.admin-password}") String p) {
    db = d;
    encoder = e;
    password = p;
  }

  /** 已有库重启不覆盖账号和业务记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var dep = new Department();
    dep.name = "总部";
    db.save(dep);
    var names =
        Map.of(
            "permit.read",
            "查看授权资料",
            "permit.write",
            "申请与编辑",
            "permit.review",
            "技术复核",
            "permit.approve",
            "独立批准与关闭",
            "permit.use",
            "指定使用登记",
            "dashboard",
            "授权统计",
            "export",
            "导出记录",
            "audit",
            "操作审计",
            "admin",
            "系统管理");
    new TreeMap<>(names)
        .forEach(
            (k, v) -> {
              var p = new Permission();
              p.code = k;
              p.name = v;
              db.save(p);
            });
    role("管理员", "ALL", names.keySet());
    role(
        "申请人员",
        "DEPARTMENT",
        Set.of("permit.read", "permit.write", "dashboard", "export", "audit"));
    role(
        "技术复核员",
        "DEPARTMENT",
        Set.of("permit.read", "permit.review", "dashboard", "export", "audit"));
    role(
        "批准人员",
        "DEPARTMENT",
        Set.of("permit.read", "permit.approve", "dashboard", "export", "audit"));
    role("执行人员", "SELF", Set.of("permit.read", "permit.use", "dashboard", "export"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.departmentId = dep.id;
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.passwordHash = encoder.encode(password);
    a.enabled = true;
    db.save(a);
    String[][] menu = {
      {"workbench", "授权工作台", "Workbench", "permit.read"},
      {"permits", "偏差授权", "Permits", "permit.read"},
      {"dashboard", "使用统计", "Statistics", "dashboard"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门管理", "Departments", "admin"},
      {"menus", "导航管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "偏差分类", "Categories", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menu.length; i++) {
      var m = new NavMenu();
      m.code = menu[i][0];
      m.name = menu[i][1];
      m.nameEn = menu[i][2];
      m.permissionCode = menu[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    Map.of("timezone", "Asia/Shanghai", "companyName", "知华工艺偏差协作", "dueSoonDays", "7")
        .forEach(
            (k, v) -> {
              var s = new SystemSetting();
              s.code = k;
              s.value = v;
              db.save(s);
            });
    for (var k :
        new String[][] {
          {"PROCESS", "工艺调整", "Process"}, {"FIXTURE", "工装替代", "Fixture"}, {"OTHER", "其他偏差", "Other"}
        }) {
      var e = new DictionaryEntry();
      e.type = "permit";
      e.code = k[0];
      e.name = k[1];
      e.nameEn = k[2];
      db.save(e);
    }
  }

  private void role(String n, String s, Set<String> p) {
    var r = new AccessRole();
    r.name = n;
    r.scope = s;
    r.permissions = new HashSet<>(p);
    db.save(r);
  }
}
