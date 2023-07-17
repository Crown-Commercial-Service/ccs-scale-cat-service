package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPackage;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Record1;

import java.util.function.BiFunction;

public interface ProjectRecordHandler extends BiFunction<ProjectQuery, Record1, MapperResponse> {
}
