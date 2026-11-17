<template>
  <div class="learning-page">
    <aside class="course-sidebar">
      <a class="sidebar-home" @click.prevent="openLessons">
        <span class="home-mark">ML</span>
        <span><strong>机器学习教程</strong><small>循序渐进 · 边学边练</small></span>
      </a>

      <div class="progress-card">
        <div class="progress-caption"><span>学习进度</span><strong>{{ progress }}%</strong></div>
        <div class="progress-track"><span :style="{ width: `${progress}%` }"></span></div>
        <small>{{ completed.size }} / {{ lessons.length }} 节已完成</small>
      </div>

      <nav v-if="view === 'lesson'" class="lesson-nav">
        <template v-for="(lesson, index) in lessons" :key="lesson.id">
          <div v-if="index === 0 || lessons[index - 1].category !== lesson.category" class="nav-group">
            {{ lesson.category }}
          </div>
          <button type="button" class="lesson-link" :class="{ selected: activeId === lesson.id }" @click="selectLesson(lesson.id)">
            <span class="lesson-number">{{ completed.has(lesson.id) ? '✓' : String(index + 1).padStart(2, '0') }}</span>
            <span>{{ lesson.title }}</span>
          </button>
        </template>
      </nav>

      <div class="study-tools">
        <div class="nav-group">考核与复习</div>
        <button type="button" class="tool-link" :class="{ selected: view === 'lesson' }" @click="openLessons">
          <span class="tool-icon">文</span><span>理论课程</span>
        </button>
        <button type="button" class="tool-link" :class="{ selected: view === 'quiz' }" @click="startQuiz()">
          <span class="tool-icon">✓</span><span>理论考核</span>
        </button>
        <button type="button" class="tool-link" :class="{ selected: view === 'wrongbook' }" @click="openWrongbook">
          <span class="tool-icon">≡</span><span>错题集</span>
          <span class="wrong-count">{{ wrongQuestionIds.length }}</span>
        </button>
      </div>

      <router-link class="sidebar-practice" to="/"><span>↗</span> 前往训练中心</router-link>
    </aside>

    <main class="lesson-main">
      <!-- 课程 -->
      <template v-if="view === 'lesson'">
        <div class="breadcrumbs">
          <span>课程</span><span>/</span><span>{{ activeLesson.category }}</span><span>/</span>
          <strong>{{ activeLesson.title }}</strong>
        </div>

        <section class="lesson-heading">
          <div class="eyebrow">
            <span class="eyebrow-dot"></span>机器学习基础课程
            <span class="duration">◷ {{ activeLesson.duration }}</span>
          </div>
          <h1>{{ activeLesson.title }}</h1>
          <p>{{ activeLesson.summary }}</p>
        </section>

        <div class="lesson-content">
          <section class="article-section">
            <h2>本节内容</h2>
            <p v-for="paragraph in activeLesson.paragraphs" :key="paragraph">{{ paragraph }}</p>
          </section>

          <section class="concept-sections">
            <article v-for="(section, index) in activeLesson.sections" :key="section.title" class="concept-section">
              <div class="concept-index">{{ String(index + 1).padStart(2, '0') }}</div>
              <div>
                <h3>{{ section.title }}</h3>
                <p>{{ section.body }}</p>
              </div>
            </article>
          </section>

          <section class="example-card">
            <div class="example-label"><span>✳</span> 概念示例</div>
            <h3>{{ activeLesson.exampleTitle }}</h3>
            <p>{{ activeLesson.example }}</p>
            <router-link to="/" class="example-link">前往训练中心试一试 <span>→</span></router-link>
          </section>

          <section class="keypoints">
            <h2>记住这几点</h2>
            <ul>
              <li v-for="point in activeLesson.keyPoints" :key="point"><span class="check">✓</span>{{ point }}</li>
            </ul>
          </section>

          <section class="self-check">
            <div class="self-check-label">学完自测</div>
            <h2>{{ activeLesson.checkQuestion }}</h2>
            <div class="self-check-options">
              <button
                v-for="(option, index) in activeLesson.checkOptions"
                :key="option"
                type="button"
                class="self-check-option"
                :class="{
                  chosen: lessonCheckChoice === index,
                  'answer-correct': lessonCheckSubmitted && index === activeLesson.checkAnswerIndex,
                  'answer-wrong': lessonCheckSubmitted && lessonCheckChoice === index && index !== activeLesson.checkAnswerIndex
                }"
                :disabled="lessonCheckSubmitted"
                @click="lessonCheckChoice = index"
              >
                <span class="option-letter">{{ ['A', 'B', 'C', 'D'][index] }}</span><span>{{ option }}</span>
              </button>
            </div>
            <div class="self-check-actions">
              <button v-if="!lessonCheckSubmitted" class="answer-toggle" type="button" :disabled="lessonCheckChoice === null" @click="submitLessonCheck">
                检查答案 →
              </button>
              <button v-else class="answer-toggle" type="button" @click="resetLessonCheck">再试一次 ↻</button>
            </div>
            <div
              v-if="lessonCheckSubmitted"
              class="self-check-feedback"
              :class="{ 'is-correct': lessonCheckChoice === activeLesson.checkAnswerIndex }"
            >
              <strong>{{ lessonCheckChoice === activeLesson.checkAnswerIndex ? '回答正确' : '再复习一下这个知识点' }}</strong>
              <p>{{ activeLesson.checkAnswer }}</p>
            </div>
          </section>

          <div class="lesson-actions">
            <button class="previous-button" type="button" :disabled="activeIndex === 0" @click="goTo(-1)">← 上一节</button>
            <button class="complete-button" type="button" @click="markComplete">
              {{ completed.has(activeId) ? '✓ 已完成' : '标记为已完成' }}
            </button>
            <button class="next-button" type="button" :disabled="activeIndex === lessons.length - 1" @click="goTo(1)">
              下一节 →
            </button>
          </div>
        </div>

        <footer class="lesson-footer">
          <span>机器学习，从理解数据开始。</span><span>{{ activeIndex + 1 }} / {{ lessons.length }}</span>
        </footer>
      </template>

      <!-- 考核 -->
      <template v-else-if="view === 'quiz'">
        <div class="breadcrumbs"><span>学习中心</span><span>/</span><strong>理论考核</strong></div>
        <section class="assessment-heading">
          <div class="eyebrow"><span class="eyebrow-dot"></span>章节知识检查</div>
          <h1>理论考核</h1>
          <p>
            题目覆盖理论课程各章节与知识点，每题 20 分，满分 100 分。提交后会显示得分，并为答错的题目提供解析、自动加入错题集。
          </p>
          <div class="exam-meta">
            <span>每次 5 道单选题</span><span>◷ 不限时</span><span>满分 100 分</span>
          </div>
        </section>

        <section class="quiz-list">
          <article
            v-for="(question, index) in currentQuizQuestions"
            :key="question.id"
            class="quiz-card"
            :class="{
              'correct-card': quizSubmitted && quizAnswers[question.id] === question.answerIndex,
              'wrong-card': quizSubmitted && quizAnswers[question.id] !== question.answerIndex
            }"
          >
            <div class="question-meta">
              <span>第 {{ index + 1 }} 题</span><span>{{ lessonTitle(question.lessonId) }}</span><span>20 分</span>
            </div>
            <h2>{{ question.question }}</h2>
            <div class="knowledge-point">对应课程知识点：{{ question.knowledgePoint }}</div>
            <div class="option-list">
              <button
                v-for="(option, optionIndex) in question.options"
                :key="option"
                type="button"
                class="option"
                :class="{
                  chosen: quizAnswers[question.id] === optionIndex,
                  'option-correct': quizSubmitted && optionIndex === question.answerIndex,
                  'option-wrong': quizSubmitted && quizAnswers[question.id] === optionIndex && optionIndex !== question.answerIndex
                }"
                :disabled="quizSubmitted"
                @click="chooseOption(question.id, optionIndex)"
              >
                <span class="option-letter">{{ ['A', 'B', 'C', 'D'][optionIndex] }}</span><span>{{ option }}</span>
              </button>
            </div>
            <div v-if="quizSubmitted && quizAnswers[question.id] !== question.answerIndex" class="question-feedback">
              <strong>正确答案：{{ ['A', 'B', 'C', 'D'][question.answerIndex] }}. {{ question.options[question.answerIndex] }}</strong>
              <p>{{ question.explanation }}</p>
              <small>已加入错题集</small>
            </div>
            <div v-if="quizSubmitted && quizAnswers[question.id] === question.answerIndex" class="question-feedback is-correct">
              <strong>回答正确</strong><p>{{ question.explanation }}</p>
            </div>
            <button class="lesson-review-link" type="button" @click="openLesson(question.lessonId)">
              回到课程复习：{{ lessonTitle(question.lessonId) }} →
            </button>
          </article>
        </section>

        <section v-if="quizSubmitted" class="score-panel">
          <div>
            <span>本次得分</span>
            <strong>{{ quizScore }}<small> / 100</small></strong>
            <p>答对 {{ (quizScore || 0) / 20 }} 题 · 错题已保存，可随时在错题集中复习</p>
          </div>
          <div class="score-actions">
            <button class="complete-button" type="button" @click="openLessons">返回理论课程</button>
            <button class="next-button" type="button" @click="startQuiz()">再做一组</button>
          </div>
        </section>

        <div v-if="!quizSubmitted" class="quiz-actions">
          <span>已完成 {{ answeredCount }} / 5 题</span>
          <button class="next-button" type="button" :disabled="answeredCount !== 5" @click="submitQuiz">提交考核</button>
        </div>
      </template>

      <!-- 错题集 -->
      <template v-else>
        <div class="breadcrumbs"><span>学习中心</span><span>/</span><strong>错题集</strong></div>
        <section class="assessment-heading">
          <div class="eyebrow"><span class="eyebrow-dot"></span>持续复习</div>
          <h1>错题集</h1>
          <p>考核中答错的题目会保存在这台设备上，方便之后回顾知识点。完成一次新考核后，错题会自动更新到这里。</p>
          <div class="exam-meta"><span>当前收录 {{ wrongQuestions.length }} 道错题</span><span>自动保存于本机</span></div>
        </section>

        <div v-if="!wrongQuestions.length" class="empty-wrongbook">
          <span>✓</span>
          <h2>暂时没有错题</h2>
          <p>完成理论考核后，答错的题目和解析会自动收录在这里。</p>
          <button class="next-button" type="button" @click="startQuiz()">开始考核</button>
        </div>

        <section v-else class="wrongbook-list">
          <article v-for="(question, index) in wrongQuestions" :key="question.id" class="quiz-card wrong-card">
            <div class="question-meta"><span>错题 {{ index + 1 }}</span><span>{{ lessonTitle(question.lessonId) }}</span></div>
            <h2>{{ question.question }}</h2>
            <div class="knowledge-point">对应课程知识点：{{ question.knowledgePoint }}</div>
            <div class="answer-key">
              <strong>正确答案：{{ ['A', 'B', 'C', 'D'][question.answerIndex] }}. {{ question.options[question.answerIndex] }}</strong>
              <p>{{ question.explanation }}</p>
            </div>
            <button class="lesson-review-link" type="button" @click="openLesson(question.lessonId)">
              回到课程复习：{{ lessonTitle(question.lessonId) }} →
            </button>
          </article>
          <button class="next-button review-button" type="button" @click="startQuiz(true)">抽取错题练习</button>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { LESSONS } from '@/services/lessons'
