package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.SupplierCSVReader;
import uk.gov.crowncommercial.dts.scale.cat.csvreader.SupplierModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.Consumer;

@RequiredArgsConstructor
public class SupplierCSVtoXLSConverter {

    private final String baseFolder;
    private final String lot;
    private final XSSFWorkbook workbook = new XSSFWorkbook();

    public void convertToXLS(String agreement, String lot) {
        writeTo(workbook, agreement + "_" + lot + "_completed",  "Suppliers Mapped CAS-Jaggaer");
        writeTo(workbook, agreement + "_" + lot + "_missing_jaggaer",  "In CAS & Not in Jaggaer");
    }

    public void convertToXLS(String agreement) {
        writeTo(workbook, agreement + "_completed",  "Suppliers Mapped CAS-Jaggaer");
        writeTo(workbook, agreement + "_missing_cas",  "In Jaggaer & Not CAS");
        writeTo(workbook, agreement + "_missing_jaggaer",  "In CAS & Not in Jaggaer");
        writeTo(workbook, agreement + "_missing", "Not in Jaggaer& CAS");
    }

    public void writeTo(String xlsFileName) throws  IOException{
        File file = Paths.get(baseFolder, xlsFileName + ".xlsx").toFile();
        workbook.write(new FileOutputStream(file));
        workbook.close();
    }

    private void writeTo(XSSFWorkbook workbook, String csvFile, String sheetName) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        SupplierCSVReader reader = new SupplierCSVReader();
        File file;
        if(null == lot)
            file = Paths.get(baseFolder, csvFile + ".csv").toFile();
        else
            file = Paths.get(baseFolder, lot,csvFile + ".csv").toFile();
        if(!file.exists()){
            System.out.println("File " + csvFile +" does not exist");
            return;
        }


//        DataFormat fmt = workbook.createDataFormat();
//        CellStyle textStyle = workbook.createCellStyle();
//        textStyle.setDataFormat(fmt.getFormat("@"));
//        sheet.setDefaultColumnStyle(0, textStyle);
//        sheet.setDefaultColumnStyle(1, textStyle);
//        sheet.setDefaultColumnStyle(3, textStyle);
//        sheet.setDefaultColumnStyle(4, textStyle);
//        sheet.setDefaultColumnStyle(5, textStyle);
//        sheet.setDefaultColumnStyle(6, textStyle);

        int charWidth = 60;

        sheet.setColumnWidth(0, charWidth * 50);
        sheet.setColumnWidth(1, charWidth * 70);
        sheet.setColumnWidth(2, charWidth * 50);
        sheet.setColumnWidth(3, charWidth * 125);
        sheet.setColumnWidth(4, charWidth * 125);
        sheet.setColumnWidth(5, charWidth * 125);
        sheet.setColumnWidth(6, charWidth * 125);

        XSSFRow bodyRow = sheet.createRow(0);
        int colNum = 0;
        bodyRow.createCell(colNum++).setCellValue("EntityId");
        bodyRow.createCell(colNum++).setCellValue("House Number");
        bodyRow.createCell(colNum++).setCellValue("Bravo Id");
        bodyRow.createCell(colNum++).setCellValue("Legal Name (CAS)");
        bodyRow.createCell(colNum++).setCellValue("Trading Name (CAS)");
        bodyRow.createCell(colNum++).setCellValue("Supplier Name (As provided)");
        bodyRow.createCell(colNum++).setCellValue("Supplier Name(Jaggaer)");
        bodyRow.createCell(colNum).setCellValue("Similarity");

        reader.processFile(file, new Consumer<SupplierModel>() {
            int rowNum = 1;
            int colNum = 0;
            @Override
            public void accept(SupplierModel supplierModel) {
                XSSFRow bodyRow = sheet.createRow(rowNum++);
                colNum = 0;
                setValue(bodyRow.createCell(colNum++), supplierModel.getEntityId());
                setValue(bodyRow.createCell(colNum++), supplierModel.getHouseNumber());
                setValue(bodyRow.createCell(colNum++), supplierModel.getBravoId());
                setValue(bodyRow.createCell(colNum++), supplierModel.getLegalName());
                setValue(bodyRow.createCell(colNum++), supplierModel.getTradingName());
                setValue(bodyRow.createCell(colNum++), supplierModel.getSupplierName());
                setValue(bodyRow.createCell(colNum++), supplierModel.getJaggaerSupplierName());
                setValue(bodyRow.createCell(colNum), supplierModel.getSimilarity());
            }
        });
    }

    private void setValue(XSSFCell cell, String value) {
        if(null != value){
            cell.setCellValue(value);
            cell.setCellType(CellType.STRING);
        }
    }

    private void setValue(XSSFCell cell, Integer value) {
        if(null != value){
            cell.setCellValue(value);
            cell.setCellType(CellType.NUMERIC);
        }
    }

    private void setValue(XSSFCell cell, Double value) {
        if(null != value){
            cell.setCellValue(value);
            cell.setCellType(CellType.NUMERIC);
        }
    }
}
