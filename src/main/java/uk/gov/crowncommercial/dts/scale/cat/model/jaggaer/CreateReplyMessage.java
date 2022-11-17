package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
*
*/
@Value
@Builder
@Jacksonized
public class CreateReplyMessage {

  private String objectReferenceCode;
  
  private String objectType;
  
  private String subject;
  
  private String body;
  
  private String broadcast;
  
  private String messageClassificationCategoryName;
  
  private SuppliersList supplierList;
  
  private OperatorUser operatorUser;
  
  private String parentMessageId;
  
}
