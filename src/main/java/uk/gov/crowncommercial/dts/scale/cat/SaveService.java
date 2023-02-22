package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.repo.OrganisationMappingRepo;

import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
@Service
public class SaveService {
    private final OrganisationMappingRepo repo;

    @Transactional
    public void save(String dunsNumber, ReturnCompanyData data) {
        save(dunsNumber, Integer.parseInt(data.getReturnCompanyInfo().getBravoId()));
    }

    public OrganisationMapping query(String dunsNumber) {
        String orgId = "US-DUNS-" + dunsNumber;
        return repo.findByOrganisationId(orgId).orElse(null);
    }

    public List<OrganisationMapping> listAll() {
        return repo.findAll();
    }

    public void save(OrganisationMapping om, Integer externalOrgId) {
        OrganisationMapping omByExtOrgId = repo.findByExternalOrganisationId(externalOrgId).orElse(null);

        if (null != om) {
            if (null != omByExtOrgId && omByExtOrgId.getId().equals(om.getId()))
                return;

            om.setExternalOrganisationId(externalOrgId);
            om.setCreatedAt(Instant.now());
            om.setCreatedBy("syncLoader");
            repo.save(om);
        }
    }

    @Transactional
    public void save(String dunsNumber, Integer externalOrgId) {
        String orgId = "US-DUNS-" + dunsNumber;
        System.out.println("Saving orgId:" + orgId);
        OrganisationMapping om = repo.findByExternalOrganisationId(externalOrgId).orElse(null);

        if (null != om) {
            if (null == om.getCasOrganisationId()) {
                om.setCasOrganisationId(orgId);
            } else if (null != om.getOrganisationId()
                    && om.getOrganisationId().equalsIgnoreCase(om.getCasOrganisationId())
                && !sameEntityCode(om.getCasOrganisationId(), orgId)) {
                om.setCasOrganisationId(orgId);
            } else if (om.getCasOrganisationId().equalsIgnoreCase(orgId))
                return;
            else {
                throw new RuntimeException("different orgId between jaggaer Mapped in database :" + om.getOrganisationId() + " and jaggaer queried:" + dunsNumber
                        + " for the jaggaerId " + externalOrgId);
            }
        } else {
            om = repo.findByOrganisationId(orgId).orElse(null);
            if(null == om)
                om = repo.findByCasOrganisationId(orgId);
        }

        if (null == om) {
            om = new OrganisationMapping();
            om.setOrganisationId(orgId);
            om.setCasOrganisationId(orgId);
            om.setExternalOrganisationId(externalOrgId);
            om.setCreatedBy("syncLoader");
            om.setCreatedAt(Instant.now());
            repo.save(om);
            return;
        } else {
            if(!om.getExternalOrganisationId().equals(externalOrgId)) {
                throw new RuntimeException("different bravoId between database:" + om.getExternalOrganisationId() + " and Jaggaer:" + externalOrgId
                        + " for the orgid " + om.getOrganisationId());
            }
        }
    }

    private boolean sameEntityCode(String casOrganisationId, String dunsNumber) {
        String casPrefix =  casOrganisationId.substring(0,casOrganisationId.lastIndexOf('-'));
        String dunsPrefix =  dunsNumber.substring(0,dunsNumber.lastIndexOf('-'));
        return casPrefix.equalsIgnoreCase(dunsPrefix);
    }
}
