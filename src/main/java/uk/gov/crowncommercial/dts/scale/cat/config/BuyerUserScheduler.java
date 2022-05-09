package uk.gov.crowncommercial.dts.scale.cat.config;

import static java.time.Duration.ofSeconds;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.oauth2.sdk.GrantType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BuyerUserDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.GetCompanyDataResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;

@Component
@RequiredArgsConstructor
@Slf4j
public class BuyerUserScheduler {

  @Value("${spring.security.oauth2.client.registration.jaggaer.client-id}")
  private String clientId;

  @Value("${spring.security.oauth2.client.registration.jaggaer.client-secret}")
  private String clientSecret;

  @Value("${spring.security.oauth2.client.provider.jaggaer.token-uri}")
  private String tokenUri;

  private final BuyerUserDetailsRepo buyerDetailsRepo;
  private final JaggaerAPIConfig jaggaerAPIConfig;
  private XSSFWorkbook workbook;
  private XSSFSheet sheet;
  private static final String SHEET_NAME = "Buyers_Details";

  // TODO cron pattern should be applied after decision
  @Scheduled(fixedDelayString = "PT1M")
  public void scheduleSelfServiceBuyers() {
    log.info("- Scheduler started to get Self Service Buyers -");
    var buyersInJaggaer = this.getSelfServiceBuyers();

    if (buyersInJaggaer == null || !"0".equals(buyersInJaggaer.getReturnCode())
        || !"OK".equals(buyersInJaggaer.getReturnMessage())) {
      log.info("Invalid response in scheduler");
    } else if (buyersInJaggaer.getReturnCompanyData().size() != 1) {
      log.info("Empty details in User-pool scheduler");
    } else {
      var buyersInTender = buyerDetailsRepo.findAll();
      var usersToAdd = new ArrayList<SubUser>();

      buyersInJaggaer.getReturnCompanyData().stream()
          .forEach(e -> usersToAdd.addAll(e.getReturnSubUser().getSubUsers().stream()
              .filter(ssoDataUser -> ssoDataUser.getSsoCodeData() != null)
              .filter(subUser -> buyersInTender.stream()
                  .noneMatch(supplier -> supplier.getUserId().equals(subUser.getUserId())))
              .collect(Collectors.toList())));

      log.info("Scheduler - Save unmatched Self Service Buyers in DB - userCount {}",
          usersToAdd.size());
      var savedUsers = buyerDetailsRepo.saveAll(usersToAdd.stream()
          .map(e -> BuyerUserDetails.builder().userId(e.getUserId())
              .userPassword(RandomStringUtils.randomAlphanumeric(8)).createdAt(Instant.now())
              .createdBy("Scheduler").build())
          .collect(Collectors.toList()));

      generateExcelSheet(savedUsers);
    }
  }

  private void generateExcelSheet(List<BuyerUserDetails> usersList) {
    workbook = new XSSFWorkbook();
    sheet = workbook.createSheet(SHEET_NAME);
    Row row = sheet.createRow(0);
    CellStyle style = workbook.createCellStyle();
    XSSFFont font = workbook.createFont();
    font.setBold(true);
    font.setFontHeight(16);
    style.setFont(font);
    createCell(row, 0, "UserId", style);
    createCell(row, 1, "Password", style);
    writeData(usersList);
  }

  private void createCell(Row row, int columnCount, Object value, CellStyle style) {
    sheet.autoSizeColumn(columnCount);
    Cell cell = row.createCell(columnCount);
    if (value instanceof Integer)
      cell.setCellValue((Integer) value);
    else
      cell.setCellValue((String) value);

    cell.setCellStyle(style);
  }

  private void writeData(List<BuyerUserDetails> usersList) {
    CellStyle style = workbook.createCellStyle();
    XSSFFont font = workbook.createFont();
    font.setFontHeight(14);
    style.setFont(font);
    usersList.stream().forEach(user -> {
      int rowCount = 1;
      Row row = sheet.createRow(rowCount++);
      int columnCount = 0;
      createCell(row, columnCount++, user.getUserId(), style);
      createCell(row, columnCount++, user.getUserPassword(), style);
    });
  }

  private GetCompanyDataResponse getSelfServiceBuyers() {
    WebClient client = WebClient.builder().build();
    final var selfServiceUsersUrl = jaggaerAPIConfig.getGetBuyerCompanyProfile().get(ENDPOINT);
    Mono<GetCompanyDataResponse> resource = client.post().uri(tokenUri)
        .header(HttpHeaders.AUTHORIZATION,
            "Basic " + Base64Utils.encodeToString((clientId + ":" + clientSecret).getBytes()))
        .body(BodyInserters.fromFormData(OAuth2ParameterNames.GRANT_TYPE,
            GrantType.CLIENT_CREDENTIALS.getValue()))
        .retrieve().bodyToMono(JsonNode.class).flatMap(tokenResponse -> {
          String accessTokenValue = tokenResponse.get("token").textValue();
          return client.get().uri(jaggaerAPIConfig.getBaseUrl() + "" + selfServiceUsersUrl)
              .headers(h -> h.setBearerAuth(accessTokenValue)).retrieve()
              .bodyToMono(GetCompanyDataResponse.class);
        });
    return resource.block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration()));
  }
}
