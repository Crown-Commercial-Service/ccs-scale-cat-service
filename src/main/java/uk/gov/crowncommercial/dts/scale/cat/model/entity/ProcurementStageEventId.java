package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProcurementStageEventId implements Serializable
{
    Integer id;

    Integer stageNumber;
}
