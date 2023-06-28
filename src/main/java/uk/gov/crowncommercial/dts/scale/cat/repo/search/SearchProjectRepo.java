package uk.gov.crowncommercial.dts.scale.cat.repo.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.search.ProcurementEventSearch;
@Repository
public interface SearchProjectRepo extends ElasticsearchRepository<ProcurementEventSearch, String> {
}
