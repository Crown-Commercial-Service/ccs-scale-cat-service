package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.Set;

/**
 * Commercial agreement organisation role
 */
@Entity
@Immutable
@Table(name = "commercial_agreement_organisation_roles")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "organisationRoles")
public class CommercialAgreementOrgRole {

  @Id
  @Column(name = "commercial_agreement_organisation_role_id")
  Integer id;

  @ManyToOne
  @JoinColumn(name = "organisation_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "organisation")
  Organisation organisation;

  @ManyToOne
  @JoinColumn(name = "role_type_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "roleType")
  RoleType roleType;

  @OneToMany(fetch = FetchType.LAZY)
  @JoinColumn(name = "commercial_agreement_organisation_role_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "contactPointCommercialAgreementOrgRoles")
  Set<ContactPointCommercialAgreementOrgRole> contactPointCommercialAgreementOrgRoles;

  @Column(name = "start_date")
  LocalDate startDate;

  @Column(name = "end_date")
  LocalDate endDate;

}
