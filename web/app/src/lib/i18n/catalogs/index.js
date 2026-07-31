// @ts-check
import { en } from "./en.js";
import { de } from "./de.js";
import { es } from "./es.js";
import { fr } from "./fr.js";
import { ru } from "./ru.js";
import { nl } from "./nl.js";
import { it } from "./it.js";
import { ar } from "./ar.js";
import { ja } from "./ja.js";
import { ko } from "./ko.js";
import { hi } from "./hi.js";
import { ro } from "./ro.js";
import { az } from "./az.js";
import { pt_BR } from "./pt-BR.js";
import { zh_CN } from "./zh-CN.js";

/** @type {Readonly<Record<string, Readonly<Record<string, string>>>>} */
export const CATALOGS = Object.freeze({
  en,
  de,
  es,
  fr,
  ru,
  nl,
  it,
  ar,
  ja,
  ko,
  hi,
  ro,
  az,
  "pt-BR": pt_BR,
  "zh-CN": zh_CN,
});
