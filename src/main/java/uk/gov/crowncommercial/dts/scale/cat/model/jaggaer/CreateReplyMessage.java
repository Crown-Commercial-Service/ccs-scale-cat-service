package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import com.amazonaws.services.s3.model.Owner;
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
  
  private OwnerUser operatorUser;
  
  private String parentMessageId;
  
}
