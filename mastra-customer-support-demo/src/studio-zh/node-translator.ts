import { translateText } from './translations.ts';

export type TextTranslationState = {
  original: string;
  translated: string;
};

export function translateNodeValue(
  currentValue: string,
  previousState?: TextTranslationState,
): TextTranslationState {
  const original =
    previousState && currentValue === previousState.translated
      ? previousState.original
      : currentValue;
  const translatedText = translateText(original);
  const translated = original.replace(original.trim(), translatedText);

  return {
    original,
    translated,
  };
}
