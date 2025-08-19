package com.vanky.im.message.client;

import com.vanky.im.message.model.dto.SequenceRequest;
import com.vanky.im.message.model.dto.SequenceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author vanky
 * @date 2024/7/21
 */
@FeignClient(name = "im-sequence", path = "/api/sequence")
public interface SequenceServiceFeignClient {

    @PostMapping("/next")
    ResponseEntity<SequenceResponse.Single> getNextSequence(@RequestBody SequenceRequest.Single request);

    @PostMapping("/next-batch")
    ResponseEntity<SequenceResponse.Batch> getBatchSequences(@RequestBody SequenceRequest.Batch request);
}