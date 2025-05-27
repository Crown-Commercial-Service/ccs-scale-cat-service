package uk.gov.crowncommercial.dts.scale.cat.start;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.crowncommercial.dts.scale.cat.config.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.search.SearchProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.*;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentScoreExportService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.GCloudAssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.service.documentupload.DocumentUploadService;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.ProjectPackageService;
import uk.gov.crowncommercial.dts.scale.cat.service.scheduler.ProjectsCSVGenerationScheduledTask;
import uk.gov.crowncommercial.dts.scale.cat.service.scheduler.ProjectsToOpenSearchScheduledTask;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;
import uk.gov.service.notify.NotificationClient;

@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
public class AppStartTests {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AccessDeniedResponseDecorator accessDeniedResponseDecorator;

    @MockitoBean
    JaggaerTokenResponseConverter jaggaerTokenResponseConverter;

    @MockitoBean
    UnauthorizedResponseDecorator unauthorizedResponseDecorator;

    @MockitoBean
    AssessmentService assessmentService;

    @MockitoBean
    AwardService awardService;

    @MockitoBean
    ProcurementEventService procurementEventService;

    @MockitoBean
    CriteriaService criteriaService;

    @MockitoBean
    DocumentTemplateService documentTemplateService;

    @MockitoBean
    AssessmentScoreExportService assessmentScoreExportService;

    @MockitoBean
    GCloudAssessmentService gCloudAssessmentService;

    @MockitoBean
    AgreementsService agreementsService;

    @MockitoBean
    EventTransitionService eventTransitionService;

    @MockitoBean
    DocGenService docGenService;

    @MockitoBean
    TendersAPIModelUtils tendersAPIModelUtils;

    @MockitoBean
    ApplicationFlagsConfig applicationFlagsConfig;

    @MockitoBean
    JourneyService journeyService;

    @MockitoBean
    MessageService messageService;

    @MockitoBean
    ProjectPackageService projectPackageService;

    @MockitoBean
    ProcurementProjectService procurementProjectService;

    @MockitoBean
    QuestionAndAnswerService questionAndAnswerService;

    @MockitoBean
    SupplierService supplierService;

    @MockitoBean
    ProfileManagementService profileManagementService;

    @MockitoBean
    JaggaerService jaggaerService;

    @MockitoBean
    ConclaveService conclaveService;

    @MockitoBean
    DocumentUploadService documentUploadService;

    @MockitoBean
    NotificationService notificationService;

    @MockitoBean
    UserProfileService userProfileService;

    @MockitoBean
    ProjectsCSVGenerationScheduledTask projectsCSVGenerationScheduledTask;

    @MockitoBean
    ProjectsToOpenSearchScheduledTask projectsToOpenSearchScheduledTask;

    @MockitoBean
    NotificationClient notificationClient;

    @MockitoBean
    RestHighLevelClient opensearchClient;

    @MockitoBean
    TendersS3ClientConfig tendersS3ClientConfig;

    @MockitoBean
    SearchProjectRepo searchProjectRepo;

    @Test
    public void testAppStarts() {
    }
}