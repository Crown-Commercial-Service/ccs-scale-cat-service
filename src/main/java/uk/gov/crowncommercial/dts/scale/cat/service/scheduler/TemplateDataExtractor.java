package uk.gov.crowncommercial.dts.scale.cat.service.scheduler;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.service.ocds.EventsHelper;

public class TemplateDataExtractor {
  
  public static Long getOpenForCount(OffsetDateTime publishedDate, OffsetDateTime closeDate) {
    if (Objects.nonNull(publishedDate) && Objects.nonNull(closeDate)) {
      Duration duration = Duration.between(publishedDate, closeDate);
      // if more than 12h - consider as full day
      return duration.toHoursPart() > 12 ? duration.toDaysPart() + 1 : duration.toDaysPart();
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
          return periodFormat(period);
        }
      }
    } catch (Exception e) {
    }
    return "";
  }
  
  public static final String periodFormat(Period period) {
    if (period != null && period == Period.ZERO) {
      return "0 days";
    } else {
      StringBuilder buf = new StringBuilder();
      if (period.getYears() != 0) {
        buf.append(period.getYears()).append(period.getYears() > 1 ? " years" : " year");
        if (period.getMonths() != 0 || period.getDays() != 0) {
          buf.append(", ");
        }
      }

      if (period.getMonths() != 0) {
        buf.append(period.getMonths()).append(period.getMonths() > 1 ? " months" : " month");
        if (period.getDays() != 0) {
          buf.append(", ");
        }
      }

      if (period.getDays() != 0) {
        buf.append(period.getDays()).append(period.getDays() > 1 ? " days" : " day");
      }
      return buf.toString();
    }
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
          return "£" + minValue + " - £" + maxValue;
        } else if (!StringUtils.isBlank(maxValue)) {
          return "up to £" + maxValue;
        } else
          return "Not prepared to share details";
      }
    } catch (Exception e) {
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
    }
    return "";
  }
  
  /**
   * TODO This method output will only work for DOS6. This should be refactor as generic one
   */
  public static String getLocation(final ProcurementEvent event) {
    try {
      String criterionId = "Criterion 3";
      String groupId = event.getProject().getLotNumber().equals("1") ? "Group 5" : "Group 4";
      String questionId = "Question 6";
      String location = EventsHelper.getData(criterionId, groupId, questionId,
          event.getProcurementTemplatePayload().getCriteria());
      return Objects.nonNull(location) ? location : "";
    } catch (Exception e) {
    }
    return "";
  }

}
