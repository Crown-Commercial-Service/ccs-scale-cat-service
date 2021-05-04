package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import java.util.Set;
import javax.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 *
 */
@Entity
@Table(name = "procurement_projects")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProcurementProject {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "project_id")
  Integer id;

  @OneToMany(fetch = FetchType.EAGER)
  @JoinColumn(name = "project_id")
  Set<ProcurementEvent> procurementEvents;

  @Column(name = "commercial_agreement_number")
  String caNumber;

  @Column(name = "lot_number")
  String lotNumber;

  @Column(name = "jaggaer_project_id")
  String jaggaerProjectId;

  @Column(name = "project_name")
  String projectName;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "updated_at")
  Instant updatedAt;

}
