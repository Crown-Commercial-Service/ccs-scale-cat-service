package uk.gov.crowncommercial.dts.scale.cat.controller;

import java.util.Collections;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.crowncommercial.dts.scale.cat.model.AgreementSummary;

/**
 *
 */
@RestController
public class AgreementsController {

  @GetMapping("/agreement-summaries")
  public Set<AgreementSummary> getAgreementSummaries() {
    AgreementSummary tp2 = new AgreementSummary();
    tp2.setName("Technology Products 2");
    tp2.setNumber("RM1045");
    return Collections.singleton(tp2);
  }

}
