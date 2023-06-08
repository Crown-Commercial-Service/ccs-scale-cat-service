package uk.gov.crowncommercial.dts.scale.cat.opensearch.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import uk.gov.crowncommercial.dts.scale.cat.opensearch.model.ElProject;

import java.util.List;

public interface ElasticProjectRepo extends ElasticsearchRepository<ElProject, String> {

    @Query("{\"bool\" : {\"must\" : {\"field\" : {\"name\" : \"?0\"}}}}")
    Page<ElProject> findByNasd(String name, Pageable pageable);

    @Query("{\"bool\" : {\"must\" : {\"field\" : {\"name\" : \"?0\"}}}}")
    List<ElProject> findByNasd(String name);
}
