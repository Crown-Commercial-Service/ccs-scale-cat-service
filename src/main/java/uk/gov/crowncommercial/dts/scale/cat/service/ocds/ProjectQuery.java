package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ProjectQuery {
    ProcurementProject getProject();

    Integer getProcId();

    List<String> getSections();

    String getPrincipal();

    CompletableFuture<ExportRfxResponse> getLatestEventRFXWithSuppliers();

//    void setLatestEventRFXWithSuppliers();
}
