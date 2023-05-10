package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.input;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.PublishDates;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class JaggaerPublishEventData {
    Integer procId;
    String eventId;
    PublishDates publishDates;
}
