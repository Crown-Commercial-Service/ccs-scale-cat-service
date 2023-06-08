package uk.gov.crowncommercial.dts.scale.cat.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.model.ElasticProject;
import uk.gov.crowncommercial.dts.scale.cat.opensearch.model.ElProject;
import uk.gov.crowncommercial.dts.scale.cat.opensearch.repo.ElasticProjectRepo;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(path = "/tenders/opensearch/", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class ElasticController extends AbstractRestController{
    private final ElasticProjectRepo projectRepo;
    private final ElasticsearchOperations elasticsearchOperations;

    @Transactional
    @GetMapping("findAll")
    public ArrayList<ElasticProject> findAll(){
        ArrayList<ElasticProject> result = new ArrayList<>();
        projectRepo.findAll().forEach(new Consumer<ElProject>() {
            @Override
            public void accept(ElProject elProject) {
                result.add(get(elProject));
            }
        });
        return result;
    }

    @Transactional
    @PostMapping("save")
    public ElProject saveProject(@RequestBody ElProject project){
        System.out.println(project.getName());
        return projectRepo.save(project);
    }

    @Transactional
    @GetMapping("search")
    public List<ElasticProject> Search(@RequestParam("name") String name){

        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(QueryBuilders
                        .multiMatchQuery(name)
                        .field("name")
                        .field("description")
                        .fuzziness(Fuzziness.ONE)
                        .type(MultiMatchQueryBuilder.Type.BEST_FIELDS))
                .build();

        SearchHits<ElProject> results = elasticsearchOperations.search(searchQuery, ElProject.class);

        return results.get().map(s -> get(s.getContent())).collect(Collectors.toList());

    }

    public static ElasticProject get(ElProject p){
        ElasticProject pr = new ElasticProject();
        pr.setId(p.getId());
        pr.setName(p.getName());
        pr.setDescription(p.getDescription());
        return pr;
    }
}
