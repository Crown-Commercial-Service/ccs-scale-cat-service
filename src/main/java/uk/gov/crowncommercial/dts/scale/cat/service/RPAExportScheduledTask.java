package uk.gov.crowncommercial.dts.scale.cat.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.RPATransferS3Config;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BuyerUserDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Component
@RequiredArgsConstructor
@Slf4j
public class RPAExportScheduledTask {

  private static final String SHEET_NAME = "Buyers_Details";
  private static final String MIMETYPE_XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final String ACCOUNT_ID = "52423";
  private static final String TIME_ZONE = "Europe/London";
  private static final String DIVISION = "Central Government";

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final EncryptionService encryptionService;
  private final RPATransferS3Config rpaTransferS3Config;
  private final UserProfileService userProfileService;
  private final AmazonS3 rpaTransferS3Client;

//  @Scheduled(cron = "${config.external.s3.rpa.exportSchedule}")
  @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
  @Transactional
  public void scheduleSelfServiceBuyers() {
    log.info("Begin scheduled processing of unexported buyer records for RPA");

    // Get list of all buyer userIds from CaS DB:
    var buyerUserIds = retryableTendersDBDelegate.findAll().parallelStream()
        .map(BuyerUserDetails::getUserId).toList();

    // Get all self-serve buyers and retain any with SSO data and NOT in DB
    var nonExportedJaggaerBuyers =
        userProfileService.getSelfServiceBuyerCompany().getReturnSubUser().getSubUsers().stream()
            .filter(subUser -> subUser.getSsoCodeData() != null)
            .filter(subUser -> !buyerUserIds.contains(subUser.getUserId())).toList();

    // Add new recs to DB
    var newBuyerDetails = nonExportedJaggaerBuyers.stream()
        .map(subUser -> BuyerUserDetails.builder().userId(subUser.getUserId())
            .userPassword(encryptionService.generateBuyerPassword()).exported(Boolean.FALSE)
            .createdAt(Instant.now()).createdBy("RPAExportScheduledTask").build())
        .toList();

    try {
      retryableTendersDBDelegate.saveAll(newBuyerDetails);
      log.debug("Saved {} non exported Jaggaer user profiles to DB", newBuyerDetails.size());

    } catch (ExhaustedRetryException ex) {
      var cause = ex.getCause();
      if (cause instanceof DataIntegrityViolationException divex) {
        log.warn("Data integrity exception saving new buyer details: " + divex.getMessage());
        return;
      }
      throw ex;
    }
    // Query DB for non-exported buyers and go from there..
    var nonExportedBuyers = retryableTendersDBDelegate.findByExported(false);
    
    Map<String, SubUser> jaggaerBuyerMap = nonExportedJaggaerBuyers.stream()
        .filter(jaggerUser -> nonExportedBuyers.stream()
            .anyMatch(buyerDetail -> buyerDetail.getUserId().equals(jaggerUser.getUserId())))
        .collect(Collectors.toMap(SubUser::getUserId, Function.identity()));

    if (!nonExportedBuyers.isEmpty()) {
      var workbook = generateWorkbook(nonExportedBuyers, jaggaerBuyerMap);
      transferToS3(workbook);
      nonExportedBuyers.forEach(buyer -> buyer.setExported(Boolean.TRUE));
      retryableTendersDBDelegate.saveAll(nonExportedBuyers);
    }
    log.info("Completed scheduled processing of {} buyer records for RPA",
        nonExportedBuyers.size());
  }

