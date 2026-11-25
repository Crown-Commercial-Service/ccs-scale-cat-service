package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPackage;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPackageSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Record1;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.RecordPackageAllOfPublisher;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectPackageService {
    private final ProjectRecordService projectRecordService;
    private final NonOCDSProjectService nonOCDSService;
    private final RetryableTendersDBDelegate dbDelegate;

    /**
     * Looks up a given project and returns an instance of ProjectPackageSummary
     */
    public ProjectPackageSummary lookupProject(Integer projectId) {
        // First, grab the project
        ProcurementProject project = getProjectEntity(projectId);

        // Now map the data to our output model - no need to null check here, it's been handled above if the data wasn't found
        ProjectPackageSummary model = new ProjectPackageSummary();
        model.setProjectId(project.getId());
        model.setProjectName(project.getProjectName());
        model.setAgreementId(project.getCaNumber());
        model.setLotId(project.getLotNumber());

        // Job done, return our model
        return model;
    }

    public ProjectPackage getProjectPackage(Integer procId, String principal, List<String> sections) {
        log.error("TESTING in getProjectPackage");    // TODO - BM
        ProjectRequest query = new ProjectRequest();
        query.setPrincipal(principal);
        query.setProcId(procId);
        log.error("TESTING in getProjectPackage - 1");    // TODO - BM
        query.setProject(getProjectEntity(procId));
        query.setSections(sections);
        log.error("TESTING in getProjectPackage - 2");    // TODO - BM
        return getProjectSummary(query);
    }

    private ProcurementProject getProjectEntity(Integer procId) {
        log.error("TESTING in getProjectEntity - 1");    // TODO - BM
        Optional<ProcurementProject> optProject = dbDelegate.findProcurementProjectById(procId);
        log.error("TESTING in getProjectEntity - 2");    // TODO - BM
        if(optProject.isPresent()){
            log.error("TESTING in getProjectEntity - 3");    // TODO - BM
            return optProject.get();
        }else{
            log.error("TESTING in getProjectEntity - 4");    // TODO - BM
            log.error("TESTING Procurement not found for the ID + " + procId);    // TODO - BM
            throw new ResourceNotFoundException("Procurement not found for the ID" + procId);
        }
    }

    private ProjectPackage getProjectSummary(ProjectQuery query){
        log.error("TESTING in getProjectSummary - 1");    // TODO - BM
        ProjectPackage procPackage = populateGeneralInfo(query);
        log.error("TESTING in getProjectSummary - 2");    // TODO - BM
        nonOCDSService.populateCancelled(procPackage, query.getProject());
        log.error("TESTING in getProjectSummary - 3");    // TODO - BM
        return populateRecords(query, procPackage);
    }

    private ProjectPackage populateGeneralInfo(ProjectQuery query){
        log.error("TESTING in populateGeneralInfo - 1");    // TODO - BM
        ProjectPackage projectPackage = new ProjectPackage();
        log.error("TESTING in populateGeneralInfo - 2");    // TODO - BM
        projectPackage.setPublisher(getPublisherInfo());
        log.error("TESTING in populateGeneralInfo - 3");    // TODO - BM
        projectPackage.setUri(getUri());
        log.error("TESTING in populateGeneralInfo - 4");    // TODO - BM
        projectPackage.setVersion("1.0");
        log.error("TESTING in populateGeneralInfo - 5");    // TODO - BM
        projectPackage.setPublicationPolicy(getPublicationPolicy());
        log.error("TESTING in populateGeneralInfo - 6");    // TODO - BM
        return projectPackage;
    }

    private ProjectPackage populateRecords(ProjectQuery query, ProjectPackage projectPackage){
        log.error("TESTING in populateRecords - 1");    // TODO - BM
        Record1 record = new Record1();
        projectPackage.addRecordsItem(projectRecordService.populate(record, query));
        log.error("TESTING in populateRecords - 2");    // TODO - BM
        return projectPackage;
    }

    @SneakyThrows
    private Object getPublicationPolicy() {
        return new URI("https://www.gca.gov.uk/agreements/publicationPolicy");
    }

    @SneakyThrows
    private URI getUri(){
        return new URI("https://www.gca.gov.uk/tenders/projects/");
    }

    private RecordPackageAllOfPublisher getPublisherInfo() {
        RecordPackageAllOfPublisher publisher = new RecordPackageAllOfPublisher();
        publisher.setName("Government Commercial Agency");
        publisher.setUri("https://www.gca.gov.uk/");
        return publisher;
    }
}
