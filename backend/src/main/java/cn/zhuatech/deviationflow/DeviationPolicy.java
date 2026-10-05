// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.deviationflow;

import java.time.*;
import java.util.*;

/** 日期与整数用量规则，不接收客户端累计量或放行时间。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class DeviationPolicy {
  private DeviationPolicy() {}

  /** 上海日期，截止日包含全天。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static LocalDate today(Instant now) {
    return now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
  }

  /** 撤销与关闭优先；批准后按日期及剩余额度显示状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String status(DeviationPermit p, Instant now) {
    if (!p.status.equals("ACTIVE")) return p.status;
    var day = today(now);
    if (day.isBefore(p.validFrom)) return "SCHEDULED";
    if (day.isAfter(p.validUntil)) return "EXPIRED";
    return p.usedQuantity >= p.quantityLimit ? "EXHAUSTED" : "ACTIVE";
  }

  /** 拒绝零、负、超限及超量使用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long consume(long used, long limit, Long amount) {
    if (amount == null || amount < 1 || amount > 1000000000L)
      throw new Problem(400, "INVALID_QUANTITY");
    if (amount > limit - used) throw new Problem(409, "QUANTITY_EXCEEDED");
    return used + amount;
  }

  /** 旧页面不能覆盖已变更的状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void version(Long actual, Long input) {
    if (!Objects.equals(actual, input)) throw new Problem(409, "STALE_VERSION");
  }
}
