package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.WhereJoinTable;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

import javax.persistence.*;
import java.time.Instant;
import java.util.List;

/**
*
*/
@Entity
@Table(name = "task_group")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaskGroupEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "task_group_id")
  Long id;

  @Column(name = "group_name")
  private String name;

  @Column(name = "group_reference")
  private String reference;

  @Column(name = "group_status")
  private char status;

  @Column(name = "task_node")
  private String node;

  @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @OrderBy("tobe_executed_at DESC")
  @JoinColumn(name = "group_id", referencedColumnName="task_group_id" )
  private List<TaskEntity> tasks;

  @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @OrderBy("tobe_executed_at DESC")
  @JoinTable(name="task_entity")
  @WhereJoinTable(clause="task_status in ('I', 'S')")
  @JoinColumn(name = "group_id", referencedColumnName="task_group_id" )
  private List<TaskEntity> activeTasks;

  @Embedded
  private Timestamps timestamps;
}
