package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProjectRequest implements ProjectQuery{
    private ProcurementProject project;
    private Integer procId;
    private List<String> sections;
    private String principal;
    private CompletableFuture<ExportRfxResponse> latestEventRFXWithSuppliers;
}
