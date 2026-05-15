package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mi_questions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MiQuestionsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    Integer questionId;

    @Column(name = "question_framework")
    private String questionFramework;

    @Column(name = "question_framework_lot")
    private String questionFrameworkLotNumber;

    @Column(name = "question_title")
    private String questionTitle;

    @Column(name = "question_description")
    private String questionDescription;

    @Column(name = "question_data_type")
    String questionDataType;

    @Column(name = "question_order")
    Integer questionOrder;

    @Column(name = "question_mandatory")
    Boolean questionMandatory;

    @Type(JsonType.class)
    @Column(name = "question_dependency", columnDefinition = "jsonb")
    String questionDependency;

    @Column(name = "question_type")
    String questionType;

    @Type(JsonType.class)
    @Column(name = "question_option", columnDefinition = "jsonb")
    String options;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
