#!/usr/bin/env python3
"""Insert the fasting follow-up strings (presets + start reminder) into every
Android locale pack (before </resources>). One-shot; deleted at the shipping
release together with insert_fasting_android_strings.py."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
RES = ROOT / "android/app/src/main/res"

# locale-dir -> {name: value} (XML-escaped apostrophes, %N$ placeholders match EN).
STRINGS = {
    "values-ar": {
        "fasting_preset_detail": "%1$d ساعة صيام · %2$d ساعة نافذة أكل",
        "fasting_window_opens_in": "تفتح نافذة الأكل خلال %1$s",
        "settings_fasting_section_reminders": "التذكيرات",
        "settings_fasting_start_reminder": "تذكير البدء",
        "settings_fasting_start_reminder_time": "وقت التذكير",
        "settings_fasting_start_reminder_help": "تذكير يومي لبدء الصيام التالي. يُتخطّى إذا كان صيام نشط بالفعل.",
        "notif_fasting_start_title": "ابدأ صيامك",
        "notif_fasting_start_text": "ابدأ صيامك الآن.",
        "notif_fasting_start_text_goal": "ابدأ صيامك البالغ %1$d ساعة الآن.",
    },
    "values-az": {
        "fasting_preset_detail": "%1$d saat oruc · %2$d saat yemək pəncərəsi",
        "fasting_window_opens_in": "Yemək pəncərəsi %1$s sonra açılır",
        "settings_fasting_section_reminders": "Xatırlatmalar",
        "settings_fasting_start_reminder": "Başlama xatırlatması",
        "settings_fasting_start_reminder_time": "Xatırlatma vaxtı",
        "settings_fasting_start_reminder_help": "Növbəti oruca başlamaq üçün gündəlik xatırlatma. Artıq aktiv oruc varsa keçilir.",
        "notif_fasting_start_title": "Oruca başla",
        "notif_fasting_start_text": "İndi oruca başla.",
        "notif_fasting_start_text_goal": "%1$d saatlıq orucuna indi başla.",
    },
    "values-de": {
        "fasting_preset_detail": "%1$d h Fasten · %2$d h Essensfenster",
        "fasting_window_opens_in": "Essensfenster öffnet in %1$s",
        "settings_fasting_section_reminders": "Erinnerungen",
        "settings_fasting_start_reminder": "Start-Erinnerung",
        "settings_fasting_start_reminder_time": "Erinnerungszeit",
        "settings_fasting_start_reminder_help": "Eine tägliche Erinnerung, dein nächstes Fasten zu starten. Übersprungen, wenn bereits gefastet wird.",
        "notif_fasting_start_title": "Starte dein Fasten",
        "notif_fasting_start_text": "Starte jetzt dein Fasten.",
        "notif_fasting_start_text_goal": "Starte jetzt dein %1$d-stündiges Fasten.",
    },
    "values-es": {
        "fasting_preset_detail": "%1$d h de ayuno · %2$d h de ventana de comida",
        "fasting_window_opens_in": "La ventana de comida abre en %1$s",
        "settings_fasting_section_reminders": "Recordatorios",
        "settings_fasting_start_reminder": "Recordatorio de inicio",
        "settings_fasting_start_reminder_time": "Hora del recordatorio",
        "settings_fasting_start_reminder_help": "Un aviso diario para empezar tu próximo ayuno. Se omite si ya hay un ayuno activo.",
        "notif_fasting_start_title": "Empieza tu ayuno",
        "notif_fasting_start_text": "Empieza tu ayuno ahora.",
        "notif_fasting_start_text_goal": "Empieza ahora tu ayuno de %1$d h.",
    },
    "values-fr": {
        "fasting_preset_detail": "%1$d h de jeûne · %2$d h de fenêtre de repas",
        "fasting_window_opens_in": "La fenêtre de repas s\'ouvre dans %1$s",
        "settings_fasting_section_reminders": "Rappels",
        "settings_fasting_start_reminder": "Rappel de début",
        "settings_fasting_start_reminder_time": "Heure du rappel",
        "settings_fasting_start_reminder_help": "Un rappel quotidien pour démarrer votre prochain jeûne. Ignoré si un jeûne est déjà en cours.",
        "notif_fasting_start_title": "Démarrez votre jeûne",
        "notif_fasting_start_text": "Démarrez votre jeûne maintenant.",
        "notif_fasting_start_text_goal": "Démarrez maintenant votre jeûne de %1$d h.",
    },
    "values-hi": {
        "fasting_preset_detail": "%1$d घंटे उपवास · %2$d घंटे खाने की विंडो",
        "fasting_window_opens_in": "खाने की विंडो %1$s में खुलेगी",
        "settings_fasting_section_reminders": "अनुस्मारक",
        "settings_fasting_start_reminder": "शुरुआत अनुस्मारक",
        "settings_fasting_start_reminder_time": "अनुस्मारक समय",
        "settings_fasting_start_reminder_help": "अगला उपवास शुरू करने के लिए दैनिक अनुस्मारक। उपवास पहले से चल रहा हो तो छोड़ दिया जाता है।",
        "notif_fasting_start_title": "अपना उपवास शुरू करें",
        "notif_fasting_start_text": "अभी अपना उपवास शुरू करें।",
        "notif_fasting_start_text_goal": "अभी अपना %1$d घंटे का उपवास शुरू करें।",
    },
    "values-it": {
        "fasting_preset_detail": "%1$d h di digiuno · %2$d h di finestra dei pasti",
        "fasting_window_opens_in": "La finestra dei pasti si apre tra %1$s",
        "settings_fasting_section_reminders": "Promemoria",
        "settings_fasting_start_reminder": "Promemoria di inizio",
        "settings_fasting_start_reminder_time": "Ora del promemoria",
        "settings_fasting_start_reminder_help": "Un promemoria quotidiano per iniziare il prossimo digiuno. Saltato se un digiuno è già in corso.",
        "notif_fasting_start_title": "Inizia il tuo digiuno",
        "notif_fasting_start_text": "Inizia subito il tuo digiuno.",
        "notif_fasting_start_text_goal": "Inizia subito il tuo digiuno di %1$d h.",
    },
    "values-ja": {
        "fasting_preset_detail": "ファスティング %1$d 時間 · 食事時間 %2$d 時間",
        "fasting_window_opens_in": "食事時間まで %1$s",
        "settings_fasting_section_reminders": "リマインダー",
        "settings_fasting_start_reminder": "開始リマインダー",
        "settings_fasting_start_reminder_time": "リマインダー時刻",
        "settings_fasting_start_reminder_help": "次のファスティング開始を促す毎日の通知。すでに実行中ならスキップされます。",
        "notif_fasting_start_title": "ファスティングを開始",
        "notif_fasting_start_text": "今すぐファスティングを開始しましょう。",
        "notif_fasting_start_text_goal": "今すぐ %1$d 時間のファスティングを開始しましょう。",
    },
    "values-ko": {
        "fasting_preset_detail": "단식 %1$d시간 · 식사 시간 %2$d시간",
        "fasting_window_opens_in": "식사 시간까지 %1$s",
        "settings_fasting_section_reminders": "알림",
        "settings_fasting_start_reminder": "시작 알림",
        "settings_fasting_start_reminder_time": "알림 시간",
        "settings_fasting_start_reminder_help": "다음 단식 시작을 알리는 매일 알림입니다. 단식이 이미 진행 중이면 건너뜁니다.",
        "notif_fasting_start_title": "단식 시작",
        "notif_fasting_start_text": "지금 단식을 시작하세요.",
        "notif_fasting_start_text_goal": "지금 %1$d시간 단식을 시작하세요.",
    },
    "values-nl": {
        "fasting_preset_detail": "%1$d u vasten · %2$d u eetvenster",
        "fasting_window_opens_in": "Eetvenster gaat open over %1$s",
        "settings_fasting_section_reminders": "Herinneringen",
        "settings_fasting_start_reminder": "Startherinnering",
        "settings_fasting_start_reminder_time": "Herinneringstijd",
        "settings_fasting_start_reminder_help": "Een dagelijkse herinnering om je volgende vasten te starten. Overgeslagen als er al wordt gevast.",
        "notif_fasting_start_title": "Start je vasten",
        "notif_fasting_start_text": "Start nu je vasten.",
        "notif_fasting_start_text_goal": "Start nu je vasten van %1$d u.",
    },
    "values-pl": {
        "fasting_preset_detail": "%1$d h postu · %2$d h okna jedzenia",
        "fasting_window_opens_in": "Okno jedzenia otwiera się za %1$s",
        "settings_fasting_section_reminders": "Przypomnienia",
        "settings_fasting_start_reminder": "Przypomnienie o rozpoczęciu",
        "settings_fasting_start_reminder_time": "Godzina przypomnienia",
        "settings_fasting_start_reminder_help": "Codzienne przypomnienie o rozpoczęciu następnego postu. Pomijane, gdy post już trwa.",
        "notif_fasting_start_title": "Rozpocznij post",
        "notif_fasting_start_text": "Rozpocznij post teraz.",
        "notif_fasting_start_text_goal": "Rozpocznij teraz swój %1$d-godzinny post.",
    },
    "values-pt-rBR": {
        "fasting_preset_detail": "%1$d h de jejum · %2$d h de janela de refeições",
        "fasting_window_opens_in": "A janela de refeições abre em %1$s",
        "settings_fasting_section_reminders": "Lembretes",
        "settings_fasting_start_reminder": "Lembrete de início",
        "settings_fasting_start_reminder_time": "Hora do lembrete",
        "settings_fasting_start_reminder_help": "Um lembrete diário para iniciar o próximo jejum. Ignorado quando já há um jejum ativo.",
        "notif_fasting_start_title": "Inicie seu jejum",
        "notif_fasting_start_text": "Inicie seu jejum agora.",
        "notif_fasting_start_text_goal": "Inicie agora seu jejum de %1$d h.",
    },
    "values-ro": {
        "fasting_preset_detail": "%1$d h de post · %2$d h de fereastră de mese",
        "fasting_window_opens_in": "Fereastra de mese se deschide în %1$s",
        "settings_fasting_section_reminders": "Memento-uri",
        "settings_fasting_start_reminder": "Memento de începere",
        "settings_fasting_start_reminder_time": "Ora mementoului",
        "settings_fasting_start_reminder_help": "Un memento zilnic pentru a începe următorul post. Omis dacă un post este deja activ.",
        "notif_fasting_start_title": "Începe postul",
        "notif_fasting_start_text": "Începe postul acum.",
        "notif_fasting_start_text_goal": "Începe acum postul tău de %1$d h.",
    },
    "values-ru": {
        "fasting_preset_detail": "%1$d ч поста · %2$d ч окна приёма пищи",
        "fasting_window_opens_in": "Окно приёма пищи откроется через %1$s",
        "settings_fasting_section_reminders": "Напоминания",
        "settings_fasting_start_reminder": "Напоминание о начале",
        "settings_fasting_start_reminder_time": "Время напоминания",
        "settings_fasting_start_reminder_help": "Ежедневное напоминание о начале следующего поста. Пропускается, если пост уже идёт.",
        "notif_fasting_start_title": "Начните пост",
        "notif_fasting_start_text": "Начните пост прямо сейчас.",
        "notif_fasting_start_text_goal": "Начните сейчас свой %1$d-часовой пост.",
    },
    "values-tr": {
        "fasting_preset_detail": "%1$d saat oruç · %2$d saat yemek penceresi",
        "fasting_window_opens_in": "Yemek penceresi %1$s sonra açılır",
        "settings_fasting_section_reminders": "Hatırlatıcılar",
        "settings_fasting_start_reminder": "Başlangıç hatırlatıcısı",
        "settings_fasting_start_reminder_time": "Hatırlatıcı saati",
        "settings_fasting_start_reminder_help": "Sonraki oruca başlamak için günlük hatırlatıcı. Zaten aktif bir oruç varsa atlanır.",
        "notif_fasting_start_title": "Orucunu başlat",
        "notif_fasting_start_text": "Şimdi orucuna başla.",
        "notif_fasting_start_text_goal": "Şimdi %1$d saatlik orucuna başla.",
    },
    "values-uk": {
        "fasting_preset_detail": "%1$d год посту · %2$d год вікна прийому їжі",
        "fasting_window_opens_in": "Вікно прийому їжі відкриється через %1$s",
        "settings_fasting_section_reminders": "Нагадування",
        "settings_fasting_start_reminder": "Нагадування про початок",
        "settings_fasting_start_reminder_time": "Час нагадування",
        "settings_fasting_start_reminder_help": "Щоденне нагадування про початок наступного посту. Пропускається, якщо пост уже триває.",
        "notif_fasting_start_title": "Почніть пост",
        "notif_fasting_start_text": "Почніть пост зараз.",
        "notif_fasting_start_text_goal": "Почніть зараз свій %1$d-годинний пост.",
    },
    "values-zh-rCN": {
        "fasting_preset_detail": "禁食 %1$d 小时 · 进食 %2$d 小时",
        "fasting_window_opens_in": "进食窗口将在 %1$s 后开启",
        "settings_fasting_section_reminders": "提醒",
        "settings_fasting_start_reminder": "开始提醒",
        "settings_fasting_start_reminder_time": "提醒时间",
        "settings_fasting_start_reminder_help": "每日提醒开始下一次禁食。已有进行中的禁食时会跳过。",
        "notif_fasting_start_title": "开始禁食",
        "notif_fasting_start_text": "现在开始禁食。",
        "notif_fasting_start_text_goal": "现在开始 %1$d 小时禁食。",
    },
}


def xml_escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def main() -> int:
    for folder, keys in STRINGS.items():
        path = RES / folder / "strings.xml"
        text = path.read_text(encoding="utf-8")
        if "name=\"fasting_window_opens_in\"" in text:
            print(f"skip {folder}: already present")
            continue
        block = "\n".join(
            f'    <string name="{name}">{xml_escape(value)}</string>'
            for name, value in keys.items()
        )
        if "</resources>" not in text:
            print(f"ERROR {folder}: no </resources> anchor")
            return 1
        text = text.replace("</resources>", block + "\n</resources>", 1)
        path.write_text(text, encoding="utf-8")
        print(f"ok {folder}: inserted {len(keys)} strings")
    return 0


if __name__ == "__main__":
    sys.exit(main())
