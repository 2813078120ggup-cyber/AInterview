package com.tyut.aiinterview.position;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/positions")
@PreAuthorize("hasRole('ADMIN')")
public class PositionController {
    private final PositionService service;
    public PositionController(PositionService service) { this.service = service; }
    @GetMapping public ApiResponse<PageResult<PositionDtos.PositionVO>> page(PositionDtos.PositionQuery query) { return ApiResponse.ok(service.page(query)); }
    @GetMapping("/{id}") public ApiResponse<PositionDtos.PositionVO> detail(@PathVariable Long id) { return ApiResponse.ok(service.detail(id)); }
    @PostMapping public ApiResponse<PositionDtos.PositionVO> create(@Valid @RequestBody PositionDtos.PositionRequest request) { return ApiResponse.ok(service.create(request)); }
    @PutMapping("/{id}") public ApiResponse<PositionDtos.PositionVO> update(@PathVariable Long id, @Valid @RequestBody PositionDtos.PositionRequest request) { return ApiResponse.ok(service.update(id, request)); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(); }
}
