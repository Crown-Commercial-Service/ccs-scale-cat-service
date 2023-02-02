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

/**
 * Sector.
 */
@Entity
@Immutable
@Table(name = "sectors")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "lotSectors")
public class Sector {

  @Id
  @Column(name = "sector_code")
  String code;

  @Column(name = "sector_name")
  String name;

  @Column(name = "sector_description")
  String description;

}
