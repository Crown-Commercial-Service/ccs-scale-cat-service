package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotSupplier;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Organization;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Project;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseService{
    private final AgreementsService agreementsService;
    private final ModelMapper modelMapper;

    private final ConclaveService conclaveService;

    public MapperResponse populate(Record1 record, ProjectQuery query) {
        log.debug("populating basic details");
        Release release = OcdsHelper.getRelease(record);
        ProcurementProject pp = query.getProject();
        release.setTitle(pp.getProjectName());
        release.setLanguage("en");
        ensureProjectPublished(pp);
        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        release.setOcid(pe.getOcdsAuthorityName() + "-"+ pe.getOcidPrefix()+ "-" + pe.getId());
        if(null != pe) {
            release.setDate(OffsetDateTime.ofInstant(pe.getPublishDate(), ZoneId.systemDefault()));
            release.setDescription(EventsHelper.getData("Criterion 3", "Group 3", "Question 1", pe.getProcurementTemplatePayload().getCriteria()));
        }
        release.setTag(ReleaseTag.TENDER); // TODO get the status from Jaggaer ?
        release.setInitiationType(InitiationType.TENDER);

        return new MapperResponse(record);
    }

    private void ensureProjectPublished(ProcurementProject pp) {
        if(null == EventsHelper.getFirstPublishedEvent(pp)){
            throw new ValidationException("Project is not published");
        }
    }

    public MapperResponse populateParties(Record1 re, ProjectQuery pq) {
        log.debug("populating parties");
        Release release = OcdsHelper.getRelease(re);
        release.setParties(new ArrayList<>());
        ProcurementProject pp = pq.getProject();

        CompletableFuture cf = CompletableFuture.runAsync(()-> {
            Collection<LotSupplier> suppliers = agreementsService.getLotSuppliers(pp.getCaNumber(), pp.getLotNumber());
            List<Organization1> parties = suppliers.stream()
                    .map(OcdsConverter::convertSupplierToOrg).toList();
            release.getParties().addAll(parties);
        });

        return new MapperResponse(re, cf);
    }

    public MapperResponse populateBuyer(Record1 record, ProjectQuery pq) {
        log.debug("populating Buyer");

        ProcurementProject pp = pq.getProject();
        OrganisationMapping om =  pp.getOrganisationMapping();
        Release release = OcdsHelper.getRelease(record);
        if(null != om) {
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

        return new MapperResponse(record);
    }

    public MapperResponse populateContracts(Record1 record, ProjectQuery query) {
        log.warn("populating Contracts not implemented yet");
        Release release = OcdsHelper.getRelease(record);
        return new MapperResponse(record);
    }
}
