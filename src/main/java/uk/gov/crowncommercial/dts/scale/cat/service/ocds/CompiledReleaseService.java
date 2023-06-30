package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotSupplier;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Organization;
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
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompiledReleaseService{
    private final JaggaerService jaggaerService;
    private final AgreementsService agreementsService;

    private final ConclaveService conclaveService;

    public MapperResponse populate(Record1 record, ProjectQuery query) {
        log.debug("populating basic details");
        Release release = OcdsHelper.getRelease(record);
        ProcurementProject pp = query.getProject();
        release.setTitle(pp.getProjectName());
        release.setLanguage("en");
        release.setOcid("ocid");

        ProcurementEvent pe = EventsHelper.getFirstPublishedEvent(pp);
        if(null != pe) {
            release.setDate(OffsetDateTime.ofInstant(pe.getPublishDate(), ZoneId.systemDefault()));
            release.setDescription(EventsHelper.getData("Group 3","Summary of work", "Question 1", pe.getProcurementTemplatePayload().getCriteria()));
        }

        CompletableFuture cf = CompletableFuture.runAsync(()-> {
            ExportRfxResponse rfxResponse = jaggaerService.getSingleRfx(pe.getExternalEventId());
            release.setDescription(rfxResponse.getRfxSetting().getStatus());
        });
        release.setTag(ReleaseTag.TENDER); // TODO get the status from Jaggaer ?
        release.setInitiationType(InitiationType.TENDER);
        return new MapperResponse(record, cf);
    }

    public MapperResponse populateParties(Record1 re, ProjectQuery pq) {
        log.debug("populating parties");
        Release release = OcdsHelper.getRelease(re);
        release.setParties(new ArrayList<>());
        CompletableFuture cf = CompletableFuture.runAsync(()-> {
            ProcurementProject pp = pq.getProject();
            Collection<LotSupplier> suppliers = agreementsService.getLotSuppliers(pp.getCaNumber(), pp.getLotNumber());
            List<Organization1> parties = suppliers.stream()
                    .limit(2) // TODO  remove this artificial limit
                    .map(OcdsConverter::convertSupplierToOrg).toList();
            release.getParties().addAll(parties);
        });

        return new MapperResponse(re, cf);
    }

    public MapperResponse populateBuyer(Record1 record, ProjectQuery pq) {
        log.debug("populating Buyer");
        // conclaveService.getOrganisationIdentity();
        ProcurementProject pp = pq.getProject();
        OrganisationMapping om =  pp.getOrganisationMapping();

        if(null != om) {
            OrganizationReference1 orgRef = new OrganizationReference1();
            Organization1 buyerParty = new Organization1();
            orgRef.setId(om.getCasOrganisationId());
            buyerParty.setId(om.getCasOrganisationId());
            Release release = OcdsHelper.getRelease(record);
            release.setBuyer(orgRef);
            release.addPartiesItem(buyerParty);
        }

        return new MapperResponse(record);
    }

    public MapperResponse populateContracts(Record1 record, ProjectQuery query) {
        log.warn("populating Contracts not implemented yet");
        Release release = OcdsHelper.getRelease(record);
        return new MapperResponse(record);
    }
}
