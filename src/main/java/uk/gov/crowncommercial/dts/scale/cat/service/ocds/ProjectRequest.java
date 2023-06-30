package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProjectRequest implements ProjectQuery{
    private ProcurementProject project;
    private Integer procId;
    private List<String> sections;
    private String principal;
}
