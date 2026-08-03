package uk.gov.crowncommercial.dts.scale.cat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.odftoolkit.odfdom.dom.element.table.TableCoveredTableCellElement;
import org.odftoolkit.odfdom.dom.element.table.TableTableElement;
import org.odftoolkit.odfdom.dom.element.text.TextPElement;
import org.odftoolkit.odfdom.dom.style.props.OdfTableCellProperties;
import org.odftoolkit.odfdom.pkg.OdfElement;
import org.odftoolkit.odfdom.pkg.OdfFileDom;
import org.odftoolkit.simple.TextDocument;
import org.odftoolkit.simple.common.navigation.InvalidNavigationException;
import org.odftoolkit.simple.common.navigation.TextNavigation;
import org.odftoolkit.simple.common.navigation.TextSelection;
import org.odftoolkit.simple.style.Font;
import org.odftoolkit.simple.style.StyleTypeDefinitions;
import org.odftoolkit.simple.table.Cell;
import org.odftoolkit.simple.table.CellRange;
import org.odftoolkit.simple.table.Row;
import org.odftoolkit.simple.table.Table;
import org.odftoolkit.simple.text.Paragraph;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Node;
import uk.gov.crowncommercial.dts.scale.cat.mapper.FieldMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentTemplateSource;

import java.util.*;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class TableGroupGenerator {

    private static final String TOKEN_L = "«";
    private static final String TOKEN_R = "»";
    private static final String SUPPLIER_MARKER = "supplier response";
    private static final String PLACEHOLDER_UNKNOWN = "Not Specified";
    private static final String SELECT_GROUP_NAME_TITLE = "Select group name";
    private static final String COND_OF_PART = "COND_OF_PART";
    private static final String AWARD_CRITERIA = "AWARD_CRITERIA";
    private static final String COND_OF_PART_DESCRIPTION = "Conditions of participation";
    private static final String AWARD_CRITERIA_DESCRIPTION = "Award criteria";
    private static final String STAGE_DESCRIPTION_HEADER_TAG = "Stage description";
    private static final String CURRENT_STAGE = "CURRENT_STAGE";
    private static final String STAGE_DESCRIPTION = "STAGE_DESCRIPTION";
    private static final String TOTAL_STAGES = "TOTAL_STAGES";
    private static final String TEXT_FONT_NAME = "Arial";
    private static final double TEXT_FONT_SIZE = 12.0;
    private static final String TOKEN_CLEAN_PATTERN_REGEX = "«[^»]*»|«[^”]*”";
    private static final String COP_QUESTION_ANCHOR_TAG = "«cop_question»";
    private static final String QUESTION_TITLE = "Enter your question";
    private static final String UNNAMED_GROUP_KEY = "unnamed";

    private final ObjectMapper objectMapper;

    public void fillTableData(String eventData, DocumentTemplateSource templateSource, TextDocument textODT) {
        if (!StringUtils.hasText(eventData) || templateSource == null || textODT == null) {
            return;
        }

        String tableName = templateSource.getTableName();
        List<FieldMapping> fieldMappings = FieldMapping.getFieldsByTableName(tableName);
        String anchorPlaceholder = FieldMapping.getAnchorPlaceholder(tableName);
        String groupNamePlaceholder = FieldMapping.getTableGroupName(tableName);

        List<Map<String, Object>> requirementGroups = readRequirementGroups(eventData, templateSource.getSourcePath());
        requirementGroups = filterRequirementGroupsForTable(requirementGroups, tableName);

        if (requirementGroups.isEmpty()) {
            replaceAllPlaceholdersWithUnknown(textODT, groupNamePlaceholder, fieldMappings);
            return;
        }

        LinkedHashMap<String, GroupBucket> grouped = groupRequirementGroups(requirementGroups);

        sortGroupedTableData(grouped);

        buildTableGroup(grouped, textODT, tableName, anchorPlaceholder, groupNamePlaceholder, fieldMappings);
    }

    private List<Map<String, Object>> readRequirementGroups(String eventData, String sourcePath) {
        if (!StringUtils.hasText(sourcePath)) {
            return Collections.emptyList();
        }

        try {
            Configuration jsonPathConfig = Configuration.builder()
                    .options(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST)
                    .jsonProvider(new JacksonJsonProvider(objectMapper))
                    .mappingProvider(new JacksonMappingProvider(objectMapper))
                    .build();

            TypeRef<List<Map<String, Object>>> typeRef = new TypeRef<>() {};
            return JsonPath.using(jsonPathConfig)
                    .parse(eventData)
                    .read(sourcePath, typeRef);
        } catch (Exception ex) {
            log.error("Unable to parse grouped table data from sourcePath '{}'", sourcePath, ex);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> filterRequirementGroupsForTable(List<Map<String, Object>> requirementGroups,
                                                                      String tableName) {
        if (!StringUtils.hasText(tableName)) {
            return requirementGroups;
        }

        String expectedDescription = switch (tableName.toUpperCase(Locale.UK)) {
            case COND_OF_PART -> COND_OF_PART_DESCRIPTION;
            case AWARD_CRITERIA -> AWARD_CRITERIA_DESCRIPTION;
            default -> null;
        };

        if (!StringUtils.hasText(expectedDescription)) {
            return requirementGroups;
        }

        return requirementGroups.stream()
                .filter(requirementGroup -> expectedDescription.equalsIgnoreCase(extractGroupDescription(requirementGroup)))
                .toList();
    }

    private LinkedHashMap<String, GroupBucket> groupRequirementGroups(List<Map<String, Object>> requirementGroups) {
        LinkedHashMap<String, GroupBucket> grouped = new LinkedHashMap<>();

        for (Map<String, Object> rgMap : requirementGroups) {
            String selectedGroupName = extractSelectedGroupName(rgMap);
            Integer groupOrder = getRequirementGroupOrder(rgMap);

            String key;
            String displayName;

            if (StringUtils.hasText(selectedGroupName) && groupOrder != null && groupOrder > 0) {
                key = "named::" + norm(selectedGroupName);
                displayName = selectedGroupName.trim();
            } else {
                key = UNNAMED_GROUP_KEY;
                displayName = "(no group)";
            }

            GroupBucket bucket = grouped.computeIfAbsent(key, k -> new GroupBucket(displayName));
            bucket.requirementGroups.add(rgMap);
        }

        return grouped;
    }

    @SuppressWarnings("unchecked")
    private String extractSelectedGroupName(Map<String, Object> rgMap) {
        Map<String, Object> ocds = (Map<String, Object>) rgMap.get("OCDS");
        if (ocds == null) {
            return null;
        }

        List<Map<String, Object>> requirements = (List<Map<String, Object>>) ocds.get("requirements");
        if (requirements == null) {
            return null;
        }

        for (Map<String, Object> req : requirements) {
            Map<String, Object> reqOcds = (Map<String, Object>) req.get("OCDS");
            String title = reqOcds == null ? null : (String) reqOcds.get("title");

            if (!SELECT_GROUP_NAME_TITLE.equalsIgnoreCase(title)) {
                continue;
            }

            Map<String, Object> nonOcds = (Map<String, Object>) req.get("nonOCDS");
            List<Map<String, Object>> options = nonOcds == null ? null : (List<Map<String, Object>>) nonOcds.get("options");

            if (options == null) {
                return null;
            }

            for (Map<String, Object> option : options) {
                if (Boolean.TRUE.equals(option.get("select"))) {
                    Object value = option.get("value");

                    if (value != null && StringUtils.hasText(value.toString())) {
                        return value.toString().trim();
                    }

                    return null;
                }
            }

            return null;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractGroupDescription(Map<String, Object> rgMap) {
        Map<String, Object> ocds = (Map<String, Object>) rgMap.get("OCDS");
        if (ocds == null) {
            return null;
        }

        Object description = ocds.get("description");
        return description == null ? null : description.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> extractRowsGeneric(Map<String, Object> rgMap,
                                                         Map<String, String> titleToPlaceholder,
                                                         Collection<String> placeholders) {

        List<Map<String, String>> rows = new ArrayList<>();

        Map<String, Object> ocds = (Map<String, Object>) rgMap.get("OCDS");
        if (ocds == null) {
            return rows;
        }

        List<Map<String, Object>> requirements = (List<Map<String, Object>>) ocds.get("requirements");
        if (requirements == null) {
            return rows;
        }

        // One output row per requirement group
        Map<String, String> row = new LinkedHashMap<>();
        for (String placeholder : placeholders) {
            row.put(placeholder, PLACEHOLDER_UNKNOWN);
        }

        for (Map<String, Object> req : requirements) {
            Map<String, Object> reqOcds = (Map<String, Object>) req.get("OCDS");
            String title = reqOcds == null ? null : (String) reqOcds.get("title");

            if (title == null) {
                continue;
            }

            String placeholder = titleToPlaceholder.get(norm(title));
            if (placeholder == null) {
                continue;
            }

            row.put(placeholder, readSelectedOptionValue(req));
        }

        rows.add(row);
        return rows;
    }

    @SuppressWarnings("unchecked")
    private String readSelectedOptionValue(Map<String, Object> req) {
        Map<String, Object> nonOcds = (Map<String, Object>) req.get("nonOCDS");
        if (nonOcds == null) {
            return "";
        }

        List<Map<String, Object>> options = (List<Map<String, Object>>) nonOcds.get("options");
        if (options == null) {
            return "";
        }

        for (Map<String, Object> option : options) {
            if (Boolean.TRUE.equals(option.get("select"))) {
                Object value = option.get("value");
                String strValue = value == null ? "" : value.toString().trim();
                return strValue.isEmpty() ? PLACEHOLDER_UNKNOWN : strValue;
            }
        }

        return PLACEHOLDER_UNKNOWN;
    }

    private void fillOneTable(Table table,
                              List<Map<String, Object>> groupRequirementGroups,
                              String rowAnchorPlaceholder,
                              List<FieldMapping> mappings) {

        int anchorIdx = findRowContaining(table, rowAnchorPlaceholder);
        if (anchorIdx < 0) {
            log.warn("Anchor row not found for placeholder '{}'", rowAnchorPlaceholder);
            return;
        }

        Map<String, String> titleToPlaceholder = new HashMap<>();
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();

        for (FieldMapping mapping : mappings) {
            placeholders.add(mapping.getPlaceholder());
            titleToPlaceholder.put(norm(mapping.getTitle()), mapping.getPlaceholder());
        }

        List<Integer> blockIdxs = findTemplateBlockRowIndexes(table, anchorIdx);

        List<Row> templateRows = new ArrayList<>(blockIdxs.size());
        for (int idx : blockIdxs) {
            templateRows.add(table.getRowByIndex(idx));
        }

        String lastProcessedGroupTitle = null;
        int numberColIdx = findNumberColumnIndex(table);
        int counter = 1;

        for (Map<String, Object> rgMap : groupRequirementGroups) {
            List<Map<String, String>> rows = extractRowsGeneric(rgMap, titleToPlaceholder, placeholders);

            for (Map<String, String> rowMap : rows) {
                final String currentGroupItemTitle = extractSelectedGroupName(rgMap);

                if (StringUtils.hasText(currentGroupItemTitle) && !currentGroupItemTitle.equals(lastProcessedGroupTitle)) {
                    injectInlineHeaderBannerRow(table, templateRows, currentGroupItemTitle);
                    lastProcessedGroupTitle = currentGroupItemTitle;
                    counter = 1;
                }

                List<Row> createdBlockRows = new ArrayList<>(templateRows.size());

                for (Row templateRow : templateRows) {
                    Row newRow = appendClonedRow(table, templateRow);
                    replacePlaceholdersInRow(newRow, rowMap);
                    createdBlockRows.add(newRow);
                }

                if (numberColIdx >= 0) {
                    String number = String.valueOf(counter);

                    for (Row row : createdBlockRows) {
                        setCellTextIfNotCovered(row, numberColIdx, number);
                    }
                }

                counter++;
            }
        }

        // Remove the original template block rows
        for (int i = blockIdxs.size() - 1; i >= 0; i--) {
            table.removeRowsByIndex(blockIdxs.get(i), 1);
        }
    }

    private void buildTableGroup(Map<String, GroupBucket> grouped,
                                 TextDocument textODT,
                                 String tableName,
                                 String rowAnchorPlaceholder,
                                 String groupNamePlaceholder,
                                 List<FieldMapping> mappings) {

        if (grouped.isEmpty()) {
            replaceAllPlaceholdersWithUnknown(textODT, groupNamePlaceholder, mappings);
            return;
        }

        Table prototype = textODT.getTableByName(tableName);
        if (prototype == null) {
            throw new IllegalStateException("Table not found in ODT: " + tableName);
        }

        TableTableElement prototypeSnapshot = (TableTableElement) prototype.getOdfElement().cloneNode(true);

        Iterator<GroupBucket> iterator = grouped.values().iterator();

        // First group uses the existing group-name placeholder already in the template
        GroupBucket first = iterator.next();
        replaceFirstTextOccurrence(textODT, groupNamePlaceholder, first.displayName);
        fillOneTable(prototype, first.requirementGroups, rowAnchorPlaceholder, mappings);

        TableTableElement lastTableElem = prototype.getOdfElement();
        int cloneIndex = 2;

        while (iterator.hasNext()) {
            GroupBucket bucket = iterator.next();
            String newTableName = tableName + "_" + (cloneIndex++);

            TextPElement heading = insertGroupHeadingAfterTable(textODT, lastTableElem, bucket.displayName);
            Table cloned = cloneTableAfterParagraph(textODT, prototypeSnapshot, heading, newTableName);

            fillOneTable(cloned, bucket.requirementGroups, rowAnchorPlaceholder, mappings);
            lastTableElem = (TableTableElement) cloned.getOdfElement();
        }
    }

    private void replaceAllPlaceholdersWithUnknown(TextDocument textODT,
                                                   String groupNamePlaceholder,
                                                   List<FieldMapping> fieldMappings) {

        replaceAllTextOccurrences(textODT, groupNamePlaceholder, PLACEHOLDER_UNKNOWN);

        for (FieldMapping mapping : fieldMappings) {
            replaceAllTextOccurrences(textODT, mapping.getPlaceholder(), PLACEHOLDER_UNKNOWN);
        }
    }

    private static List<Integer> findTemplateBlockRowIndexes(Table table, int anchorIdx) {
        List<Integer> indexes = new ArrayList<>();

        for (int r = anchorIdx; r < table.getRowCount(); r++) {
            Row row = table.getRowByIndex(r);

            if (r == anchorIdx) {
                indexes.add(r);
                continue;
            }

            if (!rowContainsPlaceholderToken(row) && !isSupplierRow(row)) {
                break;
            }

            indexes.add(r);
        }

        return indexes;
    }

    private static boolean isSupplierRow(Row row) {
        int cols = row.getTable().getColumnCount();

        for (int c = 0; c < cols; c++) {
            Cell cell = row.getCellByIndex(c);

            if (isCovered(cell)) {
                continue;
            }

            String txt = cell.getOdfElement().getTextContent();
            if (txt != null && txt.toLowerCase(Locale.UK).contains(SUPPLIER_MARKER)) {
                return true;
            }
        }

        return false;
    }

    private static boolean rowContainsPlaceholderToken(Row row) {
        int cols = row.getTable().getColumnCount();

        for (int c = 0; c < cols; c++) {
            Cell cell = row.getCellByIndex(c);

            if (isCovered(cell)) {
                continue;
            }

            String txt = cell.getOdfElement().getTextContent();
            if (containsToken(txt)) {
                return true;
            }
        }

        return false;
    }

    private static void replacePlaceholdersInRow(Row row, Map<String, String> replacements) {
        int cols = row.getTable().getColumnCount();

        for (int c = 0; c < cols; c++) {
            Cell cell = row.getCellByIndex(c);

            if (isCovered(cell)) {
                continue;
            }

            try {
                String txt = cell.getOdfElement().getTextContent();
                if (!containsToken(txt)) {
                    continue;
                }

                String out = txt;

                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    String placeholder = entry.getKey();
                    String value = entry.getValue() == null ? "" : entry.getValue();
                    out = out.replace(placeholder, value);
                }

                // Remove anything unresolved in this cloned row
                if (containsToken(out)) {
                    out = out.replaceAll("«.*?»", "");
                }

                if (!out.equals(txt)) {
                    cell.removeTextContent();
                    cell.setStringValue(out);
                }
            } catch (Exception ex) {
                log.warn("Error replacing grouped-table token in column {}", c, ex);
                safeClearCell(cell);
            }
        }
    }

    private static void safeClearCell(Cell cell) {
        try {
            cell.removeTextContent();
            cell.setStringValue("");
        } catch (Exception ignored) {
            // Intentionally fail-soft
        }
    }

    private static Row appendClonedRow(Table table, Row templateRow) {
        try {
            org.odftoolkit.odfdom.dom.element.table.TableTableRowElement clone =
                    (org.odftoolkit.odfdom.dom.element.table.TableTableRowElement)
                            templateRow.getOdfElement().cloneNode(true);

            table.getOdfElement().appendChild(clone);
            return table.getRowByIndex(table.getRowCount() - 1);
        } catch (Exception ex) {
            log.warn("Row cloning failed for grouped table", ex);
            return table.appendRow();
        }
    }

    private static boolean isCovered(Cell cell) {
        return cell.getOdfElement() instanceof TableCoveredTableCellElement;
    }

    private static void setCellTextIfNotCovered(Row row, int colIdx, String value) {
        Cell cell = row.getCellByIndex(colIdx);

        if (isCovered(cell)) {
            return;
        }

        cell.setStringValue(value == null ? "" : value);
    }

    private static int findRowContaining(Table table, String needle) {
        for (int r = 0; r < table.getRowCount(); r++) {
            Row row = table.getRowByIndex(r);
            int cols = row.getTable().getColumnCount();

            for (int c = 0; c < cols; c++) {
                Cell cell = row.getCellByIndex(c);

                if (isCovered(cell)) {
                    continue;
                }

                String txt = cell.getOdfElement().getTextContent();
                if (txt != null && txt.contains(needle)) {
                    return r;
                }
            }
        }

        return -1;
    }

    private static int findNumberColumnIndex(Table table) {
        if (table.getRowCount() == 0) {
            return -1;
        }

        Row header = table.getRowByIndex(0);
        int cols = table.getColumnCount();

        for (int c = 0; c < cols; c++) {
            String txt = header.getCellByIndex(c).getStringValue();

            if (txt != null && (txt.trim().equals("#") || txt.trim().equals("1"))) {
                return c;
            }
        }

        return -1;
    }

    private static void replaceFirstTextOccurrence(TextDocument doc, String placeholder, String value) {
        if (!StringUtils.hasText(placeholder)) {
            return;
        }

        TextNavigation nav = new TextNavigation(placeholder, doc);
        TextSelection selection = (TextSelection) nav.nextSelection();

        if (selection == null) {
            log.warn("Group heading placeholder not found: {}", placeholder);
            return;
        }

        try {
            selection.replaceWith(value == null ? "" : value);
        } catch (InvalidNavigationException ex) {
            log.warn("Failed to replace grouped heading placeholder {}", placeholder, ex);
        }
    }

    private static void replaceAllTextOccurrences(TextDocument doc, String placeholder, String value) {
        if (!StringUtils.hasText(placeholder)) {
            return;
        }

        TextNavigation nav = new TextNavigation(placeholder, doc);

        while (nav.hasNext()) {
            try {
                TextSelection selection = (TextSelection) nav.nextSelection();
                selection.replaceWith(value == null ? "" : value);
            } catch (Exception ex) {
                log.warn("Failed to replace placeholder {}", placeholder, ex);
            }
        }
    }

    private static Table cloneTableAfterParagraph(TextDocument doc,
                                                  TableTableElement prototypeTableElem,
                                                  TextPElement afterParagraph,
                                                  String newTableName) {
        try {
            TableTableElement clone = (TableTableElement) prototypeTableElem.cloneNode(true);
            clone.setTableNameAttribute(newTableName);

            if (afterParagraph != null) {
                Node parent = afterParagraph.getParentNode();
                Node next = afterParagraph.getNextSibling();

                if (next != null) {
                    parent.insertBefore(clone, next);
                } else {
                    parent.appendChild(clone);
                }
            } else {
                doc.getContentRoot().appendChild(clone);
            }

            return doc.getTableByName(newTableName);
        } catch (Exception ex) {
            log.warn("Failed to clone grouped table {}", newTableName, ex);
            return doc.addTable(1, 1);
        }
    }

    private static TextPElement insertGroupHeadingAfterTable(TextDocument doc,
                                                             TableTableElement tableElem,
                                                             String groupName) {
        try {
            Node parent = tableElem.getParentNode();
            Node next = tableElem.getNextSibling();

            TextPElement blankBefore = new TextPElement(doc.getContentDom());
            blankBefore.setTextContent("");

            if (next != null) {
                parent.insertBefore(blankBefore, next);
            } else {
                parent.appendChild(blankBefore);
            }

            TextPElement heading = new TextPElement(doc.getContentDom());
            heading.setTextContent(groupName == null ? "" : groupName);
            applyGroupHeadingStyle(heading);

            Node afterBlank = blankBefore.getNextSibling();
            if (afterBlank != null) {
                parent.insertBefore(heading, afterBlank);
            } else {
                parent.appendChild(heading);
            }

            TextPElement blankAfter = new TextPElement(doc.getContentDom());
            blankAfter.setTextContent("");

            Node afterHeading = heading.getNextSibling();
            if (afterHeading != null) {
                parent.insertBefore(blankAfter, afterHeading);
            } else {
                parent.appendChild(blankAfter);
            }

            return heading;
        } catch (Exception ex) {
            log.warn("Failed inserting grouped heading paragraph", ex);

            try {
                return new TextPElement((OdfFileDom) tableElem.getOwnerDocument());
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static void applyGroupHeadingStyle(TextPElement heading){
        try {
            Paragraph paragraph = Paragraph.getInstanceof(heading);
            paragraph.setFont(new Font(TEXT_FONT_NAME, StyleTypeDefinitions.FontStyle.BOLD, TEXT_FONT_SIZE));
        } catch (Exception ex) {
           log.debug("Failed to apply group header style", ex);
        }
    }

    private static boolean containsToken(String s) {
        return s != null && s.contains(TOKEN_L) && s.contains(TOKEN_R);
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }

        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.UK);
    }

    private static class GroupBucket {
        private final String displayName;
        private final List<Map<String, Object>> requirementGroups = new ArrayList<>();

        private GroupBucket(String displayName) {
            this.displayName = displayName;
        }
    }


    // --- Multi stage grouping code (Separated to prevent breaking existing logic) ---

    /**
     * Fill multi stages data with stage details and group name
     * stage details are injected on the fly programmatically
     *
     */
    public void fillMultiStageTableData(final String eventData,
                                        final DocumentTemplateSource templateSource,
                                        final TextDocument textODT) {

        if (!StringUtils.hasText(eventData)) {
            log.warn("Unable to fill multi stage table data as eventData is empty");
            return;
        }

        final String tableName = templateSource.getTableName();
        final List<FieldMapping> mappings = getCombinedMappings(tableName);
        final String anchorPlaceholder = FieldMapping.getAnchorPlaceholder(tableName);

        final Table prototype = textODT.getTableByName(tableName);
        if (prototype == null) {
            log.warn("Unable to fill multi stage table data as prototype is null");
            return;
        }

        final List<Map<String, Object>> requirementGroups =
                readRequirementGroups(eventData, templateSource.getSourcePath());

        if (requirementGroups.isEmpty()) {
            insertNotSpecifiedText(prototype);
            prototype.remove();
            log.warn("Removed empty multi-stage table as requirementGroups is empty");
            return;
        }

        final TableTableElement snapshot = (TableTableElement) prototype.getOdfElement().cloneNode(true);

        final LinkedHashMap<String, List<Map<String, Object>>> stageBuckets = bucketGroupsByStage(requirementGroups);
        if (stageBuckets.isEmpty()) {
            prototype.remove();
            log.warn("Removed empty multi-stage table as stageBuckets is empty");
            return;
        }

        final Pattern tokenCleanupPattern = Pattern.compile(TOKEN_CLEAN_PATTERN_REGEX);
        TableTableElement lastTableElem = prototype.getOdfElement();
        int stageCount = 1;

        for (final Map.Entry<String, List<Map<String, Object>>> entry : stageBuckets.entrySet()) {
            final List<Map<String, Object>> stageGroups = entry.getValue();
            if (stageGroups.isEmpty()) continue;

            final Table currentTable = createOrResolveStageTable(textODT,
                    snapshot, lastTableElem, tableName, entry.getKey(), stageGroups, stageCount == 1);

            final int anchorIdx = findRowContaining(currentTable, anchorPlaceholder);
            if (anchorIdx >= 0) {
                renderStageTableRows(currentTable, stageGroups, mappings, anchorIdx, tokenCleanupPattern);
            }

            lastTableElem = currentTable.getOdfElement();
            stageCount++;
        }
    }

    /**
     * Groups active requirement records by their respective stages.
     */
    private LinkedHashMap<String, List<Map<String, Object>>> bucketGroupsByStage(final List<Map<String, Object>> groups) {
        final LinkedHashMap<String, List<Map<String, Object>>> stageBuckets = new LinkedHashMap<>();
        for (final Map<String, Object> rg : groups) {
            if (hasRealAnswers(rg)) {
                final String stageNum = extractMetadataValue(rg, CURRENT_STAGE);
                stageBuckets.computeIfAbsent(stageNum, k -> new ArrayList<>()).add(rg);
            }
        }
        return stageBuckets;
    }

    /**
     * Initializes headers and registers standalone table clones on the canvas.
     */
    private Table createOrResolveStageTable(final TextDocument textODT,
                                            final TableTableElement snapshot,
                                            final TableTableElement lastTableElem,
                                            final String tableName,
                                            final String stageNum,
                                            final List<Map<String, Object>> stageGroups,
                                            final boolean isFirstStage) {

        final Map<String, Object> firstGroup = stageGroups.getFirst();
        final String stageDesc = extractMetadataValue(firstGroup, STAGE_DESCRIPTION);
        final String totalStages = extractMetadataValue(firstGroup, TOTAL_STAGES);

        if (isFirstStage) {
            insertHeadersAboveElement(textODT, snapshot, lastTableElem, stageDesc, stageNum, totalStages);
            return textODT.getTableByName(tableName);
        } else {
            final TextPElement lastAddedElem = insertHeadersAfterElement(textODT, snapshot, lastTableElem,
                    stageDesc, stageNum, totalStages);
            return cloneTableAfterParagraph(textODT, snapshot, lastAddedElem, tableName + "_Stage_" + stageNum);
        }
    }

    /**
     * Handles layout generation loops across active stage records.
     */
    private void renderStageTableRows(final Table currentTable,
                                      final List<Map<String, Object>> stageGroups,
                                      final List<FieldMapping> mappings,
                                      final int anchorIdx,
                                      final Pattern cleanupPattern) {

        final Map<String, String> titleToPlaceholder = new HashMap<>();
        for (final FieldMapping m : mappings) titleToPlaceholder.put(norm(m.getTitle()), m.getPlaceholder());

        final String questionPlaceholderTag = mappings.stream()
                .map(FieldMapping::getPlaceholder)
                .filter(ph -> ph != null && (ph.contains("question") || ph.contains("quest")))
                .findFirst().orElse(COP_QUESTION_ANCHOR_TAG);

        final List<Integer> blockIdxs = findTemplateBlockRowIndexes(currentTable, anchorIdx);
        final List<Row> templateRows = blockIdxs.stream().map(currentTable::getRowByIndex).toList();
        final int numberColIdx = findNumberColumnIndex(currentTable);

        int counter = 1;
        String lastProcessedGroupTitle = null;

        for (final Map<String, Object> rgMap : stageGroups) {
            final List<Map<String, String>> rows = extractRowsGeneric(rgMap, titleToPlaceholder, titleToPlaceholder.values());

            for (final Map<String, String> rowMap : rows) {
                injectPrimaryQuestionTextValue(rgMap, rowMap, questionPlaceholderTag);

                final String currentGroupItemTitle = extractSelectedGroupName(rgMap);
                if (StringUtils.hasText(currentGroupItemTitle) && !currentGroupItemTitle.equals(lastProcessedGroupTitle)) {
                    injectInlineHeaderBannerRow(currentTable, templateRows, currentGroupItemTitle);
                    lastProcessedGroupTitle = currentGroupItemTitle;
                    counter = 1;
                }

                final List<Row> newlyAddedBlockRows = new ArrayList<>(templateRows.size());
                for (final Row templateRow : templateRows) {
                    newlyAddedBlockRows.add(appendClonedRow(currentTable, templateRow));
                }

                for (final Row newRow : newlyAddedBlockRows) {
                    renderBlockMatrixRowCells(newRow, rowMap, cleanupPattern);
                    if (numberColIdx >= 0) setCellTextIfNotCovered(newRow, numberColIdx, String.valueOf(counter));
                }
                counter++;
            }
        }

        for (int i = blockIdxs.size() - 1; i >= 0; i--) {
            currentTable.removeRowsByIndex(blockIdxs.get(i), 1);
        }
    }

    /**
     * Intercepts and extracts nested question options data cleanly.
     */
    @SuppressWarnings("unchecked")
    private void injectPrimaryQuestionTextValue(final Map<String, Object> rgMap,
                                                final Map<String, String> rowMap,
                                                final String placeholderTag) {

        final Map<String, Object> ocds = (Map<String, Object>) rgMap.get("OCDS");
        if (ocds == null) return;
        final List<Map<String, Object>> requirements = (List<Map<String, Object>>) ocds.get("requirements");
        if (requirements == null) return;

        requirements.stream()
                .filter(Objects::nonNull)
                .map(req -> (Map<String, Object>) req.get("OCDS"))
                .filter(reqOcds -> reqOcds != null
                        && QUESTION_TITLE.equalsIgnoreCase((String) reqOcds.get("title")))
                .findFirst()
                .ifPresent(reqOcds -> requirements.stream()
                        .filter(r -> r != null && reqOcds.equals(r.get("OCDS")))
                        .findFirst()
                        .ifPresent(matchedReq -> {
                            final Map<String, Object> nonOcds = (Map<String, Object>) matchedReq.get("nonOCDS");
                            final List<Map<String, Object>> options = nonOcds == null ? null
                                    : (List<Map<String, Object>>) nonOcds.get("options");
                            if (options != null && !options.isEmpty()) {
                                final Object val = options.getFirst().get("value");
                                if (val != null && StringUtils.hasText(val.toString())) {
                                    rowMap.put(placeholderTag, val.toString().trim());
                                } else {
                                    rowMap.put(placeholderTag, PLACEHOLDER_UNKNOWN);
                                }
                            }
                        }));
    }

    /**
     * Programmatically forces cell splitting structures to create full-width merged rows.
     */
    private void injectInlineHeaderBannerRow(final Table currentTable,
                                             final List<Row> templateRows,
                                             final String titleText) {

        try {
            if (templateRows.isEmpty()) return;
            final int totalColumns = currentTable.getColumnCount();

            Row groupHeaderBannerRow = appendClonedRow(currentTable, templateRows.getFirst());
            int rowIndex = groupHeaderBannerRow.getRowIndex();

            CellRange range = currentTable.getCellRangeByPosition(0, rowIndex, totalColumns - 1, rowIndex);
            range.merge();
            Cell simpleBannerCell = groupHeaderBannerRow.getCellByIndex(0);

            if (simpleBannerCell != null) {
                simpleBannerCell.removeTextContent();
                simpleBannerCell.setStringValue(titleText.trim());
                simpleBannerCell.setFont(new Font(TEXT_FONT_NAME, StyleTypeDefinitions.FontStyle.BOLD, TEXT_FONT_SIZE));
                simpleBannerCell.getOdfElement().setProperty(OdfTableCellProperties.PaddingTop, "5pt");
                simpleBannerCell.getOdfElement().setProperty(OdfTableCellProperties.PaddingBottom, "5pt");
                simpleBannerCell.getOdfElement().setProperty(OdfTableCellProperties.PaddingLeft, "2pt");

                simpleBannerCell.setVerticalAlignment(StyleTypeDefinitions.VerticalAlignmentType.MIDDLE);
            }

        } catch (Exception ex) {
            log.debug("Skipped inline table header banner row generation variance pass.", ex);
        }
    }

    /**
     * Iterates cell margins to map placeholder text data variables safely.
     */
    private void renderBlockMatrixRowCells(final Row newRow,
                                           final Map<String, String> rowMap,
                                           final Pattern cleanupPattern) {

        final int cols = newRow.getTable().getColumnCount();
        for (int c = 0; c < cols; c++) {
            final Cell cell = newRow.getCellByIndex(c);
            try {
                final String txt = cell.getOdfElement().getTextContent();
                if (txt == null || !txt.contains(TOKEN_L) || !txt.contains(TOKEN_R)) continue;

                String out = txt;
                for (final Map.Entry<String, String> edge : rowMap.entrySet()) {
                    out = out.replace(edge.getKey(), edge.getValue() == null ? "" : edge.getValue());
                }

                if (out.contains(TOKEN_L) && out.contains(TOKEN_R)) {
                    out = cleanupPattern.matcher(out).replaceAll("");
                }

                if (!out.equals(txt)) {
                    // COP and AC table row text
                    cell.removeTextContent();
                    cell.setStringValue(out);
                    cell.setFont(new Font(TEXT_FONT_NAME, StyleTypeDefinitions.FontStyle.REGULAR, TEXT_FONT_SIZE));
                }
            } catch (Exception ex) {
                log.debug("Gracefully skipped rendering variance in column grid segment {}", c);
            }
        }
    }

    private boolean hasRealAnswers(Map<String, Object> rg) {

        try {
            List<Map<String, Object>> requirements = (List<Map<String, Object>>) ((Map)rg.get("OCDS")).get("requirements");

            return requirements.stream().anyMatch(r -> {
                String rid = (String) ((Map)r.get("OCDS")).get("id");

                if (Arrays.asList(CURRENT_STAGE, TOTAL_STAGES, STAGE_DESCRIPTION).contains(rid)) {
                    return false;
                }

                if (rid == null || !rid.startsWith("Question 1")) {
                    return false;
                }

                Map<String, Object> nonOcds = (Map<String, Object>) r.get("nonOCDS");
                List<Map<String, Object>> options = (List<Map<String, Object>>) nonOcds.get("options");

                return options != null && options.stream().anyMatch(o ->
                        Boolean.TRUE.equals(o.get("select")) &&
                                o.get("value") != null &&
                                StringUtils.hasText(o.get("value").toString())
                );
            });
        } catch (Exception e) {
            return false;
        }
    }

    private void insertHeadersAboveElement(TextDocument textODT,
                                           TableTableElement snapshot,
                                           OdfElement elem,
                                           String stageDesc,
                                           String stageNum,
                                           String totalStages) {

        OdfFileDom dom = (OdfFileDom) elem.getOwnerDocument();
        Node parent = elem.getParentNode();

        // Stage Header
        TextPElement p1 = new TextPElement(dom);
        p1.setTextContent("Stage " + stageNum + " (of " + totalStages + "):");
        applyHeaderStyle(p1);

        TextPElement beforeDescTableSpacer = new TextPElement(dom);
        beforeDescTableSpacer.setTextContent("");

        // Stage Description Table
        Table descTable = createStageDescTable(textODT, snapshot, stageDesc);

        // MID SPACER (The fix: Move this between Table and Group Title)
        TextPElement midSpacer = new TextPElement(dom);
        midSpacer.setTextContent("");

        // Bottom Spacer (Separates Group Title from Question Table)
        TextPElement bottomSpacer = new TextPElement(dom);
        bottomSpacer.setTextContent("");

        // Sequential insertion order: Header -> Spacer -> Table -> Spacer -> Group Title -> Spacer -> Table
        parent.insertBefore(p1, elem);
        parent.insertBefore(beforeDescTableSpacer, elem);
        parent.insertBefore(descTable.getOdfElement(), elem);
        parent.insertBefore(midSpacer, elem);
        parent.insertBefore(bottomSpacer, elem);

    }

    private TextPElement insertHeadersAfterElement(TextDocument textODT,
                                                   TableTableElement snapshot,
                                                   OdfElement elem,
                                                   String stageDesc,
                                                   String stageNum,
                                                   String totalStages) {

        OdfFileDom dom = (OdfFileDom) elem.getOwnerDocument();
        Node parent = elem.getParentNode();
        Node next = elem.getNextSibling();

        // Top Spacer (Separates from the previous table)
        TextPElement topSpacer = new TextPElement(dom);
        topSpacer.setTextContent("");

        // Stage Header
        TextPElement p1 = new TextPElement(dom);
        p1.setTextContent("Stage " + stageNum + " (of " + totalStages + "):");
        applyHeaderStyle(p1);

        TextPElement beforeDescTableSpacer = new TextPElement(dom);
        beforeDescTableSpacer.setTextContent("");

        // Stage Description Table
        Table descTable = createStageDescTable(textODT, snapshot, stageDesc);

        // MID SPACER (The fix: Inserted here)
        TextPElement midSpacer = new TextPElement(dom);
        midSpacer.setTextContent("");

        // Bottom Spacer
        TextPElement bottomSpacer = new TextPElement(dom);
        bottomSpacer.setTextContent("");

        List<Node> nodes = Arrays.asList(topSpacer, p1, beforeDescTableSpacer,
                descTable.getOdfElement(), midSpacer, bottomSpacer);

        if (next != null) {
            for (Node n : nodes) parent.insertBefore(n, next);
        } else {
            for (Node n : nodes) parent.appendChild(n);
        }

        return bottomSpacer;
    }

    private Table createStageDescTable(TextDocument textODT,
                                       TableTableElement snapshot,
                                       String stageDescValue) {

        Table table = Table.newTable(textODT, 1, 2);
        try {
            String styleName = snapshot.getTableStyleNameAttribute();
            if (StringUtils.hasText(styleName)) table.getOdfElement().setTableStyleNameAttribute(styleName);

            Cell labelCell = table.getCellByPosition(0, 0);
            labelCell.setStringValue(STAGE_DESCRIPTION_HEADER_TAG);
            labelCell.setFont(new Font(TEXT_FONT_NAME, StyleTypeDefinitions.FontStyle.BOLD, TEXT_FONT_SIZE));

            Cell valueCell = table.getCellByPosition(1, 0);
            valueCell.setStringValue(stageDescValue);
            valueCell.setFont(new Font(TEXT_FONT_NAME, StyleTypeDefinitions.FontStyle.REGULAR, TEXT_FONT_SIZE));

        } catch (Exception ignored) {}

        table.getColumnByIndex(0).setWidth(55.0);
        table.getColumnByIndex(1).setWidth(110.0);

        return table;
    }

    private void applyHeaderStyle(TextPElement p) {
        try {
            Paragraph para = Paragraph.getInstanceof(p);
            para.setFont(new Font(TEXT_FONT_NAME, StyleTypeDefinitions.FontStyle.BOLD, TEXT_FONT_SIZE));
        } catch (Exception ignored) {}
    }

    private String extractMetadataValue(Map<String, Object> rgMap, String metadataId) {
        try {
            List<Map<String, Object>> reqs = (List<Map<String, Object>>) ((Map)rgMap.get("OCDS")).get("requirements");
            return reqs.stream().filter(r ->
                            metadataId.equals(((Map)r.get("OCDS")).get("id")))
                    .map(this::readSelectedOptionValue)
                    .findFirst()
                    .orElse("");
        } catch (Exception e) { return ""; }
    }

    private List<FieldMapping> getCombinedMappings(String tableName) {
        List<FieldMapping> mappings = new ArrayList<>(FieldMapping.getFieldsByTableName(tableName));
        mappings.addAll(FieldMapping.getFieldsByTableName(COND_OF_PART));
        mappings.addAll(FieldMapping.getFieldsByTableName(AWARD_CRITERIA));
        return mappings;
    }

    public void replacePlaceholderText(TextDocument doc, String placeholder, String value) {
        if (doc == null || !StringUtils.hasText(placeholder)) return;
        replaceAllTextOccurrences(doc, placeholder, value != null ? value : PLACEHOLDER_UNKNOWN);
    }

    private void insertNotSpecifiedText(Table table) {
        try {
            TableTableElement tableElem = table.getOdfElement();
            Node parent = tableElem.getParentNode();
            OdfFileDom dom = (OdfFileDom) tableElem.getOwnerDocument();

            TextPElement p = new TextPElement(dom);
            p.setTextContent(PLACEHOLDER_UNKNOWN);

            try {
                Paragraph para = Paragraph.getInstanceof(p);
                para.setFont(new Font(TEXT_FONT_NAME, StyleTypeDefinitions.FontStyle.REGULAR, TEXT_FONT_SIZE));
            } catch (Exception ignored) {}

            parent.insertBefore(p, tableElem);
        } catch (Exception ex) {
            log.warn("Failed to insert Not Specified text replacement", ex);
        }
    }

    private static void sortGroupedTableData(LinkedHashMap<String, GroupBucket> grouped) {
        grouped.values().forEach(bucket ->
                bucket.requirementGroups.sort(
                        Comparator.comparingInt(TableGroupGenerator::getLowestRequirementOrder)
                )
        );

        List<Map.Entry<String, GroupBucket>> entries =
                new ArrayList<>(grouped.entrySet());

        entries.sort(Comparator
                .<Map.Entry<String, GroupBucket>>comparingInt(entry -> getUnnamedGroupSortOrder(entry.getKey()))
                .thenComparingInt(entry -> getSortableGroupOrder(entry.getValue()))
                .thenComparingInt(entry -> getSortableFirstRowOrder(entry.getValue())));

        grouped.clear();

        for (Map.Entry<String, GroupBucket> entry : entries) {
            grouped.put(entry.getKey(), entry.getValue());
        }
    }

    private static int getUnnamedGroupSortOrder(String groupKey) {
        return UNNAMED_GROUP_KEY.equals(groupKey) ? 1 : 0;
    }

    private static int getBucketFirstRowOrder(GroupBucket bucket) {
        return bucket.requirementGroups.stream()
                .mapToInt(TableGroupGenerator::getLowestRequirementOrder)
                .min()
                .orElse(Integer.MIN_VALUE);
    }

    private static Integer getBucketGroupOrder(GroupBucket bucket) {
        return bucket.requirementGroups.stream()
                .map(TableGroupGenerator::getRequirementGroupOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private static int getSortableGroupOrder(GroupBucket bucket) {
        Integer groupOrder = getBucketGroupOrder(bucket);
        return groupOrder == null ? Integer.MAX_VALUE : groupOrder;
    }

    private static int getSortableFirstRowOrder(GroupBucket bucket) {
        int firstRowOrder = getBucketFirstRowOrder(bucket);

        if (getBucketGroupOrder(bucket) == null && firstRowOrder != Integer.MIN_VALUE) {
            return -firstRowOrder;
        }

        return firstRowOrder;
    }

    private static Integer getRequirementGroupOrder(Map<String, Object> requirementGroup) {
        Map<String, Object> ocds = (Map<String, Object>) requirementGroup.get("OCDS");
        if (ocds == null) {
            return null;
        }
        Object requirementsObj = ocds.get("requirements");
        if (!(requirementsObj instanceof List<?> requirements)) {
            return null;
        }

        for (Object requirementObj : requirements) {
            if (!(requirementObj instanceof Map<?, ?> requirement)) {
                continue;
            }

            Map<String, Object> requirementMap = (Map<String, Object>) requirement;
            if (isSelectGroupNameRequirement(requirementMap)) {
                return getSelectRequirementGroupOrder(requirementMap);
            }
        }

        return getHighestRequirementGroupOrder(requirements);
    }

    private static boolean isSelectGroupNameRequirement(Map<String, Object> requirement) {
        Map<String, Object> ocds = (Map<String, Object>) requirement.get("OCDS");
        Object title = ocds == null ? null : ocds.get("title");
        return title != null && SELECT_GROUP_NAME_TITLE.equalsIgnoreCase(title.toString());
    }

    private static Integer getSelectRequirementGroupOrder(Map<String, Object> requirement) {
        Map<String, Object> nonOCDS = (Map<String, Object>) requirement.get("nonOCDS");
        return getNullableOrder(nonOCDS, "groupOrder");
    }

    private static Integer getHighestRequirementGroupOrder(List<?> requirements) {
        return requirements.stream()
                .filter(Map.class::isInstance)
                .map(requirement -> (Map<String, Object>) requirement)
                .map(requirement -> (Map<String, Object>) requirement.get("nonOCDS"))
                .map(nonOCDS -> getNullableOrder(nonOCDS, "groupOrder"))
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private static int getLowestRequirementOrder(Map<String, Object> requirementGroup) {
        Map<String, Object> ocds = (Map<String, Object>) requirementGroup.get("OCDS");
        if (ocds == null) {
            return Integer.MAX_VALUE;
        }
        Object requirementsObj = ocds.get("requirements");
        if (!(requirementsObj instanceof List<?> requirements)) {
            return Integer.MAX_VALUE;
        }
        return requirements.stream()
                .filter(Map.class::isInstance)
                .map(requirement -> (Map<String, Object>) requirement)
                .mapToInt(TableGroupGenerator::getRequirementOrder)
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    private static int getRequirementOrder(Map<String, Object> requirement) {
        Map<String, Object> nonOCDS = (Map<String, Object>) requirement.get("nonOCDS");
        return getOrder(nonOCDS, "order", Integer.MAX_VALUE);
    }

    private static Integer getNullableOrder(Map<String, Object> nonOCDS, String fieldName) {
        if (nonOCDS == null) {
            return null;
        }
        Object order = nonOCDS.get(fieldName);
        if (order instanceof Number number) {
            return number.intValue();
        }
        if (order instanceof String orderText && StringUtils.hasText(orderText)) {
            try {
                return Integer.parseInt(orderText);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int getOrder(Map<String, Object> nonOCDS, String fieldName, int defaultValue) {
        if (nonOCDS == null) {
            return defaultValue;
        }
        Object order = nonOCDS.get(fieldName);
        if (order instanceof Number number) {
            return number.intValue();
        }
        if (order instanceof String orderText && StringUtils.hasText(orderText)) {
            try {
                return Integer.parseInt(orderText);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

}
