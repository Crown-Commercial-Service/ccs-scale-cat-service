package uk.gov.crowncommercial.dts.scale.cat.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.QuestionWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.QuestionWriteResponse;

/**
 * FeignClient, use Web client under the hood to interface with the Question and Answer Service API.
 */
@FeignClient(name = "questionAndAnswerClient", url = "${config.external.questionAndAnswerService.baseUrl}")
public interface QuestionAndAnswerClient {

    @PostMapping("${config.external.questionAndAnswerService.insertOrUpdateQuestions}")
    QuestionWriteResponse createQuestions(@RequestBody QuestionWrite questionWrite, @RequestParam String eventType, @RequestHeader("x-api-key") String apiKey);
}
