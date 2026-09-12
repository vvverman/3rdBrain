package brain.studio

/** Новые продуктовые строки. Порядок языков совпадает с Languages.codes. */
object KashaCopy {
    private val rows = """
tasks|Задачи|Tasks|Tareas|Tâches|Aufgaben|Завдання|Задачы|Тапсырмалар
task|Задача|Task|Tarea|Tâche|Aufgabe|Завдання|Задача|Тапсырма
note|Заметка|Note|Nota|Note|Notiz|Нотатка|Нататка|Жазба
noteText|Текст заметки|Note text|Texto de la nota|Texte de la note|Notiztext|Текст нотатки|Тэкст нататкі|Жазба мәтіні
sendToNotes|В заметки|To notes|A notas|Vers les notes|Zu Notizen|У нотатки|У нататкі|Жазбаларға
sendToTasks|В задачи|To tasks|A tareas|Vers les tâches|Zu Aufgaben|У завдання|У задачы|Тапсырмаларға
noTasks|Пока нет задач|No tasks yet|Aún no hay tareas|Aucune tâche|Noch keine Aufgaben|Поки немає завдань|Пакуль няма задач|Әзірге тапсырма жоқ
sortAlphabetical|А-Я|A-Z|A-Z|A-Z|A-Z|А-Я|А-Я|А-Я
sortCreated|Создано|Created|Creado|Créé|Erstellt|Створено|Створана|Жасалған
sortUpdated|Изменено|Updated|Actualizado|Modifié|Geändert|Змінено|Зменена|Өзгертілген
sortManual|Вручную|Manual|Manual|Manuel|Manuell|Вручну|Уручную|Қолмен
archive|Архив|Archive|Archivo|Archives|Archiv|Архів|Архіў|Мұрағат
activeTasks|Активные|Active|Activas|Actives|Aktiv|Активні|Актыўныя|Белсенді
completedTasks|Выполненные|Completed|Completadas|Terminées|Erledigt|Виконані|Выкананыя|Орындалған
completeTask|Выполнить|Complete|Completar|Terminer|Erledigen|Виконати|Выканаць|Орындау
changeTime|Изменить время|Change time|Cambiar hora|Changer l’heure|Zeit ändern|Змінити час|Змяніць час|Уақытты өзгерту
deleteTask|Удалить|Delete|Eliminar|Supprimer|Löschen|Видалити|Выдаліць|Жою
reminder|Напоминание|Reminder|Recordatorio|Rappel|Erinnerung|Нагадування|Напамін|Еске салу
dueDate|Дата|Date|Fecha|Date|Datum|Дата|Дата|Күні
dueTime|Время|Time|Hora|Heure|Uhrzeit|Час|Час|Уақыты
repeatReminder|Повторять напоминание|Repeat reminder|Repetir recordatorio|Répéter le rappel|Erinnerung wiederholen|Повторювати нагадування|Паўтараць напамін|Еске салуды қайталау
every10m|Раз в 10 минут|Every 10 minutes|Cada 10 minutos|Toutes les 10 minutes|Alle 10 Minuten|Кожні 10 хвилин|Кожныя 10 хвілін|Әр 10 минут
every30m|Раз в полчаса|Every 30 minutes|Cada 30 minutos|Toutes les 30 minutes|Alle 30 Minuten|Кожні 30 хвилин|Кожныя 30 хвілін|Әр 30 минут
hourly|Раз в час|Every hour|Cada hora|Toutes les heures|Stündlich|Щогодини|Штогадзіну|Әр сағат
daily|Каждый день|Every day|Cada día|Tous les jours|Täglich|Щодня|Штодня|Күн сайын
weekly|Каждую неделю|Every week|Cada semana|Chaque semaine|Wöchentlich|Щотижня|Штотыдзень|Апта сайын
weekends|По выходным|Weekends|Fines de semana|Le week-end|Am Wochenende|На вихідних|Па выходных|Демалыста
weekdays|По будням|Weekdays|Días laborables|En semaine|Werktags|У будні|Па буднях|Жұмыс күндері
saveReminder|Сохранить задачу|Save task|Guardar tarea|Enregistrer la tâche|Aufgabe speichern|Зберегти завдання|Захаваць задачу|Тапсырманы сақтау
invalidSchedule|Укажите будущие дату и время|Choose a future date and time|Elige una fecha y hora futuras|Choisissez une date et une heure futures|Wähle Datum und Uhrzeit in der Zukunft|Вкажіть майбутні дату й час|Укажыце будучыя дату і час|Болашақ күн мен уақытты көрсетіңіз
dueLabel|Срок|Due|Vence|Échéance|Fällig|Термін|Тэрмін|Мерзімі
completedLabel|Выполнено|Completed|Completada|Terminée|Erledigt|Виконано|Выканана|Орындалды
localOnly|Все данные и ИИ работают только на этом устройстве.|All data and AI stay on this device.|Todos los datos y la IA permanecen en este dispositivo.|Toutes les données et l’IA restent sur cet appareil.|Alle Daten und KI bleiben auf diesem Gerät.|Усі дані та ШІ залишаються на цьому пристрої.|Усе даныя і ШІ застаюцца на гэтай прыладзе.|Барлық дерек пен ЖИ осы құрылғыда қалады.
    """.trimIndent().lineSequence().filter { it.isNotBlank() }.associate { line ->
        val cells = line.split('|')
        require(cells.size == 9) { "Incomplete Kasha localization: ${cells.first()}" }
        cells.first() to cells.drop(1)
    }

    fun has(key: String): Boolean = key in rows
    fun text(language: String, key: String): String? = rows[key]?.getOrNull(Languages.codes.indexOf(language).coerceAtLeast(0))
}
