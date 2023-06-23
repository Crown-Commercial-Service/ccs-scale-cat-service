package uk.gov.crowncommercial.dts.scale.cat.mapper;

import lombok.RequiredArgsConstructor;
<<<<<<< HEAD
import org.apache.commons.beanutils.BeanUtils;
=======

>>>>>>> release/int
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

<<<<<<< HEAD
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Collectors;

=======
import java.util.stream.Collectors;



>>>>>>> release/int
@RequiredArgsConstructor
@Component
public class TimelineDependencyMapper {

    public TimelineDependency convertToTimelineDependency(final Requirement requirement) {

        final var timelineDependency = new TimelineDependency();
        final var timeline = requirement.getNonOCDS().getTimelineDependency();

        timelineDependency.nonOCDS(new TimelineDependencyNonOCDS().answered(timeline.getNonOCDS().getAnswered())
                .options(timeline.getNonOCDS().getOptions().stream().map(option -> new QuestionNonOCDSOptions().value(option.getValue()).text(option.getText()).selected(option.getSelect())).collect(Collectors.toList())));
        timelineDependency.OCDS( new Requirement1().title(timeline.getOcds().getTitle()).description(timeline.getOcds().getDescription()));
<<<<<<< HEAD
=======

>>>>>>> release/int
        return timelineDependency;
    }
}
