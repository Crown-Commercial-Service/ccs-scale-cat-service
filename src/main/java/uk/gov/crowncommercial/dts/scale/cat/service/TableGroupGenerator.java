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
import org.odftoolkit.odfdom.pkg.OdfElement;
import org.odftoolkit.odfdom.pkg.OdfFileDom;
import org.odftoolkit.simple.TextDocument;
import org.odftoolkit.simple.common.navigation.InvalidNavigationException;
import org.odftoolkit.simple.common.navigation.TextNavigation;
import org.odftoolkit.simple.common.navigation.TextSelection;
import org.odftoolkit.simple.style.Font;
import org.odftoolkit.simple.style.StyleTypeDefinitions;
import org.odftoolkit.simple.table.Cell;
import org.odftoolkit.simple.table.Row;
import org.odftoolkit.simple.table.Table;
import org.odftoolkit.simple.text.Paragraph;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Node;
import uk.gov.crowncommercial.dts.scale.cat.mapper.FieldMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentTemplateSource;

import java.util.*;

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
    private static final String STAGE_DESCRIPTION_HEADER_TAG = "Stage description";
    private static final String CURRENT_STAGE = "CURRENT_STAGE";
    private static final String STAGE_DESCRIPTION = "STAGE_DESCRIPTION";
    private static final String TOTAL_STAGES = "TOTAL_STAGES";
    private static final String TEXT_FONT_NAME = "Arial";
    private static final double TEXT_FONT_SIZE = 12.0;

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

        if (requirementGroups.isEmpty()) {
            replaceAllPlaceholdersWithUnknown(textODT, groupNamePlaceholder, fieldMappings);
            return;
        }

        LinkedHashMap<String, GroupBucket> grouped = groupRequirementGroups(requirementGroups);

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

    private LinkedHashMap<String, GroupBucket> groupRequirementGroups(List<Map<String, Object>> requirementGroups) {
        LinkedHashMap<String, GroupBucket> grouped = new LinkedHashMap<>();
        int unnamedCounter = 0;

        for (Map<String, Object> rgMap : requirementGroups) {
            String selectedGroupName = extractSelectedGroupName(rgMap);

            String key;
            String displayName;

            if (StringUtils.hasText(selectedGroupName)) {
                key = "named::" + norm(selectedGroupName);
                displayName = selectedGroupName.trim();
            } else {
                key = "unnamed::" + (++unnamedCounter);
                displayName = extractDisplayNameForUnnamedGroup(rgMap);
            }

            GroupBucket bucket = grouped.computeIfAbsent(key, k -> new GroupBucket(displayName));
            bucket.requirementGroups.add(rgMap);
        }

        return grouped;
    }

    private String extractDisplayNameForUnnamedGroup(Map<String, Object> rgMap) {
        String description = extractGroupDescription(rgMap);

        if (StringUtils.hasText(description)) {
            return description.trim();
        }

        return PLACEHOLDER_UNKNOWN;
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
            row.put(placeholder, "");
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
                return value == null ? "" : value.toString();
            }
        }

        return "";
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

        int numberColIdx = findNumberColumnIndex(table);
        int counter = 1;

        for (Map<String, Object> rgMap : groupRequirementGroups) {
            List<Map<String, String>> rows = extractRowsGeneric(rgMap, titleToPlaceholder, placeholders);

            for (Map<String, String> rowMap : rows) {
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

        TableTableElement lastTableElem = (TableTableElement) prototype.getOdfElement();
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

            if (txt != null && txt.trim().equals("#")) {
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
     *
     * stage details are injected on the fly programmatically
     *
     */
    public void fillMultiStageTableData(String eventData,
                                        DocumentTemplateSource templateSource,
                                        TextDocument textODT) {
        if (!StringUtils.hasText(eventData)) return;

        String tableName = templateSource.getTableName();
        List<FieldMapping> mappings = getCombinedMappings(tableName);
        String anchorPlaceholder = FieldMapping.getAnchorPlaceholder(tableName);

        List<Map<String, Object>> requirementGroups = readRequirementGroups(eventData, templateSource.getSourcePath());
        if (requirementGroups.isEmpty()) return;

        Table prototype = textODT.getTableByName(tableName);
        if (prototype == null) return;
        TableTableElement snapshot = (TableTableElement) prototype.getOdfElement().cloneNode(true);

        // Look for real answers
        LinkedHashMap<String, List<Map<String, Object>>> stageBuckets = new LinkedHashMap<>();
        for (Map<String, Object> rg : requirementGroups) {
            if (hasRealAnswers(rg)) { // The Gatekeeper
                String stageNum = extractMetadataValue(rg, CURRENT_STAGE);
                stageBuckets.computeIfAbsent(stageNum, k -> new ArrayList<>()).add(rg);
            }
        }

        if (stageBuckets.isEmpty()) {
            prototype.remove();
            return;
        }

        TableTableElement lastTableElem = prototype.getOdfElement();
        int stageCount = 1;

        for (Map.Entry<String, List<Map<String, Object>>> entry : stageBuckets.entrySet()) {
            List<Map<String, Object>> stageGroups = entry.getValue();

            Map<String, Object> firstGroup = stageGroups.getFirst();
            String stageNum = entry.getKey();
            String stageDesc = extractMetadataValue(firstGroup, STAGE_DESCRIPTION);
            String totalStages = extractMetadataValue(firstGroup, TOTAL_STAGES);
            String groupTitle = extractSelectedGroupName(firstGroup);

            Table currentTable;
            if (stageCount == 1) {
                currentTable = prototype;
                insertHeadersAboveElement(textODT, snapshot, lastTableElem, stageDesc, stageNum, totalStages, groupTitle);
            } else {
                TextPElement lastAddedElem = insertHeadersAfterElement(textODT, snapshot, lastTableElem, stageDesc, stageNum, totalStages, groupTitle);
                currentTable = cloneTableAfterParagraph(textODT, snapshot, lastAddedElem, tableName + "_Stage_" + stageNum);
            }

            fillOneTableStandard(currentTable, stageGroups, anchorPlaceholder, mappings);
            lastTableElem = currentTable.getOdfElement();
            stageCount++;
        }
    }

    private boolean hasRealAnswers(Map<String, Object> rg) {
        try {
            List<Map<String, Object>> requirements = (List<Map<String, Object>>) ((Map)rg.get("OCDS")).get("requirements");

            return requirements.stream().anyMatch(r -> {
                String rid = (String) ((Map)r.get("OCDS")).get("id");

                // Do not count stage metadata as a "Real Answer"
                if (Arrays.asList(CURRENT_STAGE, TOTAL_STAGES, STAGE_DESCRIPTION).contains(rid)) {
                    return false;
                }

                Map<String, Object> nonOcds = (Map<String, Object>) r.get("nonOCDS");
                List<Map<String, Object>> options = (List<Map<String, Object>>) nonOcds.get("options");

                // A "Real Answer" is where select is true and value is not blank
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
                                           String totalStages,
                                           String groupTitle) {

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

        // Group Title
        TextPElement pGroup = new TextPElement(dom);
        pGroup.setTextContent((StringUtils.hasText(groupTitle) ? groupTitle : PLACEHOLDER_UNKNOWN));
        applyHeaderStyle(pGroup);

        // Bottom Spacer (Separates Group Title from Question Table)
        TextPElement bottomSpacer = new TextPElement(dom);
        bottomSpacer.setTextContent("");

        // Sequential insertion order: Header -> Spacer -> Table -> Spacer -> Group Title -> Spacer -> Table
        parent.insertBefore(p1, elem);
        parent.insertBefore(beforeDescTableSpacer, elem);
        parent.insertBefore(descTable.getOdfElement(), elem);
        parent.insertBefore(midSpacer, elem);
        parent.insertBefore(pGroup, elem);
        parent.insertBefore(bottomSpacer, elem);

    }

    private TextPElement insertHeadersAfterElement(TextDocument textODT,
                                                   TableTableElement snapshot,
                                                   OdfElement elem,
                                                   String stageDesc,
                                                   String stageNum,
                                                   String totalStages,
                                                   String groupTitle) {

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

        // Group Title
        TextPElement pGroup = new TextPElement(dom);
        pGroup.setTextContent((StringUtils.hasText(groupTitle) ? groupTitle : PLACEHOLDER_UNKNOWN));
        applyHeaderStyle(pGroup);

        // Bottom Spacer
        TextPElement bottomSpacer = new TextPElement(dom);
        bottomSpacer.setTextContent("");

        List<Node> nodes = Arrays.asList(topSpacer, p1, beforeDescTableSpacer,
                descTable.getOdfElement(), midSpacer, pGroup, bottomSpacer);

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

    private void fillOneTableStandard(Table table,
                                      List<Map<String, Object>> groups,
                                      String anchor,
                                      List<FieldMapping> mappings) {
        int anchorIdx = findRowContaining(table, anchor);
        if (anchorIdx < 0) return;
        Map<String, String> titleToPlaceholder = new HashMap<>();
        for (FieldMapping m : mappings) titleToPlaceholder.put(norm(m.getTitle()), m.getPlaceholder());

        List<Integer> blockIdxs = findTemplateBlockRowIndexes(table, anchorIdx);
        List<Row> templateRows = blockIdxs.stream().map(table::getRowByIndex).toList();

        int numberColIdx = findNumberColumnIndex(table);
        int counter = 1;
        for (Map<String, Object> rgMap : groups) {
            List<Map<String, String>> rows = extractRowsGeneric(rgMap, titleToPlaceholder, titleToPlaceholder.values());
            for (Map<String, String> rowMap : rows) {
                for (Row templateRow : templateRows) {
                    Row newRow = appendClonedRow(table, templateRow);
                    replacePlaceholdersInRow(newRow, rowMap);
                    if (numberColIdx >= 0) setCellTextIfNotCovered(newRow, numberColIdx, String.valueOf(counter));
                }
                counter++;
            }
        }
        for (int i = blockIdxs.size() - 1; i >= 0; i--) table.removeRowsByIndex(blockIdxs.get(i), 1);
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

}
