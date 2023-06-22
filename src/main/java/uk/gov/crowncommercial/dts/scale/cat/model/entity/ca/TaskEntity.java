package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

import javax.persistence.*;
import java.time.Instant;
import java.util.List;

/**
*
*/
@Entity
@Table(name = "tasks")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaskEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "task_id")
  Long id;

  @Column(name = "task_name")
  private String name;

  @Column(name="group_id")
  private Integer groupId;

  @Column(name = "task_object")
  private String recordType;

  @Column(name = "task_record_id")
  private String recordId;

  @Column(name = "principal")
  private String principal;

  @Column(name = "taskExecutor")
  private String taskExecutor;

  @Column(name = "task_data_type")
  private String dataClass;

  @Column(name = "task_data")
  private String data;

  @Column(name = "scheduled_on", updatable = false)
  private Instant scheduledOn;

  @Column(name = "tobe_executed_at")
  private Instant tobeExecutedAt;

  @Column(name = "last_executed_on")
  private Instant lastExecutedOn;

  @Column(name = "task_status")
  private char status;

  @Column(name = "task_node")
  private String node;

  @Column(name = "task_stage")
  private String stage;

  @Column(name = "task_response")
  private String Response;

  @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @OrderBy("task_history_id DESC")
  @JoinColumn(name = "task_id", referencedColumnName="task_id" )
  private List<TaskHistoryEntity> history;

  @Embedded
  private Timestamps timestamps;
}
