package uk.gov.crowncommercial.dts.scale.cat.service.documentupload.callables;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentUpload;
import uk.gov.crowncommercial.dts.scale.cat.service.documentupload.DocumentUploadService;
import uk.gov.crowncommercial.dts.scale.cat.utils.ByteArrayMultipartFile;

import java.util.Map;
import java.util.concurrent.Callable;


@RequiredArgsConstructor
@Slf4j
public class RetrieveDocumentCallable implements Callable<Map<DocumentUpload, ByteArrayMultipartFile>> {

    private  DocumentUploadService documentUploadService;

    private  DocumentUpload documentUpload;

    private String principal;

    public RetrieveDocumentCallable(DocumentUploadService documentUploadService,DocumentUpload documentUpload, String principal){

        this.documentUploadService=documentUploadService;
        this.documentUpload=documentUpload;
        this.principal=principal;

    }

    @Override
    public Map<DocumentUpload, ByteArrayMultipartFile> call() throws Exception {

        var docKey = DocumentKey.fromString(documentUpload.getDocumentId());
        var multipartFile = new ByteArrayMultipartFile(
                documentUploadService.retrieveDocument(documentUpload, principal),
                docKey.getFileName(), documentUpload.getMimetype());

        return Map.of(documentUpload,multipartFile);
    }
}
