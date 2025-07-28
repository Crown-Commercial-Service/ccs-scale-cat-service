package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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
        ProjectRequest query = new ProjectRequest();
        query.setPrincipal(principal);
        query.setProcId(procId);
        query.setProject(getProjectEntity(procId));
        query.setSections(sections);
        return getProjectSummary(query);
    }

    private ProcurementProject getProjectEntity(Integer procId) {
        Optional<ProcurementProject> optProject = dbDelegate.findProcurementProjectById(procId);
        if(optProject.isPresent()){
            return optProject.get();
        }else{
            throw new ResourceNotFoundException("Procurement not found for the ID" + procId);
        }
    }

    private ProjectPackage getProjectSummary(ProjectQuery query){
        ProjectPackage procPackage = populateGeneralInfo(query);
        nonOCDSService.populateCancelled(procPackage, query.getProject());
        return populateRecords(query, procPackage);
    }

    private ProjectPackage populateGeneralInfo(ProjectQuery query){
        ProjectPackage projectPackage = new ProjectPackage();
        projectPackage.setPublisher(getPublisherInfo());
        projectPackage.setUri(getUri());
        projectPackage.setVersion("1.0");
        projectPackage.setPublicationPolicy(getPublicationPolicy());
        return projectPackage;
    }

    private ProjectPackage populateRecords(ProjectQuery query, ProjectPackage projectPackage){
        Record1 record = new Record1();
        projectPackage.addRecordsItem(projectRecordService.populate(record, query));
        return projectPackage;
    }

    @SneakyThrows
    private Object getPublicationPolicy() {
        return new URI("https://www.crowncommercial.gov.uk/agreements/publicationPolicy");
    }

    @SneakyThrows
    private URI getUri(){
        return new URI("https://www.crowncommercial.gov.uk/tenders/projects/");
    }

    private RecordPackageAllOfPublisher getPublisherInfo() {
        RecordPackageAllOfPublisher publisher = new RecordPackageAllOfPublisher();
        publisher.setName("Crown Commercial Services");
        publisher.setUri("https://www.crowncommercial.gov.uk/");
        return publisher;
    }
}
