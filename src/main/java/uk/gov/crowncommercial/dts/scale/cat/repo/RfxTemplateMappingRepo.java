package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxTemplateMapping;

/**
*
*/
@Repository
public interface RfxTemplateMappingRepo extends JpaRepository<RfxTemplateMapping, Integer>{
	
//    @Query("select DISTINCT rtm from RfxTemplateMapping rtm where rtm.rfxShortDescription = :rfxShortDescription")
//    Optional<RfxTemplateMapping> findByRfxShortDescription(@Param("rfxShortDescription") String rfxShortDescription);

    Optional<RfxTemplateMapping> findByRfxShortDescription(String rfxShortDescription);

    Optional<RfxTemplateMapping> findByCommercialAgreementNumberAndLotNumber(String commercial_agreement_number, String lot_number);

}
