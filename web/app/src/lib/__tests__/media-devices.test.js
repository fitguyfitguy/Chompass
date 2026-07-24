// @ts-check
import test from "node:test";
import assert from "node:assert/strict";
import {
  buildVideoConstraints,
  cameraErrorMessage,
  frameCropRect,
  mealCaptureCropRatio,
  nextVideoDeviceId,
  prefersMobileCameraUx,
  shouldUseNativeCaptureHint,
} from "../media-devices.js";

/**
 * @param {Record<string, boolean>} matches
 */
function fakeMatchMedia(matches) {
  return {
    /** @param {string} q */
    matchMedia(q) {
      return {
        matches: Boolean(matches[q]),
        media: q,
        onchange: null,
        addListener() {},
        removeListener() {},
        addEventListener() {},
        removeEventListener() {},
        dispatchEvent() {
          return false;
        },
      };
    },
  };
}

test("prefersMobileCameraUx_desktopFinePointer", () => {
  const env = fakeMatchMedia({
    "(pointer: fine)": true,
    "(hover: hover)": true,
    "(pointer: coarse)": false,
    "(max-width: 720px)": false,
  });
  assert.equal(prefersMobileCameraUx(env), false);
  assert.equal(shouldUseNativeCaptureHint(env), false);
});

test("prefersMobileCameraUx_phoneCoarsePointer", () => {
  const env = fakeMatchMedia({
    "(pointer: fine)": false,
    "(hover: hover)": false,
    "(pointer: coarse)": true,
    "(max-width: 720px)": true,
  });
  assert.equal(prefersMobileCameraUx(env), true);
  assert.equal(shouldUseNativeCaptureHint(env), true);
});

test("buildVideoConstraints_mobileMealUsesFacingModeAndPortrait", () => {
  const c = buildVideoConstraints({ purpose: "meal", mobileUx: true });
  assert.deepEqual(c.facingMode, { ideal: "environment" });
  assert.deepEqual(c.height, { ideal: 1720 });
});

test("buildVideoConstraints_desktopWebcamOmitsFacingMode", () => {
  const c = buildVideoConstraints({ purpose: "meal", mobileUx: false });
  assert.equal(c.facingMode, undefined);
  assert.deepEqual(c.width, { ideal: 1280 });
  assert.deepEqual(c.height, { ideal: 720 });
});

test("buildVideoConstraints_deviceIdExactOverridesFacing", () => {
  const c = buildVideoConstraints({
    purpose: "barcode",
    mobileUx: true,
    deviceId: "cam-2",
  });
  assert.deepEqual(c.deviceId, { exact: "cam-2" });
  assert.equal(c.facingMode, undefined);
});

test("cameraErrorMessage_mapsPermissionAndMissingDevice", () => {
  assert.match(cameraErrorMessage({ name: "NotAllowedError", message: "x" }), /permission denied/i);
  assert.match(cameraErrorMessage({ name: "NotFoundError", message: "x" }), /no camera found/i);
  assert.match(cameraErrorMessage({ name: "NotReadableError", message: "x" }), /in use/i);
  assert.equal(cameraErrorMessage(new Error("weird")), "weird");
});

test("frameCropRect_fullFrameWhenNoRatio", () => {
  assert.deepEqual(frameCropRect(1280, 720, null), { sx: 0, sy: 0, sw: 1280, sh: 720 });
});

test("frameCropRect_centersPortraitCrop", () => {
  // 16:9 frame cropped to 3:4 → width shrinks, centered.
  const r = frameCropRect(1600, 900, 3 / 4);
  assert.equal(r.sh, 900);
  assert.equal(r.sw, Math.round(900 * (3 / 4)));
  assert.equal(r.sx, Math.round((1600 - r.sw) / 2));
  assert.equal(r.sy, 0);
});

test("mealCaptureCropRatio_mobileVsDesktop", () => {
  assert.equal(mealCaptureCropRatio({ mobileUx: true }), 3 / 4);
  assert.equal(mealCaptureCropRatio({ mobileUx: false }), null);
});

test("nextVideoDeviceId_cycles", () => {
  const devices = /** @type {MediaDeviceInfo[]} */ ([
    { deviceId: "a", kind: "videoinput", label: "A", groupId: "g1", toJSON() { return {}; } },
    { deviceId: "b", kind: "videoinput", label: "B", groupId: "g2", toJSON() { return {}; } },
  ]);
  assert.equal(nextVideoDeviceId(devices, "a"), "b");
  assert.equal(nextVideoDeviceId(devices, "b"), "a");
  assert.equal(nextVideoDeviceId(devices, null), "a");
  assert.equal(nextVideoDeviceId(devices.slice(0, 1), "a"), "a");
  assert.equal(nextVideoDeviceId([], "a"), null);
});
