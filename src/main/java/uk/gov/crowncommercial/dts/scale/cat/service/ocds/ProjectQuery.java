package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;

import java.util.List;

public interface ProjectQuery {
    ProcurementProject getProject();

    Integer getProcId();

    List<String> getSections();

    String getPrincipal();
}
