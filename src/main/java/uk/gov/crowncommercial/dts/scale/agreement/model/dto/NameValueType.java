package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Name Value Type.
 */
@Data
public class NameValueType {

  /**
   * The name of the 'other' bound to be used as map key.
   */
  private String name;

  /**
   * The data type of the 'other' lotBound value
   */
  private DataType dataType;

  private String valueText;
  private Integer valueInteger;
  private BigDecimal valueNumber;
  private LocalDate valueDate;

}
