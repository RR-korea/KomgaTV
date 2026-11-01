// 보기 설정 항목 (Android T14 ViewSettingsForm): 리더의 보기 설정 패널과 서버 설정의 "보기 기본값"이 같이 쓴다.
// 항목은 한 줄(왼쪽 이름, 오른쪽 ‹ 값 ›) — 1080p 한 화면에 스크롤 없이 들어간다. 좌/우 = 값 바꾸기, OK = 다음 값.
import { fmt, t } from '../i18n';
import { FOCUSABLE_CLASS } from '../platform/focus';
import { MAX_ZOOM_PERCENT, MIN_ZOOM_PERCENT } from '../reader/layout';
import {
  DIRECTIONS,
  ENHANCEMENTS,
  PADDING_STEPS,
  PAGE_LAYOUTS,
  READER_TYPES,
  type ReaderSettings,
  SCALE_TYPES,
  SPACING_STEPS,
} from '../state/readerPrefs';

const ZOOM_STEPS: number[] = [];
for (let percent = MIN_ZOOM_PERCENT; percent <= MAX_ZOOM_PERCENT; percent++) ZOOM_STEPS.push(percent);

export type ViewKey =
  | 'opt-type'
  | 'opt-layout'
  | 'opt-offset'
  | 'opt-direction'
  | 'opt-scale'
  | 'opt-padding'
  | 'opt-spacing'
  | 'opt-stretch'
  | 'opt-enhance'
  | 'opt-zoom';

export interface ViewOption {
  key: ViewKey;
  label: string;
  values: readonly unknown[];
  labels: string[];
  value: unknown;
  enabled: boolean;
  /** 끝에서 처음으로 돌아가지 않는 항목 (확대 %) */
  clamp?: boolean;
}

/**
 * 보일 항목. reader-paged / reader-continuous = 리더 패널(지금 종류의 항목만, 한 쪽 밀기는 세션 값),
 * defaults = 서버 설정의 보기 기본값(모든 항목, 한 쪽 밀기 없음).
 */
export function viewOptions(
  s: ReaderSettings,
  mode: 'reader-paged' | 'reader-continuous' | 'defaults',
  offset = false,
): ViewOption[] {
  const onOff = [t.off, t.on];
  const type: ViewOption = {
    key: 'opt-type',
    label: t.readerType,
    values: READER_TYPES,
    labels: [t.readerTypePaged, t.readerTypeContinuous],
    value: s.readerType,
    enabled: true,
  };
  const layout: ViewOption = {
    key: 'opt-layout',
    label: t.readerLayout,
    values: PAGE_LAYOUTS,
    labels: [t.readerLayoutSingle, t.readerLayoutDouble, t.readerLayoutDoubleNoCover],
    value: s.pageLayout,
    enabled: true,
  };
  const offsetRow: ViewOption = {
    key: 'opt-offset',
    label: t.readerOffset,
    values: [false, true],
    labels: onOff,
    value: offset,
    enabled: s.pageLayout !== 'SINGLE_PAGE',
  };
  const direction: ViewOption = {
    key: 'opt-direction',
    label: t.readerDirection,
    values: DIRECTIONS,
    labels: [t.readerDirectionLtr, t.readerDirectionRtl],
    value: s.readingDirection,
    enabled: true,
  };
  const scale: ViewOption = {
    key: 'opt-scale',
    label: t.readerScale,
    values: SCALE_TYPES,
    labels: [t.readerScaleScreen, t.readerScaleFitWidth, t.readerScaleFitHeight, t.readerScaleOriginal],
    value: s.scaleType,
    enabled: true,
  };
  const padding: ViewOption = {
    key: 'opt-padding',
    label: t.readerPadding,
    values: PADDING_STEPS,
    labels: PADDING_STEPS.map((p) => fmt(t.viewPercent, Math.round(p * 100))),
    value: s.continuousPadding,
    enabled: true,
  };
  const spacing: ViewOption = {
    key: 'opt-spacing',
    label: t.readerSpacing,
    values: SPACING_STEPS,
    labels: SPACING_STEPS.map((v) => fmt(t.viewDp, v)),
    value: s.continuousPageSpacing,
    enabled: true,
  };
  const stretch: ViewOption = {
    key: 'opt-stretch',
    label: t.readerStretch,
    values: [false, true],
    labels: onOff,
    value: s.stretchToFit,
    enabled: true,
  };
  const enhance: ViewOption = {
    key: 'opt-enhance',
    label: t.viewEnhancement,
    values: ENHANCEMENTS,
    labels: [t.viewEnhancementOff, t.viewEnhancementSmooth, t.viewEnhancementSharpen, t.viewEnhancementSharpenStrong],
    value: s.imageEnhancement,
    enabled: true,
  };
  // 크기 맞춤 결과를 1%씩 더 확대 — 길게 누르면 키 반복으로 빨리 바뀐다. 작품 설정(리더)에서만
  const zoom: ViewOption = {
    key: 'opt-zoom',
    label: t.viewZoom,
    values: ZOOM_STEPS,
    labels: ZOOM_STEPS.map((v) => fmt(t.viewPercent, v)),
    value: s.zoomPercent,
    enabled: true,
    clamp: true,
  };
  switch (mode) {
    case 'reader-paged':
      return [type, layout, offsetRow, direction, scale, zoom, stretch, enhance];
    case 'reader-continuous':
      return [type, padding, spacing, stretch, enhance];
    default:
      return [type, layout, direction, scale, padding, spacing, stretch, enhance];
  }
}

