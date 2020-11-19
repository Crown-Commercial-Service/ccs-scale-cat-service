package uk.gov.crowncommercial.dts.scale.cat.controller;

import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.AgreementSummary;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;

/**
 *
 */
@RestController
@RequiredArgsConstructor
public class AgreementsController {

  private final AgreementsService agreementsService;

  @GetMapping("/agreement-summaries")
  public Set<AgreementSummary> getAgreementSummaries() {
    return Set.of(agreementsService.findAll());
  }

}
