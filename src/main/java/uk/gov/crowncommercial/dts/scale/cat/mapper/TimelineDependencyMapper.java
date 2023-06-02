package uk.gov.crowncommercial.dts.scale.cat.mapper;

import lombok.RequiredArgsConstructor;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

import java.lang.reflect.InvocationTargetException;

@RequiredArgsConstructor
@Component
public class TimelineDependencyMapper {

    public TimelineDependency convertToTimelineDependency(final Requirement requirement) {

        final var timelineDependency = new TimelineDependency();
        final var timeline = requirement.getNonOCDS().getTimelineDependency();

        try {
            BeanUtils.copyProperties(timelineDependency, timeline);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        return timelineDependency;
    }
}
