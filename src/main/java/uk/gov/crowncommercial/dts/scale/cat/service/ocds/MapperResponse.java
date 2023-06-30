package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Record1;

import java.util.concurrent.CompletableFuture;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class MapperResponse {
    private final Record1 record;
    CompletableFuture<Void> completableFuture;
}
