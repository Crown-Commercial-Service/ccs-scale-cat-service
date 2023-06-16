package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

import jakarta.persistence.*;
import java.time.Instant;

/**
*
*/
@Entity
@Table(name = "task_history")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaskHistoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "task_history_id")
  Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "task_id")
  private TaskEntity taskEntity;

  @Column(name = "scheduled_on", updatable = false)
  private Instant scheduledOn;

  @Column(name = "executed_on")
  private Instant executedOn;

  @Column(name = "task_history_status")
  private char status;

  @Column(name = "task_node")
  private String node;

  @Column(name = "task_stage")
  private String stage;

  @Column(name = "task_response")
  private String Response;

  @Embedded
  private Timestamps timestamps;
}
