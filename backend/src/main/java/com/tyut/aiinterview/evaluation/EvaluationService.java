package com.tyut.aiinterview.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tyut.aiinterview.common.BusinessException;
import com.tyut.aiinterview.domain.Evaluation;
import com.tyut.aiinterview.domain.Interview;
import com.tyut.aiinterview.domain.InterviewQuestion;
import com.tyut.aiinterview.mapper.EvaluationMapper;
import com.tyut.aiinterview.mapper.InterviewMapper;
import com.tyut.aiinterview.mapper.InterviewQuestionMapper;
import com.tyut.aiinterview.security.CurrentUser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {
    private final EvaluationMapper evaluationMapper;
    private final InterviewMapper interviewMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final CurrentUser currentUser;
    public EvaluationService(EvaluationMapper evaluationMapper, InterviewMapper interviewMapper, InterviewQuestionMapper interviewQuestionMapper, CurrentUser currentUser) {
        this.evaluationMapper = evaluationMapper; this.interviewMapper = interviewMapper; this.interviewQuestionMapper = interviewQuestionMapper; this.currentUser = currentUser;
    }
    public Evaluation submitHuman(Long interviewId, Long interviewQuestionId, EvaluationDtos.HumanEvaluationRequest request) {
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) throw BusinessException.notFound("面试不存在");
        if (!currentUser.id().equals(interview.getInterviewerId())) throw BusinessException.forbidden("仅指定面试官可提交人工评测");
        if (interview.getStatus() != Interview.COMPLETED) throw BusinessException.badRequest("面试结束后才能提交评测");
        InterviewQuestion selected = interviewQuestionMapper.selectById(interviewQuestionId);
        if (selected == null || !selected.getInterviewId().equals(interviewId)) throw BusinessException.notFound("面试题目不存在");
        Evaluation evaluation = evaluationMapper.selectOne(new LambdaQueryWrapper<Evaluation>().eq(Evaluation::getInterviewQuestionId, interviewQuestionId)
                .eq(Evaluation::getSource, "human").eq(Evaluation::getEvaluatorId, currentUser.id()));
        if (evaluation == null) { evaluation = new Evaluation(); evaluation.setInterviewQuestionId(interviewQuestionId); evaluation.setSource("human"); evaluation.setEvaluatorId(currentUser.id()); }
        evaluation.setProfessionalScore(request.professionalScore()); evaluation.setExpressionScore(request.expressionScore()); evaluation.setLogicScore(request.logicScore());
        evaluation.setAdaptabilityScore(request.adaptabilityScore()); evaluation.setOverallScore(request.overallScore()); evaluation.setComment(request.comment()); evaluation.setStatus(1);
        if (evaluation.getId() == null) evaluationMapper.insert(evaluation); else evaluationMapper.updateById(evaluation);
        return evaluation;
    }
    public List<Evaluation> list(Long interviewId) {
        Interview interview = interviewMapper.selectById(interviewId);
        if (interview == null) throw BusinessException.notFound("面试不存在");
        Long userId = currentUser.id();
        if (!(userId.equals(interview.getInterviewerId()) || currentUser.hasRole("ADMIN"))) throw BusinessException.forbidden("无权查看评测");
        List<Long> ids = interviewQuestionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getInterviewId, interviewId)).stream().map(InterviewQuestion::getId).toList();
        return ids.isEmpty() ? List.of() : evaluationMapper.selectList(new LambdaQueryWrapper<Evaluation>().in(Evaluation::getInterviewQuestionId, ids));
    }
}
