package uk.gov.crowncommercial.dts.scale.cat.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.QuestionWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.QuestionWriteResponse;

import java.util.List;

/**
 * FeignClient, use Web client under the hood to interface with the Question and Answer Service API.
 */
@FeignClient(name = "questionAndAnswerClient", url = "${config.external.questionAndAnswerService.baseUrl}")
public interface QuestionAndAnswerClient {

    @PostMapping("${config.external.questionAndAnswerService.insertOrUpdateQuestions}")
    QuestionWriteResponse createQuestions(@RequestBody QuestionWrite questionWrite, @RequestParam String eventType, @RequestHeader("x-api-key") String apiKey);

    @GetMapping("${config.external.questionAndAnswerService.getLotEventTypeDataTemplates}")
    List<DataTemplate> getEventDataTemplates(@PathVariable("agreement-id") String agreementId, @PathVariable("lot-id") String lotId, @PathVariable("event-type") String eventType, @RequestHeader("x-api-key") String apiKey);
}
