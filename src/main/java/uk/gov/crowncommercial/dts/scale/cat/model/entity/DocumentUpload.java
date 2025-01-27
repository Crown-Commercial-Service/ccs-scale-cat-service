package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;

/**
 *
 */
@Entity
@Table(name = "document_uploads")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(exclude = "procurementEvent")
public class DocumentUpload {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "document_upload_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "event_id")
  ProcurementEvent procurementEvent;

  @Column(name = "document_id")
  String documentId;

  @Column(name = "external_document_id")
  String externalDocumentId;

  @Column(name = "external_status")
  @Enumerated(EnumType.STRING)
  VirusCheckStatus externalStatus;

  @Column(name = "audience")
  @Enumerated(EnumType.STRING)
  DocumentAudienceType audience;

  @Column(name = "document_description")
  String documentDescription;

  @Column(name = "size")
  Long size;

  @Column(name = "mimetype")
  String mimetype;

  @Embedded
  private Timestamps timestamps;
}
