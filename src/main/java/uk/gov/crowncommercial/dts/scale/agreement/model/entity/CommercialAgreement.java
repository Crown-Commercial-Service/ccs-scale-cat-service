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
 * Commercial Agreement.
 */
@Entity
@Immutable
@Table(name = "commercial_agreements")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "commercial_agreements") //Provide cache strategy.
public class CommercialAgreement {

  @Id
  @Column(name = "commercial_agreement_id")
  Integer id;

  @Column(name = "commercial_agreement_number")
  String number;

  @Column(name = "commercial_agreement_name")
  String name;

  @Column(name = "commercial_agreement_description")
  String description;

  @Column(name = "start_date")
  LocalDate startDate;

  @Column(name = "end_date")
  LocalDate endDate;

  @Column(name = "agreement_url")
  String detailUrl;

  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "lots")
  @OneToMany(mappedBy = "agreement")
  Set<Lot> lots;

  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "documents")
  @OneToMany
  @JoinColumn(name = "commercial_agreement_id")
  Set<CommercialAgreementDocument> documents;

  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "updates")
  @OneToMany
  @JoinColumn(name = "commercial_agreement_id")
  Set<CommercialAgreementUpdate> updates;

  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "organisationRoles")
  @OneToMany
  @JoinColumn(name = "commercial_agreement_id")
  Set<CommercialAgreementOrgRole> organisationRoles;

  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "benefits")
  @OneToMany
  @JoinColumn(name = "commercial_agreement_id")
  Set<CommercialAgreementBenefit> benefits;

  @Column(name = "lot_required")
  Boolean preDefinedLotRequired;

  @Column(name = "lot_assessment_tool_id")
  Integer lotAssessmentTool;
}
