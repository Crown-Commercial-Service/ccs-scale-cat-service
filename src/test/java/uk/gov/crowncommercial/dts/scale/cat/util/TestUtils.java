package uk.gov.crowncommercial.dts.scale.cat.util;

import java.util.Arrays;
import java.util.List;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.ProjectEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Project;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ProjectList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ProjectListResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Tender;

public class TestUtils {

  public static final String EXPRESSION_OF_INTEREST = "Expression of Interest";
  public static final String REQUEST_FOR_INFORMATION = "Request for Information";
  public static final String CAPABILITY_ASSESSMENT = "Capability Assessment";
  public static final String DIRECT_AWARD = "Direct Award";
  public static final String FURTHER_COMPETITION = "Further Competition";

  public static final String USER_NAME = "Jim Beam";
  public static final String USERID = "jbeam@ccs.org.uk";

  public static EventType getEventType(final DefineEventType defineEventType,
      final String description, final boolean preMarketActivity) {
    var eoiEventType = new EventType();
    eoiEventType.setType(defineEventType);
    eoiEventType.setDescription(description);
    eoiEventType.setPreMarketActivity(preMarketActivity);
    return eoiEventType;
  }

  public static List<EventType> getEventTypes() {
    var eventTypes =
        new EventType[] {getEventType(DefineEventType.EOI, EXPRESSION_OF_INTEREST, true),
            getEventType(DefineEventType.RFI, REQUEST_FOR_INFORMATION, true),
            getEventType(DefineEventType.CA, CAPABILITY_ASSESSMENT, true),
            getEventType(DefineEventType.DA, DIRECT_AWARD, true),
            getEventType(DefineEventType.FC, FURTHER_COMPETITION, true)};
    return Arrays.asList(eventTypes);
  }

  private static ProjectEventType getProjectEventType(final String type, final String description,
      final boolean preMarketActivity) {
    var eoiEventType = new ProjectEventType();
    eoiEventType.setType(type);
    eoiEventType.setDescription(description);
    eoiEventType.setPreMarketActivity(preMarketActivity);
    return eoiEventType;
  }

  public static ProjectEventType[] getProjectEvents() {
    return new ProjectEventType[] {getProjectEventType("EOI", EXPRESSION_OF_INTEREST, true),
        getProjectEventType("RFI", REQUEST_FOR_INFORMATION, true),
        getProjectEventType("CA", CAPABILITY_ASSESSMENT, true),
        getProjectEventType("DA", DIRECT_AWARD, false),
        getProjectEventType("FC", FURTHER_COMPETITION, false)};
  }

  public static TeamMember getTeamMember() {
    TeamMember teamMember = new TeamMember();
    ContactPoint1 contact = new ContactPoint1();
    contact.setEmail(USERID);
    contact.setName(USER_NAME);

    TeamMemberOCDS tmOCDS = new TeamMemberOCDS();
    tmOCDS.setId(USERID);
    tmOCDS.setContact(contact);

    TeamMemberNonOCDS tmNoNOCDS = new TeamMemberNonOCDS();
    tmNoNOCDS.setTeamMember(true);
    tmNoNOCDS.setEmailRecipient(false);
    tmNoNOCDS.setProjectOwner(false);

    teamMember.setOCDS(tmOCDS);
    teamMember.setNonOCDS(tmNoNOCDS);
    return teamMember;
  }

  public static ProjectListResponse getProjectListResponse() {
    var test = "Test";
    var projectListResponse = ProjectListResponse.builder()
            .projectList(ProjectList.builder()
                    .project(Arrays.asList(
                            Project.builder()
                                    .tender(Tender.builder()
                                            .tenderReferenceCode(test)
                                            .tenderStatusLabel("project.state.running")
                                            .tenderCode(test)
                                            .title(test)
                                            .build())
                                    .build()
                    ))
                    .build())
            .build();

    return projectListResponse;
  }
}
