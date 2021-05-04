package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;

/**
 *
 */
@Value
@Builder
public class Tender {

  /*
   * { "operationCode": "CREATEUPDATE", "project": { "tender": { "title":
   * "RM0001-Lot1a-TomBPlcAPI-Test3", "buyerCompany": { "id": 52423 }, "projectOwner": { "id":
   * "102463" } } } }
   */

  String title;
  BuyerCompany buyerCompany;
  ProjectOwner projectOwner;

}
