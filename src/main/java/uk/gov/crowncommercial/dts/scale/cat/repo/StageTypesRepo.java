package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageTypesEntity;

public interface StageTypesRepo extends JpaRepository<StageTypesEntity, Long> {

}
