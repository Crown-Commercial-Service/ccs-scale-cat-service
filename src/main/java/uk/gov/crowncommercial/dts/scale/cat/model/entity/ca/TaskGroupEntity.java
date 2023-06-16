package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

import jakarta.persistence.*;
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
  Integer id;

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

  @Embedded
  private Timestamps timestamps;
}