import { createQuestionBank } from '@/services/questionBank'

defineOptions({ name: 'LearningView' })

const STORAGE_KEY = 'ml-learning-wrong-questions'

const lessons = LESSONS
const questionBank = createQuestionBank()

const view = ref('lesson')
const activeId = ref(lessons[0].id)
const completed = ref(new Set())
const lessonCheckChoice = ref(null)
const lessonCheckSubmitted = ref(false)

const currentQuizQuestions = ref([])
const quizAnswers = ref({})
const quizSubmitted = ref(false)
const quizScore = ref(null)
const wrongQuestionIds = ref([])

const activeIndex = computed(() => Math.max(0, lessons.findIndex(item => item.id === activeId.value)))
const activeLesson = computed(() => lessons[activeIndex.value] || lessons[0])
const progress = computed(() => Math.round((completed.value.size / lessons.length) * 100))
const questionCount = computed(() => questionBank.length)
const answeredCount = computed(() =>
  currentQuizQuestions.value.filter(question => quizAnswers.value[question.id] !== undefined).length
)
const wrongQuestions = computed(() =>
  questionBank.filter(question => wrongQuestionIds.value.includes(question.id))
)

function lessonTitle(lessonId) {
  return lessons.find(item => item.id === lessonId)?.title ?? ''
}

