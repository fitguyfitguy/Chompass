// @ts-check
// Canvas-based resize/JPEG-encode for photo analysis — keeps upload payloads
// small and normalizes format before it's attached to a coach message.

/**
 * @param {File} file
 * @param {number} [maxDim]
 * @param {number} [quality]
 * @returns {Promise<{mimeType: string, base64: string}>}
 */
export async function fileToJpegBase64(file, maxDim = 1024, quality = 0.82) {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, maxDim / Math.max(bitmap.width, bitmap.height));
  const w = Math.max(1, Math.round(bitmap.width * scale));
  const h = Math.max(1, Math.round(bitmap.height * scale));
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext("2d");
  ctx.drawImage(bitmap, 0, 0, w, h);
  bitmap.close?.();
  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("JPEG encode failed"))), "image/jpeg", quality);
  });
  const buf = await blob.arrayBuffer();
  return { mimeType: "image/jpeg", base64: arrayBufferToBase64(buf) };
}

/** @param {ArrayBuffer} buf */
function arrayBufferToBase64(buf) {
  let binary = "";
  const bytes = new Uint8Array(buf);
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}
