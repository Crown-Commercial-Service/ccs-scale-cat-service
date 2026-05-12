package uk.gov.crowncommercial.dts.scale.cat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.odftoolkit.simple.TextDocument;
import org.odftoolkit.simple.common.navigation.TextNavigation;
import org.odftoolkit.simple.common.navigation.TextSelection;
import org.odftoolkit.simple.table.Cell;
import org.odftoolkit.simple.table.Row;
import org.odftoolkit.simple.table.Table;
import org.odftoolkit.simple.text.list.ListItem;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.DocGenValueException;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.ByteArrayMultipartFile;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType.SUPPLIER;

/**
 * Generates an ODT text document based on a template and data sources as provided via Tenders DB
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocGenService {
  public static final String PLACEHOLDER_ERROR = "";
  public static final String PLACEHOLDER_UNKNOWN = "Not Specified";
  public static final String PLACEHOLDER_EMPTY = "";
  public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
  public static final DateTimeFormatter ONLY_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
  public static final String PERIOD_FMT = "%d years, %d months, %d days";
  public static final String DOCUMENT_DESC_JOINER = " pro forma for tender: ";
  public static final String DB_PLACEHOLDER_PROJECTS = "project";
  public static final String DB_PLACEHOLDER_METHOD_PREFIX = "get";
  public static final String DELIMITER = ",";
  public static final String UNSURE_FIXED_OUTPUT = "The buyer is unsure whether it will be a new or a replacement product or service.";
  public static final String CELL_LINE_REQUIRED = "1";
  public static final String UNSUPPORTED_BEAN_NAMES = "DocumentValueAdaptorPricingScheduleFileName,DocumentValueAdaptorTCFileNames,DocumentValueAdaptorAssessmentFileNames";

  public static final String REPLACEMENT_CONDITIONAL = "Conditional";
  public static final String REPLACEMENT_YES = "Yes";
  public static final String REPLACEMENT_NO = "No";
  public static final String REPLACEMENT_CONDITIONAL_STEP = "Step_18 Conditional";
  public static final String REPLACEMENT_BUDGET_MIN = "«Project_Budget_Min_Conditional»";
  public static final String REPLACEMENT_BUDGET_MAX = "«Project_Budget_Max»";
  public static final String REPLACEMENT_BUDGET_TERM = "Conditional Insert Project Term Budget";
  public static final String REPLACEMENT_PROJECT_BUDGET = "Project_Budget";
  public static final String REPLACEMENT_PROJECT_BUDGET_TERM = "Project Term Budget";
  public static final String REPLACEMENT_DOC_FILENAME = "«Upload_document_filename_#n»";
  public static final String REPLACEMENT_INCUMBENT_NAME = "«Project_Incumbent_Yes_No_Supplier_Name_Step_22»";
  public static final String REPLACEMENT_DATE_INSERTION = "«Insert Time, Date, Month, Year of #1»";
  public static final String REPLACEMENT_PRODUCT_REPLACEMENT = "Replacement products or services";
  public static final String REPLACEMENT_PRODUCT_EXPANDED = "Expanded products or services";
  public static final String REPLACEMENT_PRODUCT_NEW = "New products or services";
  public static final String REPLACEMENT_UNSURE = "Not sure";

  private static final String CURRENT_STAGE_TITLE = "CURRENT_STAGE";
  private static final String TOTAL_STAGES_TITLE = "TOTAL_STAGES";
  private static final String STAGE_DESCRIPTION_TITLE = "STAGE_DESCRIPTION";
  private static final String OCDS = "OCDS";
  private static final String NON_OCDS = "nonOCDS";
  private static final String MULTI_STAGE_JSON_PATH = "$.criteria[?(@.id == 'Criterion 2')].requirementGroups[?(@.OCDS.id == '%s')]";
  private static final String ID = "id";
  private static final String PAYLOAD_TAG = "payload";
  private static final String STAGE_NUMBER_TAG = "stageNumber";
  private static final String STAGE_DESCRIPTION_TAG = "stageDescription";
  private static final String CRITERION2 = "Criterion 2";
  private static final String CRITERIA = "criteria";
  private static final String REQUIREMENT_GROUPS = "requirementGroups";
  private static final String REQUIREMENTS = "requirements";
  private static final String ORDER = "order";
  private static final String TITLE = "title";
  private static final String OPTIONS = "options";
  private static final String VALUE = "value";
  private static final String SELECT = "select";
    private static final String TOTAL_STAGES_TAG = "totalStages";

    private final ApplicationContext applicationContext;
  private final ValidationService validationService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final ObjectMapper objectMapper;
  private final JaggaerService jaggaerService;
  private final DocumentTemplateResourceService documentTemplateResourceService;
  private final TableGroupGenerator tableGroupGenerator;
  private final StageService stageService;

  private static final String ATTACHMENT_4 = "Attachment 4 Responses to Stage 2 assessment criteria";
  private static final Predicate<DocumentTemplate> IS_TEMPLATE_4 =
          template -> template.getTemplateUrl().contains(ATTACHMENT_4);

  /**
   * Trigger the generation and upload of all documents for a given event
   */
  public void generateAndUploadDocuments(final Integer projectId, final String eventId, boolean isLastStageEvent) {
    // Start by validating the event passed into us is good to use
    ProcurementEvent procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId, null);

    if (procurementEvent != null && procurementEvent.getProject() != null) {
      // We've got the event, now grab the data we need from it and then use that to fetch the list of documents needed for it
      String eventType = procurementEvent.getEventType(),
              caNumber = procurementEvent.getProject().getCaNumber(),
              lotNum = procurementEvent.getProject().getLotNumber();
      Integer templateId = procurementEvent.getTemplateId();

      if (eventType != null && caNumber != null && lotNum != null) {
        Set<DocumentTemplate> docTemplates = retryableTendersDBDelegate.findByEventTypeAndCommercialAgreementNumberAndLotNumberAndTemplateGroup(eventType, caNumber, lotNum, templateId);

        if (docTemplates != null && !docTemplates.isEmpty()) {
          Set<DocumentTemplate> filteredDocTemplates = filterTemplates(isLastStageEvent, docTemplates);
          // Now we have the list of documents needed - iterate over them and process them
          filteredDocTemplates.forEach(template -> {
            ByteArrayOutputStream document = generateDocument(procurementEvent, template, isLastStageEvent, Boolean.TRUE);

            if (document != null) {
              // Document has been generated, now trigger the upload
              uploadProforma(procurementEvent, document, template);
            }
          });
        }
      }
    }
  }

  private Set<DocumentTemplate> filterTemplates(boolean isLastStageEvent,
                                                Set<DocumentTemplate> templates) {
    return templates.stream()
            .filter(isLastStageEvent ? IS_TEMPLATE_4 : IS_TEMPLATE_4.negate())
            .collect(Collectors.toSet());
  }

  /**
   * Generate a given event's version of a document based on a supplied template
   */
  @SneakyThrows
  @Transactional
  public ByteArrayOutputStream generateDocument(final ProcurementEvent procurementEvent, final DocumentTemplate documentTemplate, final boolean isLastStageEvent, final boolean isPublish) {
    // Start by grabbing the template document we need to work against
    if (documentTemplate != null && documentTemplate.getTemplateUrl() != null && !documentTemplate.getTemplateUrl().isEmpty() && documentTemplate.getDocumentTemplateSources() != null) {
      Resource templateResource = documentTemplateResourceService.getResource(documentTemplate.getTemplateUrl());

      if (templateResource != null) {
        final TextDocument textODT = TextDocument.loadDocument(templateResource.getInputStream());
        final ConcurrentHashMap<String, Object> requestCache = new ConcurrentHashMap<>();

        // Now we have everything we need to begin, so work our way through each template source (i.e. value we need to populate)
        documentTemplate.getDocumentTemplateSources().forEach(templateSource -> {
          // Grab the value for the replacement, and then apply it to our templated source
          try {
            if (templateSource.getTargetType() == TargetType.TABLE_GROUP) {
                if ("MS1".equals(procurementEvent.getEventType())) {
                    handleMultiStageTableGroups(procurementEvent, templateSource, textODT, templateResource);
                } else {
                    // Old standard logic
                    String eventData = isLastStageEvent ? getStage1EventData(procurementEvent) : procurementEvent.getProcurementTemplatePayloadRaw();
                    tableGroupGenerator.fillTableData(eventData, templateSource, textODT);
                }
            } else {
              List<String> dataReplacement = getDataReplacement(procurementEvent, templateSource, requestCache);
              replacePlaceholder(templateSource, dataReplacement, textODT, procurementEvent.getPublishDate() == null ? isPublish : Boolean.TRUE);
            }
          } catch (Exception ex) {
              log.error("Unable to replace document placeholder of '{}' for event ID '{}'", templateSource.getId(), procurementEvent.getEventID(), ex);
          }
        });

        // Our document should now be complete with all placeholders populated - return it
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        textODT.save(outputStream);

        return outputStream;
      }
    }

    // Something has gone wrong that wasn't handled elsewhere if we've reached this point - just return null
    return null;
  }

  private String getStage1EventData(ProcurementEvent currentEvent) {
    return retryableTendersDBDelegate
            .findProcurementEventsByProjectId(currentEvent.getProject().getId())
            .stream()
            .filter(e -> e.getPublishDate() != null
                    && e.getId() != null
                    && e.getId() < currentEvent.getId())
            .min(Comparator.comparing(ProcurementEvent::getId))
            .map(ProcurementEvent::getProcurementTemplatePayloadRaw)
            .orElse(null);
  }

  /**
   * Triggers the upload of a given document into Jaegger
   */
  private void uploadProforma(final ProcurementEvent procurementEvent, final ByteArrayOutputStream documentOutputStream, final DocumentTemplate documentTemplate) {
    if (procurementEvent != null && documentTemplate != null && procurementEvent.getEventID() != null && !procurementEvent.getEventID().isEmpty() && procurementEvent.getEventType() != null && !procurementEvent.getEventType().isEmpty() && documentTemplate.getTemplateUrl() != null && !documentTemplate.getTemplateUrl().isEmpty()) {
      // Start by generating the necessary descriptive information about our file
      String fileName = getFileName(procurementEvent, documentTemplate.getTemplateUrl());
      String fileDescription = procurementEvent.getEventType() + DOCUMENT_DESC_JOINER + procurementEvent.getExternalReferenceId();
      // Now transform the contents we've been passed into a file which we can upload
      if (documentOutputStream != null) {
        ByteArrayMultipartFile multipartFile = new ByteArrayMultipartFile(documentOutputStream.toByteArray(), fileName, Constants.MEDIA_TYPE_ODT.toString());

        // Now finally trigger the upload to Jaegger
        try {
          jaggaerService.eventUploadDocument(procurementEvent, fileName, fileDescription, SUPPLIER, multipartFile);
        } catch (Exception ex) {
            log.error("Error uploading document '{}' for event ID '{}'", fileName, procurementEvent.getEventID(), ex);
        }
      }
    }
  }

  private String getFileName(ProcurementEvent event, String fileName) {

    int dashIndex = fileName.indexOf('-');
    String templateName = dashIndex >= 0
            ? fileName.substring(dashIndex + 1).trim()
            : fileName;

    return Constants.GENERATED_DOCUMENT_FILENAME_FMT.formatted(
            event.getProject().getId(),
            event.getExternalReferenceId(),
            templateName
    );
  }

  /**
   * Builds a list of values for replacement within a document template, based on the data source type being requested
   */
  private List<String> getDataReplacement(final ProcurementEvent event, final DocumentTemplateSource documentTemplateSource, final ConcurrentMap<String, Object> requestCache) {
    if (documentTemplateSource != null && documentTemplateSource.getSourceType() != null) {
      // Populate the value list based on the source type passed to us
      try {
        return switch (documentTemplateSource.getSourceType()) {
          case JSON -> getQuestionFromJSONDataTemplate(event, documentTemplateSource);
          case JAVA -> getValueFromBean(event, documentTemplateSource, requestCache);
          case SQL -> List.of(getValueFromDB(event, documentTemplateSource));
          case STATIC -> List.of(getStaticValueFromDB(documentTemplateSource));
        };
      } catch (Exception ex) {
          log.error("Error in document generation value replacement build for data type '{}' and template ID: '{}'", documentTemplateSource.getSourceType(), documentTemplateSource.getId(), new DocGenValueException(ex));
      }
    }

    // We want to return an empty list if nothing has been returned by this point, so it doesn't cause other issues
    return List.of(PLACEHOLDER_ERROR);
  }

  /**
   * Builds a list of question values which require replacement from a JSON data template source
   */
  private List<String> getQuestionFromJSONDataTemplate(final ProcurementEvent event, final DocumentTemplateSource documentTemplateSource) {
    // We need to work against the raw JSON data for this task, so grab it
    if (event != null && event.getProcurementTemplatePayloadRaw() != null && !event.getProcurementTemplatePayloadRaw().isEmpty()) {
      String eventData = event.getProcurementTemplatePayloadRaw();

      // Now parse the JSON into a list of the values that we need to replace as part of document generation
      try {
        Configuration jsonPathConfig = Configuration.builder().options(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST).jsonProvider(new JacksonJsonProvider(objectMapper)).mappingProvider(new JacksonMappingProvider(objectMapper)).build();
        TypeRef<List<String>> typeRef = new TypeRef<>() {};

        return JsonPath.using(jsonPathConfig).parse(eventData).read(documentTemplateSource.getSourcePath(), typeRef);
      } catch (Exception ex) {
          log.error("Error parsing JSON for document template ID: '{}'", documentTemplateSource.getId(), ex);
      }
    }

    // Something has gone wrong if we're at this point - return an empty list
    return List.of(PLACEHOLDER_ERROR);
  }

  /**
   * Builds a list of question values which require replacement from a Java Bean
   */
  private List<String> getValueFromBean(final ProcurementEvent event, final DocumentTemplateSource documentTemplateSource, final ConcurrentMap<String, Object> requestCache) {
    // We need to work against a given Java Bean for this, so grab its definition
    if (documentTemplateSource != null && documentTemplateSource.getSourcePath() != null && !documentTemplateSource.getSourcePath().isEmpty()) {
      String beanName = documentTemplateSource.getSourcePath();

      try {
        // There are specific names we don't want to deal with here, as they'll always be null. So only carry on if it's not one of these
        if (isBeanNameSupported(beanName)) {
          DocGenValueAdaptor documentValueAdaptor = applicationContext.getBean(beanName, DocGenValueAdaptor.class);

          // Now just return the data we need from the Bean
          return documentValueAdaptor.getValue(event, requestCache);
        }
      } catch (Exception ex) {
          log.error("Error parsing Java Bean '{}' for document template ID: '{}'", beanName, documentTemplateSource.getId(), ex);
      }
    }

    // Something has gone wrong, or the request was for an unsupported type, if we're at this point - return an empty list
    return List.of(PLACEHOLDER_ERROR);
  }

  /**
   * Returns a boolean indicating whether a given bean is supported for document generation
   */
  private boolean isBeanNameSupported(String beanName) {
    List<String> unsupportedBeanTypes = Arrays.stream(UNSUPPORTED_BEAN_NAMES.split(",")).toList();

    // Unless we find a match for the bean name in the unsupported types, the bean is supported
    String matchingUnsupportedBeanName = unsupportedBeanTypes.stream().filter(p -> p.equalsIgnoreCase(beanName)).findFirst().orElse(null);

    return matchingUnsupportedBeanName == null;
  }

  /**
   * Builds a question value which requires replacement from the database
   */
  private String getValueFromDB(final ProcurementEvent event, final DocumentTemplateSource documentTemplateSource) {
    // For this, we're using the template source data to build values from the project and event entities (which map to the DB), so first prep that data
    if (documentTemplateSource != null && documentTemplateSource.getSourcePath() != null && !documentTemplateSource.getSourcePath().isEmpty() && event != null && event.getProject() != null) {
      String[] tableColumnSource = documentTemplateSource.getSourcePath().split("/");

      if (tableColumnSource.length >= 2) {
        String table = tableColumnSource[0],
                column = tableColumnSource[1];

        if (table != null && !table.isEmpty() && column != null && !column.isEmpty()) {
          // We have the info as to which table and column we're interested in from the DB.  So now use the entities to build the value we want to return from that info
          if (table.equalsIgnoreCase(DB_PLACEHOLDER_PROJECTS)) {
            // We're dealing with project level data, so use the projects entity and grab the needed column access method
            Method getter = ReflectionUtils.findMethod(ProcurementProject.class, DB_PLACEHOLDER_METHOD_PREFIX + column);

            if (getter != null) {
              // We've got the method, so now just call it and return the data
              return (String) ReflectionUtils.invokeMethod(getter, event.getProject());
            }
          } else {
            // We're dealing with event level data, so use the events entity and grab the needed column access method
            Method getter = ReflectionUtils.findMethod(ProcurementEvent.class, DB_PLACEHOLDER_METHOD_PREFIX + column);

            if (getter != null) {
              // We've got the method, so now just call it and return the data
              return (String) ReflectionUtils.invokeMethod(getter, event);
            }
          }
        }
      }
    }

    // Something has gone wrong if we're at this point - return an empty string
    return PLACEHOLDER_ERROR;
  }

  /**
   * Builds a static value which requires replacement from the database
   */
  private String getStaticValueFromDB(final DocumentTemplateSource documentTemplateSource) {
    // In this instance, all we need to do is return the source path of the template - so do that
    if (documentTemplateSource != null && documentTemplateSource.getSourcePath() != null && !documentTemplateSource.getSourcePath().isEmpty()) {
        return documentTemplateSource.getSourcePath();
    }

    // Something has gone wrong if we're at this point - return an empty string
    return PLACEHOLDER_ERROR;
  }

  /**
   * Performs a single data item replacement within a given document template, using supplied values
   */
  private void replacePlaceholder(final DocumentTemplateSource documentTemplateSource, final List<String> dataReplacement, final TextDocument textODT, final boolean isPublish) {
    // This method effectively just hands the app off to a targeted replacement method for the type of data being processed - so just work that out and do it
    if (documentTemplateSource != null && documentTemplateSource.getTargetType() != null) {
      try {
        switch (documentTemplateSource.getTargetType()) {
          case SIMPLE:
            // Treat this as a text replacement, using a default value as a substitute if no data has been passed to us
            replaceText(documentTemplateSource,  dataReplacement != null ? getString(dataReplacement) : PLACEHOLDER_UNKNOWN, textODT, isPublish);
            break;

          case DATETIME:
            // Treat this as a text replacement, just format the text into a date/time string first
            if (dataReplacement != null && !dataReplacement.isEmpty()) {
              String formattedDatetime = formatDateOrDateAndTime(dataReplacement.getFirst());
              replaceText(documentTemplateSource, formattedDatetime, textODT, isPublish);
            }
            break;

          case DURATION:
            // Treat this as a text replacement, just format the text into a duration of time string first
            String formattedPeriod = PLACEHOLDER_UNKNOWN;

            if (dataReplacement != null && !dataReplacement.isEmpty()) {
              try {
                // Since format is always "Y-MM-DD" (e.g., "2-00-00"), split and parse as integers.
                String[] parts = dataReplacement.getFirst().split("-");
                int years = Integer.parseInt(parts[0]);
                int months = Integer.parseInt(parts[1]);
                int days = Integer.parseInt(parts[2]);

                formattedPeriod = String.format(PERIOD_FMT, years, months, days);
              } catch (Exception ex) {
                // The value wasn't a Period, so just log the error and then move on allowing this to use the default fallback
                log.error("Unable to parse value as a Period for document generation. Value: '{}'", dataReplacement.getFirst(), ex);
              }
            }

            replaceText(documentTemplateSource, formattedPeriod, textODT, isPublish);
            break;

          case TABLE:
            // We're dealing with a table column here - this is complicated. Let the sub-method handle it
            populateTableColumn(documentTemplateSource, dataReplacement, textODT);
            break;

          case LIST:
            // We're dealing with a list here - this is complicated. Let the sub-method handle it
            replaceList(documentTemplateSource, dataReplacement, textODT);
            break;

          case TABLE_GROUP:
            // Handled earlier in generateDocument(...)
            break;

          default:
            // If nothing else matches, just replace with an empty string
            replaceText(documentTemplateSource, PLACEHOLDER_ERROR, textODT, isPublish);
        }
      } catch (Exception ex) {
        // There's been an issue. We want this to fail silently, so log the error and replace the placeholder with an empty string
        log.error("Error in doc gen placeholder replacement for template '{}', type '{}'", documentTemplateSource.getId(), documentTemplateSource.getTargetType(), new DocGenValueException(ex));
        replaceText(documentTemplateSource, PLACEHOLDER_ERROR, textODT, isPublish);
      }
    }
  }

  /**
   * Calculates a single String value from a list of Strings to return for data replacement
   */
  private static String getString(List<String> dataReplacement) {
    if (dataReplacement != null) {
      // Firstly, filter our list down to contain only non-null entries
      dataReplacement = dataReplacement.stream().filter(Objects::nonNull).toList();

      // Now check the size of the list, as we need to do different things depending on that
      if (dataReplacement.size() > 1) {
        // We've more than one entry, so return them as a comma-delimited list
        return String.join(DELIMITER, dataReplacement);
      } else if (dataReplacement.size() == 1) {
        // There's only one entry, so return it directly
        return dataReplacement.getFirst();
      } else {
        // There's no entries, so return a default placeholder
        return PLACEHOLDER_UNKNOWN;
      }
    }

    // We weren't given the data we need if we've got this far - return the default placeholder
    return PLACEHOLDER_UNKNOWN;
  }

  /**
   * Formats a given date / time value into a string representation
   */
  private String formatDateOrDateAndTime(String dateValue) {
    if (dateValue != null && !dateValue.isEmpty()) {
      try {
        // The format we want to use depends on the length of the input apparently, so check that
        if (dateValue.length() <= 10) {
          // Format this as a date only
          return ONLY_DATE_FMT.format(LocalDate.parse(dateValue));
        } else {
          // Format date/time to handle daylight savings
          OffsetDateTime odt = OffsetDateTime.parse(dateValue);
          ZonedDateTime ukZone = odt.atZoneSameInstant(ZoneId.of("Europe/London"));
          return DATE_FMT.format(ukZone);
        }
      } catch (Exception ex) {
        // There was an issue during conversion - the data was likely in an unexpected format.  Log this then return the default placeholder
        log.error("Error formatting date / time for document generation. Value was '{}'", dateValue, ex);
        return PLACEHOLDER_UNKNOWN;
      }
    }

    // We weren't given the data we need if we've got this far - return the default placeholder
    return PLACEHOLDER_UNKNOWN;
  }

  /**
   * Performs a single data replacement action for text based input
   */
  private void replaceText(final DocumentTemplateSource documentTemplateSource, String dataReplacement, final TextDocument textODT, final boolean isPublish) {
    if (documentTemplateSource != null && documentTemplateSource.getPlaceholder() != null && !documentTemplateSource.getPlaceholder().isEmpty() && textODT != null) {
      // Iterate over the items in the placeholder data and process them
      TextNavigation textNavigation = new TextNavigation(documentTemplateSource.getPlaceholder(), textODT);

      StringJoiner value = new StringJoiner(" ");
      value.add(documentTemplateSource.getConditionalValue() == null ? "" : documentTemplateSource.getConditionalValue());

      if (textNavigation.hasNext()) {
        while (textNavigation.hasNext()) {
          // Grab the item we're working on, then see what we need to do to it
          try {
            TextSelection item = (TextSelection) textNavigation.nextSelection();

            if (item.getText() != null && !item.getText().isEmpty() && item.getText().contains(REPLACEMENT_CONDITIONAL) && org.apache.commons.lang3.StringUtils.isBlank(dataReplacement)) {
              // This is a conditional item - perform replacements targeted at this
              if ((dataReplacement.contains(REPLACEMENT_YES)) || (dataReplacement.contains(REPLACEMENT_NO) && !dataReplacement.equals(PLACEHOLDER_UNKNOWN))) {
                value.add(PLACEHOLDER_EMPTY);
              } else {
                value.add(dataReplacement);
              }

              // We now need to update the data replacement value now, in a general way but specifically overridden for a specific conditional item
              if (item.getText() != null && item.getText().contains(REPLACEMENT_CONDITIONAL_STEP) && dataReplacement.isEmpty()) {
                dataReplacement = PLACEHOLDER_UNKNOWN;
              } else {
                dataReplacement = value.toString();
              }
            }

            // Update the data replacement value with EOI specific data
            dataReplacement = eoiConditionalAndOptionalData(dataReplacement, documentTemplateSource.getConditionalValue() == null ? "" : documentTemplateSource.getConditionalValue());

            // Budget, Terms, Documents, and Date Insertion specific replacements
            if (item.getText() != null) {
              if ((item.getText().equals(REPLACEMENT_BUDGET_MIN) || item.getText().equals(REPLACEMENT_BUDGET_MAX) || item.getText().contains(REPLACEMENT_BUDGET_TERM)) && isNotBlankAndNumeric(dataReplacement)) {
                dataReplacement = NumberFormat.getCurrencyInstance().format(new BigDecimal(dataReplacement.trim())).substring(1).replaceAll("\\.\\d+$", PLACEHOLDER_EMPTY);
              }

              if ((item.getText().contains(REPLACEMENT_PROJECT_BUDGET) || item.getText().contains(REPLACEMENT_PROJECT_BUDGET_TERM) || item.getText().equals(REPLACEMENT_DOC_FILENAME) || item.getText().contains(REPLACEMENT_INCUMBENT_NAME)) && org.apache.commons.lang3.StringUtils.isBlank(dataReplacement)) {
                dataReplacement = PLACEHOLDER_UNKNOWN;
              }

              if (item.getText().equals(REPLACEMENT_DATE_INSERTION) && !isPublish) {
                dataReplacement = PLACEHOLDER_EMPTY;
              }
            }

            // Replacement value should now be finalised - perform the replacement
            item.replaceWith(dataReplacement);
          } catch (Exception ex) {
            // It's errored somewhere - just log this and allow the process to proceed to the next item
            log.error("Error performing textual data replacement for document generation", ex);
          }
        }
      }
    }
  }

  /**
   * Performs a check to see whether a value passed is neither blank nor a numeric
   */
  private static boolean isNotBlankAndNumeric(String dataReplacement) {
    // Just check to see if the input isn't blank or a numeric then return a boolean representation
    return !org.apache.commons.lang3.StringUtils.isBlank(dataReplacement) && dataReplacement.trim().matches(Pattern.quote("-?\\d+"));
  }

  /**
   * Performs conditional data replacements for EOI events
   */
  private String eoiConditionalAndOptionalData(String dataReplacement, String conditionalValue) {
    // We want to perform specific data replacements on conditional data entries - so look for them
    String conditionalData;

    if (dataReplacement != null && !dataReplacement.isEmpty()) {
      if (dataReplacement.contentEquals(REPLACEMENT_PRODUCT_REPLACEMENT) || dataReplacement.contentEquals(REPLACEMENT_PRODUCT_EXPANDED) || dataReplacement.contentEquals(REPLACEMENT_PRODUCT_NEW)) {
        // In these instances, we need to concatenate the conditional value to the core data
        conditionalData = conditionalValue + dataReplacement;

        return conditionalData;
      } else if (dataReplacement.contentEquals(REPLACEMENT_UNSURE)) {
        // In this instance, we want to just return a fixed value outright
        return UNSURE_FIXED_OUTPUT;
      }
    }

    // Any amendments have now been made, so return the amended data
    return dataReplacement;
  }

  /**
   * Performs a single data replacement action for list based input
   */
  private void replaceList(final DocumentTemplateSource documentTemplateSource, final List<String> dataReplacement, final TextDocument textODT) {
    // This list looks like it contains children which can have lists as part of them.  Iterate over the core list to begin
    Iterator<org.odftoolkit.simple.text.list.List> listIterator = textODT.getListIterator();

    if (listIterator != null) {
      while (listIterator.hasNext()) {
        // For this item, check its children and try to find a list we want to work against
        boolean foundList = false;
        org.odftoolkit.simple.text.list.List list = listIterator.next();

        if (list.getItems() != null && !list.getItems().isEmpty()) {
          for (ListItem li : list.getItems()) {
            if (li.getTextContent() != null && documentTemplateSource.getPlaceholder() != null && StringUtils.hasText(li.getTextContent()) && li.getTextContent().matches(Pattern.quote(documentTemplateSource.getPlaceholder()))) {
              // Looks like we have found a list amongst the children - remember this, and break out of the check
              foundList = true;

              break;
            }
          }
        }

        // Now, if we found a list for this child as part of the check above, we need to action data replacement
        if (foundList) {
          // We basically want to replace this item in the iterator list with our content, so do that
          list.removeItem(0);

          if (dataReplacement.isEmpty()) {
            dataReplacement.add(PLACEHOLDER_UNKNOWN);
          }

          list.addItems(dataReplacement.toArray(new String[0]));
        }
      }
    }
  }

  /**
   * Performs a single data replacement action for database based input
   */
  private void populateTableColumn(final DocumentTemplateSource documentTemplateSource, final List<String> dataReplacement, final TextDocument textODT) {
    // We need to start by grabbing the DB table that we need to work against
    Table table = textODT.getTableByName(documentTemplateSource.getTableName());

    if (table != null) {
      // Ok, we've got the table - make sure its contents are in line with the data replacement contents (adjusting for headings row).  Append our content if necessary
      if (table.getRowCount() - 1 < dataReplacement.size()) {
        table.appendRows(dataReplacement.size() + 1 - table.getRowCount());
      }

      boolean isLineRequired = false;
      int columnIndex = -1;

      // Before we begin, if our data replacement has no entries we want to add in an empty entry so that gets used everywhere
      if (dataReplacement.isEmpty()) {
        dataReplacement.add(PLACEHOLDER_EMPTY);
      }

      // Now we're ready to actually start. Iterate over each row in the table
      for (int r = 1; r <= dataReplacement.size(); r++) {
        Row row = table.getRowByIndex(r);

        if (row != null) {
          if (columnIndex == -1) {
            // Now we have the row, we need to find the column index which holds the placeholder value - so iterate over the columns to find it
            for (int c = 0; c < row.getCellCount(); c++) {
              Cell checkCell = table.getCellByPosition(c, r);

              if (checkCell != null) {
                if (checkCell.getDisplayText() != null && StringUtils.hasText(checkCell.getDisplayText()) && checkCell.getDisplayText().equals(CELL_LINE_REQUIRED)) {
                  // This row is required - remember this
                  isLineRequired = true;
                }

                if (checkCell.getDisplayText() != null && documentTemplateSource.getPlaceholder() != null && StringUtils.hasText(checkCell.getDisplayText()) && checkCell.getDisplayText().contains(documentTemplateSource.getPlaceholder())) {
                  // This is the column index we're looking for - remember it, then break from the iterator
                  columnIndex = c;

                  break;
                }
              }
            }
          }
        }

        // Now, if we found a column index we want to work with, we need to process that cell itself on this row
        if (columnIndex > -1) {
          // Start by grabbing the cell, and our replacement data for this row in question
          Cell cell = table.getCellByPosition(columnIndex, r);

          if (cell != null) {
            try {
              String rowData = dataReplacement.get(r - 1);
              String cellDisplayText = cell.getDisplayText();

              // Now let's deal with the cell
              if (r < dataReplacement.size() && cellDisplayText != null) {
                // Looks like we have an entry for this row in our data replacement input, and display text, so time to process it. Start by copying the cell text to the next row, so that placeholders exist there too
                Cell cellDown = table.getCellByPosition(columnIndex, r + 1);

                // Looks like the cell could be a cell which just holds a row number.  We need to check this
                if (columnIndex == 0 && "[0-9]+".matches(Pattern.quote(cellDisplayText))) {
                  // This appears to be a row number cell, so we need to increment the value
                  cellDown.setDisplayText(String.valueOf(Integer.parseInt(cellDisplayText) + 1));
                } else {
                  if (isLineRequired) {
                    // This isn't a row number cell, but it's a required row, so we need to make it a row number cell by setting it to the current row number
                    Cell cellNum = table.getCellByPosition(0, r);
                    cellNum.setStringValue(String.valueOf(r));
                  }

                  // This isn't a row number cell, and it's not required, so we just move the display text down into the next row
                  cellDown.setDisplayText(cellDisplayText);
                }
              } else if (isLineRequired) {
                // Looks like this is a required row, but we don't have a row entry for it in the data replacement input. We still need to convert it into a row number cell though
                Cell cellNum = table.getCellByPosition(0, r);
                cellNum.setStringValue(String.valueOf(r));
              }

              // Now that all that is done, we need to replace the display text in the row with the row replacement data we found earlier
              if (cellDisplayText != null) {
                cell.setStringValue(cellDisplayText.replace(documentTemplateSource.getPlaceholder(), org.apache.commons.lang3.StringUtils.isBlank(rowData) ? PLACEHOLDER_UNKNOWN : rowData));
              }
            } catch (Exception ex) {
              // We encountered an error processing this cell. Could be a number of things, so just log it and move on
                log.error("Error processing cell for document generation. Table: '{}'  Column Index: '{}'", documentTemplateSource.getTableName(), columnIndex, ex);
            }
          }
        }
      }

      // At this point we should be done with everything passed into us. But we also need to handle if no data input was passed to us
      if (dataReplacement.isEmpty() || dataReplacement.stream().anyMatch(org.apache.commons.lang3.StringUtils::isAllBlank)) {
        placeholderMappingForDb(documentTemplateSource, textODT);
      }
    } else {
      // The requested DB table couldn't be found. This is somehow expected, so just replace with placeholder content
      placeholderMappingForDb(documentTemplateSource, textODT);
    }
  }

  /**
   * Applies a default placeholder to all entries in a file
   * Only used when either no input was passed in for a DB replacement, or the DB mapping couldn't be found
   */
  private void placeholderMappingForDb(final DocumentTemplateSource documentTemplateSource, final TextDocument textODT) {
    try {
      // At this point we have no data to replace, for whatever reason. We therefore need to replace all placeholders with the standard unknown placeholder content
      if (documentTemplateSource.getPlaceholder() != null) {
        TextNavigation textNavigation = new TextNavigation(documentTemplateSource.getPlaceholder(), textODT);

        if (textNavigation.hasNext()) {
          while (textNavigation.hasNext()) {
            TextSelection item = (TextSelection) textNavigation.nextSelection();

            // Replace the whole of the item with placeholder content
            item.replaceWith(PLACEHOLDER_UNKNOWN);
          }
        }
      }
    } catch (Exception ex) {
      log.error("Unable to complete default placeholder mapping for DB table: '{}'", documentTemplateSource.getTableName(), ex);
    }
  }


    /**
     * Multi stages code
     * Main entry point for multi-stage table generation.
     * Collects all payloads and triggers the merged document generation.
     */
    private void handleMultiStageTableGroups(ProcurementEvent procurementEvent,
                                             DocumentTemplateSource templateSource,
                                             TextDocument textODT,
                                             Resource templateResource) {

        // Identify the Resource Name
        String resourceName = templateResource.getFilename();
        log.debug("Processing multi-stage logic for resource: {}", resourceName);

        // Define the "Merge Rule" Condition
        // We only apply the merge logic if it's 'Attachment 1'.
        // Using .contains or a regex is safer than .equals in case of versioning (e.g. Attachment 1 v2)
        boolean shouldMerge = resourceName != null && resourceName.toLowerCase().contains("attachment 1");

        // Fetch Stage Information
        StagesRead stageInfo = stageService.getStagesForEventId(procurementEvent.getEventID());
        if (stageInfo == null || stageInfo.getNumberOfStages() <= 0) return;

        final int totalStages = stageInfo.getNumberOfStages();

        Integer firstEventId = retryableTendersDBDelegate.findEventIdOfFirstStageForMultiStageEvent(
                procurementEvent.getEventID(), stageInfo.getNumberOfStages());

        List<Map<String, Object>> stageDataList = new ArrayList<>();

        if (shouldMerge) {
            for (int i = 1; i <= stageInfo.getNumberOfStages(); i++) {
                fillStageSpecificJson(firstEventId, i, totalStages, stageDataList);
            }
        } else {
            // TODO journey for attachment 3 hardcoded at the moment
            // TODO we need to implement logic for stage 4 and others up to 10 stage and write new file
            // TODO stage 5,6,7,8,9 and 10 should use attachment 4 template placeholder and add stage number in the file name
            // TODO file name:  DOS_7 MultiStage - Lot{LotNumber} - Attachment {AttachmentNumber} Responses to Stage {StageNumber} assessment criteria
            fillStageSpecificJson(firstEventId, 1, totalStages, stageDataList);
        }


        if (!stageDataList.isEmpty()) {
            String mergedJson = mergeStageJsonPayloads(stageDataList);
            tableGroupGenerator.fillMultiStageTableData(mergedJson, templateSource, textODT);
        }
    }

    /**
     * Encapsulated logic for the Attachment 1 Merge Rule
     */

    private void fillStageSpecificJson(int eventId,
                                       int currentStage,
                                       int totalStage,
                                       List<Map<String, Object>> stageDataList) {

        Optional<ProcurementStageEvent> stageEventOpt = retryableTendersDBDelegate.findByIdAndStageNumber(eventId, currentStage);

        stageEventOpt.ifPresent(stageEvent -> {
            String payload = stageEvent.getProcurementTemplatePayloadRaw();
            if (StringUtils.hasText(payload)) {
                Map<String, Object> data = new HashMap<>();
                data.put(PAYLOAD_TAG, payload);
                data.put(STAGE_NUMBER_TAG, stageEvent.getStageNumber());
                data.put(TOTAL_STAGES_TAG, totalStage);
                data.put(STAGE_DESCRIPTION_TAG, stageEvent.getStageDescription());
                stageDataList.add(data);
            }
        });
    }

    @SneakyThrows
    public String mergeStageJsonPayloads(List<Map<String, Object>> stageDataList) {
        if (stageDataList == null || stageDataList.isEmpty()) return "";

        // Use the first entry as Foundation
        Map<String, Object> stage1Data = stageDataList.getFirst();
        ObjectNode baseRoot = (ObjectNode) objectMapper.readTree((String) stage1Data.get(PAYLOAD_TAG));
        ArrayNode baseCriteria = (ArrayNode) baseRoot.get(CRITERIA);
        ArrayNode targetRequirementGroups = null;

        for (JsonNode criterion : baseCriteria) {
            if (CRITERION2.equals(criterion.path(ID).asText())) {
                targetRequirementGroups = (ArrayNode) criterion.get(REQUIREMENT_GROUPS);
                targetRequirementGroups.removeAll();
                break;
            }
        }

        if (targetRequirementGroups == null) return objectMapper.writeValueAsString(baseRoot);

        // Initial indices for IDs and Order
        int nextCopSuffix = 1;
        int nextAcSuffix = 1;
        int nextOrder = 1;

        // Process ALL stages (including the first one) to ensure they all get the same treatment
        for (int i = 0; i < stageDataList.size(); i++) {
            Map<String, Object> currentData = stageDataList.get(i);
            String payload = (String) currentData.get(PAYLOAD_TAG);
            int stageNum = (Integer) currentData.get(STAGE_NUMBER_TAG);
            int totalStages = (Integer) currentData.get(TOTAL_STAGES_TAG);
            String stageDesc = (String) currentData.get(STAGE_DESCRIPTION_TAG);

            // Unique IDs for each stage's groups
            String newCopId = "Group 1." + (nextCopSuffix++);
            String newAcId = "Group 2." + (nextAcSuffix++);

            // Rename and Inject metadata for both COP and AC groups
            appendRenameAndInject(payload, "Group 1", newCopId, targetRequirementGroups, stageNum, totalStages, stageDesc, nextOrder++);
            appendRenameAndInject(payload, "Group 2", newAcId, targetRequirementGroups, stageNum, totalStages, stageDesc, nextOrder++);
        }

        return objectMapper.writeValueAsString(baseRoot);
    }

    private void appendRenameAndInject(String sourceJson, String sourceId, String newId,
                                       ArrayNode targetArray, int currentNum, int total,
                                       String stageDesc, int newOrder) {
        try {
            String jsonPath =String.format(MULTI_STAGE_JSON_PATH, sourceId);
            List<Map<String, Object>> result = JsonPath.read(sourceJson, jsonPath);

            if (result != null && !result.isEmpty()) {
                ObjectNode groupNode = objectMapper.valueToTree(result.getFirst());

                ((ObjectNode) groupNode.path(OCDS)).put(ID, newId);
                ((ObjectNode) groupNode.path(NON_OCDS)).put(ORDER, newOrder);

                ObjectNode ocdsPart = (ObjectNode) groupNode.path(OCDS);
                ArrayNode reqs = ocdsPart.withArray(REQUIREMENTS);

                addVirtualRequirement(reqs, CURRENT_STAGE_TITLE, String.valueOf(currentNum));
                addVirtualRequirement(reqs, TOTAL_STAGES_TITLE, String.valueOf(total));
                addVirtualRequirement(reqs, STAGE_DESCRIPTION_TITLE, stageDesc);

                targetArray.add(groupNode);
            }
        } catch (Exception e) {
            log.error("Failed to append group {} as {} for stage {}", sourceId, newId, currentNum, e);
        }
    }

    private void addVirtualRequirement(ArrayNode requirements, String title, String value) {
        ObjectNode req = objectMapper.createObjectNode();
        ObjectNode ocds = req.putObject(OCDS);
        ocds.put(ID, title);
        ocds.put(TITLE, title);

        ObjectNode nonOcds = req.putObject(NON_OCDS);
        ArrayNode options = nonOcds.putArray(OPTIONS);
        ObjectNode opt = options.addObject();
        opt.put(VALUE, (value != null) ? value : "");
        opt.put(SELECT, true);

        requirements.add(req);
    }

}
