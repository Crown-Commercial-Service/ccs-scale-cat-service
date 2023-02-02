package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.time.LocalDate;

/**
 * Contact point - CA organisation role (M:M join entity)
 */
@Entity
@Immutable
@Table(name = "contact_point_commercial_agreement_ors")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "contactPointCommercialAgreementOrgRoles")
public class ContactPointCommercialAgreementOrgRole {

  @Id
  @Column(name = "contact_point_id")
  Integer id;

  @Column(name = "contact_point_name")
  String contactPointName;

  @ManyToOne
  @JoinColumn(name = "contact_detail_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "contactDetail")
  ContactDetail contactDetail;

  @ManyToOne
  @JoinColumn(name = "contact_point_reason_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "contactPointReason")
  ContactPointReason contactPointReason;

  @Column(name = "effective_from")
  LocalDate effectiveFrom;

  @Column(name = "effective_to")
  LocalDate effectiveTo;

  @Column(name = "primary_ind")
  Boolean primary;

}
