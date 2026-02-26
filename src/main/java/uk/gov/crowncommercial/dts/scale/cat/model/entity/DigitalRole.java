package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "digital_roles")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DigitalRole {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  Long id;

  @Column(name = "job_family", nullable = false)
  String jobFamily;

  @Column(name = "role", nullable = false)
  String role;

  @Column(name = "level", nullable = false)
  String level;

  @Column(name = "count", nullable = false)
  Integer count;

  @Column(name = "project_id", nullable = false)
  String projectId;

  @Column(name = "event_id", nullable = false)
  String eventId;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  LocalDateTime createdAt;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "updated_at")
  LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  public void preSave() {
    if (Objects.isNull(getCreatedAt())) {
      setCreatedAt(LocalDateTime.now());
    }
    setUpdatedAt(LocalDateTime.now());
  }
}
