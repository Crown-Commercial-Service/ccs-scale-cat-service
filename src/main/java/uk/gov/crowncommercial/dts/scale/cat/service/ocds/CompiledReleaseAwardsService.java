package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import jakarta.persistence.Id;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationIdentifier;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseAwardsService extends AbstractOcdsService{
    private final RetryableTendersDBDelegate tendersDBDelegate;
    private final ConclaveService conclaveService;
    private final ModelMapper modelMapper;
    public MapperResponse populateGeneral(Record1 re, ProjectQuery pq) {
        log.debug("populating General");
        ProcurementProject pp = pq.getProject();

        CompletableFuture cf = CompletableFuture.runAsync(()-> {
            ExportRfxResponse rfxResponse = getLatestRFXWithSuppliers(pq);
            RfxSetting rfxSetting = rfxResponse.getRfxSetting();

            if(EventStatusHelper.isAwarded(rfxSetting)){
                List<Supplier> awardedSuppliers = rfxResponse.getSuppliersList().getSupplier().stream().filter(f -> 3 == f.getStatusCode()).toList();

                Award2 award = OcdsHelper.getAward(re);
                award.setTitle(pp.getProjectName());
                award.setSuppliers(awardedSuppliers.stream().map(this::convertSuppliers).toList());
                award.setDescription(null);
                award.setDate(rfxSetting.getAwardDate());
                award.setValue(null);
            }
        });
        return new MapperResponse(re, cf);
    }

    public OrganizationReference1 convertSuppliers(Supplier supplier) {
        CompanyData companyData = supplier.getCompanyData();
        Optional<OrganisationMapping> om = tendersDBDelegate.findOrganisationMappingByExternalOrganisationId(companyData.getId());
        if (om.isPresent()) {
            OrganisationMapping organisationMapping = om.get();
            Optional<OrganisationProfileResponseInfo> optOrgProfile = conclaveService.getOrganisationIdentity(organisationMapping.getOrganisationId());

            if (optOrgProfile.isPresent()) {
                OrganisationProfileResponseInfo orgProfile = optOrgProfile.get();
                OrganizationReference1 ref = modelMapper.map(orgProfile, OrganizationReference1.class);
                String casId = organisationMapping.getCasOrganisationId();
                if(null != casId) {
                    if (!hasId(orgProfile, casId)) {
                        Identifier1 id = OcdsConverter.getId(casId);
                        if(null != orgProfile.getIdentifier())
                            id.setLegalName(orgProfile.getIdentifier().getLegalName());

                        if (null == ref.getAdditionalIdentifiers()) {
                            ref.setAdditionalIdentifiers(new ArrayList<>());
                        }
                        ref.getAdditionalIdentifiers().add(id);
                    }
                }
                return ref;
            } else {
                String orgId = null != organisationMapping.getCasOrganisationId() ? organisationMapping.getCasOrganisationId()
                        : organisationMapping.getOrganisationId();

                OrganizationReference1 ref = new OrganizationReference1();
                ref.setId(orgId);
                ref.setIdentifier(OcdsConverter.getId(orgId));
                ref.setName(companyData.getName());
                return ref;
            }
        } else {
            OrganizationReference1 ref = new OrganizationReference1();
            ref.setId(companyData.getCode());
            ref.setName(companyData.getName());
            return ref;
        }
    }

    private boolean hasId(OrganisationProfileResponseInfo orgProfile, String orgId) {
        Identifier1 id = OcdsConverter.getId(orgId);
        if(isSame(orgProfile.getIdentifier(), id)){
            return true;
        }
        for(OrganisationIdentifier identifier : orgProfile.getAdditionalIdentifiers()){
            if(isSame(identifier, id)){
                return true;
            }
        }
        return false;
    }

    private boolean isSame(OrganisationIdentifier identifier, Identifier1 id) {
        return
                id.getId().toString().equals(identifier.getId()) &&
                id.getScheme().getValue().equals(identifier.getScheme());
    }


    public MapperResponse populateItems(Record1 re, ProjectQuery pq) {
        log.warn("populating Items not yet implemented");
        return new MapperResponse(re);
    }

    public MapperResponse populateDocuments(Record1 re, ProjectQuery pq) {
        log.debug("populating Documents");
        return new MapperResponse(re);
    }

    public MapperResponse populateAmendments(Record1 re, ProjectQuery pq) {
        log.warn("populating Amendments not yet implemented");
        return new MapperResponse(re);
    }

    public MapperResponse populateRequiremetResponses(Record1 re, ProjectQuery pq) {
        log.warn("populating Enquiries not yet implemented");
        return new MapperResponse(re);
    }
}
