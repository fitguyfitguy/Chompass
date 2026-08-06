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
import { seedDemo } from "./demo-seed.js";
import { startDemo } from "./demo-driver.js";

setActiveLocale("en");

await seedDemo();
startDemo();