/** 항목 값 → 설정 (한 쪽 밀기는 설정이 아니라 세션 값이라 그대로) */
export function applyViewOption(s: ReaderSettings, key: ViewKey, value: unknown): ReaderSettings {
  switch (key) {
    case 'opt-type':
      return { ...s, readerType: value as ReaderSettings['readerType'] };
    case 'opt-layout':
      return { ...s, pageLayout: value as ReaderSettings['pageLayout'] };
    case 'opt-direction':
      return { ...s, readingDirection: value as ReaderSettings['readingDirection'] };
    case 'opt-scale':
      return { ...s, scaleType: value as ReaderSettings['scaleType'] };
    case 'opt-padding':
      return { ...s, continuousPadding: value as number };
    case 'opt-spacing':
      return { ...s, continuousPageSpacing: value as number };
    case 'opt-stretch':
      return { ...s, stretchToFit: value as boolean };
    case 'opt-enhance':
      return { ...s, imageEnhancement: value as ReaderSettings['imageEnhancement'] };
    case 'opt-zoom':
      return { ...s, zoomPercent: value as number };
    default:
      return s;
  }
}

/** 순환해서 다음/이전 값. 꺼진 항목이면 null */
export function cycledValue(option: ViewOption, delta: number): unknown {
  if (!option.enabled) return null;
  const at = option.values.indexOf(option.value);
  if (option.clamp) {
    const to = Math.min(option.values.length - 1, Math.max(0, at + delta));
    return to === at ? null : option.values[to];
  }
  return option.values[(at + delta + option.values.length) % option.values.length];
}

export function ViewRows({ options, onCycle }: { options: ViewOption[]; onCycle: (key: ViewKey, delta: number) => void }) {
  return (
    <div class="view-rows">
      {options.map((option) => (
        <div
          key={option.key}
          class={`${FOCUSABLE_CLASS} view-row${option.enabled ? '' : ' disabled'}`}
          tabIndex={-1}
          data-key={option.key}
          data-view-row="1"
          onClick={() => onCycle(option.key, 1)}
        >
          <span class="view-row-label">{option.label}</span>
          <span class="view-row-value">
            {option.enabled && <span class="view-row-arrow">‹</span>}
            <span>{option.labels[option.values.indexOf(option.value)] ?? ''}</span>
            {option.enabled && <span class="view-row-arrow">›</span>}
          </span>
        </div>
      ))}
    </div>
  );
}
