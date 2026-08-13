package com.tyut.aiinterview.recruitment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.ApplicationNote;
import com.tyut.aiinterview.domain.CompanyCandidate;
import com.tyut.aiinterview.domain.CompanyCandidateTagRelation;
import com.tyut.aiinterview.mapper.ApplicationNoteMapper;
import com.tyut.aiinterview.mapper.CompanyCandidateMapper;
import com.tyut.aiinterview.mapper.CompanyCandidateTagMapper;
import com.tyut.aiinterview.mapper.CompanyCandidateTagRelationMapper;
import com.tyut.aiinterview.mapper.CompanyTalentPoolMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.JobApplicationMapper;
import com.tyut.aiinterview.mapper.JobPositionMapper;
import com.tyut.aiinterview.mapper.OfflineInterviewMapper;
import com.tyut.aiinterview.mapper.UserMapper;
import com.tyut.aiinterview.observability.OperationAuditService;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

class CompanyTalentPoolServiceTest {
    private final CompanyTalentPoolMapper talentPoolMapper = mock(CompanyTalentPoolMapper.class);
    private final CompanyCandidateMapper candidateMapper = mock(CompanyCandidateMapper.class);
    private final ApplicationNoteMapper noteMapper = mock(ApplicationNoteMapper.class);
    private final CompanyCandidateTagMapper tagMapper = mock(CompanyCandidateTagMapper.class);
    private final CompanyCandidateTagRelationMapper relationMapper = mock(CompanyCandidateTagRelationMapper.class);
    private final JobApplicationMapper applicationMapper = mock(JobApplicationMapper.class);
    private final JobPositionMapper positionMapper = mock(JobPositionMapper.class);
    private final InterviewMapper interviewMapper = mock(InterviewMapper.class);
    private final OfflineInterviewMapper offlineInterviewMapper = mock(OfflineInterviewMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final CompanyAccessService companyAccess = mock(CompanyAccessService.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final OperationAuditService auditService = mock(OperationAuditService.class);
    private final CompanyTalentPoolService service = new CompanyTalentPoolService(talentPoolMapper, candidateMapper,
            noteMapper, tagMapper, relationMapper, applicationMapper, positionMapper, interviewMapper,
            offlineInterviewMapper, userMapper, companyAccess, currentUser, auditService);

    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        for (Class<?> entityType : List.of(ApplicationNote.class, CompanyCandidate.class,
                CompanyCandidateTagRelation.class)) {
            if (TableInfoHelper.getTableInfo(entityType) == null) TableInfoHelper.initTableInfo(assistant, entityType);
        }
        when(currentUser.id()).thenReturn(88L);
        when(userMapper.selectBatchIds(anyList())).thenReturn(List.of());
        when(relationMapper.selectList(any())).thenReturn(List.of());
        when(tagMapper.selectBatchIds(anyList())).thenReturn(List.of());
    }

    @Test
    void addingAndRemovingUsesSoftMembershipAndAppendsAudit() {
        when(companyAccess.requirePermission("application:review")).thenReturn(100L);
        when(candidateMapper.selectForUpdate(100L, 7L)).thenReturn(null);
        when(candidateMapper.insert(any(CompanyCandidate.class))).thenAnswer(invocation -> {
            CompanyCandidate candidate = invocation.getArgument(0);
            candidate.setId(41L);
            return 1;
        });

        var added = service.add(7L);

        assertEquals(41L, added.poolId());
        assertEquals(0, added.version());
        verify(auditService).success(eq("RECRUITMENT"), eq("TALENT_POOL_ADDED"), eq("COMPANY_CANDIDATE"),
                eq(41L), eq(100L), any(String.class));

        CompanyCandidate active = new CompanyCandidate();
        active.setId(41L);
        active.setCompanyId(100L);
        active.setCandidateId(7L);
        active.setStatus("ACTIVE");
        active.setVersion(0);
        when(candidateMapper.selectForUpdate(100L, 7L)).thenReturn(active);

        var removed = service.remove(7L);

        assertFalse(removed.active());
        assertEquals("REMOVED", active.getStatus());
        assertEquals(1, active.getVersion());
        verify(candidateMapper).updateById(active);
        verify(auditService).success(eq("RECRUITMENT"), eq("TALENT_POOL_REMOVED"), eq("COMPANY_CANDIDATE"),
                eq(41L), eq(100L), any(String.class));
    }

    @Test
    void foreignCompanyCandidateIsRejectedBeforeTalentPoolLookup() {
        when(companyAccess.requirePermission("application:read")).thenReturn(200L);
        when(companyAccess.isRestrictedInterviewer()).thenReturn(false);
        doThrow(BusinessException.notFound("候选人不存在")).when(companyAccess).requireCandidateAccess(7L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.membership(7L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(candidateMapper, org.mockito.Mockito.never()).selectOne(any());
    }

    @Test
    void staleNoteVersionReturnsConflictWithoutOverwritingNewerContent() {
        when(companyAccess.requirePermission("application:review")).thenReturn(100L);
        CompanyCandidate pool = pool(41L, 100L, 7L);
        when(candidateMapper.selectOne(any())).thenReturn(pool);
        ApplicationNote note = new ApplicationNote();
        note.setId(52L);
        note.setCompanyId(100L);
        note.setCompanyCandidateId(41L);
        note.setCandidateId(7L);
        note.setAuthorId(88L);
        note.setVersion(3);
        note.setContent("最新备注");
        when(noteMapper.selectForUpdate(52L, 100L)).thenReturn(note);
        when(noteMapper.update(any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.updateNote(7L, 52L,
                new TalentPoolDtos.NoteUpdateRequest("旧版本覆盖", 2)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("最新备注", note.getContent());
        verify(auditService, org.mockito.Mockito.never()).success(any(), eq("TALENT_POOL_NOTE_UPDATED"), any(),
                any(), any(), any());
    }

    @Test
    void tagFromAnotherCompanyCannotBeAssigned() {
        when(companyAccess.requirePermission("application:review")).thenReturn(100L);
        when(candidateMapper.selectOne(any())).thenReturn(pool(41L, 100L, 7L));
        when(tagMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.addTag(7L, 900L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(relationMapper, org.mockito.Mockito.never()).insert(any(CompanyCandidateTagRelation.class));
    }

    @Test
    void pagePassesCurrentCompanyAndRestrictedInterviewerScopeToDatabaseQuery() {
        when(companyAccess.requirePermission("application:read")).thenReturn(100L);
        when(companyAccess.isRestrictedInterviewer()).thenReturn(true);
        when(currentUser.id()).thenReturn(88L);
        when(talentPoolMapper.selectPage(eq(100L), eq(88L), eq(true), isNull(Long.class), isNull(String.class),
                isNull(Long.class), eq("Java"), isNull(Long.class), isNull(LocalDateTime.class),
                isNull(LocalDateTime.class), eq("UPDATED"), eq(0), eq(20))).thenReturn(List.of());
        when(talentPoolMapper.count(eq(100L), eq(88L), eq(true), isNull(Long.class), isNull(String.class),
                isNull(Long.class), eq("Java"), isNull(Long.class), isNull(LocalDateTime.class),
                isNull(LocalDateTime.class))).thenReturn(0L);

        var result = service.page(new TalentPoolDtos.Query(1L, 20L, "", null, "Java", null,
                null, null, "UPDATED"));

        assertEquals(0, result.total());
        verify(talentPoolMapper).selectPage(eq(100L), eq(88L), eq(true), isNull(Long.class), isNull(String.class),
                isNull(Long.class), eq("Java"), isNull(Long.class), isNull(LocalDateTime.class),
                isNull(LocalDateTime.class), eq("UPDATED"), eq(0), eq(20));
    }

    private CompanyCandidate pool(Long id, Long companyId, Long candidateId) {
        CompanyCandidate pool = new CompanyCandidate();
        pool.setId(id);
        pool.setCompanyId(companyId);
        pool.setCandidateId(candidateId);
        pool.setStatus("ACTIVE");
        pool.setVersion(0);
        return pool;
    }
}
