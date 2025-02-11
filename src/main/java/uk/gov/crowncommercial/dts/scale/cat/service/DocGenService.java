package uk.gov.crowncommercial.dts.scale.cat.service;

import static uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType.SUPPLIER;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import jakarta.transaction.Transactional;
import org.odftoolkit.simple.TextDocument;
import org.odftoolkit.simple.common.navigation.InvalidNavigationException;
import org.odftoolkit.simple.common.navigation.TextNavigation;
import org.odftoolkit.simple.common.navigation.TextSelection;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.DocGenValueException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentTemplateSource;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.ByteArrayMultipartFile;

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

  private final ApplicationContext applicationContext;
  private final ValidationService validationService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final ObjectMapper objectMapper;
  private final JaggaerService jaggaerService;
  private final DocumentTemplateResourceService documentTemplateResourceService;

  /**
   * Trigger the generation and upload of all documents for a given event
   */
  public void generateAndUploadDocuments(final Integer projectId, final String eventId) {
    // Start by validating the event passed into us is good to use
    ProcurementEvent procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);

    if (procurementEvent != null && procurementEvent.getProject() != null) {
      // We've got the event, now grab the data we need from it and then use that to fetch the list of documents needed for it
      String eventType = procurementEvent.getEventType(),
              caNumber = procurementEvent.getProject().getCaNumber(),
              lotNum = procurementEvent.getProject().getLotNumber();
      Integer templateId = procurementEvent.getTemplateId();

      if (eventType != null && caNumber != null && lotNum != null) {
        Set<DocumentTemplate> docTemplates = retryableTendersDBDelegate.findByEventTypeAndCommercialAgreementNumberAndLotNumberAndTemplateGroup(eventType, caNumber, lotNum, templateId);

        if (docTemplates != null && !docTemplates.isEmpty()) {
          // Now we have the list of documents needed - iterate over them and process them
          docTemplates.forEach(template -> {
            ByteArrayOutputStream document = generateDocument(procurementEvent, template, Boolean.TRUE);

            if (document != null) {
              // Document has been generated, now trigger the upload
              uploadProforma(procurementEvent, document, template);
            }
          });
        }
      }
    }
  }

  /**
   * Generate a given event's version of a document based on a supplied template
   */
  @SneakyThrows
  @Transactional
  public ByteArrayOutputStream generateDocument(final ProcurementEvent procurementEvent, final DocumentTemplate documentTemplate, final boolean isPublish) {
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
            List<String> dataReplacement = getDataReplacement(procurementEvent, templateSource, requestCache);

            replacePlaceholder(templateSource, dataReplacement, textODT, procurementEvent.getPublishDate() == null ? isPublish : Boolean.TRUE);
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

  /**
   * Triggers the upload of a given document into Jaegger
   */
  private void uploadProforma(final ProcurementEvent procurementEvent, final ByteArrayOutputStream documentOutputStream, final DocumentTemplate documentTemplate) {
    if (procurementEvent != null && documentTemplate != null && procurementEvent.getEventID() != null && !procurementEvent.getEventID().isEmpty() && procurementEvent.getEventType() != null && !procurementEvent.getEventType().isEmpty() && documentTemplate.getTemplateUrl() != null && !documentTemplate.getTemplateUrl().isEmpty()) {
      // Start by generating the necessary descriptive information about our file
      String fileName = String.format(Constants.GENERATED_DOCUMENT_FILENAME_FMT, procurementEvent.getEventID(), procurementEvent.getEventType(), StringUtils.getFilename(documentTemplate.getTemplateUrl())),
              fileDescription = procurementEvent.getEventType() + DOCUMENT_DESC_JOINER + procurementEvent.getEventID();

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
        DocGenValueAdaptor documentValueAdaptor = applicationContext.getBean(beanName, DocGenValueAdaptor.class);

        // Now just return the data we need from the Bean
        return documentValueAdaptor.getValue(event, requestCache);
      } catch (Exception ex) {
          log.error("Error parsing Java Bean '{}' for document template ID: '{}'", beanName, documentTemplateSource.getId(), ex);
      }
    }

    // Something has gone wrong if we're at this point - return an empty list
    return List.of(PLACEHOLDER_ERROR);
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
            String formattedDatetime = formatDateOrDateAndTime(dataReplacement.getFirst());
            replaceText(documentTemplateSource, formattedDatetime, textODT, isPublish);
            break;

          case DURATION:
            // Treat this as a text replacement, just format the text into a duration of time string first
            String formattedPeriod = PLACEHOLDER_UNKNOWN;

            try {
              // Try to fetch the value as a Period then parse it
              Period period = Period.parse(dataReplacement.getFirst());
              formattedPeriod = String.format(PERIOD_FMT, period.getYears(), period.getMonths(), period.getDays());
            } catch (Exception ex) {
              // The value wasn't a Period, so just log the error and then move on allowing this to use the default fallback
                log.error("Unable to parse value as a Period for document generation: '{}'", dataReplacement.getFirst(), ex);
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

          default:
            // If nothing else matches, just replace with an empty string
            replaceText(documentTemplateSource, PLACEHOLDER_ERROR, textODT, isPublish);
        }
      } catch (Exception ex) {
        // There's been an issue. We want this to fail silently, so log the error and replace the placeholder with an empty string
        log.error("Error in doc gen placeholder replacement for template '{}'", documentTemplateSource.getId(), new DocGenValueException(ex));
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
          // Format this as a date / time
          return DATE_FMT.format(OffsetDateTime.parse(dateValue));
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
    if (documentTemplateSource != null && documentTemplateSource.getPlaceholder() != null && !documentTemplateSource.getPlaceholder().isEmpty()) {
      // Iterate over the items in the placeholder data and process them
      TextNavigation textNavigation = new TextNavigation(documentTemplateSource.getPlaceholder(), textODT);

      StringJoiner value = new StringJoiner(" ");
      value.add(documentTemplateSource.getConditionalValue() == null ? "" : documentTemplateSource.getConditionalValue());

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

  // TODO: HERE
  private static boolean isNotBlankAndNumeric(String dataReplacement) {
    return !org.apache.commons.lang3.StringUtils.isBlank(dataReplacement) && dataReplacement.trim().matches(Pattern.quote("-?\\d+"));
  }

  //TODO remove static content and add in app.yaml file
  private String eoiConditionalAndOptionalData(String dataReplacement, String conditionalValue) {
    String conditionlaData = "";
    if (dataReplacement.contentEquals("Replacement products or services")
        || dataReplacement.contentEquals("Expanded products or services")
        || dataReplacement.contentEquals("New products or services")) {
      conditionlaData = conditionalValue + dataReplacement;
      return conditionlaData;
    } else if (dataReplacement.contentEquals("Not sure")) {
      conditionlaData =
          "The buyer is unsure whether it will be a new or a replacement product or service.";
      return conditionlaData;
    }
    
    return dataReplacement;
  }

  void replaceList(final DocumentTemplateSource documentTemplateSource,
      final List<String> dataReplacement, final TextDocument textODT) {

    var listIterator = textODT.getListIterator();
    while (listIterator.hasNext()) {
      var list = listIterator.next();
      var foundList = false;
      for (var li : list.getItems()) {
        if (StringUtils.hasText(li.getTextContent())
            && li.getTextContent().matches(Pattern.quote(documentTemplateSource.getPlaceholder()))) {
          foundList = true;
          break;
        }
      }
      if (foundList) {
        list.removeItem(0);
        if(dataReplacement.isEmpty()) {
          dataReplacement.add(PLACEHOLDER_UNKNOWN);
        }
        list.addItems(dataReplacement.toArray(new String[0]));
      }
    }
  }

  void populateTableColumn(final DocumentTemplateSource documentTemplateSource,
      final List<String> dataReplacement, final TextDocument textODT) throws InvalidNavigationException {
    var table = textODT.getTableByName(documentTemplateSource.getTableName());
    if (table != null) {
      // Append rows if necessary (assume header row present)
      if (table.getRowCount() - 1 < dataReplacement.size()) {
        table.appendRows(dataReplacement.size() + 1 - table.getRowCount());
      }
      var isLineRequired = false;
      var columnIndex = -1;
      
      if(dataReplacement.isEmpty()) {
        dataReplacement.add("");
      }
      
      for (var r = 1; r <= dataReplacement.size(); r++) {
        var row = table.getRowByIndex(r);

        if (columnIndex == -1) {
          // Find the right column by searching for the placeholder
          for (var c = 0; c < row.getCellCount(); c++) {
            var checkCell = table.getCellByPosition(c, r);
            if (StringUtils.hasText(checkCell.getDisplayText())
                && checkCell.getDisplayText().equals("1")) {
              isLineRequired = true;
            }
            if (StringUtils.hasText(checkCell.getDisplayText())
                && checkCell.getDisplayText().contains(documentTemplateSource.getPlaceholder())) {
              columnIndex = c;
              break;
            }
          }
        }
        if (columnIndex > -1) {
          var cell = table.getCellByPosition(columnIndex, r);
          var datum = dataReplacement.get(r - 1);

          // Copy the cell text to the one below, so placeholders are present in next row
          var cellDisplayText = cell.getDisplayText();
          if (r < dataReplacement.size()) {
            var cellDown = table.getCellByPosition(columnIndex, r + 1);

            // Check if row number cell, if so increment
            //TODO unused code need to remove
            if (columnIndex == 0 && "[0-9]+".matches(Pattern.quote(cellDisplayText))) {
              cellDown.setDisplayText(String.valueOf(Integer.parseInt(cellDisplayText) + 1));
            } else {
              if (isLineRequired) {
                var cellNum = table.getCellByPosition(0, r);
                cellNum.setStringValue(String.valueOf(r));
              }
              cellDown.setDisplayText(cellDisplayText);
            }
          } else if (isLineRequired) {
            var cellNum = table.getCellByPosition(0, r);
            cellNum.setStringValue(String.valueOf(r));
          }
          
          // Replace placeholder ONLY in the cell's display text
          // TODO: Remove OptionsProperty - redundant.
         cell.setStringValue(cellDisplayText.replace(documentTemplateSource.getPlaceholder(),
              org.apache.commons.lang3.StringUtils.isBlank(datum) ? PLACEHOLDER_UNKNOWN : datum));
        }
      }
      
      if (dataReplacement.isEmpty() || dataReplacement.stream()
          .anyMatch(org.apache.commons.lang3.StringUtils::isAllBlank)) {
        var textNavigation = new TextNavigation(documentTemplateSource.getPlaceholder(), textODT);
        while (textNavigation.hasNext()) {
          var item = (TextSelection) textNavigation.nextSelection();
          item.replaceWith(PLACEHOLDER_UNKNOWN);
        }
      }
    } else {
     
      log.warn("Unable to find table: [" + documentTemplateSource.getTableName() + "]");
    }
  }
}
