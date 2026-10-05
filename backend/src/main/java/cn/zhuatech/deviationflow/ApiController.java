// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.deviationflow;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 授权业务与管理接口，服务层执行实时权限、范围和状态校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final DeviationService service;
  final AdminService admin;

  public ApiController(DeviationService s, AdminService a) {
    service = s;
    admin = a;
  }

  /** 当前范围的业务选择目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 本人相关待办。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/workbench")
  public Object workbench() {
    return service.workbench();
  }

  /** 授权统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** 部门操作审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** 搜索、状态、分页及固定排序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/permits")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return service.list(search, status, page, size, sort);
  }

  /** 完整授权与使用详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/permits/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 按相同范围导出业务JSON。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/permits/{id}/report.json")
  public ResponseEntity<Object> report(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=permit-" + id + ".json")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(service.report(id));
  }

  /** 创建草稿，不接收审批结论。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/permits")
  public Object create(@RequestBody DeviationService.Input v) {
    return service.save(null, v);
  }

  /** 作者编辑尚未提交草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/permits/{id}")
  public Object save(@PathVariable Long id, @RequestBody DeviationService.Input v) {
    return service.save(id, v);
  }

  /** 仅删除未提交历史的本人草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/permits/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version) {
    service.delete(id, version);
    return Map.of("ok", true);
  }

  /** 指定岗位和独立身份处理状态命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/permits/{id}/commands/{action}")
  public Object command(
      @PathVariable Long id, @PathVariable String action, @RequestBody DeviationService.Command v) {
    return service.command(id, action, v);
  }

  /** 扣减本次使用额度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/permits/{id}/usages")
  public Object use(@PathVariable Long id, @RequestBody DeviationService.UsageInput v) {
    return service.use(id, v);
  }

  /** 独立冲正误登记。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/permits/{id}/usages/{usageId}/void")
  public Object voidUsage(
      @PathVariable Long id, @PathVariable Long usageId, @RequestBody DeviationService.Command v) {
    return service.voidUsage(id, usageId, v);
  }

  /** ALL系统管理员目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 创建校验后的管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 编辑管理资源并保护系统管理员。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminSave(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 外键保护已引用管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
