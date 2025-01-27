package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

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

/**
 * Populates release award related information regarding a given project
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseAwardsService extends AbstractOcdsService{
    private final RetryableTendersDBDelegate tendersDBDelegate;
    private final ConclaveService conclaveService;
    private final ModelMapper modelMapper;

    /**
     * Populate general award related information regarding the project
     */
    public MapperResponse populateGeneral(Record1 re, ProjectQuery pq) {
        CompletableFuture<Void> cf = null;

        if (pq != null && pq.getProject() != null && re != null) {
            ProcurementProject pp = pq.getProject();

            cf = CompletableFuture.runAsync(() -> {
                ExportRfxResponse rfxResponse = getLatestRFXWithSuppliers(pq);

                if (rfxResponse != null && rfxResponse.getRfxSetting() != null) {
                    RfxSetting rfxSetting = rfxResponse.getRfxSetting();

                    if (EventStatusHelper.isAwarded(rfxSetting) && rfxResponse.getSuppliersList() != null && rfxResponse.getSuppliersList().getSupplier() != null) {
                        List<Supplier> awardedSuppliers = rfxResponse.getSuppliersList().getSupplier().stream().filter(f -> 3 == f.getStatusCode()).toList();

                        Award2 award = OcdsHelper.getAward(re);

                        if (award != null) {
                            if (pp != null && pp.getProjectName() != null) {
                                award.setTitle(pp.getProjectName());
                            }

                            award.setSuppliers(awardedSuppliers.stream().map(this::convertSuppliers).toList());
                            award.setDescription(null);
                            award.setValue(null);

                            if (rfxSetting.getAwardDate() != null) {
                                award.setDate(rfxSetting.getAwardDate());
                            }
                        }
                    }
                }
            });
        }

        return new MapperResponse(re, cf);
    }

    /**
     * Converts a Supplier object into an OrganizationReference1 object
     */
    public OrganizationReference1 convertSuppliers(Supplier supplier) {
        if (supplier != null && supplier.getCompanyData() != null) {
            CompanyData companyData = supplier.getCompanyData();

            if (companyData.getId() != null) {
                Optional<OrganisationMapping> om = tendersDBDelegate.findOrganisationMappingByExternalOrganisationId(companyData.getId());

                if (om.isPresent()) {
                    OrganisationMapping organisationMapping = om.get();

                    if (organisationMapping.getOrganisationId() != null) {
                        Optional<OrganisationProfileResponseInfo> optOrgProfile = conclaveService.getOrganisationIdentity(organisationMapping.getOrganisationId());

                        if (optOrgProfile.isPresent()) {
                            OrganisationProfileResponseInfo orgProfile = optOrgProfile.get();
                            OrganizationReference1 ref = modelMapper.map(orgProfile, OrganizationReference1.class);


                            if (organisationMapping.getCasOrganisationId() != null) {
                                String casId = organisationMapping.getCasOrganisationId();

                                if (!hasId(orgProfile, casId)) {
                                    Identifier1 id = OcdsConverter.getId(casId);

                                    if (orgProfile.getIdentifier() != null && orgProfile.getIdentifier().getLegalName() != null) {
                                        id.setLegalName(orgProfile.getIdentifier().getLegalName());
                                    }

                                    if (ref.getAdditionalIdentifiers() == null) {
                                        ref.setAdditionalIdentifiers(new ArrayList<>());
                                    }

                                    ref.getAdditionalIdentifiers().add(id);
                                }
                            }

                            return ref;
                        } else {
                            String orgId = null != organisationMapping.getCasOrganisationId() ? organisationMapping.getCasOrganisationId() : organisationMapping.getOrganisationId();

                            if (orgId != null) {
                                OrganizationReference1 ref = new OrganizationReference1();
                                ref.setId(orgId);
                                ref.setIdentifier(OcdsConverter.getId(orgId));

                                if (companyData.getName() != null) {
                                    ref.setName(companyData.getName());
                                }

                                return ref;
                            }
                        }
                    }
                } else {
                    OrganizationReference1 ref = new OrganizationReference1();

                    if (companyData.getCode() != null) {
                        ref.setId(companyData.getCode());
                    }

                    if (companyData.getName() != null) {
                        ref.setName(companyData.getName());
                    }

                    return ref;
                }
            }
        }

        return null;
    }

    /**
     * Validates whether a given organisation profile has a given ID
     */
    private boolean hasId(OrganisationProfileResponseInfo orgProfile, String orgId) {
        if (orgId != null && orgProfile != null) {
            Identifier1 id = OcdsConverter.getId(orgId);

            if (id != null) {
                if (orgProfile.getIdentifier() != null && isSame(orgProfile.getIdentifier(), id)) {
                    return true;
                }

                if (orgProfile.getAdditionalIdentifiers() != null) {
                    for (OrganisationIdentifier identifier : orgProfile.getAdditionalIdentifiers()) {
                        if (isSame(identifier, id)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Validates whether two given identifiers are the same
     */
    private boolean isSame(OrganisationIdentifier identifier, Identifier1 id) {
        if (id != null && identifier != null && id.getId() != null && identifier.getId() != null && id.getScheme() != null && id.getScheme().getValue() != null && identifier.getScheme() != null) {
            return id.getId().toString().equals(identifier.getId()) && id.getScheme().getValue().equals(identifier.getScheme());
        }

        return false;
    }
}