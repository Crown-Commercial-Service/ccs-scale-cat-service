package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.MessageAsync;

import java.util.List;

@Repository
public interface MessageTaskRepo extends JpaRepository<MessageAsync, Integer> {

    @Query("SELECT m FROM message_task m WHERE m.status = 'INPROGRESS'")
    public List<MessageAsync> findAllByEventId(Integer eventId);
}
