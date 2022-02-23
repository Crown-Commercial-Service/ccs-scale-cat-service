package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;

import javax.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing a mapping between a project event OCID (authority + prefix + internal ID)
 * and Jaggaer internal event code
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

  @Column(name = "event_id")
  Integer eventId;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "project_id")
  ProcurementProject project;

  @Column(name = "event_type")
  String eventType;

  @Column(name = "user_id")
  String userId;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_at")
  Instant updatedAt;

}
