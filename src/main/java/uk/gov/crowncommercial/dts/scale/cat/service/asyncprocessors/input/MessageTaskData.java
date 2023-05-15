package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.input;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class MessageTaskData {
    private Integer messageId;
    private Integer eventId;
    private String profile;
}
