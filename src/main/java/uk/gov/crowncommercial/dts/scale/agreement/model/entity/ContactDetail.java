package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDate;

/**
 *
 */
@Entity
@Immutable
@Table(name = "contact_details")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "contactDetail")
public class ContactDetail {

  @Id
  @Column(name = "contact_detail_id")
  Integer id;

  @Column(name = "effective_from")
  LocalDate effectiveFrom;

  @Column(name = "effective_to")
  LocalDate effectiveTo;

  @Column(name = "street_address")
  String streetAddress;

  @Column(name = "locality")
  String locality;

  @Column(name = "region")
  String region;

  @Column(name = "postal_code")
  String postalCode;

  @Column(name = "country_code")
  String countryCode;

  @Column(name = "country_name")
  String countryName;

  @Column(name = "uprn")
  Integer uprn;

  @Column(name = "emailAddress")
  String emailAddress;

  @Column(name = "telephone_number")
  String telephoneNumber;

  @Column(name = "fax_number")
  String faxNumber;

  @Column(name = "url")
  String url;
}