  private XSSFWorkbook generateWorkbook(final Set<BuyerUserDetails> buyerUsers, final Map<String, SubUser> jaggaerUsers) {
    var workbook = new XSSFWorkbook();
    var sheet = workbook.createSheet(SHEET_NAME);
    var row = sheet.createRow(0);
    var style = workbook.createCellStyle();
    var font = workbook.createFont();

    font.setBold(true);
    font.setFontHeight(16);
    style.setFont(font);
    createCell(row, 0, "accountId", style);
    createCell(row, 1, "Username", style);
    createCell(row, 2, "First Name", style);
    createCell(row, 3, "Last Name", style);
    createCell(row, 4, "Email", style);
    createCell(row, 5, "Telephone", style);
    createCell(row, 6, "Preferred Language", style);
    createCell(row, 7, "Time Zone", style);
    createCell(row, 8, "password", style);
    createCell(row, 9, "Department", style);
    createCell(row, 10, "Division", style);
    createCell(row, 11, "Role", style);
    createCell(row, 12, "NHS-sid Profile Number (e.g.sid023339)", style);

    writeData(buyerUsers, workbook, jaggaerUsers);
    return workbook;
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

  private void writeData(final Set<BuyerUserDetails> buyerUsers, final XSSFWorkbook workbook,
      final Map<String, SubUser> jaggaerUserMap) {
    var style = workbook.createCellStyle();
    var font = workbook.createFont();
    var sheet = workbook.getSheet(SHEET_NAME);

    font.setFontHeight(12);
    style.setFont(font);
    var rowCount = new AtomicInteger(1);
    buyerUsers.forEach(user -> {
      if (jaggaerUserMap.get(user.getUserId()) != null) {
        var row = sheet.createRow(rowCount.getAndIncrement());
        var columnCount = 0;
        createCell(row, columnCount++, ACCOUNT_ID, style);
        createCell(row, columnCount++, jaggaerUserMap.get(user.getUserId()).getEmail(), style);
        createCell(row, columnCount++, jaggaerUserMap.get(user.getUserId()).getName(), style);
        createCell(row, columnCount++, jaggaerUserMap.get(user.getUserId()).getSurName(), style);
        createCell(row, columnCount++, jaggaerUserMap.get(user.getUserId()).getEmail(), style);
        createCell(row, columnCount++, jaggaerUserMap.get(user.getUserId()).getMobilePhoneNumber(),
            style);
        createCell(row, columnCount++, jaggaerUserMap.get(user.getUserId()).getLanguage(), style);
        createCell(row, columnCount++, TIME_ZONE, style);
        createCell(row, columnCount++, encryptionService.decryptPassword(user.getUserPassword()),
            style);
        createCell(row, columnCount++, jaggaerUserMap.get(user.getUserId()).getBusinessUnit(),
            style);
        createCell(row, columnCount++, DIVISION, style);
        createCell(row, columnCount++, jaggaerUserMap.get(user.getUserId()).getRightsProfile(),
            style);
        createCell(row, columnCount++, "", style);
      }
    });
  }

  @SneakyThrows
  private ByteArrayOutputStream encryptWorkbook(final XSSFWorkbook workbook) {

    try (POIFSFileSystem fs = new POIFSFileSystem()) {
      EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
      // EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile, CipherAlgorithm.aes256,
      // HashAlgorithm.sha384, -1, -1, null);
      Encryptor enc = info.getEncryptor();
      enc.confirmPassword(rpaTransferS3Config.getWorkbookPassword());

      var baos = new ByteArrayOutputStream();
      workbook.write(baos);
      workbook.close();

      // Encrypt
      // TODO - something not right here, file gets created and is password protected but Excel 2016
      // not able to open it for some reason
      try (OPCPackage opc = OPCPackage.create(baos); OutputStream os = enc.getDataStream(fs)) {
        opc.save(os);
      }

      // Write encrypted to our output stream
      try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
        fs.writeFilesystem(byteArrayOutputStream);
        return byteArrayOutputStream;
      }
    }
  }

  private void transferToS3(final XSSFWorkbook workbook) {

    var filename = DateTimeFormatter.ISO_DATE_TIME
        .format(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)).replace(":", "") + ".xlsx";
    var objectPrefix = StringUtils.hasText(rpaTransferS3Config.getObjectPrefix())
        ? rpaTransferS3Config.getObjectPrefix() + "/"
        : "";
    var objectKey = objectPrefix + filename;

    var byteArrayOutputStream = new ByteArrayOutputStream();
    try {
      workbook.write(byteArrayOutputStream);
      var bytes = byteArrayOutputStream.toByteArray();
      // var bytes = encryptWorkbook(workbook).toByteArray();
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
