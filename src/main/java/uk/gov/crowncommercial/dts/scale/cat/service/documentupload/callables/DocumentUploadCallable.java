package uk.gov.crowncommercial.dts.scale.cat.service.documentupload.callables;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentUpload;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;
import uk.gov.crowncommercial.dts.scale.cat.utils.ByteArrayMultipartFile;

import java.util.concurrent.Callable;


@RequiredArgsConstructor
@Slf4j
public class DocumentUploadCallable implements Callable<Boolean> {

    private JaggaerService jaggaerService;

    private  DocumentUpload documentUpload;

    private ByteArrayMultipartFile multipartFile;

    private ProcurementEvent procurementEvent;

    public DocumentUploadCallable(JaggaerService jaggaerService, ProcurementEvent procurementEvent,DocumentUpload documentUpload,ByteArrayMultipartFile multipartFile){

        this.jaggaerService=jaggaerService;
        this.procurementEvent=procurementEvent;
        this.documentUpload=documentUpload;
        this.multipartFile=multipartFile;

    }

    @Override
    public Boolean call() throws Exception {

        var docKey=DocumentKey.fromString(documentUpload.getDocumentId());
        try{
            StopWatch publishStopWatch= new StopWatch();
            publishStopWatch.start();
            jaggaerService.eventUploadDocument(procurementEvent, docKey.getFileName(),
                documentUpload.getDocumentDescription(), documentUpload.getAudience(), multipartFile);
            publishStopWatch.stop();
            log.info("publishEvent : Total time taken to Upload Document for procID {} : eventId :{} , Timetaken : {}  ", procurementEvent.getProject().getId(),procurementEvent.getEventID(),publishStopWatch.getLastTaskTimeMillis());

        }catch(Exception e){
            return false;
        }
        return true;
    }
}
