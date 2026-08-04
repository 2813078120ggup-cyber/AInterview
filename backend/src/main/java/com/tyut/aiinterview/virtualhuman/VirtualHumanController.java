package com.tyut.aiinterview.virtualhuman;

import com.tyut.aiinterview.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/virtual-human")
public class VirtualHumanController {
    private final VirtualHumanService service;

    public VirtualHumanController(VirtualHumanService service) {
        this.service = service;
    }

    @GetMapping("/sdk-config")
    public ApiResponse<VirtualHumanDtos.SdkConfigResponse> sdkConfig() {
        return ApiResponse.ok(service.sdkConfig());
    }

}
