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
@FieldDefaults(level = AccessLevel.PRIVATE)
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

}
