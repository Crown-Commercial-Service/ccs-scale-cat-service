package uk.gov.crowncommercial.dts.scale.cat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.services.s3.S3Client;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.AWSS3Service;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationIdentifier;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProjectUserMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.CreateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.repo.search.SearchProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.util.TestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcurementProjectServiceTest {

  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final String CONCLAVE_ORG_ID = "654891633619851306";
  private static final String CONCLAVE_ORG_SCHEME = "US-DUNS";
  private static final String CONCLAVE_ORG_SCHEME_ID = "123456789";
  private static final String CONCLAVE_ORG_LEGAL_ID = CONCLAVE_ORG_SCHEME + '-' + CONCLAVE_ORG_SCHEME_ID;
  private static final String CONCLAVE_ORG_NAME = "ACME Products Ltd";
  private static final String JAGGAER_USER_ID = "12345";
  private static final String JAGGAER_BUYER_COMPANY_ID = "2345678";
  private static final String TENDER_CODE = "tender_0001";
  private static final String TENDER_REF_CODE = "project_0001";
  private static final String CA_NUMBER = "RM1234";
  private static final String LOT_NUMBER = "Lot1a";
  private static final String PROJ_NAME = CA_NUMBER + '-' + LOT_NUMBER + '-' + CONCLAVE_ORG_NAME;
  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final String EVENT_NAME = "Test Event";
  private static final Integer EVENT_ID = 1;
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String UPDATED_PROJECT_NAME = "New name";
  private static final CreateEvent CREATE_EVENT = new CreateEvent();
  private static final OrganisationMapping ORG_MAPPING = OrganisationMapping.builder()
          .externalOrganisationId(Integer.valueOf(JAGGAER_BUYER_COMPANY_ID))
          .organisationId(CONCLAVE_ORG_ID).build();
  private static final AgreementDetails AGREEMENT_DETAILS = new AgreementDetails();
  private static final Optional<SubUsers.SubUser> JAGGAER_USER =
          Optional.of(SubUsers.SubUser.builder().userId(JAGGAER_USER_ID)
                  .ssoCodeData(SSOCodeData.builder()
                          .ssoCode(Set.of(SSOCodeData.SSOCode.builder().ssoUserLogin(PRINCIPAL).build()))
                          .build())
                  .email(PRINCIPAL).phoneNumber("123456789").build());
  private static final CompanyInfo BUYER_COMPANY_INFO = CompanyInfo.builder().bravoId(JAGGAER_BUYER_COMPANY_ID).build();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient jaggaerWebClient;

  @Mock
  private UserProfileService userProfileService;
  @Mock
  private ConclaveService conclaveService;
  @Mock
  private RetryableTendersDBDelegate retryableTendersDBDelegate;
  @Mock
  private ProcurementEventService procurementEventService;
  @Mock
  private WebclientWrapper webclientWrapper;
  @Mock
  private AgreementsService agreementsService;
  @Mock
  private EventTransitionService eventTransitionService;
  @InjectMocks
  private ProcurementProjectService procurementProjectService;
  @Mock
  private SearchProjectRepo searchProjectRepo;
  @Mock
  private JaggaerAPIConfig jaggaerAPIConfig;
  @Mock
  private ModelMapper modelMapper;
  @Mock
  private S3Client tendersS3Client;
  @Mock
  private AWSS3Service tendersS3Service;
  @Mock
  private JaggaerService jaggaerService;

  @BeforeAll
  static void beforeAll() {
    AGREEMENT_DETAILS.setAgreementId(CA_NUMBER);
    AGREEMENT_DETAILS.setLotId(LOT_NUMBER);
  }

  @BeforeEach
  void setupConfigMock() {
    var createProjectMap = Map.of("endpoint", "/createProject", "defaultTitleFormat", "%s-%s-%s");
    var getProjectMap = Map.of("endpoint", "/getProject");
    lenient().when(jaggaerAPIConfig.getCreateProject()).thenReturn(createProjectMap);
    lenient().when(jaggaerAPIConfig.getGetProject()).thenReturn(getProjectMap);
    lenient().when(jaggaerAPIConfig.getTimeoutDuration()).thenReturn(5);
  }

  @Test
  void testCreateFromAgreementDetailsThrowsJaggaerApplicationException() throws Exception {
    var jaggaerErrorResponse = new CreateUpdateProjectResponse();
    jaggaerErrorResponse.setReturnCode(1);
    jaggaerErrorResponse.setReturnMessage("NOT OK");

    // Mock all required dependencies to avoid NullPointerException
    lenient().when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    lenient().when(userProfileService.resolveBuyerUserCompany(PRINCIPAL)).thenReturn(BUYER_COMPANY_INFO);
    lenient().when(conclaveService.getOrganisationIdentity(CONCLAVE_ORG_ID))
            .thenReturn(Optional.of(new OrganisationProfileResponseInfo()
                    .identifier(new OrganisationIdentifier().legalName(CONCLAVE_ORG_NAME)
                            .scheme(CONCLAVE_ORG_SCHEME).id(CONCLAVE_ORG_SCHEME_ID))
                    .detail(new OrganisationDetail().organisationId(CONCLAVE_ORG_ID))));
    lenient().when(conclaveService.getOrganisationIdentifer(any(OrganisationProfileResponseInfo.class)))
            .thenReturn(CONCLAVE_ORG_LEGAL_ID);
    lenient().when(retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(CONCLAVE_ORG_LEGAL_ID))
            .thenReturn(Optional.of(ORG_MAPPING));

    // Mock the WebClient chain using deep stubs
    when(jaggaerWebClient.post()
            .uri(anyString())
            .bodyValue(any(CreateUpdateProject.class))
            .retrieve()
            .bodyToMono(CreateUpdateProjectResponse.class)
            .block(any(Duration.class)))
            .thenReturn(jaggaerErrorResponse);

    var jagEx = assertThrows(JaggaerApplicationException.class, () ->
            procurementProjectService.createFromAgreementDetails(AGREEMENT_DETAILS, PRINCIPAL, CONCLAVE_ORG_ID));
    assertEquals("Jaggaer application exception, Code: [1], Message: [NOT OK]", jagEx.getMessage());
  }

  @Test
  void dashboardStatusShouldBeReturnForTheEventsForProjects() {
    var event = new ProcurementEvent();
    event.setId(EVENT_ID);
    event.setExternalEventId("rfq_0001");
    event.setEventType("RFI");
    Set<ProcurementEvent> events = Set.of(event);

    var project = ProcurementProject.builder()
            .id(PROC_PROJECT_ID)
            .procurementEvents(events)
            .build();

    // Create a ProjectUserMapping to match what the service expects
    var projectUserMapping = new ProjectUserMapping();
    projectUserMapping.setProject(project);

    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(retryableTendersDBDelegate.findProjectUserMappingByUserId(anyString(), any(), any(), any(Pageable.class)))
            .thenReturn(List.of(projectUserMapping));
    
    // Mock the Jaggaer service to return some RFx data
    when(jaggaerService.searchRFx(any(Set.class))).thenReturn(Set.of());

    var response = procurementProjectService.getProjects(PRINCIPAL, null, null, "0", "20");

    assertNotNull(response);
    assertEquals(1, response.size());
    // Convert to List to access by index, or use iterator
    var firstProject = response.iterator().next();
    assertNotNull(firstProject);
  }

  @Test
  void testGetProjectEventTypes() throws JsonProcessingException {
    var procurementProject = new ProcurementProject();
    procurementProject.setId(PROC_PROJECT_ID);
    procurementProject.setCaNumber(CA_NUMBER);
    procurementProject.setLotNumber(LOT_NUMBER);

    when(retryableTendersDBDelegate.findProcurementProjectById(PROC_PROJECT_ID))
            .thenReturn(Optional.of(procurementProject));
    
    // Mock the agreements service to return proper event types
    var lotEventTypes = TestUtils.getLotEventTypes();
    when(agreementsService.getLotEventTypes(CA_NUMBER, LOT_NUMBER))
            .thenReturn(lotEventTypes);

    var projectEventTypes = procurementProjectService.getProjectEventTypes(PROC_PROJECT_ID);
    
    // Verify we get the expected event types
    assertNotNull(projectEventTypes);
    assertFalse(projectEventTypes.isEmpty());
    assertEquals(lotEventTypes.size(), projectEventTypes.size());
  }

  @Test
  void testGetProjectEventTypesThrowsResourceNotFoundApplicationException() {
    when(retryableTendersDBDelegate.findProcurementProjectById(PROC_PROJECT_ID))
            .thenReturn(Optional.empty());

    var ex = assertThrows(ResourceNotFoundException.class,
            () -> procurementProjectService.getProjectEventTypes(PROC_PROJECT_ID));
    assertEquals("Project '1' not found", ex.getMessage());
  }

  @Test
  void convertCsvToOds_ShouldThrowOnNullInput() {
    // Expect NullPointerException if input is null
    assertThrows(NullPointerException.class, () -> procurementProjectService.convertCsvToOds(null));
  }

  @Test
  void convertCsvToOds_ShouldReturnValidOdsStream() throws Exception {
    // Given: a simple CSV content
    String csvContent = "Name,Email,Age\nJohn,john@example.com,30\nJane,jane@example.com,25";
    InputStream csvInputStream = new ByteArrayInputStream(csvContent.getBytes());

    // When: converting CSV to ODS
    InputStream odsStream = procurementProjectService.convertCsvToOds(csvInputStream);

    // Then: result should not be null
    assertNotNull(odsStream, "ODS output stream should not be null");

    // Optionally: check that the stream has content
    assertTrue(odsStream.available() > 0, "ODS stream should contain data");

    // Optionally: check that it starts with the ODS signature (ODS files are ZIPs)
    byte[] signature = new byte[4];
    odsStream.read(signature);
    String zipSignature = new String(signature, "UTF-8");
    assertEquals("PK\u0003\u0004", zipSignature, "ODS file should start with ZIP signature");

    odsStream.close();
  }

  @Test
  void convertCsvToXlsx_ShouldThrowOnNullInput() {
    // Expect NullPointerException if input is null
    assertThrows(NullPointerException.class, () -> procurementProjectService.convertCsvToXlsx(null));
  }

  @Test
  void convertCsvToXlsx_ShouldReturnValidXlsxStream() throws Exception {
    // Given: a simple CSV content
    String csvContent = "Name,Email,Age\nJohn,john@example.com,30\nJane,jane@example.com,25";
    InputStream csvInputStream = new ByteArrayInputStream(csvContent.getBytes());

    // When: converting CSV to XLSX
    InputStream xlsxStream = procurementProjectService.convertCsvToXlsx(csvInputStream);

    // Then: the output stream should not be null
    assertNotNull(xlsxStream, "XLSX output stream should not be null");

    // Check that it can be read as a valid Excel workbook
    Workbook workbook = WorkbookFactory.create(xlsxStream);
    assertEquals(1, workbook.getNumberOfSheets(), "Workbook should contain one sheet");

    // Check first row content
    var sheet = workbook.getSheetAt(0);
    assertEquals("Name", sheet.getRow(0).getCell(0).getStringCellValue());
    assertEquals("Email", sheet.getRow(0).getCell(1).getStringCellValue());
    assertEquals("Age", sheet.getRow(0).getCell(2).getStringCellValue());

    // Check second row content
    assertEquals("John", sheet.getRow(1).getCell(0).getStringCellValue());
    assertEquals("john@example.com", sheet.getRow(1).getCell(1).getStringCellValue());
    assertEquals("30", sheet.getRow(1).getCell(2).getStringCellValue());

    workbook.close();
    xlsxStream.close();
  }

  @Test
  void convertCsvToXlsx_ShouldHandleCommasInsideQuotes() throws Exception {
    // Arrange: CSV input with commas inside quotes
    String csvData = """
                Name,Email,Notes
                "John Doe","john@example.com","Hello, world, this has commas"
                "Jane Smith","jane@example.com","Another, value, with, commas"
                """;

    InputStream csvInputStream = new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8));

    // Act
    InputStream xlsxInputStream = procurementProjectService.convertCsvToXlsx(csvInputStream);

    // Read back with Apache POI
    try (Workbook workbook = WorkbookFactory.create(xlsxInputStream)) {
      Sheet sheet = workbook.getSheetAt(0);

      // Header row
      Row headerRow = sheet.getRow(0);
      assertEquals("Name", headerRow.getCell(0).getStringCellValue());
      assertEquals("Email", headerRow.getCell(1).getStringCellValue());
      assertEquals("Notes", headerRow.getCell(2).getStringCellValue());

      // First record
      Row row1 = sheet.getRow(1);
      assertEquals("John Doe", row1.getCell(0).getStringCellValue());
      assertEquals("john@example.com", row1.getCell(1).getStringCellValue());
      assertEquals("Hello, world, this has commas", row1.getCell(2).getStringCellValue());

      // Second record
      Row row2 = sheet.getRow(2);
      assertEquals("Jane Smith", row2.getCell(0).getStringCellValue());
      assertEquals("jane@example.com", row2.getCell(1).getStringCellValue());
      assertEquals("Another, value, with, commas", row2.getCell(2).getStringCellValue());
    }
  }
}
