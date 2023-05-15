package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.MessageAsync;

@Repository
public interface MessageTaskRepo extends JpaRepository<MessageAsync, Integer> {

}
