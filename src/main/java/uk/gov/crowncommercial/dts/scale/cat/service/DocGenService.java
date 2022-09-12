package uk.gov.crowncommercial.dts.scale.cat.service;

import static uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType.SUPPLIER;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.transaction.Transactional;
import org.apache.commons.validator.GenericValidator;
import org.odftoolkit.simple.TextDocument;
import org.odftoolkit.simple.common.navigation.InvalidNavigationException;
import org.odftoolkit.simple.common.navigation.TextNavigation;
import org.odftoolkit.simple.common.navigation.TextSelection;
import org.springframework.context.ApplicationContext;
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
  public static final String PLACEHOLDER_UNKNOWN = "";
  static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
  static final DateTimeFormatter ONLY_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
  static final String PERIOD_FMT = "%d years, %d months, %d days";

  private final ApplicationContext applicationContext;
  private final ValidationService validationService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final ObjectMapper objectMapper;
  private final JaggaerService jaggaerService;
  private final DocumentTemplateResourceService documentTemplateResourceService;

  public void generateAndUploadDocuments(final Integer projectId, final String eventId) {
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    for (DocumentTemplate documentTemplate : retryableTendersDBDelegate
        .findByEventTypeAndCommercialAgreementNumberAndLotNumber(procurementEvent.getEventType(),
            procurementEvent.getProject().getCaNumber(),
            procurementEvent.getProject().getLotNumber())) {
      uploadProforma(procurementEvent, generateDocument(procurementEvent, documentTemplate),
          documentTemplate);
    }
  }

  @SneakyThrows
  @Transactional
  public ByteArrayOutputStream generateDocument(final ProcurementEvent procurementEvent,
      final DocumentTemplate documentTemplate) {

    var templateResource =
        documentTemplateResourceService.getResource(documentTemplate.getTemplateUrl());

    final var textODT = TextDocument.loadDocument(templateResource.getInputStream());
    final var requestCache = new ConcurrentHashMap<String, Object>();

    for (var documentTemplateSource : documentTemplate.getDocumentTemplateSources()) {

      var dataReplacement =
          getDataReplacement(procurementEvent, documentTemplateSource, requestCache);
      replacePlaceholder(documentTemplateSource, dataReplacement, textODT);
    }

    var outputStream = new ByteArrayOutputStream();
    textODT.save(outputStream);
    return outputStream;
  }

  private void uploadProforma(final ProcurementEvent procurementEvent,
      final ByteArrayOutputStream documentOutputStream, final DocumentTemplate documentTemplate) {
    var fileName = String.format(Constants.GENERATED_DOCUMENT_FILENAME_FMT,
        procurementEvent.getEventID(), procurementEvent.getEventType(),
        StringUtils.getFilename(documentTemplate.getTemplateUrl()));
    var fileDescription =
        procurementEvent.getEventType() + " pro forma for tender: " + procurementEvent.getEventID();

    var multipartFile = new ByteArrayMultipartFile(documentOutputStream.toByteArray(), fileName,
        Constants.MEDIA_TYPE_ODT.toString());

    jaggaerService.eventUploadDocument(procurementEvent, fileName, fileDescription, SUPPLIER,
        multipartFile);
  }

  List<String> getDataReplacement(final ProcurementEvent event,
      final DocumentTemplateSource documentTemplateSource,
      final ConcurrentMap<String, Object> requestCache) {

    try {
      switch (documentTemplateSource.getSourceType()) {

        // All paths are indefinite, therefore List<String> will always be returned.
        case JSON:
          return getQuestionFromJSONDataTemplate(event, documentTemplateSource);

        case JAVA:
          return getValueFromBean(event, documentTemplateSource, requestCache);

        case SQL:
          return List.of(getValueFromDB(event, documentTemplateSource));
        
        case STATIC:  
          return List.of(getStaticValueFromDB(event, documentTemplateSource));
          
        default:
          log.warn("Unrecognised source type - unable to process");
          return List.of(PLACEHOLDER_ERROR);
      }
    } catch (Exception ex) {
      log.error("Error in doc gen value retrieval for: " + documentTemplateSource,
          new DocGenValueException(ex));
      return List.of(PLACEHOLDER_ERROR);
    }
  }

  List<String> getQuestionFromJSONDataTemplate(final ProcurementEvent event,
      final DocumentTemplateSource documentTemplateSource) {

    var eventData = event.getProcurementTemplatePayloadRaw();

    var jsonPathConfig =
        Configuration.builder().options(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST)
            .jsonProvider(new JacksonJsonProvider(objectMapper))
            .mappingProvider(new JacksonMappingProvider(objectMapper)).build();

    TypeRef<List<String>> typeRef = new TypeRef<>() {};
    return JsonPath.using(jsonPathConfig).parse(eventData)
        .read(documentTemplateSource.getSourcePath(), typeRef);
  }

  List<String> getValueFromBean(final ProcurementEvent event,
      final DocumentTemplateSource documentTemplateSource,
      final ConcurrentMap<String, Object> requestCache) {

    var beanName = documentTemplateSource.getSourcePath();
    var documentValueAdaptor = applicationContext.getBean(beanName, DocGenValueAdaptor.class);

    return documentValueAdaptor.getValue(event, requestCache);
  }

  String getValueFromDB(final ProcurementEvent event,
      final DocumentTemplateSource documentTemplateSource) {
    var tableColumnSource = documentTemplateSource.getSourcePath().split("/");
    var table = tableColumnSource[0];
    var column = tableColumnSource[1];

    if ("project".equalsIgnoreCase(table)) {
      var getter = ReflectionUtils.findMethod(ProcurementProject.class, "get" + column);
      return (String) ReflectionUtils.invokeMethod(getter, event.getProject());
    }
    var getter = ReflectionUtils.findMethod(ProcurementEvent.class, "get" + column);
    return (String) ReflectionUtils.invokeMethod(getter, event);
  }
  
  String getStaticValueFromDB(final ProcurementEvent event,
      final DocumentTemplateSource documentTemplateSource) {
    var tableColumnSource = documentTemplateSource.getSourcePath();
    return tableColumnSource;
  }
  
  void replacePlaceholder(final DocumentTemplateSource documentTemplateSource,
      final List<String> dataReplacement, final TextDocument textODT)
      throws InvalidNavigationException {

    log.debug("Searching document for placeholder: [" + documentTemplateSource.getPlaceholder()
        + "] to replace with: " + dataReplacement);

    try {
      switch (documentTemplateSource.getTargetType()) {
        case SIMPLE:
          replaceText(documentTemplateSource,
              dataReplacement.stream().findFirst().orElse(PLACEHOLDER_UNKNOWN), textODT);
          break;

        case DATETIME:
          var formattedDatetime = formatDateorDateAndTime(dataReplacement.get(0));
          replaceText(documentTemplateSource, formattedDatetime, textODT);
          break;

        case DURATION:
          var period = Period.parse(dataReplacement.get(0));
          var formattedPeriod =
              String.format(PERIOD_FMT, period.getYears(), period.getMonths(), period.getDays());
          replaceText(documentTemplateSource, formattedPeriod, textODT);
          break;

        case TABLE:
          populateTableColumn(documentTemplateSource, dataReplacement, textODT);
          break;

        case LIST:
          replaceList(documentTemplateSource, dataReplacement, textODT);
          break;
        default:
          replaceText(documentTemplateSource, "", textODT);
      }
    } catch (Exception ex) {
      log.warn("Error in doc gen placeholder replacement", new DocGenValueException(ex));
      replaceText(documentTemplateSource, "", textODT);
    }

  }
  
  String formatDateorDateAndTime(String dateValue) {
    if (dateValue.length() <= 10) {
      return ONLY_DATE_FMT.format(LocalDate.parse(dateValue));
    } else {
      return DATE_FMT.format(OffsetDateTime.parse(dateValue));
    }
  }

  void replaceText(final DocumentTemplateSource documentTemplateSource,
      String dataReplacement, final TextDocument textODT) throws InvalidNavigationException {

    var textNavigation = new TextNavigation(documentTemplateSource.getPlaceholder(), textODT);
    while (textNavigation.hasNext()) {
      var item = (TextSelection) textNavigation.nextSelection();

      if (item.getText().contains("Conditional") && !dataReplacement.isBlank()) {
        StringJoiner value = new StringJoiner(" ");
        value.add(documentTemplateSource.getConditionalValue() == null ? ""
            : documentTemplateSource.getConditionalValue());
        
        //FC-DA related condition
        //TODO Should move to seperate method
        if (dataReplacement.contains("Yes")) {
          value.add("There is an existing supplier providing the products and services:");
        } else if (dataReplacement.contains("No")) {
          value.add("");
        } else {
          value.add(dataReplacement);
        }
        dataReplacement = value.toString();
      }

      dataReplacement = eoiConditionalAndOptionalData(dataReplacement,
          documentTemplateSource.getConditionalValue() == null ? ""
              : documentTemplateSource.getConditionalValue());

      log.trace("Found: [" + item + "], replacing with: [" + dataReplacement + "]");
      item.replaceWith(dataReplacement);
    }
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
            && li.getTextContent().matches(documentTemplateSource.getPlaceholder())) {
          foundList = true;
          break;
        }
      }
      if (foundList) {
        list.removeItem(0);
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
        table.appendRows(dataReplacement.size() - table.getRowCount());
      }
      var isLineRequired = false;
      var columnIndex = -1;
      
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
            if (columnIndex == 0 && "[0-9]+".matches(cellDisplayText)) {
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
          cell.setDisplayText(
              cellDisplayText.replace(documentTemplateSource.getPlaceholder(), datum));
        }
      }
    } else {
      log.warn("Unable to find table: [" + documentTemplateSource.getTableName() + "]");
    }
  }
}
