package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import java.util.Set;
import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * JPA entity representing a mapping between an internal ID, CA/Lot and Jaggaer internal project
 * code
 */
@Entity
@Table(name = "procurement_projects")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(exclude = "procurementEvents")
public class ProcurementProject {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "project_id")
  Integer id;

  @OneToMany(fetch = FetchType.EAGER, mappedBy = "project")
  Set<ProcurementEvent> procurementEvents;

  @Column(name = "commercial_agreement_number")
  String caNumber;

  @Column(name = "lot_number")
  String lotNumber;

  @Column(name = "external_project_id")
  String externalProjectId;

  @Column(name = "external_reference_id")
  String externalReferenceId;

  @Column(name = "project_name")
  String projectName;

  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinColumn(name = "organisation_mapping_id")
  OrganisationMapping organisationMapping;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "updated_at")
  Instant updatedAt;

}
