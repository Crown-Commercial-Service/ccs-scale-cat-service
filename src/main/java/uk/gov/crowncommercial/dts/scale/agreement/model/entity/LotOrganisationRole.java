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
 * Lot organisation role
 */
@Entity
@Immutable
@Table(name = "lot_organisation_roles")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "lotOrganisationRoles")
public class LotOrganisationRole {

  @Id
  @Column(name = "lot_organisation_role_id")
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
  @JoinColumn(name = "lot_organisation_role_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "contactPointLotOrgRoles")
  Set<ContactPointLotOrgRole> contactPointLotOrgRoles;

  @ManyToOne
  @JoinColumn(name = "trading_organisation_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "trading_organisation")
  TradingOrganisation tradingOrganisation;

  @Column(name = "start_date")
  LocalDate startDate;

  @Column(name = "end_date")
  LocalDate endDate;

  @Column(name="organisation_status")
  char status;

  @Column(name = "role_type_id", insertable = false, updatable=false)
  Integer roleTypeId;
}
