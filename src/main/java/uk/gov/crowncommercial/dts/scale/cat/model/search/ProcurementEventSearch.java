package uk.gov.crowncommercial.dts.scale.cat.model.search;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "procurement_event")
public class ProcurementEventSearch {

    @JsonProperty("projectId")
    Integer id;
    @JsonProperty("projectName")
    String projectName;
    @JsonProperty("buyerName")
    String buyerName;
    @JsonProperty("location")
    String location;
    @JsonProperty("budgetRange")
    String budgetRange;
    @JsonProperty("agreement")
    String agreement;
    @JsonProperty("lot")
    String lot;
    @JsonProperty("status")
    String status;
    @JsonProperty("subStatus")
    String subStatus;
    @JsonProperty("description")
    String description;

}
