package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.Set;

/**
 * Organisation
 */
@Entity
@Immutable
@Table(name = "organisations")
@Data
@EqualsAndHashCode(exclude = "people")
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "organisation")
public class Organisation {

  @Id
  @Column(name = "organisation_id")
  Integer id;

  @Column(name = "entity_id")
  String entityId;

  @Column(name = "registry_code")
  String registryCode;

  @Column(name = "legal_name")
  String legalName;

  @Column(name = "business_type")
  String businessType;

  @Column(name = "organisation_uri")
  String uri;

  @Column(name = "status")
  String status;

  @Column(name = "incorporation_date")
  LocalDate incorporationDate;

  @Column(name = "country_of_incorporation")
  String incorporationCountry;

  @Column(name = "country_name")
  String countryName;

  @Column(name = "is_sme")
  Boolean isSme;

  @Column(name = "is_vcse")
  Boolean isVcse;

  @Column(name = "active")
  Boolean isActive;

  @OneToMany(mappedBy = "organisation")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "people")
  Set<Person> people;

}
