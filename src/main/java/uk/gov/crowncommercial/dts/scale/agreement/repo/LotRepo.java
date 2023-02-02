package uk.gov.crowncommercial.dts.scale.agreement.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.agreement.model.dto.SupplierDetails;
import uk.gov.crowncommercial.dts.scale.agreement.model.entity.Lot;

import java.util.List;

/**
 * Lot Data Repository.
 */
@Repository
public interface LotRepo extends JpaRepository<Lot, Integer> {

  @Query(
          nativeQuery = true,
          value = "SELECT distinct o.entity_id \"entityId\", o.legal_name \"legalName\", td.trading_organisation_name \"tradingName\", cd.email_address  \"emailAddress\", concat(cd.street_address,cd.locality) \"address\"\n" +
                  "FROM public.organisations o\n" +
                  "INNER JOIN lot_organisation_roles lor on lor.organisation_id  = o.organisation_id\n" +
                  "LEFT OUTER JOIN contact_point_lot_ors cplo  on cplo.lot_organisation_role_id  = lor.lot_organisation_role_id \n" +
                  "left outer join contact_details cd  on cd.contact_detail_id  = cplo.contact_detail_id \n" +
                  "left outer join trading_organisations td on td.trading_organisation_id  = lor.trading_organisation_id \n" +
                  "inner join lots l on l.lot_id  = lor.lot_id \n" +
                  "inner join commercial_agreements ca on ca.commercial_agreement_id  = l.commercial_agreement_id \n" +
                  "where ca.commercial_agreement_number = 'RM1043.8'\n" +
                  "and l.lot_number  = '3'"
  )
  List<SupplierDetails>  findAllSuppliers();


  @Query(
          nativeQuery = true,
          value = "SELECT distinct o.entity_id \"entityId\", o.legal_name \"legalName\", td.trading_organisation_name \"tradingName\", cd.email_address  \"emailAddress\", concat(cd.street_address,cd.locality) \"address\"\n" +
                  "FROM public.organisations o\n" +
                  "INNER JOIN lot_organisation_roles lor on lor.organisation_id  = o.organisation_id\n" +
                  "LEFT OUTER JOIN contact_point_lot_ors cplo  on cplo.lot_organisation_role_id  = lor.lot_organisation_role_id \n" +
                  "left outer join contact_details cd  on cd.contact_detail_id  = cplo.contact_detail_id \n" +
                  "left outer join trading_organisations td on td.trading_organisation_id  = lor.trading_organisation_id \n" +
                  "inner join lots l on l.lot_id  = lor.lot_id \n" +
                  "inner join commercial_agreements ca on ca.commercial_agreement_id  = l.commercial_agreement_id \n" +
                  "where ca.commercial_agreement_number = :agreement and l.lot_number  = :lot"
  )
  List<SupplierDetails>  findSuppliersByAgreementLot(@Param("agreement") String agreement, @Param("lot") String lot);

  @Query(
          nativeQuery = true,
          value = "SELECT distinct o.entity_id \"entityId\", o.legal_name \"legalName\", td.trading_organisation_name \"tradingName\", cd.email_address  \"emailAddress\", concat(cd.street_address,cd.locality) \"address\"\n" +
                  "FROM public.organisations o\n" +
                  "INNER JOIN lot_organisation_roles lor on lor.organisation_id  = o.organisation_id\n" +
                  "LEFT OUTER JOIN contact_point_lot_ors cplo  on cplo.lot_organisation_role_id  = lor.lot_organisation_role_id \n" +
                  "left outer join contact_details cd  on cd.contact_detail_id  = cplo.contact_detail_id \n" +
                  "left outer join trading_organisations td on td.trading_organisation_id  = lor.trading_organisation_id \n" +
                  "inner join lots l on l.lot_id  = lor.lot_id \n" +
                  "inner join commercial_agreements ca on ca.commercial_agreement_id  = l.commercial_agreement_id \n" +
                  "where ca.commercial_agreement_number = :agreement"
  )
  List<SupplierDetails>  findSuppliersByAgreement(@Param("agreement") String agreement);

  Lot findByAgreementNumberAndNumber(String caNumber, String lotNumber);

}
