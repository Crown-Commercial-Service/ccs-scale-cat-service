package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.util.Pair;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventStatusHelper;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventsHelper;

@Slf4j
public class TemplateDataExtractor {
  
  private static final String PERIOD_FMT = "%d years, %d months, %d days";
  
  public static String getStatus(Pair<ProcurementEvent, ProcurementEvent> events) {
    return EventStatusHelper
        .getEventStatus(Objects.nonNull(events.getSecond()) ? events.getSecond().getTenderStatus()
            : events.getFirst().getTenderStatus());
  }

  public static Long getOpenForCount(ProcurementEvent event) {
    if (Objects.nonNull(event.getPublishDate()) && Objects.nonNull(event.getCloseDate())) {
      return ChronoUnit.DAYS.between(event.getPublishDate(), event.getCloseDate());
    }
    return null;
  }
  
  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  public static String getExpectedContractLength(final ProcurementEvent event) {
    try {
      if (Objects.nonNull(event.getProcurementTemplatePayload())) {
        String criterionId = event.getProject().getLotNumber().equals("1") ? "Criterion 3" : "Criterion 1";
        String groupId = event.getProject().getLotNumber().equals("1") ? "Group 18" : "Key Dates";
        String dataFromJSONDataTemplate = EventsHelper.getData(criterionId, groupId, "Question 12",
            event.getProcurementTemplatePayload().getCriteria());
        if (Objects.nonNull(dataFromJSONDataTemplate)) {
          var period = Period.parse(dataFromJSONDataTemplate);
          return String.format(PERIOD_FMT, period.getYears(), period.getMonths(), period.getDays());
        }
      }
    } catch (Exception e) {
      log.warn("Error while getExpectedContractLength" + e.getMessage());
    }
    return "";
  }
  
  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  public static String getBudgetRangeData(final ProcurementEvent event) {
    try {
      String maxValue = null;
      String minValue = null;
      String groupId = event.getProject().getLotNumber().equals("1") ? "Group 20" : "Group 18";
      if (Objects.nonNull(event.getProcurementTemplatePayload())) {
        maxValue = EventsHelper.getData("Criterion 3", groupId, "Question 2",
            event.getProcurementTemplatePayload().getCriteria());
        minValue = EventsHelper.getData("Criterion 3", groupId, "Question 3",
            event.getProcurementTemplatePayload().getCriteria());
        if (!StringUtils.isBlank(minValue) & !StringUtils.isBlank(minValue)) {
          return "£" + minValue + "- £" + maxValue;
        } else if (!StringUtils.isBlank(maxValue)) {
          return "up to £" + maxValue;
        } else
          return "Not prepared to share details";
      }
    } catch (Exception e) {
      log.warn("Error while getBudgetRangeData" + e.getMessage());
    }
    return null;
  }
  
  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  public static String geContractStartData(final ProcurementEvent event) {
    try {
      if (Objects.nonNull(event.getProcurementTemplatePayload())) {
        if (event.getProject().getLotNumber().equals("1")) {
          return EventsHelper.getData("Criterion 1", "Key Dates", "Question 13",
              event.getProcurementTemplatePayload().getCriteria());
        } else if (event.getProject().getLotNumber().equals("3")) {
          return EventsHelper.getData("Criterion 1", "Key Dates", "Question 11",
              event.getProcurementTemplatePayload().getCriteria());
        }
      }
    } catch (Exception e) {
      log.warn("Error while geContractStartData" + e.getMessage());
    }
    return "";
  }
  
  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  public static String getEmploymentStatus(final ProcurementEvent event) {
    try {
      if (Objects.nonNull(event.getProcurementTemplatePayload())) {
        if (event.getProject().getLotNumber().equals("1")) {
          return EventsHelper.getData("Criterion 3", "Group 21", "Question 1",
              event.getProcurementTemplatePayload().getCriteria());
        }
      }
    } catch (Exception e) {
      log.warn("Error while getEmploymentStatus" + e.getMessage());
    }
    return "";
  }

}
