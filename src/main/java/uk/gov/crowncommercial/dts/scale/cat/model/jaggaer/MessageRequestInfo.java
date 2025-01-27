package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageDirection;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageRead;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageSort;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageSortOrder;

@Value
@Builder
public class MessageRequestInfo {
    Integer procId;
    String eventId;
    MessageDirection messageDirection;
    MessageRead messageRead;
    MessageSort messageSort;
    MessageSortOrder messageSortOrder;
    Integer page;
    Integer pageSize;
    String principal ;

}
