package uk.gov.crowncommercial.dts.scale.cat.service;

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
import org.springframework.beans.factory.annotation.Value;
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
import uk.gov.crowncommercial.dts.scale.cat.exception.DocGenValueException;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.NonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.Option;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentTemplateSource;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.TargetType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;
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
  static final String MEDIA_TYPE_OPEN_DOC_TEXT = "application/vnd.oasis.opendocument.text";
  static final String PROFORMA_FILENAME_FMT = "%s-%s-%s.odt";
  static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

  private final ApplicationContext applicationContext;
  private final ValidationService validationService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final ObjectMapper objectMapper;
  private final ProcurementEventService procurementEventService;

  // TODO: Replace with document retrieval from central location
  @Value("classpath:rfi_proforma_template.odt")
  private Resource rfiODT;

  @SneakyThrows
  public void generateProformaDocument(final Integer projectId, final String eventId) {

    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var eventType = procurementEvent.getEventType();

    var documentTemplate = retryableTendersDBDelegate.findByEventType(eventType)
        .orElseThrow(() -> new TendersDBDataException(
            "Document template for event type [" + eventType + "] not found in DB"));

    final var textODT = TextDocument.loadDocument(rfiODT.getInputStream());
    final var requestCache = new ConcurrentHashMap<String, Object>();

    for (var documentTemplateSource : documentTemplate.getDocumentTemplateSources()) {

      var dataReplacement =
          getDataReplacement(procurementEvent, documentTemplateSource, requestCache);
      replacePlaceholder(documentTemplateSource, dataReplacement, textODT);
    }

    var outputStream = new ByteArrayOutputStream();
    textODT.save(outputStream);
    var fileName = String.format(PROFORMA_FILENAME_FMT, procurementEvent.getEventID(),
        procurementEvent.getEventType(), procurementEvent.getProject().getProjectName());

    ByteArrayMultipartFile multipartFile =
        new ByteArrayMultipartFile(outputStream.toByteArray(), fileName, MEDIA_TYPE_OPEN_DOC_TEXT);

    procurementEventService.uploadDocument(projectId, eventId, multipartFile,
        DocumentAudienceType.SUPPLIER, procurementEvent.getEventType() + " pro forma for tender: "
            + procurementEvent.getEventID());
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

    switch (documentTemplateSource.getTargetType()) {
      case SIMPLE:
        replaceText(documentTemplateSource, dataReplacement, textODT);
        break;

      case DATETIME:
        var formattedDatetime = DATE_FMT.format(OffsetDateTime.parse((String) dataReplacement));
        replaceText(documentTemplateSource, formattedDatetime, textODT);
        break;

      case TABLE:
        // TODO: How do we handle this?
        break;

      case LIST:
        replaceList(documentTemplateSource, dataReplacement, textODT);
        break;
      default:
        log.warn("Unrecognised target type - assume simple text");
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

}
