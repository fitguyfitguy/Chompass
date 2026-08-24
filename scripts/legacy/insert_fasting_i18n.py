#!/usr/bin/env python3
"""Insert the fasting i18n keys into every PWA catalog after diary.water.

One-shot script for docs/local/PLAN_FASTING_TRACKER.md Phase 6 (deleted at the
release that ships the work). Translations follow the existing catalog voice
and TRANSLATION_GUIDE conventions; the {elapsed}/{goal} placeholders are kept
verbatim in every locale.
"""
import io, json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
CAT = ROOT / "web/app/src/lib/i18n/catalogs"

# locale -> {key: translation}
KEYS = {
    "ar": {
        "diary.fasting": "صيام",
        "diary.fasting_idle": "لا يوجد صيام نشط",
        "diary.fasting_start": "بدء الصيام",
        "diary.fasting_stop": "إنهاء الصيام",
        "diary.fasting_goal_reached": "تم بلوغ الهدف",
        "diary.fasting_goal_hint": "{elapsed} · الهدف {goal} ساعة",
    },
    "az": {
        "diary.fasting": "Oruc",
        "diary.fasting_idle": "Aktiv oruc yoxdur",
        "diary.fasting_start": "Oruca başla",
        "diary.fasting_stop": "Orucu bitir",
        "diary.fasting_goal_reached": "Məqsədə çatdı",
        "diary.fasting_goal_hint": "{elapsed} · məqsəd {goal} saat",
    },
    "de": {
        "diary.fasting": "Fasten",
        "diary.fasting_idle": "Kein aktives Fasten",
        "diary.fasting_start": "Fasten starten",
        "diary.fasting_stop": "Fasten beenden",
        "diary.fasting_goal_reached": "Ziel erreicht",
        "diary.fasting_goal_hint": "{elapsed} · Ziel {goal} h",
    },
    "es": {
        "diary.fasting": "Ayuno",
        "diary.fasting_idle": "Sin ayuno activo",
        "diary.fasting_start": "Iniciar ayuno",
        "diary.fasting_stop": "Terminar ayuno",
        "diary.fasting_goal_reached": "Objetivo alcanzado",
        "diary.fasting_goal_hint": "{elapsed} · objetivo {goal} h",
    },
    "fr": {
        "diary.fasting": "Jeûne",
        "diary.fasting_idle": "Aucun jeûne en cours",
        "diary.fasting_start": "Démarrer le jeûne",
        "diary.fasting_stop": "Arrêter le jeûne",
        "diary.fasting_goal_reached": "Objectif atteint",
        "diary.fasting_goal_hint": "{elapsed} · objectif {goal} h",
    },
    "hi": {
        "diary.fasting": "उपवास",
        "diary.fasting_idle": "कोई सक्रिय उपवास नहीं",
        "diary.fasting_start": "उपवास शुरू करें",
        "diary.fasting_stop": "उपवास समाप्त करें",
        "diary.fasting_goal_reached": "लक्ष्य प्राप्त हुआ",
        "diary.fasting_goal_hint": "{elapsed} · लक्ष्य {goal} घंटे",
    },
    "it": {
        "diary.fasting": "Digiuno",
        "diary.fasting_idle": "Nessun digiuno attivo",
        "diary.fasting_start": "Inizia digiuno",
        "diary.fasting_stop": "Termina digiuno",
        "diary.fasting_goal_reached": "Obiettivo raggiunto",
        "diary.fasting_goal_hint": "{elapsed} · obiettivo {goal} h",
    },
    "ja": {
        "diary.fasting": "ファスティング",
        "diary.fasting_idle": "進行中のファスティングはありません",
        "diary.fasting_start": "ファスティング開始",
        "diary.fasting_stop": "ファスティング終了",
        "diary.fasting_goal_reached": "目標達成",
        "diary.fasting_goal_hint": "{elapsed} · 目標 {goal} 時間",
    },
    "ko": {
        "diary.fasting": "단식",
        "diary.fasting_idle": "진행 중인 단식 없음",
        "diary.fasting_start": "단식 시작",
        "diary.fasting_stop": "단식 종료",
        "diary.fasting_goal_reached": "목표 달성",
        "diary.fasting_goal_hint": "{elapsed} · 목표 {goal}시간",
    },
    "nl": {
        "diary.fasting": "Vasten",
        "diary.fasting_idle": "Geen actief vasten",
        "diary.fasting_start": "Vasten starten",
        "diary.fasting_stop": "Vasten stoppen",
        "diary.fasting_goal_reached": "Doel bereikt",
        "diary.fasting_goal_hint": "{elapsed} · doel {goal} u",
    },
    "pl": {
        "diary.fasting": "Post",
        "diary.fasting_idle": "Brak aktywnego postu",
        "diary.fasting_start": "Rozpocznij post",
        "diary.fasting_stop": "Zakończ post",
        "diary.fasting_goal_reached": "Osiągnięto cel",
        "diary.fasting_goal_hint": "{elapsed} · cel {goal} h",
    },
    "pt-BR": {
        "diary.fasting": "Jejum",
        "diary.fasting_idle": "Sem jejum ativo",
        "diary.fasting_start": "Iniciar jejum",
        "diary.fasting_stop": "Encerrar jejum",
        "diary.fasting_goal_reached": "Meta atingida",
        "diary.fasting_goal_hint": "{elapsed} · meta {goal} h",
    },
    "ro": {
        "diary.fasting": "Post",
        "diary.fasting_idle": "Niciun post activ",
        "diary.fasting_start": "Începe postul",
        "diary.fasting_stop": "Oprește postul",
        "diary.fasting_goal_reached": "Obiectiv atins",
        "diary.fasting_goal_hint": "{elapsed} · obiectiv {goal} h",
    },
    "ru": {
        "diary.fasting": "Пост",
        "diary.fasting_idle": "Нет активного поста",
        "diary.fasting_start": "Начать пост",
        "diary.fasting_stop": "Завершить пост",
        "diary.fasting_goal_reached": "Цель достигнута",
        "diary.fasting_goal_hint": "{elapsed} · цель {goal} ч",
    },
    "tr": {
        "diary.fasting": "Oruç",
        "diary.fasting_idle": "Aktif oruç yok",
        "diary.fasting_start": "Orucu başlat",
        "diary.fasting_stop": "Orucu bitir",
        "diary.fasting_goal_reached": "Hedefe ulaşıldı",
        "diary.fasting_goal_hint": "{elapsed} · hedef {goal} saat",
    },
    "uk": {
        "diary.fasting": "Пост",
        "diary.fasting_idle": "Немає активного посту",
        "diary.fasting_start": "Почати пост",
        "diary.fasting_stop": "Завершити пост",
        "diary.fasting_goal_reached": "Мета досягнута",
        "diary.fasting_goal_hint": "{elapsed} · мета {goal} год",
    },
    "zh-CN": {
        "diary.fasting": "间歇性禁食",
        "diary.fasting_idle": "无进行中的禁食",
        "diary.fasting_start": "开始禁食",
        "diary.fasting_stop": "结束禁食",
        "diary.fasting_goal_reached": "已达目标",
        "diary.fasting_goal_hint": "{elapsed} · 目标 {goal} 小时",
    },
}

LINE_RE = re.compile(r'^(\s*)"diary\.water": "[^"]*",(\s*)$')


def main() -> int:
    for locale, keys in KEYS.items():
        path = CAT / f"{locale}.js"
        text = path.read_text(encoding="utf-8")
        if '"diary.fasting"' in text:
            print(f"skip {locale}: fasting keys already present")
            continue
        lines = text.splitlines()
        out = []
        inserted = False
        for line in lines:
            out.append(line)
            m = LINE_RE.match(line)
            if m and not inserted:
                indent, _ = m.group(1), m.group(2)
                for key, value in keys.items():
                    out.append(f'{indent}"{key}": "{value}",')
                inserted = True
        if not inserted:
            print(f"ERROR {locale}: diary.water anchor not found")
            return 1
        path.write_text("\n".join(out) + "\n", encoding="utf-8")
        print(f"ok {locale}: inserted {len(keys)} keys")
    return 0


if __name__ == "__main__":
    sys.exit(main())
