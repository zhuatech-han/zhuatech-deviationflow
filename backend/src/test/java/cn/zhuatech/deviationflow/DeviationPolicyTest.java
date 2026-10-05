// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.deviationflow;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

/** 上海日界、终结优先和数量边界测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class DeviationPolicyTest {
  @Test
  void shanghaiDateChangesAt16Utc() {
    assertEquals(
        LocalDate.parse("2026-10-05"),
        DeviationPolicy.today(Instant.parse("2026-10-05T15:59:59Z")));
    assertEquals(
        LocalDate.parse("2026-10-06"),
        DeviationPolicy.today(Instant.parse("2026-10-05T16:00:00Z")));
  }

  @Test
  void exactLimitAllowedWithoutOverflow() {
    assertEquals(1000000000L, DeviationPolicy.consume(999999999, 1000000000, 1L));
    assertThrows(Problem.class, () -> DeviationPolicy.consume(10, 10, 1L));
    assertThrows(Problem.class, () -> DeviationPolicy.consume(0, 10, null));
    assertThrows(Problem.class, () -> DeviationPolicy.consume(0, 10, -1L));
  }

  @Test
  void terminalStatusTakesPrecedenceOverDateAndQuantity() {
    var p = new DeviationPermit();
    p.status = "REVOKED";
    p.validFrom = LocalDate.parse("2026-01-01");
    p.validUntil = LocalDate.parse("2026-01-02");
    p.quantityLimit = 10L;
    p.usedQuantity = 10L;
    assertEquals("REVOKED", DeviationPolicy.status(p, Instant.parse("2026-10-05T00:00:00Z")));
  }

  @Test
  void nullAndStaleVersionsRejected() {
    assertThrows(Problem.class, () -> DeviationPolicy.version(0L, null));
    assertThrows(Problem.class, () -> DeviationPolicy.version(1L, 0L));
    assertDoesNotThrow(() -> DeviationPolicy.version(1L, 1L));
  }
}
