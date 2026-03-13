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
import org.odftoolkit.odfdom.pkg.OdfFileDom;
import org.odftoolkit.simple.TextDocument;
import org.odftoolkit.simple.common.navigation.InvalidNavigationException;
import org.odftoolkit.simple.common.navigation.TextNavigation;
import org.odftoolkit.simple.common.navigation.TextSelection;
import org.odftoolkit.simple.table.Cell;
import org.odftoolkit.simple.table.Row;
import org.odftoolkit.simple.table.Table;
import org.springframework.stereotype.Component;
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

    private final ObjectMapper objectMapper;

    public void fillTableData(String eventData, DocumentTemplateSource templateSource, TextDocument textODT) {

        Configuration jsonPathConfig = Configuration.builder()
                .options(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST)
                .jsonProvider(new JacksonJsonProvider(objectMapper))
                .mappingProvider(new JacksonMappingProvider(objectMapper))
                .build();

        TypeRef<List<Map<String, Object>>> typeRef = new TypeRef<>() {};
        List<Map<String, Object>> requirementGroups = JsonPath.using(jsonPathConfig)
                .parse(eventData)
                .read(templateSource.getSourcePath(), typeRef);

        // group requirementGroups by extracted group name (keeps original order)
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> rgMap : requirementGroups) {
            String key = nullToEmpty(extractGroupName(rgMap));
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(rgMap);
        }

        String tableName = templateSource.getTableName();
        List<FieldMapping> fieldMappings = FieldMapping.getFieldsByTableName(tableName);
        String anchorPlaceholder = FieldMapping.getAnchorPlaceholder(tableName);
        String groupNamePlaceholder = FieldMapping.getTableGroupName(tableName);

        buildTableGroup(grouped, textODT, tableName, anchorPlaceholder, groupNamePlaceholder, fieldMappings);
    }

    @SuppressWarnings("unchecked")
    private String extractGroupName(Map<String, Object> rgMap) {
        Map<String, Object> ocds = (Map<String, Object>) rgMap.get("OCDS");
        if (ocds == null) return "";

        List<Map<String, Object>> requirements = (List<Map<String, Object>>) ocds.get("requirements");
        if (requirements == null) return "";

        for (Map<String, Object> req : requirements) {
            Map<String, Object> reqOcds = (Map<String, Object>) req.get("OCDS");
            String title = reqOcds == null ? null : (String) reqOcds.get("title");
            if (title == null) continue;

            if (!"Select group name".equalsIgnoreCase(title)) continue;

            Map<String, Object> nonOcds = (Map<String, Object>) req.get("nonOCDS");
            List<Map<String, Object>> options = nonOcds == null ? null : (List<Map<String, Object>>) nonOcds.get("options");
            if (options == null) return "";

            for (Map<String, Object> opt : options) {
                if (Boolean.TRUE.equals(opt.get("select"))) {
                    Object v = opt.get("value");
                    return v == null ? "" : v.toString().trim();
                }
            }
            return "";
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> extractRowsGeneric(Map<String, Object> rgMap,
                                                         Map<String, String> titleToPlaceholder,
                                                         Collection<String> placeholders) {

        List<Map<String, String>> rows = new ArrayList<>();

        Map<String, Object> ocds = (Map<String, Object>) rgMap.get("OCDS");
        if (ocds == null) return rows;

        List<Map<String, Object>> requirements = (List<Map<String, Object>>) ocds.get("requirements");
        if (requirements == null) return rows;

        // One output row per requirement group
        Map<String, String> row = new LinkedHashMap<>();
        for (String ph : placeholders) row.put(ph, "");

        for (Map<String, Object> req : requirements) {
            Map<String, Object> reqOcds = (Map<String, Object>) req.get("OCDS");
            String title = reqOcds == null ? null : (String) reqOcds.get("title");
            if (title == null) continue;

            String placeholder = titleToPlaceholder.get(norm(title));
            if (placeholder == null) continue;

            try {
                row.put(placeholder, readSelectedOptionValue(req));
            } catch (Exception ex) {
                log.warn("Fail-soft: JSON read failed for {}", placeholder, ex);
                row.put(placeholder, "");
            }
        }

        rows.add(row);
        return rows;
    }

    @SuppressWarnings("unchecked")
    private String readSelectedOptionValue(Map<String, Object> req) {
        Map<String, Object> nonOcds = (Map<String, Object>) req.get("nonOCDS");
        if (nonOcds == null) return "";

        List<Map<String, Object>> options = (List<Map<String, Object>>) nonOcds.get("options");
        if (options == null) return "";

        for (Map<String, Object> opt : options) {
            if (Boolean.TRUE.equals(opt.get("select"))) {
                Object v = opt.get("value");
                return v == null ? "" : v.toString();
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
            log.warn("Fail-soft: anchor not found for {}", rowAnchorPlaceholder);
            table.appendRow();
            return;
        }

        // Build mapping once (performance)
        Map<String, String> titleToPlaceholder = new HashMap<>();
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        for (FieldMapping fm : mappings) {
            placeholders.add(fm.getPlaceholder());
            titleToPlaceholder.put(norm(fm.getTitle()), fm.getPlaceholder());
        }

        // Detect template block rows
        List<Integer> blockIdxs = findTemplateBlockRowIndexes(table, anchorIdx);

        // Snapshot template rows (wrappers are fine; we clone DOM from them)
        List<Row> templateRows = new ArrayList<>(blockIdxs.size());
        for (int idx : blockIdxs) templateRows.add(table.getRowByIndex(idx));

        int numberColIdx = findNumberColumnIndex(table);
        int counter = 1;

        for (Map<String, Object> rgMap : groupRequirementGroups) {
            List<Map<String, String>> rows = extractRowsGeneric(rgMap, titleToPlaceholder, placeholders);

            for (Map<String, String> rowMap : rows) {
                List<Row> createdBlockRows = new ArrayList<>(templateRows.size());

                for (Row tmpl : templateRows) {
                    Row newRow = appendClonedRow(table, tmpl);
                    replacePlaceholdersInRow(newRow, rowMap);
                    createdBlockRows.add(newRow);
                }

                if (numberColIdx >= 0) {
                    String num = String.valueOf(counter);
                    for (Row r : createdBlockRows) setCellTextIfNotCovered(r, numberColIdx, num);
                }

                counter++;
            }
        }

        // Remove original template block (bottom-up)
        for (int i = blockIdxs.size() - 1; i >= 0; i--) {
            table.removeRowsByIndex(blockIdxs.get(i), 1);
        }
    }

    private void buildTableGroup(Map<String, List<Map<String, Object>>> grouped,
                                 TextDocument textODT,
                                 String tableName,
                                 String rowAnchorPlaceholder,
                                 String groupNamePlaceholder,
                                 List<FieldMapping> mappings) {

        if (grouped.isEmpty()) return;

        Table prototype = textODT.getTableByName(tableName);
        if (prototype == null) throw new IllegalStateException("Table not found: " + tableName);

        TableTableElement prototypeSnapshot = (TableTableElement) prototype.getOdfElement().cloneNode(true);

        Iterator<Map.Entry<String, List<Map<String, Object>>>> it = grouped.entrySet().iterator();

        // first group uses existing placeholder paragraph above prototype table
        Map.Entry<String, List<Map<String, Object>>> first = it.next();
        replaceFirstTextOccurrence(textODT, groupNamePlaceholder, nullToEmpty(first.getKey()));
        fillOneTable(prototype, first.getValue(), rowAnchorPlaceholder, mappings);

        TableTableElement lastTableElem = (TableTableElement) prototype.getOdfElement();
        int cloneIndex = 2;

        while (it.hasNext()) {
            Map.Entry<String, List<Map<String, Object>>> e = it.next();
            String groupName = e.getKey();
            String newTableName = tableName + "_" + (cloneIndex++);

            Table cloned;
            if (groupName != null && !groupName.isBlank()) {
                TextPElement p = insertGroupHeadingAfterTable(textODT, lastTableElem, groupName);
                cloned = cloneTableAfterParagraph(textODT, prototypeSnapshot, p, newTableName);
            } else {
                cloned = cloneTableAfterTable(textODT, prototypeSnapshot, lastTableElem, newTableName);
            }

            fillOneTable(cloned, e.getValue(), rowAnchorPlaceholder, mappings);
            lastTableElem = (TableTableElement) cloned.getOdfElement();
        }
    }

    private static List<Integer> findTemplateBlockRowIndexes(Table table, int anchorIdx) {
        List<Integer> idxs = new ArrayList<>();

        for (int r = anchorIdx; r < table.getRowCount(); r++) {
            Row row = table.getRowByIndex(r);

            if (r == anchorIdx) {
                idxs.add(r);
                continue;
            }

            if (!rowContainsPlaceholderToken(row) && !isSupplierRow(row)) {
                break;
            }

            idxs.add(r);
        }

        return idxs;
    }

    private static boolean isSupplierRow(Row row) {
        int cols = row.getTable().getColumnCount();
        for (int c = 0; c < cols; c++) {
            Cell cell = row.getCellByIndex(c);
            if (isCovered(cell)) continue;

            String txt = cell.getOdfElement().getTextContent();
            if (txt != null && txt.toLowerCase().contains(SUPPLIER_MARKER)) return true;
        }
        return false;
    }

    private static boolean rowContainsPlaceholderToken(Row row) {
        int cols = row.getTable().getColumnCount();
        for (int c = 0; c < cols; c++) {
            Cell cell = row.getCellByIndex(c);
            if (isCovered(cell)) continue;

            String txt = cell.getOdfElement().getTextContent();
            if (containsToken(txt)) return true;
        }
        return false;
    }

    private static void replacePlaceholdersInRow(Row row, Map<String, String> replacements) {

        int cols = row.getTable().getColumnCount();

        for (int c = 0; c < cols; c++) {

            Cell cell = row.getCellByIndex(c);
            if (isCovered(cell)) continue;

            try {
                String txt = cell.getOdfElement().getTextContent();
                if (!containsToken(txt)) continue;

                String out = txt;

                for (Map.Entry<String, String> e : replacements.entrySet()) {
                    try {
                        String k = e.getKey();
                        String v = e.getValue() == null ? "" : e.getValue();
                        out = out.replace(k, v);
                    } catch (Exception ex) {
                        log.warn("Fail-soft: error replacing token in column {}", c, ex);
                    }
                }

                // Remove unresolved placeholders
                if (containsToken(out)) {
                    out = out.replaceAll("«.*?»", "");
                }

                if (!out.equals(txt)) {
                    try {
                        cell.removeTextContent();
                    } catch (Exception ex) {
                        log.warn("Fail-soft: removeTextContent failed column {}", c, ex);
                    }

                    try {
                        cell.setStringValue(out);
                    } catch (Exception ex) {
                        log.warn("Fail-soft: setStringValue failed column {}", c, ex);
                        safeClearCell(cell);
                    }
                }

            } catch (Exception ex) {
                log.warn("Fail-soft: column {} replacement failed. Setting empty.", c, ex);
                safeClearCell(cell);
            }
        }
    }

    private static void safeClearCell(Cell cell) {
        try {
            cell.removeTextContent();
            cell.setStringValue("");
        } catch (Exception ignored) {}
    }

    private static Row appendClonedRow(Table table, Row templateRow) {
        try {
            org.odftoolkit.odfdom.dom.element.table.TableTableRowElement clone =
                    (org.odftoolkit.odfdom.dom.element.table.TableTableRowElement)
                            templateRow.getOdfElement().cloneNode(true);

            table.getOdfElement().appendChild(clone);
            return table.getRowByIndex(table.getRowCount() - 1);

        } catch (Exception ex) {
            log.warn("Fail-soft: row cloning failed", ex);
            return table.appendRow();
        }
    }

    private static boolean isCovered(Cell cell) {
        return cell.getOdfElement() instanceof TableCoveredTableCellElement;
    }

    private static void setCellTextIfNotCovered(Row row, int colIdx, String value) {
        Cell cell = row.getCellByIndex(colIdx);
        if (isCovered(cell)) return;
        cell.setStringValue(value == null ? "" : value);
    }

    private static int findRowContaining(Table t, String needle) {
        for (int r = 0; r < t.getRowCount(); r++) {
            Row row = t.getRowByIndex(r);
            int cols = row.getTable().getColumnCount();

            for (int c = 0; c < cols; c++) {
                Cell cell = row.getCellByIndex(c);
                if (isCovered(cell)) continue;

                String txt = cell.getOdfElement().getTextContent();
                if (txt != null && txt.contains(needle)) return r;
            }
        }
        return -1;
    }

    private static int findNumberColumnIndex(Table table) {
        if (table.getRowCount() == 0) return -1;
        Row header = table.getRowByIndex(0);
        int cols = table.getColumnCount();
        for (int c = 0; c < cols; c++) {
            String txt = header.getCellByIndex(c).getStringValue();
            if (txt != null && txt.trim().equals("#")) return c;
        }
        return -1;
    }

    private static void replaceFirstTextOccurrence(TextDocument doc, String placeholder, String value) {
        if (placeholder == null || placeholder.isBlank()) return;

        TextNavigation nav = new TextNavigation(placeholder, doc);
        TextSelection sel = (TextSelection) nav.nextSelection();

        if (sel == null) {
            log.warn("Fail-soft: group placeholder missing {}", placeholder);
            return;
        }

        try {
            sel.replaceWith(value == null ? "" : value);
        } catch (InvalidNavigationException e) {
            log.warn("Fail-soft: group heading replace failed", e);
        }
    }

    private static Table cloneTableAfterParagraph(TextDocument doc,
                                                  TableTableElement prototypeTableElem,
                                                  TextPElement afterParagraph,
                                                  String newTableName) {
        try {
            TableTableElement clone = (TableTableElement) prototypeTableElem.cloneNode(true);
            clone.setTableNameAttribute(newTableName);

            Node parent = afterParagraph.getParentNode();
            Node next = afterParagraph.getNextSibling();

            if (next != null) parent.insertBefore(clone, next);
            else parent.appendChild(clone);

            return doc.getTableByName(newTableName);

        } catch (Exception ex) {
            log.warn("Fail-soft: cloneTableAfterParagraph failed {}", newTableName, ex);
            return doc.addTable(1, 1);
        }
    }

    private static Table cloneTableAfterTable(TextDocument doc,
                                              TableTableElement prototypeTableElem,
                                              TableTableElement afterTableElem,
                                              String newTableName) {
        try {
            TableTableElement clone = (TableTableElement) prototypeTableElem.cloneNode(true);
            clone.setTableNameAttribute(newTableName);

            Node parent = afterTableElem.getParentNode();
            Node next = afterTableElem.getNextSibling();

            if (next != null) parent.insertBefore(clone, next);
            else parent.appendChild(clone);

            return doc.getTableByName(newTableName);

        } catch (Exception ex) {
            log.warn("Fail-soft: cloneTableAfterTable failed {}", newTableName, ex);
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

            if (next != null) parent.insertBefore(blankBefore, next);
            else parent.appendChild(blankBefore);

            TextPElement heading = new TextPElement(doc.getContentDom());
            heading.setTextContent(groupName == null ? "" : groupName);
            heading.setTextStyleNameAttribute("GroupHeading");

            Node afterBlank = blankBefore.getNextSibling();
            if (afterBlank != null) parent.insertBefore(heading, afterBlank);
            else parent.appendChild(heading);

            TextPElement blankAfter = new TextPElement(doc.getContentDom());
            blankAfter.setTextContent("");

            Node afterHeading = heading.getNextSibling();
            if (afterHeading != null) parent.insertBefore(blankAfter, afterHeading);
            else parent.appendChild(blankAfter);

            return heading;
        } catch (Exception ex) {
            log.warn("Fail-soft: Failed inserting group heading.", ex);
            // fallback: return a safe paragraph so flow continues
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
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.UK);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}