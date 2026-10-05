// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.deviationflow;

import jakarta.persistence.*;
import java.time.*;

/** 不覆盖的逐次使用凭证；冲正保留原记录及理由。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(
    name = "permit_usage",
    uniqueConstraints = @UniqueConstraint(columnNames = {"permit_id", "reference"}))
public class PermitUsage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "permit_id", nullable = false)
  public Long permitId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(nullable = false, length = 100)
  public String reference;

  @Column(nullable = false)
  public Long quantity;

  @Column(nullable = false, length = 2000)
  public String evidence;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Boolean voided = false;

  @Column(name = "void_actor_id")
  public Long voidActorId;

  @Column(name = "void_reason", nullable = false, length = 2000)
  public String voidReason = "";

  @Column(name = "voided_at")
  public Instant voidedAt;
}
