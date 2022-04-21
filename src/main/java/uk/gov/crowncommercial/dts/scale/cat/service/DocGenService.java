package uk.gov.crowncommercial.dts.scale.cat.service;

import static uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType.SUPPLIER;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
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
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.NonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.Option;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.ByteArrayMultipartFile;

/**
 * Generates an ODT text document based on a template and data sources as provided via Tenders DB
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocGenService {

  static final String PLACEHOLDER_ERROR = "«ERROR»";
  static final String PLACEHOLDER_UNKNOWN = "«UNKNOWN»";
  static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

  private final ApplicationContext applicationContext;
  private final ValidationService validationService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final ObjectMapper objectMapper;
  private final ProcurementEventService procurementEventService;
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
        procurementEvent.getProject().getId(), procurementEvent.getEventType(),
        procurementEvent.getProject().getProjectName(), documentTemplate.getId());
    var fileDescription =
        procurementEvent.getEventType() + " pro forma for tender: " + procurementEvent.getEventID();

    var multipartFile = new ByteArrayMultipartFile(documentOutputStream.toByteArray(), fileName,
        Constants.MEDIA_TYPE_ODT.toString());

    procurementEventService.eventUploadDocument(procurementEvent, fileName, fileDescription,
        SUPPLIER, multipartFile);
  }

  Object getDataReplacement(final ProcurementEvent event,
      final DocumentTemplateSource documentTemplateSource,
      final ConcurrentMap<String, Object> requestCache) {

    try {
      switch (documentTemplateSource.getSourceType()) {
        case JSON:
          final var qstn = getQuestionFromJSONDataTemplate(event, documentTemplateSource);
          if (qstn.getOptions() != null && !qstn.getOptions().isEmpty()) {
            if (documentTemplateSource.getTargetType() == TargetType.SIMPLE
                || documentTemplateSource.getTargetType() == TargetType.DATETIME) {
              return qstn.getOptions().stream().findFirst().get().getValue();
            }
            return qstn.getOptions().stream()
                .filter(o -> Objects.equals(Boolean.TRUE, o.getSelect())
                    || StringUtils.hasText(o.getText()) || "Value".equals(qstn.getQuestionType()))
                .collect(Collectors.toList());
          }
          return PLACEHOLDER_UNKNOWN;

        case JAVA:
          return getValueFromBean(event, documentTemplateSource, requestCache);

        case SQL:
          return getValueFromDB(event, documentTemplateSource);

        default:
          log.warn("Unrecognised source type - unable to process");
          return PLACEHOLDER_ERROR;
      }
    } catch (Exception ex) {
      log.error("Error in doc gen value retrieval", new DocGenValueException(ex));
      return PLACEHOLDER_ERROR;
    }
  }

  NonOCDS getQuestionFromJSONDataTemplate(final ProcurementEvent event,
      final DocumentTemplateSource documentTemplateSource) {

    var eventData = event.getProcurementTemplatePayloadRaw();

    var jsonPathConfig = Configuration.builder().jsonProvider(new JacksonJsonProvider(objectMapper))
        .mappingProvider(new JacksonMappingProvider(objectMapper)).build();

    TypeRef<List<NonOCDS>> typeRef = new TypeRef<>() {};
    return JsonPath.using(jsonPathConfig).parse(eventData)
        .read(documentTemplateSource.getSourcePath(), typeRef).get(0);
  }

  String getValueFromBean(final ProcurementEvent event,
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
      final Object dataReplacement, final TextDocument textODT) throws InvalidNavigationException {

    log.trace("Searching document for placeholder: [" + documentTemplateSource.getPlaceholder()
        + "] to replace with: [" + dataReplacement + "]");

    try {
      switch (documentTemplateSource.getTargetType()) {
        case SIMPLE:
          replaceText(documentTemplateSource, dataReplacement, textODT);
          break;

        case DATETIME:
          var formattedDatetime = DATE_FMT.format(OffsetDateTime.parse((String) dataReplacement));
          replaceText(documentTemplateSource, formattedDatetime, textODT);
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
      log.error("Error in doc gen placeholder replacement", new DocGenValueException(ex));
    }

  }

  void replaceText(final DocumentTemplateSource documentTemplateSource,
      final Object dataReplacement, final TextDocument textODT) throws InvalidNavigationException {

    var textNavigation = new TextNavigation(documentTemplateSource.getPlaceholder(), textODT);
    while (textNavigation.hasNext()) {
      var item = (TextSelection) textNavigation.nextSelection();
      log.trace("Found: [" + item + "], replacing with: [" + dataReplacement + "]");
      item.replaceWith((String) dataReplacement);
    }
  }

  @SuppressWarnings("unchecked")
  void replaceList(final DocumentTemplateSource documentTemplateSource,
      final Object dataReplacement, final TextDocument textODT) {

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
        ((Collection<Option>) dataReplacement).stream().forEach(o -> list.addItem(o.getValue()));
      }
    }
  }

  @SuppressWarnings("unchecked")
  void populateTableColumn(final DocumentTemplateSource documentTemplateSource,
      final Object dataReplacement, final TextDocument textODT) {
    var table = textODT.getTableByName(documentTemplateSource.getTableName());
    if (table != null) {
      var options = (List<Option>) dataReplacement;
      // Append rows if necessary (assume header row present)
      if (table.getRowCount() - 1 < options.size()) {
        table.appendRows(options.size() - table.getRowCount());
      }

      var columnIndex = -1;
      for (var r = 1; r <= options.size(); r++) {
        var row = table.getRowByIndex(r);

        if (columnIndex == -1) {
          // Find the right column..
          for (var c = 0; c < row.getCellCount(); c++) {
            var checkCell = table.getCellByPosition(c, r);
            if (StringUtils.hasText(checkCell.getDisplayText())
                && checkCell.getDisplayText().matches(documentTemplateSource.getPlaceholder())) {
              columnIndex = c;
              break;
            }
          }
        }
        if (columnIndex > -1) {
          var cell = table.getCellByPosition(columnIndex, r);
          var option = options.get(r - 1);
          cell.setDisplayText(documentTemplateSource.getOptionsProperty() == OptionsProperty.VALUE
              ? option.getValue()
              : option.getText());
        }
      }
    } else {
      log.warn("Unable to find table: [" + documentTemplateSource.getTableName() + "]");
    }
  }

}