function selectLesson(id) {
  activeId.value = id
  view.value = 'lesson'
  lessonCheckChoice.value = null
  lessonCheckSubmitted.value = false
}

function openLesson(id) {
  selectLesson(id)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function openLessons() {
  view.value = 'lesson'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function goTo(offset) {
  const next = activeIndex.value + offset
  if (next >= 0 && next < lessons.length) {
    selectLesson(lessons[next].id)
  }
}

function markComplete() {
  completed.value = new Set([...completed.value, activeId.value])
}

function submitLessonCheck() {
  if (lessonCheckChoice.value === null) {
    return
  }
  lessonCheckSubmitted.value = true
}

function resetLessonCheck() {
  lessonCheckChoice.value = null
  lessonCheckSubmitted.value = false
}

function startQuiz(fromWrongbook = false) {
  const pool = fromWrongbook && wrongQuestions.value.length
    ? wrongQuestions.value
    : questionBank
  const shuffled = [...pool].sort(() => Math.random() - 0.5)
  currentQuizQuestions.value = shuffled.slice(0, Math.min(5, shuffled.length))
  quizAnswers.value = {}
  quizSubmitted.value = false
  quizScore.value = null
  view.value = 'quiz'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function chooseOption(questionId, optionIndex) {
  if (quizSubmitted.value) {
    return
  }
  quizAnswers.value = { ...quizAnswers.value, [questionId]: optionIndex }
}

function submitQuiz() {
  if (answeredCount.value !== currentQuizQuestions.value.length) {
    return
  }
  quizSubmitted.value = true
  const wrong = currentQuizQuestions.value.filter(
    question => quizAnswers.value[question.id] !== question.answerIndex
  )
  quizScore.value = (currentQuizQuestions.value.length - wrong.length) * 20

  const ids = new Set(wrongQuestionIds.value)
  wrong.forEach(question => ids.add(question.id))
  wrongQuestionIds.value = [...ids]
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(wrongQuestionIds.value))
  } catch (error) {
    // 存储不可用时保持当前会话可用
  }
}

function openWrongbook() {
  view.value = 'wrongbook'
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      wrongQuestionIds.value = JSON.parse(stored) ?? []
    }
  } catch (error) {
    wrongQuestionIds.value = []
  }
})
</script>

