package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * JPA entity representing rfx reference code mappings for Salesforce -> Jaggaer API calls
 */
@Entity
@Table(name = "rfx_template_mapping")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PUBLIC)
@EqualsAndHashCode
public class RfxTemplateMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "rfx_reference_code_mapping_id")
  Integer id;

  @Column(name = "rfx_reference_code")
  String rfxReferenceCode;

  @Column(name = "rfx_short_description")
  String rfxShortDescription;

  @Column(name = "commercial_agreement_number")
  String commercialAgreementNumber;

  @Column(name = "lot_number")
  String lotNumber;
  
  public void templateMapping(String rfxReferenceCode, String rfxShortDescription, String commercialAgreementNumber, String lotNumber) {
	  this.rfxReferenceCode=rfxReferenceCode;
	  this.rfxShortDescription=rfxShortDescription;
	  this.commercialAgreementNumber= commercialAgreementNumber;
	  this.lotNumber=lotNumber;
  }

  @Override
  public String toString() {
	  return "Rfx Template Mapping [rfxReferenceCode=" + rfxReferenceCode + ", rfxShortDescription=" + 
  rfxShortDescription + ", commercialAgreementNumber=" + commercialAgreementNumber + ", lotNumber=" + lotNumber;
	 
  }
  
  
}
