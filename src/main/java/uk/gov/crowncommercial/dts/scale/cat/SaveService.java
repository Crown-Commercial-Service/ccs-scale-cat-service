package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.repo.OrganisationMappingRepo;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
@Service
@Log4j2
public class SaveService {
    private final OrganisationMappingRepo repo;

    @Transactional
    public void save(String dunsNumber, ReturnCompanyData data, String ciiCoh) {
        save(dunsNumber, Integer.parseInt(data.getReturnCompanyInfo().getBravoId()), ciiCoh);
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
    public void save(String dunsNumber, Integer externalOrgId, String ciiIdentifier) {
        String orgId = "US-DUNS-" + dunsNumber;
        if (null == ciiIdentifier)
            ciiIdentifier = orgId;

        OrganisationMapping om = getByOrgIds(externalOrgId, ciiIdentifier, orgId);

        if (null != om) {
            log.trace("Existing record found and will update id:{}, bravoId:{}", om.getId(), om.getExternalOrganisationId());
            updateCasOrgId(ciiIdentifier, externalOrgId, orgId, om);
        } else {
            om = repo.findByOrganisationId(ciiIdentifier).orElse(null);
            if (null == om && !ciiIdentifier.equalsIgnoreCase(orgId))
                om = repo.findByOrganisationId(orgId).orElse(null);
            if(null != om)
                updateCasOrgId(ciiIdentifier, externalOrgId, orgId, om);

            if (null == om)
                om = repo.findByCasOrganisationId(orgId);
        }

        if (null == om) {
            log.debug("Creating new record orgId:{}, bravoId:{}, casId:{}", ciiIdentifier, externalOrgId, orgId);
            om = new OrganisationMapping();
            om.setOrganisationId(ciiIdentifier);
            om.setCasOrganisationId(orgId);
            om.setExternalOrganisationId(externalOrgId);
            om.setCreatedBy("syncLoader");
            om.setCreatedAt(Instant.now());
            repo.save(om);
            return;
        } else {
            if (!om.getExternalOrganisationId().equals(externalOrgId)) {
                throw new RuntimeException("different bravoId between database:" + om.getExternalOrganisationId() + " and Jaggaer:" + externalOrgId
                        + " for the orgid " + om.getOrganisationId());
            }
        }
    }

    public OrganisationMapping getByOrgIds(Integer externalOrgId, String ciiIdentifier, String orgId) {
        List<OrganisationMapping> oms = repo.findByOrgIds(ciiIdentifier, orgId, externalOrgId);
        if (oms.size() > 1) {
            String bravoIds = "";
            for(OrganisationMapping om : oms){
                bravoIds = bravoIds + om.getExternalOrganisationId() + ";";
            }
            bravoIds = bravoIds.substring(0, bravoIds.length()-1);
            throw new RuntimeException("more than one Jaggaer id(" + bravoIds + ")  found for :" + ciiIdentifier + "/" + orgId);
        }else if(1 == oms.size())
            return oms.get(0);
        return null;
    }

    private boolean updateCasOrgId(String ppgIdentifier, Integer externalOrgId, String orgId, OrganisationMapping om) {
        if (null == om.getCasOrganisationId()) {
            log.debug("{}, setting cas id to {}", om.getId(), orgId);
            om.setCasOrganisationId(orgId);
        } else if (null != om.getOrganisationId()
                && om.getOrganisationId().equalsIgnoreCase(om.getCasOrganisationId())){
            if(!sameEntityCode(om.getCasOrganisationId(), orgId)) {
                log.debug("{}, setting cas id to {}", om.getId(),  orgId);
                om.setCasOrganisationId(orgId);
            }
            if (!ppgIdentifier.startsWith("US-DUNS") && !sameEntityCode(om.getOrganisationId(), ppgIdentifier)) {
                log.debug("{}, setting ppg id to {}", om.getId(), ppgIdentifier);
                om.setOrganisationId(ppgIdentifier);
            }
        } else if (om.getCasOrganisationId().equalsIgnoreCase(orgId))
            return true;
        else {
            throw new RuntimeException("different orgId between jaggaer Mapped in database :" + om.getOrganisationId() + " and jaggaer queried:" + orgId
                    + " for the jaggaerId " + externalOrgId);
        }
        return false;
    }

    private boolean sameEntityCode(String casOrganisationId, String dunsNumber) {
        String casPrefix = casOrganisationId.substring(0, casOrganisationId.lastIndexOf('-'));
        String dunsPrefix = dunsNumber.substring(0, dunsNumber.lastIndexOf('-'));
        return casPrefix.equalsIgnoreCase(dunsPrefix);
    }
}
