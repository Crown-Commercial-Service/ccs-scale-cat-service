package uk.gov.crowncommercial.dts.scale.cat.model.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "procurment_event")
public class ProcurementEventSearch {
    @Id
    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

}
