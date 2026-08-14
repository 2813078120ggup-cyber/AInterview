import { AnimatePresence, motion } from "framer-motion";
import {
  AlertTriangle,
  ArrowLeft,
  Bot,
  Camera,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CloudOff,
  Headphones,
  Keyboard,
  Loader2,
  LockKeyhole,
  Mic,
  Play,
  Radio,
  RefreshCw,
  Save,
  Send,
  ShieldCheck,
  Square,
  Video,
  Volume2,
  VolumeX,
} from "lucide-react";
import { useEffect, useEffectEvent, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { request, upload, type Interview } from "@/lib/api";
import {
  browserSpeechRecognitionCtor,
  type BrowserSpeechRecognition,
  type SpeechRecognitionEventLike,
} from "@/lib/browser-speech";
import { INTERVIEW_STATUS, isInterviewFinished } from "@/lib/interview-status";
import {
  closeOpenTalking,
  interruptOpenTalking,
  speakOpenTalking,
  startOpenTalking,
  transcribeOpenTalking,
  type OpenTalkingRuntime,
} from "@/lib/opentalking";

type Question = {
  interviewQuestionId: string;
  content: string;
  options?: string;
  questionType: string;
  maxScore: number;
  correctAnswer?: string;
  answerTemplate?: string;
  explanation?: string;
};
type Answer = {
  interviewQuestionId: string;
  answerContent?: string;
  answerData?: string;
};
type MessageKind = "question" | "follow-up" | "transition";
type Message = {
  role: "assistant" | "candidate";
  content: string;
  kind?: MessageKind;
};
type Task = {
  id?: string;
  status: string;
  outputPayload?: string;
  errorMessage?: string;
};
type Progress = {
  activeQuestionIndex: number;
  remainingSeconds: number;
  followUpCount: number;
  followUpLimit: number;
  activeTaskId?: string;
  activeTaskStatus?: string;
  activeTaskSequence?: number;
  updatedAt?: string;
};
type EndResponse = { interview: Interview; evaluationTaskId?: string };
type SdkConfig = {
  enabled: boolean;
  provider: string;
  status: string;
  message: string;
  sceneId: string;
  avatarId: string;
  vcn: string;
  appId: string;
  protocol: string;
  endpoint: string;
};
type FinishPhase = "confirm" | "submitting" | "evaluating" | "ready" | "failed";
type AvatarRuntime = {
  provider: "opentalking";
  openTalking: OpenTalkingRuntime;
};
type FollowupContext = { questionId: string; token: number };
type SaveStatus = "idle" | "draft" | "saving" | "saved" | "failed";
type AnswerPayload = {
  answerContent: string;
  answerData: string;
  durationSeconds: number;
};
type PendingSave = {
  questionId: string;
  payload: AnswerPayload;
  clearDraft: boolean;
};
type InterviewMode = "TEXT" | "AUDIO" | "VIDEO";
type RecordingSegment = {
  id: string;
  interviewQuestionId: string;
  mediaId: string;
  segmentNo: number;
  startedOffsetMs: number;
  endedOffsetMs: number;
  contentType: string;
  contentPath: string;
};
type TimelineEvent = {
  id: string;
  interviewQuestionId?: string;
  eventType: string;
  offsetMs: number;
  content?: string;
};
type RecordingView = {
  id: string;
  interviewId: string;
  mode: InterviewMode;
  status: string;
  startedAt: string;
  endedAt?: string;
  segments: RecordingSegment[];
  events: TimelineEvent[];
};
type RecordingPart = {
  questionId: string;
  startedOffsetMs: number;
  endedOffsetMs: number;
  blob: Blob;
};
type ActiveQuestionRecorder = {
  recorder: MediaRecorder;
  questionId: string;
  startedOffsetMs: number;
  chunks: BlobPart[];
};

const FOLLOW_UP_LIMIT = 3;
const choiceTypes = ["single_choice", "multiple_choice", "true_false"];
const draftKey = (id: string, questionId: string) =>
  `interviewos_answer_draft_${id}_${questionId}`;
const remainingText = (seconds: number) =>
  String(Math.floor(seconds / 60)).padStart(2, "0") +
  ":" +
  String(seconds % 60).padStart(2, "0");
const safeJson = <T,>(value: string | undefined, fallback: T): T => {
  try {
    return value ? JSON.parse(value) : fallback;
  } catch {
    return fallback;
  }
};

const fallbackFollowUps = [
  "你刚才提到的关键机制，在什么边界条件下可能失效或需要额外处理？",
  "沿着刚才的思路，如果运行环境或输入规模发生变化，你会如何验证结论仍然成立？",
  "结合一个真实实现，你会用什么指标或现象判断这个方案达到了预期效果？",
  "这个方案出现异常时，你会先观察哪一个信号来定位问题？",
];

function normalizedFollowUp(value: string) {
  return value.toLowerCase().replace(/[^\p{Script=Han}a-z0-9]/gu, "");
}

function followUpSimilarity(left: string, right: string) {
  const pairs = (value: string) => {
    const normalized = normalizedFollowUp(value);
    const result = new Set<string>();
    for (let index = 0; index + 1 < normalized.length; index += 1)
      result.add(normalized.slice(index, index + 2));
    return result;
  };
  const leftPairs = pairs(left);
  const rightPairs = pairs(right);
  if (!leftPairs.size || !rightPairs.size)
    return normalizedFollowUp(left) === normalizedFollowUp(right) ? 1 : 0;
  const intersection = [...leftPairs].filter((item) =>
    rightPairs.has(item),
  ).length;
  return intersection / new Set([...leftPairs, ...rightPairs]).size;
}

function followUpRejection(value: string, previous: string[]) {
  const normalized = value.trim();
  if (normalized.length < 8 || normalized.length > 180) return "内容长度不合适";
  if ((normalized.match(/[?？]/g) ?? []).length !== 1)
    return "必须且只能提出一个问题";
  if (
    ["下一题", "下一个问题", "下一道题", "换一道", "进入下一", "过渡到"].some(
      (phrase) => normalized.includes(phrase),
    )
  )
    return "不能切换题目";
  if (previous.some((item) => followUpSimilarity(normalized, item) >= 0.68))
    return "与已有追问过于相似";
  return "";
}

function safeFollowUp(previous: string[]) {
  return (
    fallbackFollowUps.find((item) => !followUpRejection(item, previous)) ??
    fallbackFollowUps[previous.length % fallbackFollowUps.length]
  );
}

function answerKeys(value: string | undefined) {
  const normalized = value?.trim();
  if (!normalized) return [];
  // The question bank accepts both JSON (for example ["A","C"]) and
  // conventional raw keys (for example A or A,C). Keep both formats valid
  // so a correct choice is never misclassified merely because it was stored
  // as plain text.
  const parsed = safeJson<unknown>(normalized, normalized);
  if (Array.isArray(parsed))
    return parsed
      .map((item) => String(item).trim())
      .filter(Boolean)
      .sort();
  if (typeof parsed === "string")
    return parsed
      .split(/[,\s，、]+/)
      .map((item) => item.trim())
      .filter(Boolean)
      .sort();
  return [];
}

function sameKeys(left: string[], right: string[]) {
  return (
    left.length === right.length &&
    left.every((item, index) => item === right[index])
  );
}

function choiceText(
  keys: string[],
  options: Array<{ key: string; text: string }>,
) {
  const optionMap = new Map(options.map((option) => [option.key, option.text]));
  return keys
    .map((key) => `${key}${optionMap.has(key) ? `.${optionMap.get(key)}` : ""}`)
    .join("、");
}

export function InterviewRoom() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const [interview, setInterview] = useState<Interview>();
  const [questions, setQuestions] = useState<Question[]>([]);
  const [answers, setAnswers] = useState<Record<string, Answer>>({});
  const [active, setActive] = useState(0);
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [seconds, setSeconds] = useState(0);
  const [loading, setLoading] = useState(true);
  const [thinking, setThinking] = useState(false);
  const [error, setError] = useState("");
  const [progress, setProgress] = useState<Progress>();
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("idle");
  const [online, setOnline] = useState(() => navigator.onLine);
  const [tts, setTts] = useState(false);
  const [virtualMessage, setVirtualMessage] = useState("待启动");
  const [virtualProvider, setVirtualProvider] = useState<"opentalking" | null>(null);
  const [virtualActive, setVirtualActive] = useState(false);
  const [virtualLoading, setVirtualLoading] = useState(false);
  const [readingQuestion, setReadingQuestion] = useState(false);
  const [cameraOn, setCameraOn] = useState(false);
  const [listening, setListening] = useState(false);
  const [finishDialogOpen, setFinishDialogOpen] = useState(false);
  const [finishPhase, setFinishPhase] = useState<FinishPhase>("confirm");
  const [finishMessage, setFinishMessage] = useState("");
  const [recording, setRecording] = useState<RecordingView | null>();
  const [modeDialogOpen, setModeDialogOpen] = useState(true);
  const [modeStarting, setModeStarting] = useState<InterviewMode | "">("");
  const [recordingBusy, setRecordingBusy] = useState(false);
  const [recordingError, setRecordingError] = useState("");
  const [captureReady, setCaptureReady] = useState(false);
  const video = useRef<HTMLVideoElement>(null);
  const avatarRoot = useRef<HTMLDivElement>(null);
  const avatarRuntime = useRef<AvatarRuntime | null>(null);
  // React state updates are asynchronous. Keep the session lock in refs so a
  // double click, StrictMode remount, or a delayed event cannot open two XRTC
  // sessions before `virtualLoading` has rendered.
  const avatarStartLock = useRef(false);
  const questionReadLock = useRef(false);
  const questionSpeechStarted = useRef(false);
  const questionReadTimeout = useRef<number | undefined>(undefined);
  const sendLock = useRef(false);
  const finishRequestLock = useRef(false);
  const avatarAttempt = useRef(0);
  const stream = useRef<MediaStream | null>(null);
  const lastReadQuestionId = useRef("");
  const currentQuestion = useRef<Question | undefined>(undefined);
  const followupToken = useRef(0);
  const openTalkingRecorder = useRef<MediaRecorder | null>(null);
  const openTalkingAudioStream = useRef<MediaStream | null>(null);
  const openTalkingAudioChunks = useRef<BlobPart[]>([]);
  const browserRecognition = useRef<BrowserSpeechRecognition | null>(null);
  const browserRecognitionBase = useRef("");
  const progressReady = useRef(false);
  const restoredTaskId = useRef("");
  const pendingSave = useRef<PendingSave | null>(null);
  const saveQueue = useRef<Promise<void>>(Promise.resolve());
  const unsavedWork = useRef(false);
  const finishedState = useRef(false);
  const recordedInterviewState = useRef(false);
  const recordingStream = useRef<MediaStream | null>(null);
  const questionRecorder = useRef<ActiveQuestionRecorder | null>(null);
  const recordingQueue = useRef<Promise<void>>(Promise.resolve());
  const pendingRecordingUploads = useRef<RecordingPart[]>([]);
  const timelineQuestionId = useRef("");

  const question = questions[active];
  currentQuestion.current = question;
  const questionId = question?.interviewQuestionId;
  const finished = isInterviewFinished(interview?.status);
  const choiceQuestion = choiceTypes.includes(question?.questionType ?? "");
  const options = useMemo(
    () => safeJson<Array<{ key: string; text: string }>>(question?.options, []),
    [question?.options],
  );
  const questionPrompt = question?.content.trim() ?? "";
  const followUps = messages.filter(
    (item) =>
      item.role === "assistant" &&
      item.kind !== "question" &&
      item.kind !== "transition" &&
      item.content.trim() !== questionPrompt,
  ).length;
  const limit = FOLLOW_UP_LIMIT;
  const recordingMode = recording?.mode;
  const recordedInterview =
    recordingMode === "AUDIO" || recordingMode === "VIDEO";
  finishedState.current = finished;
  recordedInterviewState.current = recordedInterview;
  const virtualProviderName =
    virtualProvider === "opentalking" ? "OpenTalking" : "未连接";

  const activateRecordingQuestion = useEffectEvent((targetQuestionId: string) => {
    const target = currentQuestion.current;
    if (!target || target.interviewQuestionId !== targetQuestionId) return;
    if (timelineQuestionId.current !== targetQuestionId) {
      timelineQuestionId.current = targetQuestionId;
      void addTimelineEvent("QUESTION_STARTED", targetQuestionId, target.content);
    }
    if (recordedInterview && captureReady) queueQuestionRecording(target);
  });

  const finishOnTimeout = useEffectEvent(() =>
    finishWithProgress({ submitCurrentAnswer: true, timeExpired: true }),
  );

  const readCurrentQuestion = useEffectEvent((targetQuestionId: string) => {
    const target = currentQuestion.current;
    if (!target || target.interviewQuestionId !== targetQuestionId) return;
    void readQuestion(target, false);
  });

  const releaseRemoteSession = useEffectEvent(() => {
    disposeAvatarImmediately();
    stopRecordingImmediately();
  });

  const retryPendingWork = useEffectEvent(() => {
    if (pendingSave.current) void retryPendingSave();
    if (pendingRecordingUploads.current.length) void retryRecordingUploads();
  });

  const restoreFollowupTask = useEffectEvent(
    async (taskId: string, taskStatus: string, targetQuestionId: string) => {
      const target = currentQuestion.current;
      if (!target || target.interviewQuestionId !== targetQuestionId) return;
      restoredTaskId.current = taskId;
      const context = beginFollowup(targetQuestionId);
      setThinking(true);
      setVirtualMessage("正在恢复未完成的追问");
      try {
        const task =
          taskStatus === "SUCCESS"
            ? await request<Task>("/v1/ai-tasks/" + taskId)
            : await waitFollowupTask(taskId);
        const followUp = safeJson<{ followUp?: string }>(task.outputPayload, {}).followUp?.trim();
        if (!followUp) throw new Error("恢复的 AI 任务没有有效追问。");
        if (applyFollowup(followUp, context, { releaseThinking: false })) {
          await browserSpeak(followUp);
          setVirtualMessage("追问已恢复");
        }
      } catch (reason) {
        setError(reason instanceof Error ? reason.message : "未完成追问恢复失败。");
      } finally {
        if (isCurrentFollowup(context)) setThinking(false);
      }
    },
  );

  useEffect(() => {
    followupToken.current += 1;
  }, [question?.interviewQuestionId]);

  useEffect(() => {
    let cancelled = false;
    void Promise.all([
      request<Interview>("/v1/interviews/" + id),
      request<Question[]>("/v1/interviews/" + id + "/questions"),
      request<Answer[]>("/v1/interviews/" + id + "/answers"),
      request<Progress>("/v1/interviews/" + id + "/progress"),
      request<RecordingView | null>("/v1/interviews/" + id + "/recording"),
    ])
      .then(([item, questionList, answerList, progressView, recordingView]) => {
        if (cancelled) return;
        setInterview(item);
        setQuestions(questionList);
        setProgress(progressView);
        setRecording(recordingView);
        setModeDialogOpen(!recordingView || recordingView.mode !== "TEXT");
        setSeconds(
          item.status === 1 ? Math.max(0, progressView.remainingSeconds) : 0,
        );
        if (
          progressView.activeQuestionIndex >= 0 &&
          progressView.activeQuestionIndex < questionList.length
        ) {
          setActive(progressView.activeQuestionIndex);
        }
        setAnswers(
          Object.fromEntries(
            answerList.map((answer) => [answer.interviewQuestionId, answer]),
          ),
        );
      })
      .catch((reason) =>
        setError(
          reason instanceof Error
            ? reason.message
            : "面试信息加载失败，请稍后重试。",
        ),
      )
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    if (!question) return;
    const saved = answers[question.interviewQuestionId];
    const stored = safeJson<unknown>(saved?.answerData, null);
    setSelected(
      Array.isArray(stored) && stored.every((item) => typeof item === "string")
        ? stored
        : [],
    );
    setMessages(
      Array.isArray(stored) &&
        stored.every(
          (item) =>
            typeof item === "object" &&
            item &&
            "role" in item &&
            "content" in item,
        )
        ? (stored as Message[])
        : saved?.answerContent
          ? [{ role: "candidate", content: saved.answerContent }]
          : [],
    );
    setDraft(
      localStorage.getItem(draftKey(id, question.interviewQuestionId)) ?? "",
    );
  }, [id, question, answers, choiceQuestion]);

  useEffect(() => {
    if (!recording?.id || modeDialogOpen || !questionId || finished) return;
    activateRecordingQuestion(questionId);
  }, [
    recording?.id,
    modeDialogOpen,
    questionId,
    recordedInterview,
    captureReady,
    finished,
  ]);

  useEffect(() => {
    const element = video.current;
    if (!element) return;
    if (!cameraOn) {
      element.pause();
      element.srcObject = null;
      return;
    }

    const media = [recordingStream.current, stream.current].find((candidate) =>
      candidate
        ?.getVideoTracks()
        .some((track) => track.readyState === "live"),
    );
    if (!media) return;

    element.srcObject = media;
    let cancelled = false;
    let animationFrame = 0;
    const playPreview = () => {
      if (cancelled || element.srcObject !== media) return;
      animationFrame = window.requestAnimationFrame(() => {
        if (cancelled || element.srcObject !== media) return;
        void element.play().catch((reason: unknown) => {
          if (
            !cancelled &&
            (!(reason instanceof DOMException) || reason.name !== "AbortError")
          ) {
            setError("摄像头已授权，但画面预览启动失败，请检查浏览器的自动播放设置。");
          }
        });
      });
    };
    const videoTrack = media.getVideoTracks()[0];
    element.addEventListener("loadedmetadata", playPreview);
    element.addEventListener("canplay", playPreview);
    videoTrack?.addEventListener("unmute", playPreview);
    playPreview();

    return () => {
      cancelled = true;
      if (animationFrame) window.cancelAnimationFrame(animationFrame);
      element.removeEventListener("loadedmetadata", playPreview);
      element.removeEventListener("canplay", playPreview);
      videoTrack?.removeEventListener("unmute", playPreview);
    };
  }, [cameraOn, captureReady, recordingMode, modeDialogOpen]);

  useEffect(() => {
    if (interview?.status !== 1 || seconds <= 0) return;
    const timer = window.setInterval(
      () => setSeconds((value) => Math.max(0, value - 1)),
      1000,
    );
    return () => window.clearInterval(timer);
  }, [interview?.status, seconds]);

  useEffect(() => {
    if (interview?.status !== 1 || seconds !== 0 || finishRequestLock.current)
      return;
    finishRequestLock.current = true;
    setFinishDialogOpen(true);
    void finishOnTimeout();
  }, [interview?.status, seconds]);

  useEffect(() => {
    if (
      loading ||
      !questions.length ||
      interview?.status !== INTERVIEW_STATUS.IN_PROGRESS
    )
      return;
    progressReady.current = true;
    return () => {
      progressReady.current = false;
    };
  }, [id, loading, questions.length, interview?.status]);

  useEffect(() => {
    if (!progressReady.current || finished || !questions.length) return;
    const timer = window.setTimeout(() => {
      void request<Progress>("/v1/interviews/" + id + "/progress", {
        method: "PUT",
        body: JSON.stringify({ activeQuestionIndex: active }),
      })
        .then(setProgress)
        .catch((reason) => {
          setError(
            reason instanceof Error
              ? `面试进度同步失败：${reason.message}`
              : "面试进度同步失败。",
          );
        });
    }, 250);
    return () => window.clearTimeout(timer);
  }, [id, active, questions.length, finished]);

  useEffect(() => {
    if (!progressReady.current || finished) return;
    const synchronize = () => {
      void request<Progress>("/v1/interviews/" + id + "/progress")
        .then((item) => {
          setProgress(item);
          setSeconds(Math.max(0, item.remainingSeconds));
        })
        .catch(() => undefined);
    };
    const timer = window.setInterval(synchronize, 15000);
    const onVisible = () => {
      if (document.visibilityState === "visible") synchronize();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [id, finished]);

  useEffect(() => {
    if (!question || choiceQuestion || finished) return;
    const timer = window.setTimeout(() => {
      localStorage.setItem(draftKey(id, question.interviewQuestionId), draft);
      if (pendingSave.current) return;
      if (draft.trim()) setSaveStatus("draft");
      else if (!pendingSave.current) setSaveStatus("idle");
    }, 600);
    return () => window.clearTimeout(timer);
  }, [id, question, choiceQuestion, finished, draft]);

  useEffect(() => {
    const targetQuestionId = questionId;
    if (
      !recording?.id ||
      !virtualActive ||
      !tts ||
      !targetQuestionId ||
      lastReadQuestionId.current === targetQuestionId
    )
      return;
    readCurrentQuestion(targetQuestionId);
  }, [
    recording?.id,
    virtualActive,
    tts,
    questionId,
    choiceQuestion,
  ]);

  useEffect(() => {
    const savedSelection = answerKeys(
      questionId ? answers[questionId]?.answerData : undefined,
    );
    const choiceDirty =
      choiceQuestion &&
      selected.length > 0 &&
      !sameKeys([...selected].sort(), savedSelection);
    unsavedWork.current =
      Boolean(draft.trim()) ||
      choiceDirty ||
      Boolean(pendingSave.current) ||
      saveStatus === "draft" ||
      saveStatus === "saving" ||
      saveStatus === "failed";
  }, [
    draft,
    selected,
    choiceQuestion,
    questionId,
    answers,
    saveStatus,
  ]);

  useEffect(() => {
    const protectUnsavedAnswer = (event: BeforeUnloadEvent) => {
      releaseRemoteSession();
      if (
        (!unsavedWork.current && !recordedInterviewState.current) ||
        finishedState.current
      )
        return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("pagehide", releaseRemoteSession);
    window.addEventListener("beforeunload", protectUnsavedAnswer);
    return () => {
      stream.current?.getTracks().forEach((track) => track.stop());
      releaseRemoteSession();
      window.removeEventListener("pagehide", releaseRemoteSession);
      window.removeEventListener("beforeunload", protectUnsavedAnswer);
    };
  }, []);

  useEffect(() => {
    const onOnline = () => {
      setOnline(true);
      retryPendingWork();
    };
    const onOffline = () => setOnline(false);
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, []);

  useEffect(() => {
    const taskId = progress?.activeTaskId ? String(progress.activeTaskId) : "";
    const taskSequence = progress?.activeTaskSequence ?? 0;
    const targetQuestionId = questionId;
    if (
      !taskId ||
      !targetQuestionId ||
      choiceQuestion ||
      finished ||
      restoredTaskId.current === taskId
    )
      return;
    if (
      !["PENDING", "RUNNING", "SUCCESS"].includes(
        progress?.activeTaskStatus ?? "",
      )
    )
      return;
    if (taskSequence <= (progress?.followUpCount ?? 0)) return;
    void restoreFollowupTask(
      taskId,
      progress?.activeTaskStatus ?? "",
      targetQuestionId,
    );
  }, [
    progress?.activeTaskId,
    progress?.activeTaskStatus,
    progress?.activeTaskSequence,
    progress?.followUpCount,
    questionId,
    choiceQuestion,
    finished,
  ]);

  function recordingOffset() {
    if (!recording?.startedAt) return 0;
    return Math.max(0, Date.now() - new Date(recording.startedAt).getTime());
  }

  async function addTimelineEvent(
    eventType: string,
    questionId?: string,
    content?: string,
  ) {
    if (!recording) return;
    try {
      const event = await request<TimelineEvent>(
        "/v1/interviews/" + id + "/recording/events",
        {
          method: "POST",
          body: JSON.stringify({
            interviewQuestionId: questionId,
            eventType,
            offsetMs: recordingOffset(),
            content,
          }),
        },
      );
      setRecording((previous) =>
        previous
          ? { ...previous, events: [...previous.events, event] }
          : previous,
      );
    } catch (reason) {
      setRecordingError(
        reason instanceof Error
          ? `时间轴保存失败：${reason.message}`
          : "时间轴保存失败。",
      );
    }
  }

  async function prepareRecordingStream(mode: InterviewMode) {
    if (mode === "TEXT") return null;
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
      throw new Error("语音和视频面试只能在 HTTPS 或 localhost 中使用。");
    }
    const media =
      mode === "VIDEO"
        ? await navigator.mediaDevices.getUserMedia({
            audio: true,
            video: {
              width: { ideal: 1280 },
              height: { ideal: 720 },
              frameRate: { ideal: 15, max: 15 },
            },
          })
        : await navigator.mediaDevices.getUserMedia({
            audio: true,
            video: false,
          });
    recordingStream.current?.getTracks().forEach((track) => track.stop());
    recordingStream.current = media;
    if (mode === "VIDEO" && video.current) {
      video.current.srcObject = media;
      setCameraOn(true);
    }
    setCaptureReady(true);
    return media;
  }

  async function selectInterviewMode(mode: InterviewMode) {
    if (modeStarting) return;
    setModeStarting(mode);
    setRecordingError("");
    let prepared: MediaStream | null = null;
    try {
      prepared = await prepareRecordingStream(mode);
      const selected = await request<RecordingView>(
        "/v1/interviews/" + id + "/recording/select",
        {
          method: "POST",
          body: JSON.stringify({ mode }),
        },
      );
      setRecording(selected);
      setModeDialogOpen(false);
      setTts(false);
      setVirtualMessage(
        mode === "TEXT"
          ? "文字面试已就绪"
          : mode === "AUDIO"
            ? "语音录制已启动"
            : "720p 视频录制已启动",
      );
    } catch (reason) {
      prepared?.getTracks().forEach((track) => track.stop());
      if (prepared === recordingStream.current) recordingStream.current = null;
      setCaptureReady(false);
      setCameraOn(false);
      setRecordingError(
        reason instanceof Error ? reason.message : "面试方式启动失败。",
      );
    } finally {
      setModeStarting("");
    }
  }

  function preferredRecordingMime(mode: InterviewMode) {
    const candidates =
      mode === "VIDEO"
        ? [
            "video/webm;codecs=vp8,opus",
            "video/webm;codecs=vp9,opus",
            "video/webm",
          ]
        : ["audio/webm;codecs=opus", "audio/webm"];
    return candidates.find((item) => MediaRecorder.isTypeSupported(item)) ?? "";
  }

  function startQuestionRecording(target: Question) {
    const media = recordingStream.current;
    if (
      !recording ||
      !recordedInterview ||
      !media ||
      questionRecorder.current ||
      finished
    )
      return;
    if (!("MediaRecorder" in window)) {
      setRecordingError(
        "当前浏览器不支持 MediaRecorder，请使用新版 Chrome 或 Edge。",
      );
      return;
    }
    const mimeType = preferredRecordingMime(recording.mode);
    if (!mimeType) {
      setRecordingError("当前浏览器不支持 WebM 音视频录制。");
      return;
    }
    try {
      const recorder = new MediaRecorder(
        media,
        recording.mode === "VIDEO"
          ? {
              mimeType,
              videoBitsPerSecond: 850_000,
              audioBitsPerSecond: 64_000,
            }
          : { mimeType, audioBitsPerSecond: 64_000 },
      );
      const active: ActiveQuestionRecorder = {
        recorder,
        questionId: target.interviewQuestionId,
        startedOffsetMs: recordingOffset(),
        chunks: [],
      };
      recorder.ondataavailable = (event) => {
        if (event.data.size) active.chunks.push(event.data);
      };
      recorder.onerror = () =>
        setRecordingError("当前题目录制异常，请检查设备权限。");
      questionRecorder.current = active;
      recorder.start(1000);
      void addTimelineEvent("RECORDING_STARTED", target.interviewQuestionId);
    } catch (reason) {
      setRecordingError(
        reason instanceof Error ? reason.message : "当前题目录制启动失败。",
      );
    }
  }

  async function uploadRecordingPart(part: RecordingPart) {
    const form = new FormData();
    const extension = recordingMode === "VIDEO" ? "video.webm" : "audio.webm";
    form.append(
      "file",
      part.blob,
      `question-${part.questionId}-${Date.now()}-${extension}`,
    );
    form.append("interviewQuestionId", part.questionId);
    form.append("startedOffsetMs", String(part.startedOffsetMs));
    form.append("endedOffsetMs", String(part.endedOffsetMs));
    const segment = await upload<RecordingSegment>(
      "/v1/interviews/" + id + "/recording/segments",
      form,
    );
    setRecording((previous) =>
      previous
        ? { ...previous, segments: [...previous.segments, segment] }
        : previous,
    );
  }

  async function stopQuestionRecording() {
    const active = questionRecorder.current;
    if (!active) return;
    questionRecorder.current = null;
    setRecordingBusy(true);
    await new Promise<void>((resolve) => {
      active.recorder.onstop = () => {
        void (async () => {
          const endedOffsetMs = recordingOffset();
          const type =
            active.recorder.mimeType ||
            (recordingMode === "VIDEO" ? "video/webm" : "audio/webm");
          const part: RecordingPart = {
            questionId: active.questionId,
            startedOffsetMs: active.startedOffsetMs,
            endedOffsetMs,
            blob: new Blob(active.chunks, { type }),
          };
          if (part.blob.size) {
            try {
              await uploadRecordingPart(part);
              await addTimelineEvent("RECORDING_STOPPED", active.questionId);
              setRecordingError("");
            } catch (reason) {
              pendingRecordingUploads.current.push(part);
              setRecordingError(
                reason instanceof Error
                  ? `录制上传失败：${reason.message}`
                  : "录制上传失败。",
              );
            }
          }
          resolve();
        })();
      };
      if (active.recorder.state === "inactive") {
        active.recorder.onstop?.(new Event("stop"));
      } else {
        active.recorder.stop();
      }
    });
    setRecordingBusy(false);
  }

  function queueQuestionRecording(target: Question) {
    if (questionRecorder.current?.questionId === target.interviewQuestionId)
      return;
    recordingQueue.current = recordingQueue.current
      .catch(() => undefined)
      .then(async () => {
        await stopQuestionRecording();
        if (
          currentQuestion.current?.interviewQuestionId ===
          target.interviewQuestionId
        )
          startQuestionRecording(target);
      });
  }

  async function retryRecordingUploads() {
    if (!pendingRecordingUploads.current.length || !navigator.onLine) return;
    const pending = [...pendingRecordingUploads.current];
    pendingRecordingUploads.current = [];
    setRecordingBusy(true);
    for (const part of pending) {
      try {
        await uploadRecordingPart(part);
      } catch (reason) {
        pendingRecordingUploads.current.push(part);
        setRecordingError(
          reason instanceof Error
            ? `录制重试失败：${reason.message}`
            : "录制重试失败。",
        );
      }
    }
    if (!pendingRecordingUploads.current.length) setRecordingError("");
    setRecordingBusy(false);
  }

  function stopRecordingImmediately() {
    const active = questionRecorder.current;
    questionRecorder.current = null;
    if (active && active.recorder.state !== "inactive") {
      active.recorder.ondataavailable = null;
      active.recorder.onstop = null;
      try {
        active.recorder.stop();
      } catch {
        /* page teardown */
      }
    }
    recordingStream.current?.getTracks().forEach((track) => track.stop());
    recordingStream.current = null;
  }

  function invalidateAvatarAttempt() {
    avatarAttempt.current += 1;
    avatarStartLock.current = false;
  }

  function detachAvatarRuntime() {
    const runtime = avatarRuntime.current;
    avatarRuntime.current = null;
    lastReadQuestionId.current = "";
    releaseQuestionReadLock();
    stopOpenTalkingRecording(false);
    stopBrowserRecognition();
    window.speechSynthesis?.cancel();
    return runtime;
  }

  function releaseQuestionReadLock() {
    if (questionReadTimeout.current)
      window.clearTimeout(questionReadTimeout.current);
    questionReadTimeout.current = undefined;
    questionReadLock.current = false;
    questionSpeechStarted.current = false;
    setReadingQuestion(false);
  }

  function stopOpenTalkingRecording(upload: boolean) {
    const recorder = openTalkingRecorder.current;
    if (recorder && recorder.state !== "inactive") {
      recorder.onstop = upload ? recorder.onstop : null;
      recorder.stop();
      if (upload) return;
    }
    openTalkingRecorder.current = null;
    openTalkingAudioStream.current
      ?.getTracks()
      .forEach((track) => track.stop());
    openTalkingAudioStream.current = null;
    if (!upload) openTalkingAudioChunks.current = [];
  }

  function stopBrowserRecognition() {
    const recognition = browserRecognition.current;
    if (!recognition) return;
    recognition.onresult = null;
    recognition.onerror = null;
    recognition.onend = null;
    try {
      recognition.stop();
    } catch {
      /* already stopped */
    }
    browserRecognition.current = null;
  }

  async function browserSpeak(text: string, force = false) {
    if ((!tts && !force) || !text.trim() || !("speechSynthesis" in window))
      return;
    window.speechSynthesis.cancel();
    await new Promise<void>((resolve) => {
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = "zh-CN";
      utterance.rate = 0.95;
      utterance.pitch = 1;
      utterance.onend = () => resolve();
      utterance.onerror = () => resolve();
      window.speechSynthesis.speak(utterance);
      window.setTimeout(
        resolve,
        Math.min(9000, Math.max(1800, text.length * 120)),
      );
    });
  }

  // `beforeunload`/`pagehide` cannot wait for an async recorder shutdown.
  // Release the active OpenTalking session synchronously when the candidate
  // refreshes or navigates away.
  function disposeAvatarImmediately() {
    invalidateAvatarAttempt();
    const runtime = detachAvatarRuntime();
    if (!runtime) return;
    closeOpenTalking(runtime.openTalking);
  }

  async function disposeAvatar(updateState = true, invalidateAttempt = true) {
    if (invalidateAttempt) invalidateAvatarAttempt();
    const runtime = detachAvatarRuntime();
    if (runtime) closeOpenTalking(runtime.openTalking);
    avatarRoot.current?.removeAttribute("style");
    if (updateState) {
      setVirtualActive(false);
      setListening(false);
      setThinking(false);
      setVirtualProvider(null);
      setVirtualMessage("已停止");
    }
  }

  async function readQuestion(target: Question, force = true) {
    const runtime = avatarRuntime.current;
    if ((!tts && !force) || questionReadLock.current) return;
    questionReadLock.current = true;
    questionSpeechStarted.current = false;
    setReadingQuestion(true);
    try {
      if (runtime?.provider === "opentalking" && runtime.openTalking) {
        await speakOpenTalking(runtime.openTalking, target.content, {
          readOnly: true,
        });
        appendAssistantMessage(target.content, {
          persist: false,
          allowChoice: true,
          kind: "question",
        });
        setVirtualMessage("朗读中");
        // `/speak` only confirms that the job entered OpenTalking's queue.
        // The event callbacks below unlock after the queued speech actually ends.
        questionReadTimeout.current = window.setTimeout(() => {
          releaseQuestionReadLock();
          setVirtualMessage("已就绪");
        }, 45000);
      } else {
        appendAssistantMessage(target.content, {
          persist: false,
          allowChoice: true,
          kind: "question",
        });
        setVirtualMessage("浏览器朗读中");
        await browserSpeak(target.content, force);
        setVirtualMessage("浏览器语音就绪");
        releaseQuestionReadLock();
      }
      lastReadQuestionId.current = target.interviewQuestionId;
    } catch (reason) {
      releaseQuestionReadLock();
      setError(
        reason instanceof Error
          ? reason.message
          : "虚拟人播报失败，请重新连接后重试。",
      );
    }
  }

  async function startOpenTalkingAvatar(
    config: SdkConfig,
    isCurrentAttempt: () => boolean,
  ) {
    if (!config.endpoint || !config.sceneId || !config.avatarId)
      throw new Error(config.message || "OpenTalking 尚未完成配置。");
    if (!avatarRoot.current) throw new Error("虚拟人画布尚未准备完成。");
    setVirtualMessage("连接中…");
    await disposeAvatar(false, false);
    if (!isCurrentAttempt() || !avatarRoot.current) return;
    avatarRoot.current.replaceChildren();
    avatarRoot.current.style.display = "block";
    const avatarVideo = document.createElement("video");
    avatarVideo.autoplay = true;
    avatarVideo.playsInline = true;
    avatarVideo.muted = false;
    avatarRoot.current.append(avatarVideo);
    const openTalking = await startOpenTalking(
      {
        endpoint: config.endpoint,
        model: config.sceneId,
        avatarId: config.avatarId,
        ttsProvider: config.appId || "edge",
        ttsVoice: config.vcn,
        sttProvider: "dashscope",
      },
      avatarVideo,
      {
        onStatus: (status) => {
          if (status === "thinking" || status === "processing")
            setThinking(true);
          if (
            status === "idle" ||
            status === "ready" ||
            status === "speech.ended"
          )
            setThinking(false);
        },
        onSpeechStarted: () => {
          if (questionReadLock.current) questionSpeechStarted.current = true;
        },
        onSpeechEnded: () => {
          if (questionReadLock.current && questionSpeechStarted.current) {
            releaseQuestionReadLock();
            setVirtualMessage("已就绪");
          }
        },
        onError: (message) => {
          releaseQuestionReadLock();
          setThinking(false);
          setVirtualMessage("异常：" + message);
        },
      },
    );
    if (!isCurrentAttempt()) {
      closeOpenTalking(openTalking);
      return;
    }
    avatarRuntime.current = { provider: "opentalking", openTalking };
    setVirtualProvider("opentalking");
    setVirtualActive(true);
    window.requestAnimationFrame(() =>
      avatarRoot.current?.removeAttribute("style"),
    );
    setVirtualMessage("已就绪");
  }

  async function startAvatar() {
    if (
      !recording ||
      avatarStartLock.current ||
      avatarRuntime.current ||
      finished
    )
      return;
    setTts(true);
    avatarStartLock.current = true;
    const startAttempt = ++avatarAttempt.current;
    const isCurrentAttempt = () => avatarAttempt.current === startAttempt;
    setVirtualLoading(true);
    setError("");
    try {
      const config = await request<SdkConfig>("/v1/virtual-human/sdk-config");
      if (!config.enabled)
        throw new Error(config.message || "OpenTalking 尚未完成配置。");
      if (config.protocol !== "opentalking")
        throw new Error("当前仅保留 OpenTalking 虚拟人服务。");
      await startOpenTalkingAvatar(config, isCurrentAttempt);
    } catch (reason) {
      if (isCurrentAttempt()) {
        await disposeAvatar(false, false);
        setVirtualActive(false);
        setVirtualMessage(
          reason instanceof Error ? reason.message : "OpenTalking 连接失败。",
        );
      }
    } finally {
      if (isCurrentAttempt()) {
        avatarStartLock.current = false;
        setVirtualLoading(false);
      }
    }
  }

  async function toggleVoiceAnswer() {
    const runtime = avatarRuntime.current;
    if (!runtime || !virtualActive) {
      const SpeechRecognition = browserSpeechRecognitionCtor();
      if (!SpeechRecognition) {
        setError(
          "当前浏览器不支持内置语音识别，请使用新版 Chrome 或 Edge，或启动 OpenTalking。",
        );
        return;
      }
      if (!window.isSecureContext) {
        setError("语音回答只能在 HTTPS 或 localhost 环境使用。");
        return;
      }
      if (listening) {
        stopBrowserRecognition();
        setListening(false);
        setVirtualMessage("浏览器语音就绪");
        return;
      }
      const recognition = new SpeechRecognition();
      browserRecognition.current = recognition;
      browserRecognitionBase.current = draft.trim();
      recognition.lang = "zh-CN";
      recognition.continuous = false;
      recognition.interimResults = true;
      let finalText = "";
      recognition.onresult = (event: SpeechRecognitionEventLike) => {
        let interim = "";
        for (
          let index = event.resultIndex;
          index < event.results.length;
          index += 1
        ) {
          const transcript = String(
            event.results[index][0]?.transcript ?? "",
          ).trim();
          if (event.results[index].isFinal) finalText += transcript;
          else interim += transcript;
        }
        const text = (finalText || interim).trim();
        if (text)
          setDraft(
            [browserRecognitionBase.current, text].filter(Boolean).join("\n"),
          );
      };
      recognition.onerror = () => {
        setListening(false);
        browserRecognition.current = null;
        setError("浏览器语音识别失败，请检查麦克风权限。");
      };
      recognition.onend = () => {
        setListening(false);
        browserRecognition.current = null;
        setVirtualMessage("浏览器语音就绪");
      };
      try {
        recognition.start();
        setListening(true);
        setError("");
        setVirtualMessage("浏览器录音中");
      } catch {
        setListening(false);
        browserRecognition.current = null;
        setError("浏览器语音识别启动失败，请刷新后重试。");
      }
      return;
    }
    if (runtime.provider === "opentalking" && runtime.openTalking) {
      if (!window.isSecureContext) {
        setError(
          "语音回答只能在 HTTPS 或 localhost 环境使用，请先配置 HTTPS。",
        );
        return;
      }
      if (
        !navigator.mediaDevices?.getUserMedia ||
        typeof MediaRecorder === "undefined"
      ) {
        setError("当前浏览器不支持录音上传，请使用最新版 Chrome 或 Edge。");
        return;
      }
      if (listening) {
        setVirtualMessage("识别中…");
        stopOpenTalkingRecording(true);
        return;
      }
      try {
        openTalkingAudioChunks.current = [];
        const audioStream = await navigator.mediaDevices.getUserMedia({
          audio: true,
          video: false,
        });
        openTalkingAudioStream.current = audioStream;
        const recorderOptions = MediaRecorder.isTypeSupported("audio/webm")
          ? { mimeType: "audio/webm" }
          : undefined;
        const recorder = new MediaRecorder(audioStream, recorderOptions);
        openTalkingRecorder.current = recorder;
        recorder.ondataavailable = (event) => {
          if (event.data.size > 0)
            openTalkingAudioChunks.current.push(event.data);
        };
        recorder.onstop = () => {
          const chunks = openTalkingAudioChunks.current;
          openTalkingAudioChunks.current = [];
          openTalkingAudioStream.current
            ?.getTracks()
            .forEach((track) => track.stop());
          openTalkingAudioStream.current = null;
          openTalkingRecorder.current = null;
          setListening(false);
          if (!chunks.length) return;
          void (async () => {
            try {
              const audio = new Blob(chunks, { type: "audio/webm" });
              const text = await transcribeOpenTalking(
                runtime.openTalking!,
                audio,
              );
              if (!text) {
                setError("OpenTalking 未识别到有效语音，请靠近麦克风后重试。");
                return;
              }
              setDraft((previous) =>
                (previous + (previous ? "\n" : "") + text).trim(),
              );
              setVirtualMessage("已转写");
            } catch (reason) {
              setError(
                reason instanceof Error
                  ? reason.message
                  : "OpenTalking 语音识别失败，请检查 STT Key 和麦克风权限。",
              );
              setVirtualMessage("识别失败");
            }
          })();
        };
        recorder.start();
        setListening(true);
        setError("");
        setVirtualMessage("录音中");
      } catch (reason) {
        setListening(false);
        stopOpenTalkingRecording(false);
        setError(
          reason instanceof Error
            ? reason.message
            : "OpenTalking 录音启动失败，请检查麦克风权限。",
        );
      }
      return;
    }
    if (runtime.provider !== "opentalking" || !runtime.openTalking) {
      setError("当前 OpenTalking 运行时不可用，已切换为浏览器语音识别。");
      return;
    }
    if (!window.isSecureContext) {
      setError("语音回答只能在 HTTPS 或 localhost 环境使用，请先配置 HTTPS。");
      return;
    }
    setError("当前 OpenTalking 运行时不可用，请重新连接后重试。");
  }

  async function persistAnswerNow(item: PendingSave) {
    pendingSave.current = item;
    let lastError: unknown;
    for (let attempt = 0; attempt < 3; attempt += 1) {
      if (!navigator.onLine) {
        setOnline(false);
        lastError = new Error("当前网络已断开，回答已保存在本机。");
        break;
      }
      setSaveStatus("saving");
      try {
        await request(
          "/v1/interviews/" + id + "/questions/" + item.questionId + "/answer",
          {
            method: "PUT",
            body: JSON.stringify(item.payload),
          },
        );
        pendingSave.current = null;
        localStorage.removeItem(draftKey(id, item.questionId));
        setAnswers((previous) => ({
          ...previous,
          [item.questionId]: {
            interviewQuestionId: item.questionId,
            answerContent: item.payload.answerContent,
            answerData: item.payload.answerData,
          },
        }));
        if (
          item.clearDraft &&
          currentQuestion.current?.interviewQuestionId === item.questionId
        )
          setDraft("");
        setSaveStatus("saved");
        return;
      } catch (reason) {
        lastError = reason;
        if (attempt < 2)
          await new Promise((resolve) =>
            window.setTimeout(resolve, attempt === 0 ? 500 : 1200),
          );
      }
    }
    setSaveStatus("failed");
    throw lastError instanceof Error
      ? lastError
      : new Error("答案保存失败，请重试。");
  }

  function persistAnswer(item: PendingSave) {
    const operation = saveQueue.current
      .catch(() => undefined)
      .then(() => persistAnswerNow(item));
    saveQueue.current = operation;
    return operation;
  }

  async function retryPendingSave() {
    const item = pendingSave.current;
    if (!item) return;
    try {
      await persistAnswer(item);
      setError("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "答案重试保存失败。");
    }
  }

  async function save(next: Message[], options: { clearDraft?: boolean } = {}) {
    if (!question || !interview) return;
    const answerContent = next
      .filter((item) => item.role === "candidate")
      .map((item) => item.content)
      .join("\n");
    const answerData = JSON.stringify(next);
    await persistAnswer({
      questionId: question.interviewQuestionId,
      payload: {
        answerContent,
        answerData,
        durationSeconds: Math.max(0, interview.duration * 60 - seconds),
      },
      clearDraft: options.clearDraft ?? false,
    });
  }

  async function submitCurrentAnswerOnTimeout() {
    if (!question || !interview) return;
    const content = choiceQuestion ? selected.join(", ") : draft.trim();
    if (!content) return;
    const nextMessages = choiceQuestion
      ? []
      : [...messages, { role: "candidate" as const, content }];
    const answerContent = choiceQuestion
      ? content
      : nextMessages
          .filter((item) => item.role === "candidate")
          .map((item) => item.content)
          .join("\n");
    const answerData = choiceQuestion
      ? JSON.stringify(selected)
      : JSON.stringify(nextMessages);
    await persistAnswer({
      questionId: question.interviewQuestionId,
      payload: {
        answerContent,
        answerData,
        durationSeconds: interview.duration * 60,
      },
      clearDraft: true,
    });
  }

  function appendAssistantMessage(
    text: string,
    options: {
      persist?: boolean;
      allowChoice?: boolean;
      kind?: MessageKind;
    } = {},
  ) {
    const normalized = text.trim();
    if (
      !normalized ||
      !question ||
      (!options.allowChoice && choiceQuestion) ||
      finished
    )
      return;
    if (
      messages.some(
        (item) =>
          item.role === "assistant" && item.content.trim() === normalized,
      )
    )
      return;
    if (options.kind === "follow-up")
      void addTimelineEvent(
        "FOLLOW_UP",
        question.interviewQuestionId,
        normalized,
      );
    if (options.kind === "transition")
      void addTimelineEvent(
        "TRANSITION",
        question.interviewQuestionId,
        normalized,
      );
    setMessages((previous) => {
      if (
        previous.some(
          (item) =>
            item.role === "assistant" && item.content.trim() === normalized,
        )
      )
        return previous;
      const next = [
        ...previous,
        { role: "assistant" as const, content: normalized, kind: options.kind },
      ];
      if (options.persist !== false)
        void save(next).catch((reason) => {
          setError(
            reason instanceof Error ? reason.message : "对话保存失败，请重试。",
          );
        });
      return next;
    });
  }

  function isCurrentFollowup(context: FollowupContext) {
    return (
      !finished &&
      currentQuestion.current?.interviewQuestionId === context.questionId &&
      followupToken.current === context.token
    );
  }

  function beginFollowup(questionId: string): FollowupContext {
    return { questionId, token: ++followupToken.current };
  }

  function invalidatePendingFollowup() {
    followupToken.current += 1;
  }

  function previousFollowUpTexts() {
    return messages
      .filter(
        (item) =>
          item.role === "assistant" &&
          item.kind !== "question" &&
          item.kind !== "transition" &&
          item.content.trim() !== questionPrompt,
      )
      .map((item) => item.content.trim());
  }

  function applyFollowup(
    text: string,
    context: FollowupContext,
    options: { releaseThinking?: boolean } = {},
  ) {
    const normalized = text.trim();
    if (
      !normalized ||
      !isCurrentFollowup(context) ||
      !question ||
      choiceQuestion
    )
      return null;
    const previous = previousFollowUpTexts();
    const protectedText = followUpRejection(normalized, previous)
      ? safeFollowUp(previous)
      : normalized;
    appendAssistantMessage(protectedText, { kind: "follow-up" });
    if (options.releaseThinking !== false) setThinking(false);
    setVirtualMessage("已追问");
    return protectedText;
  }

  async function waitFollowupTask(taskId: string) {
    for (let attempt = 0; attempt < 60; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000));
      const task = await request<Task>("/v1/ai-tasks/" + taskId);
      if (task.status === "SUCCESS") return task;
      if (task.status === "FAILED")
        throw new Error(task.errorMessage || "AI 追问生成失败。");
    }
    throw new Error("AI 追问仍在生成中，请稍后继续。");
  }

  async function requestDeepSeekFollowup(
    answer: string,
    context: FollowupContext,
    openTalking?: OpenTalkingRuntime,
  ) {
    if (!question || !isCurrentFollowup(context)) return;
    const task = await request<Task>("/v1/interviews/" + id + "/follow-ups", {
      method: "POST",
      body: JSON.stringify({
        interviewQuestionId: question.interviewQuestionId,
        answer,
        question: question.content,
      }),
    });
    if (!task.id) throw new Error("AI 追问任务创建失败。");
    const completed = await waitFollowupTask(task.id);
    const followUp = safeJson<{ followUp?: string }>(
      completed.outputPayload,
      {},
    ).followUp?.trim();
    if (!followUp) throw new Error("AI 未返回有效追问。");
    const displayedFollowUp = applyFollowup(followUp, context, {
      releaseThinking: false,
    });
    if (!displayedFollowUp) return;
    if (openTalking) {
      await speakOpenTalking(openTalking, displayedFollowUp, {
        readOnly: true,
      });
    } else {
      await browserSpeak(displayedFollowUp);
    }
    if (isCurrentFollowup(context)) {
      setThinking(false);
      setVirtualMessage(openTalking ? "OpenTalking 已追问" : "DeepSeek 已追问");
    }
  }

  async function speakBriefly(text: string) {
    const runtime = avatarRuntime.current;
    if (runtime?.provider === "opentalking" && runtime.openTalking) {
      await speakOpenTalking(runtime.openTalking, text, { readOnly: true });
    } else {
      await browserSpeak(text);
    }
    await new Promise((resolve) =>
      window.setTimeout(
        resolve,
        Math.min(6500, Math.max(2200, text.length * 90)),
      ),
    );
  }

  function buildChoiceFeedback(selectedKeys: string[]) {
    const correctKeys = answerKeys(question?.correctAnswer);
    const correctText = choiceText(correctKeys, options);
    const correct =
      correctKeys.length > 0 &&
      sameKeys(selectedKeys.slice().sort(), correctKeys);
    const explanation = (
      question?.explanation ||
      question?.answerTemplate ||
      ""
    ).trim();
    if (correct) return "回答正确，让我们来继续下一道题。";
    return `回答错误。正确答案是 ${correctText || "题库暂未配置"}。${explanation ? `解析：${explanation}。` : ""}让我们来继续下一道题。`;
  }

  function buildQuestionClosing(answer: string, hasNext: boolean) {
    const length = answer.replaceAll(/\s+/g, "").length;
    if (!hasNext)
      return length >= 60
        ? "这个回答的关键点已经记录，本轮题目就到这里。"
        : "你的回答我已经记录，本轮题目就到这里。";
    const remarks =
      length >= 120
        ? [
            "这个回答把关键点讲清楚了，这道题先收在这里，我们继续下一题。",
            "这部分的思路比较完整，先切换到下一题。",
          ]
        : length >= 40
          ? [
              "我先记下你刚才的思路，这道题到这里，我们继续下一题。",
              "这个点我已经了解了，接着看下一题。",
            ]
          : [
              "你的回答我先记录下来，这道题先到这里，我们继续下一题。",
              "这部分先不展开了，接着进入下一题。",
            ];
    return remarks[(followUps + length) % remarks.length];
  }

  async function closeCurrentQuestion(
    answer: string,
    context: FollowupContext,
  ) {
    if (!question || !isCurrentFollowup(context)) return;
    const hasNext = active < questions.length - 1;
    const closing = buildQuestionClosing(answer, hasNext);
    appendAssistantMessage(closing, { kind: "transition" });
    setVirtualMessage(hasNext ? "正在进入下一题" : "本轮题目已完成");
    await speakBriefly(closing);
    if (!isCurrentFollowup(context)) return;
    setThinking(false);
    if (hasNext) {
      await addTimelineEvent(
        "QUESTION_COMPLETED",
        question.interviewQuestionId,
      );
      invalidatePendingFollowup();
      setActive((index) => (index === active ? index + 1 : index));
    }
  }

  async function send() {
    if (!recording) {
      setModeDialogOpen(true);
      return;
    }
    if (!question || finished || thinking || recordingBusy) return;
    if (sendLock.current) return;
    sendLock.current = true;
    const content = choiceQuestion ? selected.join(", ") : draft.trim();
    if (!content) sendLock.current = false;
    if (!content) {
      setError("请先完成本题作答。");
      return;
    }
    setError("");
    releaseQuestionReadLock();
    window.speechSynthesis?.cancel();
    const activeSpeechRuntime = avatarRuntime.current;
    if (
      activeSpeechRuntime?.provider === "opentalking" &&
      activeSpeechRuntime.openTalking
    ) {
      try {
        await interruptOpenTalking(activeSpeechRuntime.openTalking);
      } catch {
        // The next exact-text speak request retries the interrupt before playback.
      }
    }
    if (choiceQuestion) {
      setThinking(true);
      try {
        await persistAnswer({
          questionId: question.interviewQuestionId,
          payload: {
            answerContent: content,
            answerData: JSON.stringify(selected),
            durationSeconds: Math.max(
              0,
              (interview?.duration ?? 0) * 60 - seconds,
            ),
          },
          clearDraft: true,
        });
        await addTimelineEvent(
          "ANSWER_SUBMITTED",
          question.interviewQuestionId,
          content,
        );
        const candidateMessage = {
          role: "candidate" as const,
          content: `我选择：${choiceText(selected, options) || content}`,
        };
        const feedback = buildChoiceFeedback(selected);
        // Choice questions are deterministic: do not hand the answer to an LLM
        // or OpenTalking's agent. Render exactly the sentence that will be
        // spoken by the avatar, so the dialogue and media output stay in sync.
        setMessages((previous) => [
          ...previous,
          candidateMessage,
          { role: "assistant" as const, content: feedback },
        ]);
        setVirtualMessage("讲评中");
        await speakBriefly(feedback);
        if (active < questions.length - 1) {
          await addTimelineEvent(
            "QUESTION_COMPLETED",
            question.interviewQuestionId,
          );
          setActive(active + 1);
        }
      } catch (reason) {
        setError(reason instanceof Error ? reason.message : "答案保存失败。");
      } finally {
        setThinking(false);
        sendLock.current = false;
      }
      return;
    }

    const candidateMessages = [
      ...messages,
      { role: "candidate" as const, content },
    ];
    setMessages(candidateMessages);
    setThinking(true);
    const followupContext = beginFollowup(question.interviewQuestionId);
    try {
      await save(candidateMessages, { clearDraft: true });
      await addTimelineEvent(
        "ANSWER_SUBMITTED",
        question.interviewQuestionId,
        content,
      );
      if (followUps >= limit) {
        await closeCurrentQuestion(content, followupContext);
        return;
      }
      const runtime = avatarRuntime.current;
      if (!runtime || !virtualActive) {
        setVirtualMessage("DeepSeek 追问中");
        await requestDeepSeekFollowup(content, followupContext);
        return;
      }
      if (runtime.provider === "opentalking" && runtime.openTalking) {
        setVirtualMessage("追问生成中");
        await requestDeepSeekFollowup(
          content,
          followupContext,
          runtime.openTalking,
        );
        return;
      }
      setVirtualMessage("DeepSeek 追问中");
      await requestDeepSeekFollowup(content, followupContext);
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "答案保存或追问处理失败。",
      );
      setThinking(false);
    } finally {
      sendLock.current = false;
    }
  }

  async function waitEvaluationTask(taskId: string) {
    for (let attempt = 0; attempt < 120; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1500));
      const task = await request<Task>("/v1/ai-tasks/" + taskId);
      if (task.status === "SUCCESS") return task;
      if (task.status === "FAILED")
        throw new Error(
          task.errorMessage ?? "AI 报告生成失败，请稍后在报告页重试。",
        );
      setFinishMessage(
        task.status === "RUNNING"
          ? "AI 正在评估答案并生成报告…"
          : "评测任务已提交，等待 AI 处理…",
      );
    }
    throw new Error("报告生成等待超时，系统会继续在后台处理。");
  }

  async function finishWithProgress(
    options: { submitCurrentAnswer?: boolean; timeExpired?: boolean } = {},
  ) {
    invalidatePendingFollowup();
    setThinking(true);
    setFinishPhase("submitting");
    setFinishMessage(
      options.timeExpired
        ? "时间已到，正在提交当前答案并结束面试…"
        : "正在锁定本次答题记录…",
    );
    try {
      if (options.submitCurrentAnswer) {
        try {
          await submitCurrentAnswerOnTimeout();
        } catch (reason) {
          if (!options.timeExpired) throw reason;
          setError(
            reason instanceof Error
              ? `当前答案未能保存：${reason.message}，时间已到，仍将结束面试。`
              : "当前答案未能保存，时间已到，仍将结束面试。",
          );
        }
      }
      if (recordedInterview) {
        if (question)
          await addTimelineEvent(
            "QUESTION_COMPLETED",
            question.interviewQuestionId,
          );
        await stopQuestionRecording();
        await retryRecordingUploads();
        if (pendingRecordingUploads.current.length && !options.timeExpired) {
          throw new Error("仍有录制分段未上传，请恢复网络并重试后再结束面试。");
        }
      }
      const result = await request<EndResponse>(
        "/v1/interviews/" + id + "/end",
        { method: "POST" },
      );
      await disposeAvatar();
      recordingStream.current?.getTracks().forEach((track) => track.stop());
      recordingStream.current = null;
      setCaptureReady(false);
      questions.forEach((item) =>
        localStorage.removeItem(draftKey(id, item.interviewQuestionId)),
      );
      pendingSave.current = null;
      setSaveStatus("saved");
      setInterview(result.interview);
      setFinishPhase("evaluating");
      setFinishMessage("答题记录已锁定，正在生成评分与面试报告…");
      if (result.evaluationTaskId)
        await waitEvaluationTask(String(result.evaluationTaskId));
      setFinishPhase("ready");
      setFinishMessage("报告已生成，即将打开能力报告。");
      window.setTimeout(
        () => navigate("/candidate/interviews/" + id + "/report"),
        650,
      );
    } catch (reason) {
      const message = reason instanceof Error ? reason.message : "结束面试失败";
      setFinishPhase("failed");
      setFinishMessage(message);
      setError(message);
    } finally {
      setThinking(false);
    }
  }

  async function retryReportGeneration() {
    if (interview?.status === INTERVIEW_STATUS.IN_PROGRESS) {
      await finishWithProgress();
      return;
    }
    setThinking(true);
    setFinishPhase("evaluating");
    setFinishMessage("正在重新提交报告生成任务…");
    try {
      const task = await request<Task>(
        "/v1/interviews/" + id + "/evaluation-task/retry",
        { method: "POST" },
      );
      await waitEvaluationTask(String(task.id));
      setFinishPhase("ready");
      setFinishMessage("报告已生成，即将打开能力报告。");
      window.setTimeout(
        () => navigate("/candidate/interviews/" + id + "/report"),
        650,
      );
    } catch (reason) {
      const message =
        reason instanceof Error ? reason.message : "报告重新生成失败";
      setFinishPhase("failed");
      setFinishMessage(message);
      setError(message);
    } finally {
      setThinking(false);
    }
  }

  function leaveRoom() {
    if (
      !finished &&
      (unsavedWork.current || recordedInterview) &&
      !window.confirm(
        recordedInterview
          ? "当前正在录制面试，离开会停止本题录制。确定离开吗？"
          : "当前还有未保存的回答或草稿，离开后可能需要重新提交。确定离开吗？",
      )
    )
      return;
    navigate("/candidate/interviews");
  }

  async function camera() {
    if (recordedInterview) {
      setError(
        recordingMode === "VIDEO"
          ? "视频面试的摄像头由录制流程统一控制。"
          : "语音面试不会开启摄像头。",
      );
      return;
    }
    if (cameraOn) {
      stream.current?.getTracks().forEach((track) => track.stop());
      stream.current = null;
      setCameraOn(false);
      return;
    }
    if (!window.isSecureContext) {
      setError("摄像头只能在 HTTPS 或 localhost 环境使用，请先配置 HTTPS。");
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setError("当前浏览器不支持摄像头访问。");
      return;
    }
    try {
      stream.current = await navigator.mediaDevices.getUserMedia({
        video: true,
        audio: false,
      });
      if (video.current) video.current.srcObject = stream.current;
      setCameraOn(true);
    } catch {
      setError("未获得摄像头权限，请在浏览器中允许访问。");
    }
  }

  if (loading) return <Card>正在加载 AI 面试间…</Card>;
  if (!interview || !question)
    return (
      <Card>
        <strong>无法打开该面试</strong>
        <p className="mt-2 text-sm text-muted-foreground">
          {error || "面试不存在，或你没有访问权限。"}
        </p>
        <Button
          className="mt-5"
          variant="secondary"
          onClick={() => navigate("/candidate/interviews")}
        >
          返回面试大厅
        </Button>
      </Card>
    );

  const submitLabel = choiceQuestion
    ? active < questions.length - 1
      ? "提交并下一题"
      : "提交"
    : "发送";
  const answerSyncPending =
    saveStatus === "saving" || saveStatus === "failed" || recordingBusy;
  const modeLabel =
    recordingMode === "TEXT"
      ? "文字面试"
      : recordingMode === "AUDIO"
        ? "语音面试"
        : recordingMode === "VIDEO"
          ? "视频面试"
          : "待选择面试方式";

  const saveText = !online
    ? "当前离线，回答草稿保存在本机，联网后会自动重试。"
    : saveStatus === "saving"
      ? "正在保存回答…"
      : saveStatus === "saved"
        ? "回答已保存"
        : saveStatus === "failed"
          ? "回答保存失败，内容仍保留，可立即重试。"
          : saveStatus === "draft"
            ? "草稿已保存在本机，发送后同步到服务器。"
            : "";

  return (
    <div className="mx-auto max-w-[1800px] space-y-4 p-3 sm:space-y-5 sm:p-5">
      <header className="flex flex-col gap-4 rounded-[22px] border border-border bg-surface px-4 py-4 shadow-sm sm:rounded-[24px] sm:px-5 sm:py-5 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <button
            onClick={leaveRoom}
            className="mb-2 flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            返回
          </button>
          <h1 className="text-xl font-bold lg:text-2xl">{interview.title}</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            #{id} · {modeLabel}
          </p>
        </div>
        <div className="flex items-center justify-between gap-3 sm:justify-start">
          <div className="rounded-2xl bg-muted px-4 py-2 text-right">
            <p className="text-xs text-muted-foreground">
              {finished ? "面试已结束" : "剩余时间"}
            </p>
            <p className="font-mono text-xl font-bold">
              {finished ? "--:--" : remainingText(seconds)}
            </p>
          </div>
          {!finished && (
            <Button
              variant="danger"
              disabled={thinking || answerSyncPending}
              onClick={() => {
                setFinishPhase("confirm");
                setFinishMessage("");
                setFinishDialogOpen(true);
              }}
            >
              <Square className="h-4 w-4" />
              结束面试
            </Button>
          )}
        </div>
      </header>
      {error && (
        <p className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          {error}
        </p>
      )}
      {recordedInterview && (
        <div
          role="status"
          aria-live="polite"
          className={
            "flex flex-wrap items-center gap-2 rounded-2xl border px-4 py-3 text-sm " +
            (recordingError
              ? "border-rose-200 bg-rose-50 text-rose-700"
              : "border-emerald-200 bg-emerald-50 text-emerald-800")
          }
        >
          <Radio
            className={
              "h-4 w-4 " +
              (!recordingError && !recordingBusy ? "animate-pulse" : "")
            }
            aria-hidden="true"
          />
          <span>
            {recordingError ||
              (recordingBusy
                ? "正在保存上一道题的录制分段…"
                : `${modeLabel}正在录制当前题，题目只能按顺序完成。`)}
          </span>
          {pendingRecordingUploads.current.length > 0 && online && (
            <button
              type="button"
              className="ml-auto inline-flex items-center gap-1 rounded-full px-3 py-1 font-semibold hover:bg-rose-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-600"
              onClick={() => void retryRecordingUploads()}
            >
              <RefreshCw className="h-3.5 w-3.5" />
              重试上传
            </button>
          )}
        </div>
      )}
      {saveText && (
        <div
          role="status"
          aria-live="polite"
          aria-atomic="true"
          className={
            "flex flex-wrap items-center gap-2 rounded-2xl border px-4 py-3 text-sm " +
            (!online || saveStatus === "failed"
              ? "border-amber-200 bg-amber-50 text-amber-800"
              : saveStatus === "saved"
                ? "border-emerald-200 bg-emerald-50 text-emerald-800"
                : "border-border bg-muted text-muted-foreground")
          }
        >
          {!online ? (
            <CloudOff className="h-4 w-4" aria-hidden="true" />
          ) : saveStatus === "saving" ? (
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          ) : saveStatus === "saved" ? (
            <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
          ) : saveStatus === "failed" ? (
            <AlertTriangle className="h-4 w-4" aria-hidden="true" />
          ) : (
            <Save className="h-4 w-4" aria-hidden="true" />
          )}
          <span>{saveText}</span>
          {saveStatus === "failed" && online && (
            <button
              type="button"
              onClick={() => void retryPendingSave()}
              className="ml-auto inline-flex items-center gap-1 rounded-full px-3 py-1 font-semibold hover:bg-amber-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-600"
            >
              <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
              重新保存
            </button>
          )}
        </div>
      )}
      {readingQuestion && (
        <p className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          当前题目正在朗读，请等待播报结束。
        </p>
      )}
      <div
        className="grid gap-5 xl:grid-cols-[240px_minmax(0,1fr)_360px]"
        onClickCapture={(event) => {
          if (!recordedInterview) return;
          const button = (event.target as HTMLElement).closest("button");
          const label = button?.textContent?.trim() ?? "";
          const questionNavigation =
            label === "上一题" ||
            label === "下一题" ||
            questions.some((item) => label.endsWith(item.content));
          if (!questionNavigation) return;
          event.preventDefault();
          event.stopPropagation();
          setError(
            "语音或视频面试开启录制后只能按顺序完成题目，不能主动切题。",
          );
        }}
      >
        <Card className="order-2 h-fit p-3 xl:order-1">
          <div className="flex justify-between px-2 py-2">
            <strong>题目</strong>
            <span className="text-sm text-muted-foreground">
              {active + 1}/{questions.length}
            </span>
          </div>
          <div className="mx-2 h-1.5 overflow-hidden rounded-full bg-muted">
            <div
              className="h-full bg-[var(--primary)]"
              style={{
                width:
                  Math.round(((active + 1) / questions.length) * 100) + "%",
              }}
            />
          </div>
          <div className="mt-3 space-y-1">
            {questions.map((item, index) => (
              <button
                key={item.interviewQuestionId}
                disabled={recordedInterview || thinking || answerSyncPending}
                aria-current={index === active ? "step" : undefined}
                title={
                  recordedInterview && index !== active
                    ? "录制面试只能按顺序完成题目"
                    : undefined
                }
                onClick={() => setActive(index)}
                className={
                  "flex w-full gap-3 rounded-xl px-3 py-3 text-left text-sm disabled:cursor-not-allowed disabled:opacity-60 " +
                  (index === active
                    ? "bg-[var(--accent-soft)] text-[var(--foreground)]"
                    : "hover:bg-muted")
                }
              >
                <b className="text-xs">{String(index + 1).padStart(2, "0")}</b>
                <span className="line-clamp-2">{item.content}</span>
              </button>
            ))}
          </div>
        </Card>
        <Card className="order-1 flex min-h-[520px] flex-col p-4 sm:min-h-[620px] sm:p-6 xl:order-2">
          <div className="flex items-center justify-between border-b border-border pb-4">
            <Badge tone="info">{question.questionType.replace("_", " ")}</Badge>
            <span className="text-sm text-muted-foreground">
              {choiceQuestion
                ? `${question.maxScore} 分`
                : `追问 ${Math.min(followUps, limit)}/${limit}`}
            </span>
          </div>
          <div className="mt-5 rounded-2xl bg-[var(--accent-soft)] p-4">
            <p className="text-xs font-bold text-[var(--accent)]">当前题目</p>
            <p className="mt-2 leading-7">{question.content}</p>
          </div>
          <div className="my-5 flex flex-1 flex-col gap-3 overflow-y-auto">
            <AnimatePresence initial={false}>
              {messages.map((message, index) => (
                <motion.article
                  key={message.role + "-" + index}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={
                    "max-w-[88%] rounded-2xl px-4 py-3 text-sm leading-6 " +
                    (message.role === "candidate"
                      ? "ml-auto bg-[var(--primary)] text-white"
                      : "bg-muted")
                  }
                >
                  <p className="mb-1 text-xs font-bold">
                    {message.role === "candidate"
                      ? "我"
                      : "面试官"}
                  </p>
                  {message.content}
                </motion.article>
              ))}
            </AnimatePresence>
            {thinking && (
              <p className="w-fit rounded-2xl bg-muted px-4 py-3 text-sm text-muted-foreground">
                正在处理…
              </p>
            )}
          </div>
          {choiceQuestion ? (
            <div className="space-y-2">
              {options.map((option) => (
                <label
                  key={option.key}
                  className="flex cursor-pointer items-center gap-3 rounded-xl border border-border px-4 py-3 text-sm"
                >
                  <input
                    type={
                      question.questionType === "multiple_choice"
                        ? "checkbox"
                        : "radio"
                    }
                    name="answer"
                    disabled={thinking || answerSyncPending || finished}
                    checked={selected.includes(option.key)}
                    onChange={() =>
                      setSelected((previous) =>
                        question.questionType === "multiple_choice"
                          ? previous.includes(option.key)
                            ? previous.filter((value) => value !== option.key)
                            : [...previous, option.key]
                          : [option.key],
                      )
                    }
                  />
                  {option.key}. {option.text}
                </label>
              ))}
            </div>
          ) : (
            <div className="relative">
              <textarea
                value={draft}
                disabled={finished || thinking || answerSyncPending}
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={(event) => {
                  if ((event.ctrlKey || event.metaKey) && event.key === "Enter")
                    void send();
                }}
                className="min-h-32 w-full rounded-2xl border border-border bg-background p-4 pr-14 text-sm outline-none focus:border-[var(--accent)]"
                placeholder="输入回答，或点击麦克风进行语音转写。"
              />
              <button
                type="button"
                onClick={() => void toggleVoiceAnswer()}
                disabled={finished || thinking || answerSyncPending}
                className={
                  "absolute bottom-3 right-3 grid h-10 w-10 place-items-center rounded-xl " +
                  (listening
                    ? "bg-rose-500 text-white"
                    : "bg-[var(--primary)] text-white")
                }
                title={listening ? "停止录音" : "语音回答"}
                aria-label={listening ? "停止录音" : "开始语音回答"}
              >
                <Mic className="h-4 w-4" />
              </button>
              <p className="mt-2 text-xs text-muted-foreground">
                {listening
                  ? "正在录音，再次点击可停止。"
                  : "语音内容将自动转写至回答框。"}
              </p>
            </div>
          )}
          <div className="mt-4 grid grid-cols-2 gap-2 sm:flex sm:justify-between">
            <Button
              variant="secondary"
              className="order-2 w-full sm:order-none sm:w-auto"
              disabled={
                recordedInterview ||
                thinking ||
                answerSyncPending ||
                active === 0
              }
              onClick={() => setActive((value) => value - 1)}
            >
              <ChevronLeft className="h-4 w-4" />
              上一题
            </Button>
            <Button
              className="order-1 col-span-2 w-full sm:order-none sm:w-auto"
              disabled={thinking || answerSyncPending || finished}
              onClick={() => void send()}
            >
              {submitLabel}
              <Send className="h-4 w-4" />
            </Button>
            <Button
              variant="secondary"
              className="order-3 w-full sm:order-none sm:w-auto"
              disabled={
                recordedInterview ||
                thinking ||
                answerSyncPending ||
                active === questions.length - 1
              }
              onClick={() => setActive((value) => value + 1)}
            >
              下一题
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </Card>
        <div className="order-3 space-y-5">
          <Card className="overflow-hidden p-0">
            <div className="relative min-h-[360px] overflow-hidden bg-[radial-gradient(circle_at_50%_16%,rgba(235,214,255,.75),transparent_36%),linear-gradient(180deg,#fff7fb_0%,#f2ebe2_100%)] sm:min-h-[430px] dark:bg-[radial-gradient(circle_at_50%_16%,rgba(120,88,170,.35),transparent_36%),linear-gradient(180deg,#211b19_0%,#151210_100%)]">
              <div className="absolute left-4 top-4 z-20 rounded-full border border-white/55 bg-white/75 px-3 py-1 text-xs font-bold text-[#8a5f3f] shadow-sm backdrop-blur-xl dark:border-white/10 dark:bg-white/10 dark:text-amber-100">
                {virtualActive ? virtualProviderName : "未连接"}
              </div>
              <div
                ref={avatarRoot}
                className={
                  "absolute inset-0 [&_video]:h-full [&_video]:w-full [&_video]:object-cover " +
                  (virtualActive ? "block" : "hidden")
                }
              />
              {!virtualActive && (
                <div className="absolute inset-0 grid place-items-center">
                  <div className="relative grid h-56 w-56 place-items-center">
                    <div className="absolute inset-0 rounded-full border border-dashed border-[#b17653]/35" />
                    <div className="absolute h-40 w-40 animate-[spin_10s_linear_infinite] rounded-full border border-[#b17653]/20" />
                    <span className="z-10 grid h-24 w-24 place-items-center rounded-[32px] bg-[#11100f] text-white shadow-[0_24px_80px_rgba(119,83,59,.28)]">
                      <Bot className="h-10 w-10" />
                    </span>
                  </div>
                </div>
              )}
              <div className="absolute bottom-4 left-4 right-4 z-20 rounded-[22px] border border-white/45 bg-white/78 px-4 py-3 text-[#251c18] shadow-[0_18px_45px_rgba(84,58,41,.18)] backdrop-blur-2xl dark:border-white/10 dark:bg-black/35 dark:text-white">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-bold">面试官</p>
                    <p className="mt-1 text-xs leading-5 opacity-75">
                      {virtualLoading ? "连接中…" : virtualMessage}
                    </p>
                  </div>
                  <span
                    className={
                      "mt-1 h-2.5 w-2.5 shrink-0 rounded-full " +
                      (virtualActive
                        ? "bg-emerald-500 shadow-[0_0_14px_rgba(16,185,129,.7)]"
                        : "bg-amber-500")
                    }
                  />
                </div>
                {!virtualActive && (
                  <Button
                    className="mt-3 h-9 px-3"
                    disabled={virtualLoading || finished}
                    onClick={() => void startAvatar()}
                  >
                    <Play className="h-3.5 w-3.5" />
                    启动虚拟人
                  </Button>
                )}
              </div>
            </div>
            <div className="flex items-center justify-between p-4">
              <div>
                <p className="text-sm font-semibold">题目朗读</p>
              </div>
              <button
                className="rounded-xl p-2 hover:bg-muted"
                onClick={() => setTts((value) => !value)}
                aria-label={tts ? "关闭题目朗读" : "开启题目朗读"}
              >
                {tts ? (
                  <Volume2 className="h-4 w-4 text-[var(--accent)]" />
                ) : (
                  <VolumeX className="h-4 w-4" />
                )}
              </button>
            </div>
            <button
              onClick={() => void readQuestion(question)}
              className="mx-4 mb-4 flex items-center gap-2 rounded-xl bg-muted px-3 py-2 text-xs font-semibold hover:bg-[var(--accent-soft)] hover:text-[var(--accent)]"
            >
              <Volume2 className="h-3.5 w-3.5" />
              重读题目
            </button>
          </Card>
          <Card>
            <div className="flex items-center justify-between">
              <div>
                <p className="font-semibold">摄像头</p>
              </div>
              <Button
                variant="secondary"
                className="h-9 px-3"
                onClick={() => void camera()}
              >
                <Camera className="h-4 w-4" />
                {cameraOn ? "关闭" : "开启"}
              </Button>
            </div>
            <div className="relative mt-4 grid aspect-video place-items-center overflow-hidden rounded-2xl bg-muted">
              <video
                ref={video}
                autoPlay
                muted
                playsInline
                className={
                  "h-full w-full object-cover " +
                  (cameraOn ? "block -scale-x-100" : "hidden")
                }
              />
              {!cameraOn && (
                <div className="text-center text-muted-foreground">
                  <Camera className="mx-auto h-6 w-6" />
                  <p className="mt-2 text-xs">未开启</p>
                </div>
              )}
            </div>
            {!window.isSecureContext && (
              <p className="mt-3 text-xs leading-5 text-amber-700">
                当前连接无法使用摄像头或麦克风，请改用 HTTPS 访问。
              </p>
            )}
          </Card>
        </div>
      </div>
      {modeDialogOpen && !finished && (
        <div
          className="fixed inset-0 z-[80] grid place-items-center overflow-y-auto bg-black/45 p-3 backdrop-blur-md sm:p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="mode-dialog-title"
          aria-describedby="mode-dialog-description"
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.97, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            className="my-3 w-full max-w-4xl rounded-[24px] border border-border bg-surface p-5 shadow-[0_30px_100px_rgba(20,18,17,.3)] sm:rounded-[30px] sm:p-6 lg:p-8"
          >
            <div className="flex items-start gap-4">
              <span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-[var(--accent-soft)] text-[var(--accent)]">
                <ShieldCheck className="h-6 w-6" />
              </span>
              <div>
                <p className="text-xs font-semibold tracking-[.18em] text-[var(--accent)]">
                  面试方式
                </p>
                <h2 id="mode-dialog-title" className="mt-1 text-2xl font-bold">
                  {recording ? `继续${modeLabel}` : "选择本次面试方式"}
                </h2>
                <p
                  id="mode-dialog-description"
                  className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground"
                >
                  文字面试不录制；语音和视频面试按题目分段保存，并采用顺序答题。选定后不可更改。
                </p>
              </div>
            </div>
            {recordingError && (
              <p
                role="alert"
                className="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
              >
                {recordingError}
              </p>
            )}
            {recording ? (
              <div className="mt-7 rounded-3xl border border-border bg-muted/50 p-4 sm:p-6">
                <div className="flex items-center gap-3">
                  <span className="grid h-11 w-11 place-items-center rounded-2xl bg-surface text-[var(--accent)]">
                    {recording.mode === "AUDIO" ? (
                      <Headphones className="h-5 w-5" />
                    ) : (
                      <Video className="h-5 w-5" />
                    )}
                  </span>
                  <div>
                    <h3 className="font-bold">已选择{modeLabel}</h3>
                    <p className="mt-1 text-sm text-muted-foreground">
                      刷新页面后需重新授权设备，已有录制和时间轴将保留。
                    </p>
                  </div>
                </div>
                <Button
                  autoFocus
                  className="mt-5 w-full"
                  disabled={Boolean(modeStarting)}
                  onClick={() => void selectInterviewMode(recording.mode)}
                >
                  {modeStarting ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Play className="h-4 w-4" />
                  )}
                  继续并授权设备
                </Button>
              </div>
            ) : (
              <div className="mt-7 grid gap-4 md:grid-cols-3">
                <button
                  autoFocus
                  type="button"
                  disabled={Boolean(modeStarting)}
                  onClick={() => void selectInterviewMode("TEXT")}
                  className="group rounded-3xl border border-border bg-background p-5 text-left transition hover:-translate-y-1 hover:border-[var(--accent)] hover:shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"
                >
                  <span className="grid h-12 w-12 place-items-center rounded-2xl bg-muted group-hover:bg-[var(--accent-soft)] group-hover:text-[var(--accent)]">
                    <Keyboard className="h-5 w-5" />
                  </span>
                  <h3 className="mt-5 text-lg font-bold">1. 文字面试</h3>
                  <p className="mt-2 text-sm leading-6 text-muted-foreground">
                    不录音、不录像。点击“重读题目”时由浏览器朗读；启动虚拟人后改由虚拟人朗读。
                  </p>
                </button>
                <button
                  type="button"
                  disabled={Boolean(modeStarting)}
                  onClick={() => void selectInterviewMode("AUDIO")}
                  className="group rounded-3xl border border-border bg-background p-5 text-left transition hover:-translate-y-1 hover:border-[var(--accent)] hover:shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"
                >
                  <span className="grid h-12 w-12 place-items-center rounded-2xl bg-muted group-hover:bg-[var(--accent-soft)] group-hover:text-[var(--accent)]">
                    <Headphones className="h-5 w-5" />
                  </span>
                  <h3 className="mt-5 text-lg font-bold">2. 语音面试</h3>
                  <p className="mt-2 text-sm leading-6 text-muted-foreground">
                    录制麦克风并按题目生成语音分段。完成当前题后自动进入下一题。
                  </p>
                </button>
                <button
                  type="button"
                  disabled={Boolean(modeStarting)}
                  onClick={() => void selectInterviewMode("VIDEO")}
                  className="group rounded-3xl border border-border bg-background p-5 text-left transition hover:-translate-y-1 hover:border-[var(--accent)] hover:shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)]"
                >
                  <span className="grid h-12 w-12 place-items-center rounded-2xl bg-muted group-hover:bg-[var(--accent-soft)] group-hover:text-[var(--accent)]">
                    <Video className="h-5 w-5" />
                  </span>
                  <h3 className="mt-5 text-lg font-bold">3. 视频面试</h3>
                  <p className="mt-2 text-sm leading-6 text-muted-foreground">
                    录制麦克风和 1280×720、15 fps
                    视频，按题目分段并生成回放时间轴。
                  </p>
                </button>
              </div>
            )}
            <div className="mt-6 flex items-start gap-3 rounded-2xl bg-muted/60 p-4 text-xs leading-5 text-muted-foreground">
              <LockKeyhole className="mt-0.5 h-4 w-4 shrink-0" />
              <p>
                仅语音和视频面试会启动录制；离开页面时，系统将自动结束虚拟人会话。
              </p>
            </div>
          </motion.div>
        </div>
      )}
      {finishDialogOpen && (
        <div
          className="fixed inset-0 z-[70] grid place-items-end bg-black/35 p-0 backdrop-blur-md sm:place-items-center sm:p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="finish-dialog-title"
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            transition={{ duration: 0.2, ease: "easeOut" }}
            className="safe-area-bottom w-full max-w-md overflow-hidden rounded-t-[28px] border border-border bg-surface shadow-[0_28px_90px_rgba(20,18,17,.22)] sm:rounded-[30px]"
          >
            <div className="soft-emphasis-panel rounded-none border-0 p-5 shadow-none sm:p-6">
              <span
                className={
                  "grid h-12 w-12 place-items-center rounded-2xl shadow-sm " +
                  (finishPhase === "ready"
                    ? "bg-emerald-50 text-emerald-700"
                    : finishPhase === "failed"
                      ? "bg-rose-50 text-rose-700"
                      : "bg-amber-50 text-amber-700")
                }
              >
                {finishPhase === "ready" ? (
                  <CheckCircle2 className="h-6 w-6" />
                ) : finishPhase === "submitting" ||
                  finishPhase === "evaluating" ? (
                  <Loader2 className="h-6 w-6 animate-spin" />
                ) : (
                  <AlertTriangle className="h-6 w-6" />
                )}
              </span>
              <h2 id="finish-dialog-title" className="mt-5 text-2xl font-bold">
                {finishPhase === "confirm"
                  ? "确认结束本次面试？"
                  : finishPhase === "ready"
                    ? "报告生成完成"
                    : finishPhase === "failed"
                      ? "报告生成失败"
                      : "正在生成面试报告"}
              </h2>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">
                {finishPhase === "confirm"
                  ? "系统将保存当前回答与录制分段，锁定答题记录并生成评测报告。"
                  : finishMessage}
              </p>
            </div>
            <div className="space-y-3 p-5 sm:p-6">
              <div className="rounded-2xl border border-border bg-background/70 p-4 text-sm text-muted-foreground">
                <p>
                  <span className="font-semibold text-foreground">
                    当前进度：
                  </span>
                  {active + 1}/{questions.length} 题
                </p>
                <p className="mt-1">
                  <span className="font-semibold text-foreground">
                    剩余时间：
                  </span>
                  {remainingText(seconds)}
                </p>
              </div>
              {finishPhase !== "confirm" && (
                <div className="rounded-2xl border border-border bg-background/70 p-4">
                  <p className="text-sm font-semibold text-foreground">
                    {finishPhase === "ready"
                      ? "报告已生成"
                      : finishPhase === "failed"
                        ? "生成失败"
                        : "正在处理"}
                  </p>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">
                    {finishMessage}
                  </p>
                </div>
              )}
              <div className="grid grid-cols-2 gap-3 pt-2 sm:flex sm:justify-end">
                {finishPhase === "confirm" && (
                  <Button
                    variant="secondary"
                    disabled={thinking}
                    onClick={() => setFinishDialogOpen(false)}
                  >
                    继续作答
                  </Button>
                )}
                {finishPhase === "confirm" && (
                  <Button
                    variant="danger"
                    disabled={thinking || answerSyncPending}
                    onClick={() =>
                      void finishWithProgress({ submitCurrentAnswer: true })
                    }
                  >
                    <Square className="h-4 w-4" />
                    确认结束
                  </Button>
                )}
                {finishPhase === "failed" && (
                  <Button variant="secondary" onClick={leaveRoom}>
                    返回大厅
                  </Button>
                )}
                {finishPhase === "failed" && (
                  <Button
                    disabled={thinking}
                    onClick={() => void retryReportGeneration()}
                  >
                    重新生成报告
                  </Button>
                )}
              </div>
            </div>
          </motion.div>
        </div>
      )}
    </div>
  );
}
