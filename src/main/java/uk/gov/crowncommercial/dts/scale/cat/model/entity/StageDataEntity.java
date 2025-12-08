package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.util.List;

import org.hibernate.annotations.Type;

import com.vladmihalcea.hibernate.type.array.ListArrayType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  Long id;

  @Id
  @Column(name = "event_id")
  private String eventId;

  @Column(name = "number_of_stages")
  private Integer numberOfStages;

  @Type(ListArrayType.class)
  @Column(name = "stage_ids", columnDefinition = "integer[]")
  private List<Long> stageIds;
}
