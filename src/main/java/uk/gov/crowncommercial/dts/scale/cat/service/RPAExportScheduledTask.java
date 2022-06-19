package uk.gov.crowncommercial.dts.scale.cat.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.RPATransferS3Config;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BuyerUserDetails;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Component
@RequiredArgsConstructor
@Slf4j
public class RPAExportScheduledTask {

  private static final String SHEET_NAME = "Buyers_Details";
  private static final String MIMETYPE_XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final EncryptionService encryptionService;
  private final RPATransferS3Config rpaTransferS3Config;
  private final AmazonS3 rpaTransferS3Client;

  @Scheduled(fixedDelayString = "${config.external.jaggaer.rpa.buyerExport")
  public void scheduleSelfServiceBuyers() {
    log.info("Begin scheduled processing of unexported buyer records for RPA");

    var nonExportedBuyers = retryableTendersDBDelegate.findByExported(false);
    if (!nonExportedBuyers.isEmpty()) {
      generateExcelSheet(nonExportedBuyers);
      nonExportedBuyers.forEach(buyer -> buyer.setExported(true));
      retryableTendersDBDelegate.saveAll(nonExportedBuyers);
    }
    log.info("Completed scheduled processing of {} buyer records for RPA",
        nonExportedBuyers.size());
  }

  private void generateExcelSheet(final Set<BuyerUserDetails> usersList) {
    var workbook = new XSSFWorkbook();
    var sheet = workbook.createSheet(SHEET_NAME);
    var row = sheet.createRow(0);
    var style = workbook.createCellStyle();
    var font = workbook.createFont();

    font.setBold(true);
    font.setFontHeight(16);
    style.setFont(font);
    createCell(row, 0, "UserId", style);
    createCell(row, 1, "Password", style);
    writeData(usersList, workbook);

    // Encrypt workbook via password
    workbook.setWorkbookPassword(rpaTransferS3Config.getWorkbookPassword(), null);
    transferToS3(workbook);
  }

  private void createCell(final Row row, final int columnCount, final Object value,
      final CellStyle style) {

    row.getSheet().autoSizeColumn(columnCount);
    var cell = row.createCell(columnCount);
    if (value instanceof Integer cellValue) {
      cell.setCellValue(cellValue);
    } else {
      cell.setCellValue((String) value);
    }
    cell.setCellStyle(style);
  }

  private void writeData(final Set<BuyerUserDetails> usersList, final XSSFWorkbook workbook) {
    var style = workbook.createCellStyle();
    var font = workbook.createFont();
    var sheet = workbook.getSheet(SHEET_NAME);

    font.setFontHeight(14);
    style.setFont(font);
    usersList.forEach(user -> {
      var rowCount = 1;
      var row = sheet.createRow(rowCount++);
      var columnCount = 0;
      createCell(row, columnCount++, user.getUserId(), style);
      createCell(row, columnCount++, encryptionService.decryptPassword(user.getUserPassword()),
          style);
    });
  }

  private void transferToS3(final XSSFWorkbook workbook) {

    var filename = DateTimeFormatter.ISO_DATE_TIME
        .format(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)).replace(":", "") + ".xlsx";
    var objectPrefix =
        StringUtils.hasText(rpaTransferS3Config.getObjectPrefix()) ? rpaTransferS3Config + "/" : "";
    var objectKey = objectPrefix + filename;

    var byteArrayOutputStream = new ByteArrayOutputStream();
    try {
      workbook.write(byteArrayOutputStream);
      var bytes = byteArrayOutputStream.toByteArray();
      var inputStream = new ByteArrayInputStream(bytes);

      var objectMetadata = new ObjectMetadata();
      objectMetadata.setContentLength(bytes.length);
      objectMetadata.setContentType(MIMETYPE_XLSX);

      rpaTransferS3Client.putObject(rpaTransferS3Config.getBucket(), objectKey, inputStream,
          objectMetadata);
      log.info("Put RPA transfer file in S3: {}", objectKey);
    } catch (IOException e) {
      log.error("Error in RPA transfer to S3", e);
    }
  }

}