<style scoped>
.learning-page { min-height: calc(100vh - 82px); display: grid; grid-template-columns: 276px minmax(0, 1fr); background: #f7f9fc; color: #17243b; }
.course-sidebar { position: sticky; top: 82px; height: calc(100vh - 82px); min-height: 620px; display: flex; flex-direction: column; padding: 24px 16px 18px; background: #fff; border-right: 1px solid #e8edf3; overflow-y: auto; }
.sidebar-home { display: flex; align-items: center; gap: 11px; padding: 4px 8px 22px; text-decoration: none; color: inherit; }
.home-mark { width: 38px; height: 38px; display: grid; place-items: center; border-radius: 12px; background: #e8f7f1; color: #16805a; font-weight: 800; font-size: 13px; }
.sidebar-home strong, .sidebar-home small { display: block; }
.sidebar-home strong { font-size: 14px; }.sidebar-home small { margin-top: 3px; color: #91a0b3; font-size: 11px; }
.progress-card { margin: 0 4px 20px; padding: 14px; border: 1px solid #e9eef4; border-radius: 12px; background: #fbfcfd; }
.progress-caption { display: flex; justify-content: space-between; color: #607086; font-size: 12px; }.progress-caption strong { color: #16805a; }
.progress-track { height: 5px; margin: 10px 0 7px; background: #e9f0ed; border-radius: 9px; overflow: hidden; }.progress-track span { display: block; height: 100%; background: #24a879; border-radius: inherit; transition: width .2s; }
.progress-card > small { color: #9aa7b5; font-size: 10px; }
.lesson-nav { flex: 1; }.nav-group { margin: 15px 9px 7px; color: #9aa6b5; font-size: 10px; font-weight: 750; letter-spacing: .11em; text-transform: uppercase; }
.lesson-link { width: 100%; display: flex; align-items: center; gap: 10px; padding: 9px 10px; border: 0; border-radius: 8px; background: transparent; color: #64748b; text-align: left; font-size: 12px; cursor: pointer; transition: .16s; }.lesson-link:hover { background: #f4f8f6; color: #197e5c; }.lesson-link.selected { background: #eaf6f1; color: #137a56; font-weight: 700; }
.lesson-number { width: 20px; height: 20px; flex: 0 0 20px; display: grid; place-items: center; border: 1px solid #e4eaf0; border-radius: 6px; color: #9aa7b5; font-size: 9px; }.selected .lesson-number { border-color: #b8e4d1; background: #fff; color: #19825e; }
.sidebar-practice { display: flex; align-items: center; gap: 9px; margin-top: 18px; padding: 13px 12px; border: 1px solid #d8eee4; border-radius: 9px; color: #177e5b; text-decoration: none; font-size: 12px; font-weight: 700; }.sidebar-practice span { font-size: 16px; }
.lesson-main { min-width: 0; max-width: 1080px; width: 100%; margin: 0 auto; padding: 30px clamp(24px, 6vw, 82px) 22px; }
.breadcrumbs { display: flex; align-items: center; gap: 10px; color: #9aa6b5; font-size: 11px; }.breadcrumbs strong { color: #526176; font-weight: 600; }
.lesson-heading { padding: 46px 0 31px; border-bottom: 1px solid #e8edf3; }.eyebrow { display: flex; align-items: center; gap: 8px; color: #14805b; font-size: 11px; font-weight: 700; letter-spacing: .06em; }.eyebrow-dot { width: 7px; height: 7px; border-radius: 50%; background: #2ab17d; }.duration { margin-left: 8px; color: #9aa6b5; font-weight: 500; letter-spacing: 0; }
.lesson-heading h1 { margin: 13px 0 9px; color: #17243b; font-size: clamp(30px, 4vw, 42px); line-height: 1.12; letter-spacing: -.045em; }.lesson-heading p { max-width: 690px; margin: 0; color: #728197; font-size: 14px; line-height: 1.8; }
.lesson-content { max-width: 760px; }.article-section { padding: 27px 0 16px; }.article-section h2, .keypoints h2 { margin: 0 0 13px; color: #27374e; font-size: 17px; letter-spacing: -.02em; }.article-section p { margin: 0 0 13px; color: #5d6c80; font-size: 13px; line-height: 1.95; }
.example-card { margin: 10px 0 26px; padding: 19px 21px; border: 1px solid #d9eee5; border-radius: 12px; background: #f0f8f4; }.example-label { color: #17805b; font-size: 10px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }.example-label span { font-size: 14px; }.example-card h3 { margin: 10px 0 6px; color: #264236; font-size: 15px; }.example-card p { margin: 0; color: #61766d; font-size: 12px; line-height: 1.8; }.example-link { display: inline-flex; gap: 9px; align-items: center; margin-top: 13px; color: #16805a; text-decoration: none; font-size: 11px; font-weight: 700; }.example-link:hover { text-decoration: underline; }
.keypoints { padding: 0 0 22px; }.keypoints ul { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }.keypoints li { display: flex; gap: 10px; align-items: flex-start; color: #607086; font-size: 12px; line-height: 1.65; }.check { width: 17px; height: 17px; flex: 0 0 17px; display: grid; place-items: center; margin-top: 1px; border-radius: 50%; background: #e8f6ef; color: #18805b; font-size: 10px; font-weight: 800; }
.lesson-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 19px 0; border-top: 1px solid #e8edf3; }.lesson-actions button { min-height: 37px; padding: 0 14px; border-radius: 8px; font-size: 11px; font-weight: 700; cursor: pointer; transition: .15s; }.previous-button { border: 1px solid #e0e7ee; background: #fff; color: #65748a; }.complete-button { border: 1px solid #cfe9dc; background: #edf8f2; color: #187d59; }.next-button { border: 1px solid #20805e; background: #20805e; color: white; }.lesson-actions button:disabled { opacity: .45; cursor: default; }
.lesson-footer { display: flex; justify-content: space-between; max-width: 760px; padding-top: 15px; border-top: 1px solid #e8edf3; color: #a0aaba; font-size: 10px; }

.concept-sections { display: grid; gap: 19px; padding: 0 0 24px; }
.concept-section { display: grid; grid-template-columns: 29px minmax(0, 1fr); gap: 13px; align-items: start; }
.concept-index { width: 26px; height: 26px; display: grid; place-items: center; margin-top: 1px; border: 1px solid rgba(6, 182, 212, .18); border-radius: 8px; background: rgba(6, 182, 212, .07); color: #087f9c; font-size: 10px; font-weight: 800; }
.concept-section h3 { margin: 0 0 7px; color: #27374e; font-size: 14px; line-height: 1.5; }
.concept-section p { margin: 0; color: #5d6c80; font-size: 13px; line-height: 1.95; }
.self-check { margin: 0 0 25px; padding: 18px 20px; border: 1px solid rgba(139, 92, 246, .18); border-radius: 12px; background: linear-gradient(120deg, rgba(139, 92, 246, .06), rgba(6, 182, 212, .045)); }
.self-check-label { margin-bottom: 8px; color: #6653b5; font-size: 10px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }
.self-check h2 { margin: 0; color: #27374e; font-size: 14px; line-height: 1.7; }
.answer-toggle { margin-top: 12px; padding: 0; border: 0; background: none; color: #087f9c; font-size: 11px; font-weight: 750; cursor: pointer; }
.answer-toggle span { margin-left: 4px; }
.answer-text { margin: 12px 0 0; padding-top: 12px; border-top: 1px solid rgba(139, 92, 246, .14); color: #5d6c80; font-size: 12px; line-height: 1.85; }

.lesson-link { font-size: 13px; }
.breadcrumbs { font-size: 12px; }
.lesson-heading p { font-size: 15px; }
.article-section h2, .keypoints h2 { font-size: 19px; }
.article-section p, .concept-section p { font-size: 15px; line-height: 1.95; }
.concept-section h3 { font-size: 16px; }
.example-card h3 { font-size: 17px; }
.example-card p { font-size: 14px; line-height: 1.9; }
.keypoints li { font-size: 14px; }
.self-check h2 { font-size: 16px; }
.answer-text { font-size: 14px; }
.example-link, .answer-toggle { font-size: 13px; }
.lesson-actions button { font-size: 12px; }
.study-tools { padding-top: 7px; border-top: 1px solid rgba(148, 163, 184, .16); }
.tool-link { width: 100%; display: flex; align-items: center; gap: 10px; padding: 9px 10px; border: 0; border-radius: 8px; background: transparent; color: #64748b; text-align: left; font-size: 13px; cursor: pointer; }
.tool-link:hover, .tool-link.selected { background: linear-gradient(110deg, rgba(6, 182, 212, .12), rgba(139, 92, 246, .08)); color: #087b98; }
.tool-icon { width: 20px; height: 20px; display: grid; place-items: center; border-radius: 6px; background: rgba(6, 182, 212, .09); color: #087f9c; font-size: 11px; font-weight: 800; }
.wrong-count { display: grid; min-width: 20px; height: 20px; place-items: center; margin-left: auto; border-radius: 99px; background: #f0ecff; color: #6b58ba; font-size: 10px; font-weight: 800; }
.assessment-heading { padding: 38px 0 24px; border-bottom: 1px solid #e8edf3; }
.assessment-heading h1 { margin: 13px 0 9px; color: #17243b; font-size: clamp(30px, 4vw, 40px); letter-spacing: -.04em; }
.assessment-heading p { max-width: 720px; margin: 0; color: #68788e; font-size: 15px; line-height: 1.85; }
.exam-meta { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 17px; }
.exam-meta span { padding: 7px 10px; border: 1px solid rgba(148, 163, 184, .2); border-radius: 99px; background: rgba(255, 255, 255, .58); color: #65748a; font-size: 11px; }
.quiz-list, .wrongbook-list { display: grid; gap: 15px; padding: 20px 0; }
.quiz-card { padding: 18px 20px; border: 1px solid rgba(148, 163, 184, .2); border-radius: 12px; background: rgba(255, 255, 255, .74); box-shadow: 0 7px 24px rgba(15, 23, 42, .035); }
.quiz-card.wrong-card { border-color: rgba(239, 130, 130, .38); }
.quiz-card.correct-card { border-color: rgba(6, 182, 212, .28); }
.question-meta { display: flex; justify-content: space-between; gap: 8px; color: #8a98aa; font-size: 11px; }
.question-meta span:first-child { color: #087f9c; font-weight: 750; }
.quiz-card h2 { margin: 11px 0 13px; color: #27374e; font-size: 16px; line-height: 1.65; }
.knowledge-point { margin: -5px 0 12px; color: #7b6ab5; font-size: 11px; }
.lesson-review-link { margin-top: 10px; padding: 0; border: 0; background: transparent; color: #087f9c; font-size: 12px; font-weight: 700; cursor: pointer; }
.lesson-review-link:hover { text-decoration: underline; }
.option-list { display: grid; gap: 8px; }
.option { display: flex; align-items: center; gap: 10px; width: 100%; padding: 10px 12px; border: 1px solid #e5eaf0; border-radius: 8px; background: rgba(255, 255, 255, .74); color: #56667b; text-align: left; font-size: 14px; cursor: pointer; transition: .15s; }
.option:not(:disabled):hover { border-color: rgba(6, 182, 212, .4); background: rgba(6, 182, 212, .045); }
.option.chosen { border-color: rgba(6, 182, 212, .55); background: rgba(6, 182, 212, .08); color: #087b98; }
.option:disabled { cursor: default; }
.option-correct { border-color: rgba(6, 182, 212, .35); background: rgba(6, 182, 212, .07); }
.option-wrong { border-color: rgba(239, 130, 130, .45); background: rgba(239, 130, 130, .07); color: #a34d59; }
.option-letter { width: 22px; height: 22px; flex: 0 0 22px; display: grid; place-items: center; border: 1px solid #e4eaf0; border-radius: 6px; color: #8694a5; font-size: 10px; font-weight: 800; }
.chosen .option-letter { border-color: rgba(6, 182, 212, .35); color: #087f9c; }
.question-feedback, .answer-key { margin-top: 12px; padding: 12px 13px; border-radius: 8px; background: rgba(239, 130, 130, .075); color: #76565a; }
.question-feedback strong, .answer-key strong { color: #884f57; font-size: 12px; }
.question-feedback p, .answer-key p { margin: 6px 0 0; font-size: 13px; line-height: 1.8; }
.question-feedback small { display: inline-block; margin-top: 8px; color: #8d7276; font-size: 10px; }
.question-feedback.is-correct { background: rgba(6, 182, 212, .07); color: #526c75; }
.question-feedback.is-correct strong { color: #087f9c; }
.score-panel { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin: 0 0 22px; padding: 20px; border: 1px solid rgba(6, 182, 212, .23); border-radius: 12px; background: linear-gradient(120deg, rgba(6, 182, 212, .09), rgba(139, 92, 246, .075)); }
.score-panel span, .score-panel strong, .score-panel p { display: block; }
.score-panel span { color: #64748b; font-size: 12px; }.score-panel strong { margin-top: 4px; color: #087f9c; font-size: 30px; }.score-panel strong small { color: #8a98aa; font-size: 14px; font-weight: 500; }.score-panel p { margin: 5px 0 0; color: #64748b; font-size: 12px; }
.quiz-actions { display: flex; align-items: center; justify-content: space-between; padding: 14px 0 24px; border-top: 1px solid #e8edf3; color: #7d8ba0; font-size: 12px; }
.quiz-actions button:disabled { opacity: .45; cursor: default; }
.empty-wrongbook { display: grid; justify-items: center; padding: 72px 20px; text-align: center; }.empty-wrongbook > span { width: 46px; height: 46px; display: grid; place-items: center; border-radius: 50%; background: rgba(6, 182, 212, .1); color: #087f9c; font-size: 20px; }.empty-wrongbook h2 { margin: 15px 0 5px; color: #27374e; font-size: 19px; }.empty-wrongbook p { margin: 0 0 17px; color: #718096; font-size: 14px; line-height: 1.7; }
.review-button { justify-self: start; margin-bottom: 22px; padding: 10px 16px; border-radius: 8px; cursor: pointer; }
.self-check-options { display: grid; gap: 8px; margin-top: 13px; }
.self-check-option { display: flex; align-items: center; gap: 10px; width: 100%; padding: 9px 11px; border: 1px solid rgba(148, 163, 184, .22); border-radius: 8px; background: rgba(255, 255, 255, .68); color: #56667b; text-align: left; font-size: 13px; line-height: 1.6; cursor: pointer; transition: .15s; }
.self-check-option:not(:disabled):hover { border-color: rgba(6, 182, 212, .42); background: rgba(6, 182, 212, .05); }
.self-check-option.chosen { border-color: rgba(6, 182, 212, .5); background: rgba(6, 182, 212, .08); color: #087b98; }
.self-check-option.answer-correct { border-color: rgba(6, 182, 212, .38); background: rgba(6, 182, 212, .08); }
.self-check-option.answer-wrong { border-color: rgba(239, 130, 130, .45); background: rgba(239, 130, 130, .075); color: #a34d59; }
.self-check-option:disabled { cursor: default; }
.self-check-actions { margin-top: 9px; }
.answer-toggle:disabled { opacity: .45; cursor: default; }
.self-check-feedback { margin-top: 12px; padding: 11px 13px; border-radius: 8px; background: rgba(239, 130, 130, .075); color: #76565a; }
.self-check-feedback strong { color: #884f57; font-size: 12px; }
.self-check-feedback p { margin: 5px 0 0; font-size: 13px; line-height: 1.8; }
.self-check-feedback.is-correct { background: rgba(6, 182, 212, .07); color: #526c75; }
.self-check-feedback.is-correct strong { color: #087f9c; }
.score-actions { display: flex; gap: 9px; flex-wrap: wrap; }
.score-actions button { padding: 9px 13px; border-radius: 8px; cursor: pointer; }
@media (max-width: 600px) { .score-panel { align-items: flex-start; flex-direction: column; }.score-actions { width: 100%; }.score-actions button { flex: 1; } }

.learning-page {
  background:
    radial-gradient(circle at 82% 0%, rgba(139, 92, 246, .055), transparent 30%),
    radial-gradient(circle at 0% 0%, rgba(6, 182, 212, .07), transparent 32%),
    linear-gradient(180deg, #f2f7fe 0%, #eff3fa 45%, #f6f9ff 100%);
}
.course-sidebar { background: rgba(255, 255, 255, .84); border-right-color: rgba(148, 163, 184, .2); box-shadow: 8px 0 28px rgba(15, 23, 42, .025); backdrop-filter: blur(18px); }
.home-mark { background: linear-gradient(135deg, rgba(6, 182, 212, .14), rgba(139, 92, 246, .12)); color: #087f9c; }
.progress-caption strong { color: #087f9c; }
.progress-track { background: #e7edf5; }
.progress-track span { background: linear-gradient(90deg, #06b6d4, #8b5cf6); }
.lesson-link:hover { background: rgba(6, 182, 212, .07); color: #087f9c; }
.lesson-link.selected { background: linear-gradient(110deg, rgba(6, 182, 212, .12), rgba(139, 92, 246, .08)); color: #087b98; }
.selected .lesson-number { border-color: rgba(6, 182, 212, .24); color: #087f9c; }
.sidebar-practice { border-color: rgba(6, 182, 212, .22); background: rgba(6, 182, 212, .045); color: #087f9c; }
.eyebrow { color: #087f9c; }
.eyebrow-dot { background: #06b6d4; box-shadow: 0 0 0 4px rgba(6, 182, 212, .1); }
.example-card { border-color: rgba(6, 182, 212, .2); background: linear-gradient(120deg, rgba(6, 182, 212, .075), rgba(139, 92, 246, .055)); box-shadow: 0 8px 24px rgba(15, 23, 42, .035); }
.example-label, .example-link { color: #087f9c; }
.example-card h3 { color: #264052; }
.example-card p { color: #617286; }
.check { background: rgba(6, 182, 212, .1); color: #087f9c; }
.previous-button { background: rgba(255, 255, 255, .78); border-color: rgba(148, 163, 184, .28); }
.complete-button { background: rgba(6, 182, 212, .08); border-color: rgba(6, 182, 212, .2); color: #087f9c; }
.next-button { border-color: #0e7490; background: linear-gradient(135deg, #0891b2, #6366f1); box-shadow: 0 5px 14px rgba(8, 145, 178, .16); }

@media (max-width: 760px) { .learning-page { grid-template-columns: 1fr; }.course-sidebar { position: static; height: auto; min-height: 0; padding: 12px 14px; border-right: 0; border-bottom: 1px solid #e8edf3; }.sidebar-home { padding-bottom: 12px; }.progress-card { margin-bottom: 9px; }.lesson-nav { display: flex; gap: 5px; overflow-x: auto; padding: 5px 0; }.nav-group { display: none; }.lesson-link { width: auto; flex: 0 0 auto; white-space: nowrap; }.sidebar-practice { margin-top: 8px; }.lesson-main { padding: 22px 20px; }.lesson-heading { padding: 30px 0 23px; } }
</style>

