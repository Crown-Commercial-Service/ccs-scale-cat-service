package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 *
 */
@Entity
@Table(name = "supplier_selections")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupplierSelection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "supplier_selection_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "organisation_mapping_id")
  OrganisationMapping organisationMapping;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "event_id")
  ProcurementEvent procurementEvent;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

}
