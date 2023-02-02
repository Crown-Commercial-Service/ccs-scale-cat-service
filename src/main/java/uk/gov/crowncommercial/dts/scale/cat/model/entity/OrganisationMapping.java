package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import javax.persistence.*;
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
   * The Org service ID
   */
  @Column(name = "organisation_id")
  String organisationId;

  /**
   * The sales platform ID
   */
  @Column(name = "external_organisation_id")
  Integer externalOrganisationId;

  @Column(name = "primary_ind")
  boolean primaryInd;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

}
