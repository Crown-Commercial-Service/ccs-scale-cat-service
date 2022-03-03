package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.*;

/**
 * JPA entity representing a mapping between a project and its users
 */
@Entity
@Table(name = "project_user_mapping")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(exclude = "project")
public class ProjectUserMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "project_user_mapping_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "project_id")
  ProcurementProject project;

  @Column(name = "user_id")
  String userId;

  @Embedded
  private Timestamps timestamps;

}
