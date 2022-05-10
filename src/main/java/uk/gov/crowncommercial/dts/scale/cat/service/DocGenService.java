package uk.gov.crowncommercial.dts.scale.cat.service;

import static uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType.SUPPLIER;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.transaction.Transactional;
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

  public static final String PLACEHOLDER_ERROR = "«ERROR»";
  public static final String PLACEHOLDER_UNKNOWN = "«UNKNOWN»";
  static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
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
        .findByEventType(procurementEvent.getEventType())) {
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
          var formattedDatetime = DATE_FMT.format(OffsetDateTime.parse(dataReplacement.get(0)));
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
          log.warn("Unrecognised target type - assume simple text");
      }
    } catch (Exception ex) {
      log.warn("Error in doc gen placeholder replacement", new DocGenValueException(ex));
    }

  }

  void replaceText(final DocumentTemplateSource documentTemplateSource,
      final String dataReplacement, final TextDocument textODT) throws InvalidNavigationException {

    var textNavigation = new TextNavigation(documentTemplateSource.getPlaceholder(), textODT);
    while (textNavigation.hasNext()) {
      var item = (TextSelection) textNavigation.nextSelection();
      log.trace("Found: [" + item + "], replacing with: [" + dataReplacement + "]");
      item.replaceWith(dataReplacement);
    }
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
      final List<String> dataReplacement, final TextDocument textODT) {
    var table = textODT.getTableByName(documentTemplateSource.getTableName());
    if (table != null) {
      // Append rows if necessary (assume header row present)
      if (table.getRowCount() - 1 < dataReplacement.size()) {
        table.appendRows(dataReplacement.size() - table.getRowCount());
      }

      var columnIndex = -1;
      for (var r = 1; r <= dataReplacement.size(); r++) {
        var row = table.getRowByIndex(r);

        if (columnIndex == -1) {
          // Find the right column by searching for the placeholder
          for (var c = 0; c < row.getCellCount(); c++) {
            var checkCell = table.getCellByPosition(c, r);

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
            if (columnIndex == 0 && "[0-9]+".matches(cellDisplayText)) {
              cellDown.setDisplayText(String.valueOf(Integer.parseInt(cellDisplayText) + 1));
            } else {
              cellDown.setDisplayText(cellDisplayText);
            }
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
