package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import uk.gov.crowncommercial.dts.scale.cat.model.generated.OrganizationReference1;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface OcdsSections {
    String RECORDS = "records";
    String COMPILED_RELEASE = RECORDS + ".compiledRelease";
    String COMPILED_RELEASE_PARTIES = COMPILED_RELEASE + ".parties";
    String COMPILED_RELEASE_BUYER = COMPILED_RELEASE + ".buyer";
    String COMPILED_RELEASE_CONTRACTS = COMPILED_RELEASE + ".contracts";

    String COMPILED_RELEASE_PLANNING = COMPILED_RELEASE + ".planning";
    String COMPILED_RELEASE_PLANNING_BUDGET = COMPILED_RELEASE_PLANNING + ".budget";
    String COMPILED_RELEASE_PLANNING_DOCUMENTS = COMPILED_RELEASE_PLANNING + ".docs";
    String COMPILED_RELEASE_PLANNING_MILESTONES = COMPILED_RELEASE_PLANNING + ".milestones";

    String COMPILED_RELEASE_TENDER = COMPILED_RELEASE + ".tender";
    String COMPILED_RELEASE_TENDER_TENDERERS = COMPILED_RELEASE_TENDER + ".tenderers";
    String COMPILED_RELEASE_TENDER_DOCUMENTS = COMPILED_RELEASE_TENDER + ".documents";
    String COMPILED_RELEASE_TENDER_MILESTONES = COMPILED_RELEASE_TENDER + ".milestones";
    String COMPILED_RELEASE_TENDER_AMENDMENTS = COMPILED_RELEASE_TENDER + ".amendments";
    String COMPILED_RELEASE_TENDER_ENQUIRIES = COMPILED_RELEASE_TENDER + ".enquiries";
    String COMPILED_RELEASE_TENDER_CRITERIA = COMPILED_RELEASE_TENDER + ".criteria";
    String COMPILED_RELEASE_TENDER_SELECTIONCRITERIA = COMPILED_RELEASE_TENDER + ".selCriteria";
    String COMPILED_RELEASE_TENDER_TECHNIQUES = COMPILED_RELEASE_TENDER + ".techniques";


    String COMPILED_RELEASE_AWARDS = COMPILED_RELEASE + ".awards";
    String COMPILED_RELEASE_AWARDS_SUPPLIERS = COMPILED_RELEASE_AWARDS + ".suppliers";
    String COMPILED_RELEASE_AWARDS_ITEMS = COMPILED_RELEASE_AWARDS + ".items";
    String COMPILED_RELEASE_AWARDS_DOCUMENTS = COMPILED_RELEASE_AWARDS + ".documents";
    String COMPILED_RELEASE_AWARDS_AMENDMENTS = COMPILED_RELEASE_AWARDS + ".amendments";
    String COMPILED_RELEASE_AWARDS_REQRESPONSES = COMPILED_RELEASE_AWARDS + ".reqResponses";

    String COMPILED_RELEASE_STATISTICS = COMPILED_RELEASE + ".bids.statistics";

    List<String> SUMMARY_SECTIONS = Collections.unmodifiableList(Arrays.asList(
            COMPILED_RELEASE, COMPILED_RELEASE_PARTIES, COMPILED_RELEASE_BUYER,
            COMPILED_RELEASE_PLANNING, COMPILED_RELEASE_PLANNING_BUDGET,
            COMPILED_RELEASE_TENDER,
            COMPILED_RELEASE_TENDER_TENDERERS, COMPILED_RELEASE_TENDER_CRITERIA,
            COMPILED_RELEASE_AWARDS, COMPILED_RELEASE_AWARDS_SUPPLIERS,
            COMPILED_RELEASE_STATISTICS
    ));

    List<String> QA_SECTIONS = Collections.unmodifiableList(Arrays.asList(
            COMPILED_RELEASE,
            COMPILED_RELEASE_TENDER,
            COMPILED_RELEASE_TENDER_ENQUIRIES
    ));

    List<String> DETAILED_SECTIONS = Collections.unmodifiableList(Arrays.asList(
            COMPILED_RELEASE, COMPILED_RELEASE_PARTIES, COMPILED_RELEASE_BUYER,
            COMPILED_RELEASE_PLANNING, COMPILED_RELEASE_PLANNING_BUDGET,
            COMPILED_RELEASE_TENDER,
            COMPILED_RELEASE_TENDER_TENDERERS, COMPILED_RELEASE_TENDER_CRITERIA,
            COMPILED_RELEASE_AWARDS, COMPILED_RELEASE_AWARDS_SUPPLIERS
    ));

    public static List<String> getSection(String group){
        if(null == group)
            return SUMMARY_SECTIONS;
        switch (group){
            case"summary":
                return SUMMARY_SECTIONS;
            case "detail":
                return DETAILED_SECTIONS;
            case "qa":
                return QA_SECTIONS;
        }
        return SUMMARY_SECTIONS;
    }
}
