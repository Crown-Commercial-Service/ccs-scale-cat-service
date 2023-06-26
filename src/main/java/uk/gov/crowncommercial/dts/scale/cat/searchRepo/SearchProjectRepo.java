package uk.gov.crowncommercial.dts.scale.cat.searchRepo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.search.ProcurementEventSearch;

import java.util.List;

public interface SearchProjectRepo extends ElasticsearchRepository<ProcurementEventSearch, String> {

    @Query("{\"bool\" : {\"must\" : {\"field\" : {\"name\" : \"?0\"}}}}")
    Page<ProcurementEventSearch> findByNasd(String name, Pageable pageable);

    @Query("{\"bool\" : {\"must\" : {\"field\" : {\"name\" : \"?0\"}}}}")
    List<ProcurementEventSearch> findByNasd(String name);
}
