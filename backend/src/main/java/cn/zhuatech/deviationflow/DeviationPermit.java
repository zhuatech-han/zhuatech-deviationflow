// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.deviationflow;

import jakarta.persistence.*;
import java.time.*;

/** 临时工艺偏差申请及批准后冻结的边界，数量由使用台账原子扣减。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "deviation_permit")
public class DeviationPermit {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public Long version = 0L;

  @Column(nullable = false, unique = true, length = 60)
  public String code;

  @Column(nullable = false, length = 160)
  public String title;

  @Column(nullable = false, length = 60)
  public String category;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "author_id", nullable = false)
  public Long authorId;

  @Column(name = "reviewer_id", nullable = false)
  public Long reviewerId;

  @Column(name = "approver_id", nullable = false)
  public Long approverId;

  @Column(name = "executor_id", nullable = false)
  public Long executorId;

  @Column(name = "item_code", nullable = false, length = 100)
  public String itemCode;

  @Column(name = "item_revision", nullable = false, length = 60)
  public String itemRevision;

  @Column(name = "scope_tag", nullable = false, length = 120)
  public String scopeTag;

  @Column(nullable = false, length = 2000)
  public String baseline;

  @Column(nullable = false, length = 2000)
  public String deviation;

  @Column(nullable = false, length = 2000)
  public String controls;

  @Column(name = "external_authorization", nullable = false, length = 1000)
  public String externalAuthorization;

  @Column(name = "quantity_limit", nullable = false)
  public Long quantityLimit;

  @Column(name = "used_quantity", nullable = false)
  public Long usedQuantity = 0L;

  @Column(nullable = false, length = 20)
  public String unit;

  @Column(name = "valid_from", nullable = false)
  public LocalDate validFrom;

  @Column(name = "valid_until", nullable = false)
  public LocalDate validUntil;

  @Column(nullable = false, length = 20)
  public String status = "DRAFT";

  @Column(name = "review_note", nullable = false, length = 2000)
  public String reviewNote = "";

  @Column(name = "approval_note", nullable = false, length = 2000)
  public String approvalNote = "";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "approved_at")
  public Instant approvedAt;

  @Column(name = "edit_revision", nullable = false)
  public Long editRevision = 0L;
}
