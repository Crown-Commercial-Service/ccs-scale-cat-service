package uk.gov.crowncommercial.dts.scale.cat.repo.specification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ProjectSearchCriteria {

    private String searchType;
    private String searchTerm;
    private String userId;
}
