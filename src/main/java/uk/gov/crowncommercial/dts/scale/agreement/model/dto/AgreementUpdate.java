package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Agreement Update.
 */
@Data
public class AgreementUpdate {

  /**
   * Date that the update was added.
   */
  private LocalDate date;

  /**
   * Link to further information regarding the update.
   */
  private String linkUrl;

  /**
   * Actual update text.
   */
  private String text;

}
