// @ts-check
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

/** Repo root: web/app/src/lib → ../../../../ */
export const REPO_ROOT = fileURLToPath(new URL("../../../../", import.meta.url));

/** @param {string} name file under testdata/parity/ */
export function loadParityFixture(name) {
  return JSON.parse(readFileSync(`${REPO_ROOT}testdata/parity/${name}`, "utf8"));
}
