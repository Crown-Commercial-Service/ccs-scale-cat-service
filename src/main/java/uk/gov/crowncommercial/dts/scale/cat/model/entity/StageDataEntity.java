package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
*
*/
@Entity
@Table(name = "stage_data")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StageDataEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  Integer id;

  @Column(name = "event_id")
  private String eventId;

  @Column(name = "number_of_stages")
  private Integer numberOfStages;

  @Column(name = "current_stage")
  private Integer currentStage;

  @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @OrderBy("stage_number ASC")
  @JoinColumn(name = "event_id", referencedColumnName="event_id")
  private List<StageNameEntity> stageNames;

  @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @OrderBy("stage_number ASC")
  @JoinColumn(name = "event_id", referencedColumnName="event_id")
  private List<StageEventEntity> stageEvents;

  public List<StageEventEntity> getStageEvents() {
      return stageEvents;
  }
}
