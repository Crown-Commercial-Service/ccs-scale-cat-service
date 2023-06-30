package uk.gov.crowncommercial.dts.scale.cat.repo.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.search.ProcurementEventSearch;

import java.util.List;

public interface SearchProjectRepo extends ElasticsearchRepository<ProcurementEventSearch, String> {
   List<ProcurementEventSearch> findByProjectNameOrDescriptionContaining(String projectName, String description);
}
