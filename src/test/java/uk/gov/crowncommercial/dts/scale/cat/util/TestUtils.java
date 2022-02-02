package uk.gov.crowncommercial.dts.scale.cat.util;

import java.util.Collection;
import java.util.Set;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

public class TestUtils {

  public static final String EXPRESSION_OF_INTEREST = "Expression of Interest";
  public static final String REQUEST_FOR_INFORMATION = "Request for Information";
  public static final String FURTHER_COMPETITION_ASSESSMENT = "Further Competition Assessment";
  public static final String DIRECT_AWARD = "Direct Award";
  public static final String FURTHER_COMPETITION = "Further Competition";

  public static final String USER_NAME = "Jim Beam";
  public static final String USERID = "jbeam@ccs.org.uk";

  public static Collection<EventType> getEventTypes() {
    return Set.of(getEventType(DefineEventType.RFI, REQUEST_FOR_INFORMATION, true),
        getEventType(DefineEventType.EOI, EXPRESSION_OF_INTEREST, true),
        getEventType(DefineEventType.FCA, FURTHER_COMPETITION_ASSESSMENT, true),
        getEventType(DefineEventType.DA, DIRECT_AWARD, false),
        getEventType(DefineEventType.FC, FURTHER_COMPETITION, false));
  }

  public static EventType getEventType(final DefineEventType defineEventType,
      final String description, final boolean preMarketActivity) {
    var eoiEventType = new EventType();
    eoiEventType.setType(defineEventType);
    eoiEventType.setDescription(description);
    eoiEventType.setPreMarketActivity(preMarketActivity);
    return eoiEventType;
  }

  public static Collection<LotEventType> getLotEventTypes() {
    return Set.of(getLotEventType("EOI", EXPRESSION_OF_INTEREST, true),
        getLotEventType("RFI", REQUEST_FOR_INFORMATION, true),
        getLotEventType("FCA", FURTHER_COMPETITION_ASSESSMENT, true),
        getLotEventType("DA", DIRECT_AWARD, false),
        getLotEventType("FC", FURTHER_COMPETITION, false));
  }

  private static LotEventType getLotEventType(final String type, final String description,
      final boolean preMarketActivity) {
    var eoiEventType = new LotEventType();
    eoiEventType.setType(type);
    eoiEventType.setDescription(description);
    eoiEventType.setPreMarketActivity(preMarketActivity);
    return eoiEventType;
  }

  public static TeamMember getTeamMember() {
    var teamMember = new TeamMember();
    var contact = new ContactPoint1();
    contact.setEmail(USERID);
    contact.setName(USER_NAME);

    var tmOCDS = new TeamMemberOCDS();
    tmOCDS.setId(USERID);
    tmOCDS.setContact(contact);

    var tmNoNOCDS = new TeamMemberNonOCDS();
    tmNoNOCDS.setTeamMember(true);
    tmNoNOCDS.setEmailRecipient(false);
    tmNoNOCDS.setProjectOwner(false);

    teamMember.setOCDS(tmOCDS);
    teamMember.setNonOCDS(tmNoNOCDS);
    return teamMember;
  }
}
