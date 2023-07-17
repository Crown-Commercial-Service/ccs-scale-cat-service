package uk.gov.crowncommercial.dts.scale.cat.service.ocds;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Record1;

@Service
@RequiredArgsConstructor
public class ProjectRecordService {
    private final OcdsRecordMapper recordMapper;
    public Record1 populate(Record1 record, ProjectQuery query) {
         return recordMapper.populate(query, record);
    }
}
