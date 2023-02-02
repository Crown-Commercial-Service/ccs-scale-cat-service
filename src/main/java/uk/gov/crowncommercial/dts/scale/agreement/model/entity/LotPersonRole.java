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
 * Lot person role
 */
@Entity
@Immutable
@Table(name = "lot_people_roles")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LotPersonRole {

  @Id
  @Column(name = "lot_person_role_id")
  Integer id;

  @ManyToOne
  @JoinColumn(name = "person_id")
  Person person;

  @ManyToOne
  @JoinColumn(name = "role_type_id")
  RoleType roleType;

  @Column(name = "start_date")
  LocalDate startDate;

  @Column(name = "end_date")
  LocalDate endDate;

  @OneToMany(fetch = FetchType.LAZY)
  @JoinColumn(name = "lot_person_role_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "contactPointLotOrgRoles")
  Set<ContactPointLotPersonRole> contactPointLotPersonRole;
}
