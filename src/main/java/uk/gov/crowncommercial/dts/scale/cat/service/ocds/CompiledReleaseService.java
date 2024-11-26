package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotSupplier;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Populates release related information regarding a given project
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseService{
    private final AgreementsService agreementsService;
    private final ModelMapper modelMapper;

    private final ConclaveService conclaveService;

    /**
     * Populates core project level information
     */
    public MapperResponse populate(Record1 record, ProjectQuery query) {
        Release release = OcdsHelper.getRelease(record);
        ProcurementProject pp = query.getProject();

        if (release != null && pp != null) {
            // Validate the project has been published before we do anything
            ensureProjectPublished(pp);

            // Now work through and populate the data
            if (pp.getProjectName() != null) {
                release.setTitle(pp.getProjectName());
            }

            release.setLanguage(Constants.MAPPERS_RELEASE_LANG);
            release.setTag(ReleaseTag.TENDER);
            release.setInitiationType(InitiationType.TENDER);

            ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);

            if (pe != null) {
                if (pe.getOcdsAuthorityName() != null && pe.getOcidPrefix() != null && pe.getId() != null) {
                    release.setOcid(pe.getOcdsAuthorityName() + "-" + pe.getOcidPrefix() + "-" + pe.getId());
                }

                if (pe.getPublishDate() != null) {
                    release.setDate(OffsetDateTime.ofInstant(pe.getPublishDate(), ZoneId.systemDefault()));
                }

                if (pe.getProcurementTemplatePayload() != null && pe.getProcurementTemplatePayload().getCriteria() != null) {
                    release.setDescription(EventsHelper.getData(Constants.MAPPERS_RELEASE_DESC_CRITERIA, Constants.MAPPERS_RELEASE_DESC_GROUP, Constants.MAPPERS_RELEASE_DESC_QUESTION, pe.getProcurementTemplatePayload().getCriteria()));
                }
            }
        }

        // Job done, return the model
        return new MapperResponse(record);
    }

    /**
     * Checks and validates that the project has been published, and errors if not
     */
    private void ensureProjectPublished(ProcurementProject pp) {
        if (EventsHelper.getFirstPublishedEvent(pp) == null){
            throw new ValidationException("Project is not published");
        }
    }

    /**
     * Populates party related information for the project
     */
    public MapperResponse populateParties(Record1 re, ProjectQuery pq) {
        Release release = OcdsHelper.getRelease(re);
        CompletableFuture cf = null;

        if (release != null) {
            release.setParties(new ArrayList<>());
            ProcurementProject pp = pq.getProject();

            if (pp != null && pp.getCaNumber() != null && pp.getLotNumber() != null) {
                cf = CompletableFuture.runAsync(() -> {
                    Collection<LotSupplier> suppliers = agreementsService.getLotSuppliers(pp.getCaNumber(), pp.getLotNumber());

                    if (suppliers != null) {
                        List<Organization1> parties = suppliers.stream().map(OcdsConverter::convertSupplierToOrg).toList();
                        release.getParties().addAll(parties);
                    }
                });
            }
        }

        return new MapperResponse(re, cf);
    }

    /**
     * Populate information related to the buyer for the project
     */
    public MapperResponse populateBuyer(Record1 record, ProjectQuery pq) {
        ProcurementProject pp = pq.getProject();

        if (pp != null && pp.getOrganisationMapping() != null) {
            OrganisationMapping om = pp.getOrganisationMapping();
            Release release = OcdsHelper.getRelease(record);

            if (release != null && om.getOrganisationId() != null) {
                CompletableFuture cf = CompletableFuture.runAsync(() -> {
                    Optional<OrganisationProfileResponseInfo> optOrgProfile = conclaveService.getOrganisationIdentity(om.getOrganisationId());

                    if (optOrgProfile.isPresent()) {
                        OrganisationProfileResponseInfo orgProfile = optOrgProfile.get();

                        OrganizationReference1 orgRef = modelMapper.map(orgProfile, OrganizationReference1.class);
                        Organization1 buyerParty = modelMapper.map(orgProfile, Organization1.class);

                        release.setBuyer(orgRef);
                        release.addPartiesItem(buyerParty);
                    }
                });

                return new MapperResponse(record, cf);
            }
        }

        return new MapperResponse(record);
    }
}