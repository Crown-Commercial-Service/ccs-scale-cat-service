package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class EventsHelper {
    private final static ObjectMapper mapper = new ObjectMapper();

    public static ProcurementEvent getFirstPublishedEvent(ProcurementProject pp) {
        return getPublishedEvent(pp, (d, s) -> {
            return d.getPublishDate().compareTo(s.getPublishDate());
        });
    }

    public static ProcurementEvent getLastPublishedEvent(ProcurementProject pp) {
        return getPublishedEvent(pp, (s, d) -> {
            return d.getPublishDate().compareTo(s.getPublishDate());
        });
    }

    public static ProcurementEvent getPublishedEvent(ProcurementProject pp, Comparator<ProcurementEvent> comparator) {
        return pp.getProcurementEvents().stream().filter(s -> null != s.getPublishDate())
                .sorted(comparator)
                .findFirst().orElse(null);
    }

    public static ProcurementEvent getAwardEvent(ProcurementProject pp) {
        return getLastPublishedEvent(pp);
    }

    public static String getData(String criteriaId, String groupId, String requirementId,
        List<TemplateCriteria> criteria) {
      Optional<TemplateCriteria> criterias =
          criteria.stream().filter(c -> c.getId().equalsIgnoreCase(criteriaId)).findAny();
      if (criterias.isPresent()) {
        return getData(groupId, requirementId, criterias.get().getRequirementGroups());
      } else {
        return null;
      }
    }

    private static String getData(String groupId, String requirementId,
        Set<RequirementGroup> requirementGroups) {
      for (RequirementGroup rg : requirementGroups) {
        if (isReqGroupMatch(rg, groupId)) {
          String result = getData(requirementId, rg.getOcds().getRequirements());
          if (null != result)
            return result;
        }
      }
      return null;
    }

    private static String getData(String requirementId, Set<Requirement> requirements) {
      for (Requirement r : requirements) {
        if (isReqMatch(r, requirementId)) {
          if (!Objects.isNull(r.getNonOCDS().getOptions()))
            return getFirstValue(r.getNonOCDS().getOptions());
        }
      }
      return null;
    }

    public static String getEventId(ProcurementEvent pe) {
        return pe.getOcdsAuthorityName() + "-" + pe.getOcidPrefix() + "-" + pe.getId();
    }

    @SneakyThrows
    public static String serializeValue(List<Requirement.Option> options) {
      return mapper.writeValueAsString(options);
    }

    private static String getFirstValue(List<Requirement.Option> options) {
        return options.stream().filter(t -> t.getSelect()).findFirst().map(t -> null != t ? t.getValue() : null).orElseGet(() -> null);
    }

    private static boolean isReqMatch(Requirement r, String requirementId) {
      return r.getOcds().getId().equalsIgnoreCase(requirementId);
    }

    private static boolean isReqGroupMatch(RequirementGroup rg, String groupId) {
      return rg.getOcds().getId().equalsIgnoreCase(groupId)
          && (null != rg.getOcds().getDescription());
    }
    
    /**
     * TODO This method output will only work for DOS6. This should be refactor as generic one
     */
    public static String getBudgetRangeData(final ProcurementEvent event) {
      String maxValue = null;
      String minValue = null;
      String groupId = event.getProject().getLotNumber() == "1" ? "Group 20" : "Group 18";
      if (Objects.nonNull(event.getProcurementTemplatePayload())) {
        maxValue = getData("Criterion 3", groupId, "Question 2",
            event.getProcurementTemplatePayload().getCriteria());
        minValue = getData("Criterion 3", groupId, "Question 3",
            event.getProcurementTemplatePayload().getCriteria());
        if (!StringUtils.isBlank(minValue) & !StringUtils.isBlank(minValue)) {
          return "£" + minValue + "- £" + maxValue;
        } else if (!StringUtils.isBlank(maxValue)) {
          return "up to £" + maxValue;
        } else
          return "Not prepared to share details";
      }
      return null;
    }
    
    private static final String AWARD_STATUS = "complete";
    
    public static String getEventStatus(String status) {
      if (status.equals(AWARD_STATUS)) {
        return "Closed";
      }
      return "Open";
    }
    
    public static String getSummaryOfWork(ProcurementEvent event) {
      if (Objects.nonNull(event.getProcurementTemplatePayload())) {
        var summary = getData("Criterion 3", "Group 3", "Question 1",
            event.getProcurementTemplatePayload().getCriteria());
        if (!StringUtils.isBlank(summary)) {
          return summary;
        }
      }
      return null;
    }
}
