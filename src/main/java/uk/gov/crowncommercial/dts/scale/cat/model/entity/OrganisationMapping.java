package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 *
 */
@Entity
@Table(name = "organisation_mapping")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrganisationMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "organisation_mapping_id")
  Integer id;

  /**
   * The Org ID on PPG
   */
  @Column(name = "organisation_id")
  String organisationId;

  /**
   * The Org Id on CAS service
   */
  @Column(name = "cas_organisation_id")
  String casOrganisationId;

  /**
   * The sales platform ID
   */
  @Column(name = "external_organisation_id")
  Integer externalOrganisationId;

  @Builder.Default
  @Column(name = "primary_ind")
  boolean primaryInd = true;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

}
