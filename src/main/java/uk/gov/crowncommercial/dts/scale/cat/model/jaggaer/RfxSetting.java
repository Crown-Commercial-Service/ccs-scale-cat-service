package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.jackson.Jacksonized;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

@Value
@Jacksonized
@Builder
public class RfxSetting {

  String rfxId;
  String rfxReferenceCode;
  Integer rfiFlag;
  String tenderReferenceCode;
  String templateReferenceCode;
  @NonFinal
  String shortDescription;
  String longDescription;
  String rankingStrategy;
  BuyerCompany buyerCompany;
  OwnerUser ownerUser;
  String rfxType;
  String status;
  Integer statusCode;
  
  // @JsonFormat added by RoweIT for Jaggaer interface 
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXX")
  OffsetDateTime lastUpdate;
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXX")
  OffsetDateTime publishDate;
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXX")
  OffsetDateTime closeDate;
  
  //Added by PS (RoweIT) for creating SalesforceRfx
  Integer value;
  Integer qualEnvStatus;
  Integer techEnvStatus;
  Integer commEnvStatus;
  Integer visibilityEGComments;
  String procurementRoute;
  // end

  public void setShortDescription(final String shortDescription) {
    this.shortDescription = shortDescription;
  }
}
