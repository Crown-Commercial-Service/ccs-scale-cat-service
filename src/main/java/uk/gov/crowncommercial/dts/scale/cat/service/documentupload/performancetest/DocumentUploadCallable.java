package uk.gov.crowncommercial.dts.scale.cat.service.documentupload.performancetest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentUpload;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;
import uk.gov.crowncommercial.dts.scale.cat.service.documentupload.DocumentUploadService;
import uk.gov.crowncommercial.dts.scale.cat.utils.ByteArrayMultipartFile;

import java.util.Map;
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
        jaggaerService.eventUploadDocument(procurementEvent, docKey.getFileName(),
                documentUpload.getDocumentDescription(), documentUpload.getAudience(), multipartFile);
        }catch(Exception e){
            return false;
        }
        return true;
    }
}
