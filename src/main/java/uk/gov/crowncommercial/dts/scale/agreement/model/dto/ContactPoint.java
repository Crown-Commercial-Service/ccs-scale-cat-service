package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

/**
 * Contact point
 */
@Data
public class ContactPoint {

  /**
   * The name of the contact person, department, or contact point, for correspondence relating to
   * this contracting process.
   */
  private String name;

  /**
   * The e-mail address of the contact point/person.
   */
  private String email;

  /**
   * The telephone number of the contact point/person. This should include the international dialing
   * code.
   */
  private String telephone;

  /**
   * The fax number of the contact point/person. This should include the international dialing code.
   */
  private String faxNumber;

  /**
   * A web address for the contact point/person.
   */
  private String url;

}
