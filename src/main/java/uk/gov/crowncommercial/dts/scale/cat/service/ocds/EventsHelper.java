package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPublicDetail.StatusEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TenderStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

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
    
    public static Pair<ProcurementEvent, ProcurementEvent> getFirstAndLastPublishedEvent(ProcurementProject pp) {
      if (pp.getProcurementEvents().size() > 1) {
          return Pair.of(EventsHelper.getFirstPublishedEvent(pp), EventsHelper.getLastPublishedEvent(pp));
      }
        return Pair.of(EventsHelper.getFirstPublishedEvent(pp), null);
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
      return options.stream().filter(t -> t.getSelect())
          .toList().stream().map(i -> i.getValue()).collect(Collectors.joining(", "));
    }

    private static boolean isReqMatch(Requirement r, String requirementId) {
      return r.getOcds().getId().equalsIgnoreCase(requirementId);
    }

    private static boolean isReqGroupMatch(RequirementGroup rg, String groupId) {
      return rg.getOcds().getId().equalsIgnoreCase(groupId)
          && (null != rg.getOcds().getDescription());
    }
    
}
