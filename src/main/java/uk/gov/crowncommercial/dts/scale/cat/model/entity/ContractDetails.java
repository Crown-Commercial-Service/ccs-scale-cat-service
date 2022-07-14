package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ContractStatus;

@Entity
@Table(name = "contract_details")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContractDetails {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "contract_id")
  Integer contractId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id")
  ProcurementEvent event;

  @Column(name = "award_id")
  String awardId;

  @Column(name = "contract_status")
  @Enumerated(EnumType.STRING)
  ContractStatus contractStatus;
  
  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "updated_at")
  Instant updatedAt;
}
