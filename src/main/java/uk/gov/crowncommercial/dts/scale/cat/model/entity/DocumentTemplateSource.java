package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 *
 */
@Entity
@Table(name = "document_template_sources")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocumentTemplateSource {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "document_template_source_id")
  Integer id;

  @Column(name = "field_placeholder")
  String placeholder;

  @Column(name = "source_type")
  @Enumerated(EnumType.STRING)
  SourceType sourceType;

  @Column(name = "source_path")
  String sourcePath;

  @Column(name = "target_type")
  @Enumerated(EnumType.STRING)
  TargetType targetType;

  @Column(name = "table_name")
  String tableName;

  @Column(name = "options_property")
  @Enumerated(EnumType.STRING)
  OptionsProperty optionsProperty;
  
  @Column(name = "conditional_value")
  String conditionalValue;
}
