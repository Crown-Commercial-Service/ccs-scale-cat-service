package uk.gov.crowncommercial.dts.scale.cat.repo.readonly;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Read-only base repository
 */
@NoRepositoryBean
public interface ReadOnlyRepository<T, ID> extends Repository<T, ID> {

  Optional<T> findById(ID id);

  List<T> findAll();
}
