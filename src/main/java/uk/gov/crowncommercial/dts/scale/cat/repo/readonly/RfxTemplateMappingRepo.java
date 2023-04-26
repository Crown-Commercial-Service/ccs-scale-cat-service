package uk.gov.crowncommercial.dts.scale.cat.repo.readonly;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxTemplateMapping;

/**
*
*/
@Repository
public interface RfxTemplateMappingRepo extends ReadOnlyRepository<RfxTemplateMapping, Integer>{

    @Query("select DISTINCT rtm from RfxTemplateMapping rtm where rtm.rfxShortDescription = :rfxShortDescription")
    Optional<RfxTemplateMapping> findByRfxShortDescription(@Param("rfxShortDescription") String rfxShortDescription);
    
    @Query("select DISTINCT rtm from RfxTemplateMapping rtm where rtm.commercialAgreementNumber = :commercialAgreementNumber and lotNumber = :lotNumber")
    Optional<RfxTemplateMapping> findByRfxCommercialAgreementNumberAndLotNumber(
    		@Param("commercialAgreementNumber") String commercialAgreementNumber,
    		@Param("lotNumber") String lotNumber);
    
}
