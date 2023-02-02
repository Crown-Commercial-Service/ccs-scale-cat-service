package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

/**
 * A unique identifier for a party (organization).
 */
@Data
public class OrganizationIdentifier {

  /**
   * Organization identifiers should be taken from an existing organization identifier list. The
   * scheme field is used to indicate the list or register from which the identifier is taken. This
   * value should be taken from the [Organization Identifier
   * Scheme](https://standard.open-contracting.org/1.1/en/schema/codelists/#organization-identifier-
   * scheme) codelist.
   */
  private Scheme scheme;

  /**
   * The identifier of the organization in the selected scheme
   */
  private String id;

  /**
   * The legally registered name of the organization
   */
  private String legalName;

  /**
   * A URI to identify the organization, such as those provided by [Open
   * Corporates](http://www.opencorporates.com) or some other relevant URI provider. This is not for
   * listing the website of the organization: that can be done through the URL field of the
   * Organization contact point.
   */
  private String uri;

}
