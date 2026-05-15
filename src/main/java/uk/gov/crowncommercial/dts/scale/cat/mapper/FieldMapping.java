package uk.gov.crowncommercial.dts.scale.cat.mapper;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum FieldMapping {

    // Conditions of participation table
    COP_QUESTION("COND_OF_PART", "«cop_question»", "Enter your question", "«cop_group_name»"),
    COP_RES("COND_OF_PART", "«cop_res»", "What type of response would you expect from the supplier for this question?", "«cop_group_name»"),
    COP_RES_SIZE("COND_OF_PART", "«cop_res_size»", "Describe the response length/limit for this answer (e.g. 'not to exceed 2500 characters')", "«cop_group_name»"),
    COP_DOC("COND_OF_PART", "«cop_doc»", "Would the supplier need to attach any document?", "«cop_group_name»"),
    COP_DOC_DES("COND_OF_PART", "«cop_doc_des»", "What type of document do you want the supplier to attach?", "«cop_group_name»"),

    AC_QUESTION("AWARD_CRITERIA", "«ac_question»", "Enter your question", "«ac_group_name»"),

    // --- NEW MULTI-STAGE MAPPINGS ---
    // All mapped to STAGE_DESC table and share the «stage_group» anchor
    MS_STAGE_GROUP_DETAILS("STAGE_DESC", "«stage_description»", "STAGE_DESCRIPTION", "«stage_group»"),
    MS_STAGE_TOTAL("STAGE_DESC", "«total_stages»", "TOTAL_STAGES", "«stage_group»"),
    MS_STAGE_CURRENT_STAGE("STAGE_DESC", "«current_stage»", "CURRENT_STAGE", "«stage_group»");

    private final String tableName;
    private final String placeholder;
    private final String title;
    private final String groupName;

    FieldMapping(String tableName, String placeholder, String title, String groupName) {
        this.tableName = tableName;
        this.placeholder = placeholder;
        this.title = title;
        this.groupName = groupName;
    }

    public static List<FieldMapping> getFieldsByTableName(String tableName) {
        return Arrays.stream(values())
                .filter(m -> m.tableName.equalsIgnoreCase(tableName))
                .toList();
    }

    public static String getAnchorPlaceholder(String tableName) {
        return Arrays.stream(values())
                .filter(m -> m.tableName.equalsIgnoreCase(tableName))
                .findFirst()
                .map(FieldMapping::getPlaceholder)
                .orElseThrow(() ->
                        new IllegalArgumentException("No mapping found for table: " + tableName));
    }

    public static String getTableGroupName(String tableName) {
        return Arrays.stream(values())
                .filter(m -> m.tableName.equalsIgnoreCase(tableName))
                .findFirst()
                .map(FieldMapping::getGroupName)
                .orElseThrow(() ->
                        new IllegalArgumentException("No mapping found for table: " + tableName));
    }
}
