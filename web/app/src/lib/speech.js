// @ts-check
/**
 * Web Speech API helper for Add Food / Coach voice capture.
 * @returns {{supported: boolean, start: (onResult: (text: string) => void, onError?: (msg: string) => void, opts?: { lang?: string }) => () => void}}
 */
export function createSpeechCapture() {
  const SR = /** @type {any} */ (window).SpeechRecognition || /** @type {any} */ (window).webkitSpeechRecognition;
  if (!SR) {
    return {
      supported: false,
      start() {
        return () => {};
      },
    };
  }

  return {
    supported: true,
    start(onResult, onError, opts) {
      const rec = new SR();
      rec.continuous = false;
      rec.interimResults = false;
      rec.lang = opts?.lang || navigator.language || "en-US";
      rec.onresult = (ev) => {
        const text = ev.results?.[0]?.[0]?.transcript;
        if (text) onResult(String(text));
      };
      rec.onerror = (ev) => {
        onError?.(ev.error || "speech error");
      };
      try {
        rec.start();
      } catch (err) {
        onError?.(err instanceof Error ? err.message : String(err));
      }
      return () => {
        try {
          rec.stop();
        } catch {
          /* ignore */
        }
      };
    },
  };
}
