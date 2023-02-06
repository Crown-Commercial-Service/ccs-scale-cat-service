package uk.gov.crowncommercial.dts.scale.cat.repo.specification;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProjectUserMapping;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProjectSearchSpecification implements Specification<ProjectUserMapping> {

  public static final String SEARCH_KEY_PROJECT_NAME = "projectName";
  public static final String SEARCH_KEY_EVENT_ID = "id";
  public static final String SEARCH_KEY_EXTERNAL_REFERENCE_ID = "externalReferenceId";
  public static final String SEARCH_TYPE_PROJECT_NAME = "projectName";
  public static final String SEARCH_TYPE_EVENT_ID = "eventId";
  public static final String SEARCH_TYPE_EVENT_SUPPORT_ID = "eventSupportId";
  public static final String SEARCH_KEY_USER_ID = "userId";
  public static final String CHILD_ELEMENT_PROJECT = "project";
  public static final String CHILD_ELEMENT_PROCUREMENT_EVENTS = "procurementEvents";
  private ProjectSearchCriteria projectSearchCriteria;

  public ProjectSearchSpecification(final ProjectSearchCriteria projectSearchCriteria) {
    super();
    this.projectSearchCriteria = projectSearchCriteria;
  }

  public ProjectSearchCriteria getCriteria() {
    return projectSearchCriteria;
  }

  @Override
  public Predicate toPredicate(
      Root<ProjectUserMapping> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {

    List<Predicate> predicates = new ArrayList<>();

    Predicate predicate =
        criteriaBuilder.equal(root.get(SEARCH_KEY_USER_ID), projectSearchCriteria.getUserId());
    predicates.add(predicate);

    //Child Joins
    Join<ProcurementProject, ProjectUserMapping> project =
        root.join(CHILD_ELEMENT_PROJECT, JoinType.LEFT);
    Join<ProcurementEvent, ProcurementProject> event =
        project.join(CHILD_ELEMENT_PROCUREMENT_EVENTS, JoinType.LEFT);


    if (Objects.nonNull(projectSearchCriteria.getSearchType())) {
        switch (projectSearchCriteria.getSearchType()) {
            case SEARCH_TYPE_PROJECT_NAME -> buildProjectNameCriteria(criteriaBuilder, predicates, project);

            case SEARCH_TYPE_EVENT_ID -> buildEventIdCriteria(criteriaBuilder, predicates, event);

            case SEARCH_TYPE_EVENT_SUPPORT_ID -> buildEventSupportIdCriteria(criteriaBuilder, predicates, event);

            default -> {
            }
        }
    }
      return criteriaBuilder.and(predicates.toArray(new Predicate[predicates.size()]));
  }

  private void buildEventSupportIdCriteria(
      CriteriaBuilder criteriaBuilder,
      List<Predicate> predicates,
      Join<ProcurementEvent, ProcurementProject> event) {

    if(isSearchTermValid(projectSearchCriteria.getSearchTerm())){

      String sanitizedSearchTerm=projectSearchCriteria.getSearchTerm().trim();
      if (isLikeCondition(sanitizedSearchTerm)) {
            predicates.add(
              criteriaBuilder.like(criteriaBuilder.upper(
                                                   event.get(SEARCH_KEY_EXTERNAL_REFERENCE_ID)),
                                                   convertToLikeString(sanitizedSearchTerm)));
          } else {
           predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(
                                                  event.get(SEARCH_KEY_EXTERNAL_REFERENCE_ID)),
                                                  sanitizedSearchTerm.toUpperCase()));
      }

    }
  }

  private void buildEventIdCriteria(
          CriteriaBuilder criteriaBuilder,
          List<Predicate> predicates,
          Join<ProcurementEvent, ProcurementProject> event) {

    if(isSearchTermValid(projectSearchCriteria.getSearchTerm())){

      String sanitizedSearchTerm=projectSearchCriteria.getSearchTerm().trim();

      if (isLikeCondition(sanitizedSearchTerm)) {
        predicates.add(
                criteriaBuilder.like(criteriaBuilder.upper(
                                        event.get(SEARCH_KEY_EVENT_ID).as(String.class)),
                                        convertToLikeString(sanitizedSearchTerm)));

      } else {
        predicates.add(
                criteriaBuilder.equal(criteriaBuilder.upper(
                                        event.get(SEARCH_KEY_EVENT_ID)),
                                        sanitizedSearchTerm.toUpperCase()));
      }
    }
  }




  private void buildProjectNameCriteria(
      CriteriaBuilder criteriaBuilder,
      List<Predicate> predicates,
      Join<ProcurementProject, ProjectUserMapping> project) {

    if(isSearchTermValid(projectSearchCriteria.getSearchTerm())){

      String sanitizedSearchTerm=projectSearchCriteria.getSearchTerm().trim();
      if (isLikeCondition(sanitizedSearchTerm)) {
        predicates.add(
            criteriaBuilder.like(criteriaBuilder.upper(project.get(SEARCH_KEY_PROJECT_NAME)),
                convertToLikeString(sanitizedSearchTerm)));

       } else {
        predicates.add(
            criteriaBuilder.equal(criteriaBuilder.upper(project.get(SEARCH_KEY_PROJECT_NAME)),sanitizedSearchTerm.toUpperCase()));
      }
    }
  }

  private boolean isLikeCondition(String searchTerm) {
    return (Objects.nonNull(searchTerm)
        && (StringUtils.startsWith(searchTerm, "*")
            || StringUtils.endsWith(searchTerm, "*")));
  }

  private String convertToLikeString(String searchTerm) {

    if (StringUtils.isNotEmpty(searchTerm)) {

      if(StringUtils.startsWith(projectSearchCriteria.getSearchTerm(), "*") && StringUtils.endsWith(projectSearchCriteria.getSearchTerm(), "*")){
        return "%" + projectSearchCriteria.getSearchTerm().toUpperCase().replaceAll("\\*", "") + "%";
      }else if(StringUtils.startsWith(projectSearchCriteria.getSearchTerm(), "*")){
        return "%" + projectSearchCriteria.getSearchTerm().toUpperCase().replaceAll("\\*", "");
      }else if (StringUtils.endsWith(projectSearchCriteria.getSearchTerm(), "*")){
        return projectSearchCriteria.getSearchTerm().toUpperCase().replaceAll("\\*", "") + "%";
      }
    }
    return StringUtils.EMPTY;
  }

  private boolean isSearchTermValid(String searchTerm) {
    return StringUtils.isNotEmpty(searchTerm)
            && StringUtils.isNotEmpty(searchTerm.trim());
  }
}
