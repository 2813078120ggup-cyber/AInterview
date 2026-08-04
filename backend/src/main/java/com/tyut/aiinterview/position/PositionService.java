package com.tyut.aiinterview.position;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.common.PageResult;
import com.tyut.aiinterview.domain.JobPosition;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionService {
    private final JobPositionMapper positionMapper;
    private final CurrentUser currentUser;
    public PositionService(JobPositionMapper positionMapper, CurrentUser currentUser) { this.positionMapper = positionMapper; this.currentUser = currentUser; }

    public PageResult<PositionDtos.PositionVO> page(PositionDtos.PositionQuery query) {
        long pageNo = query.pageNo() == null ? 1 : Math.max(1, query.pageNo()); long pageSize = query.pageSize() == null ? 20 : Math.min(100, Math.max(1, query.pageSize()));
        LambdaQueryWrapper<JobPosition> wrapper = new LambdaQueryWrapper<JobPosition>().orderByDesc(JobPosition::getCreatedAt);
        if (query.keyword() != null && !query.keyword().isBlank()) wrapper.and(item -> item.like(JobPosition::getName, query.keyword()).or().like(JobPosition::getPositionCode, query.keyword()));
        if (query.status() != null) wrapper.eq(JobPosition::getStatus, query.status());
        Page<JobPosition> result = positionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(), result.getTotal(), pageNo, pageSize);
    }
    public PositionDtos.PositionVO detail(Long id) { return toVO(requirePosition(id)); }
    @Transactional public PositionDtos.PositionVO create(PositionDtos.PositionRequest request) {
        validateStatus(request.status()); if (positionMapper.exists(new LambdaQueryWrapper<JobPosition>().eq(JobPosition::getPositionCode, request.positionCode()))) throw BusinessException.badRequest("岗位编码已存在");
        JobPosition position = new JobPosition(); apply(position, request); position.setCreatedBy(currentUser.id()); positionMapper.insert(position); return toVO(position);
    }
    @Transactional public PositionDtos.PositionVO update(Long id, PositionDtos.PositionRequest request) {
        validateStatus(request.status()); JobPosition position = requirePosition(id);
        if (!position.getPositionCode().equals(request.positionCode()) && positionMapper.exists(new LambdaQueryWrapper<JobPosition>().eq(JobPosition::getPositionCode, request.positionCode()))) throw BusinessException.badRequest("岗位编码已存在");
        apply(position, request); positionMapper.updateById(position); return toVO(position);
    }
    @Transactional public void delete(Long id) { positionMapper.deleteById(requirePosition(id).getId()); }
    private void apply(JobPosition target, PositionDtos.PositionRequest source) { target.setPositionCode(source.positionCode()); target.setName(source.name()); target.setDepartment(source.department()); target.setDescription(source.description()); target.setCompetencyModel(source.competencyModel()); target.setStatus(source.status()); }
    private void validateStatus(Integer status) { if (status != 0 && status != 1) throw BusinessException.badRequest("岗位状态不合法"); }
    private JobPosition requirePosition(Long id) { JobPosition position = positionMapper.selectById(id); if (position == null) throw BusinessException.notFound("岗位不存在"); return position; }
    private PositionDtos.PositionVO toVO(JobPosition item) { return new PositionDtos.PositionVO(item.getId(), item.getPositionCode(), item.getName(), item.getDepartment(), item.getDescription(), item.getCompetencyModel(), item.getStatus(), item.getCreatedBy(), item.getCreatedAt(), item.getUpdatedAt()); }
}
