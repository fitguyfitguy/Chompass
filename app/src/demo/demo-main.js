// @ts-check
// Demo shell entry for the marketing hero (web/app/demo.html). Imports only
// the views the hero beats need (no coach/settings/measurements, no sw.js,
// no install/update prompts), seeds the throwaway demo database, and starts
// the beat driver.
import "../components/diary-view.js";
import "../components/analyze-view.js";
import "../components/entry-form.js";
import "../components/barcode-scanner.js";
import "../components/progress-view.js";
import { setActiveLocale } from "../lib/i18n/index.js";
import { startDemo } from "./demo-driver.js";

setActiveLocale("en");

// startDemo renders the home immediately, seeds the throwaway demo database
// in the background, and begins the beat loop — the hero stage reveals as
// soon as the home has painted instead of after the full seed.
startDemo();
