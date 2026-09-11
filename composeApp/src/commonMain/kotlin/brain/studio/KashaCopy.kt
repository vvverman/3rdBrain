package brain.studio

/** Новые продуктовые строки. Порядок языков совпадает с Languages.codes. */
object KashaCopy {
    private val rows = """
tasks|Задачи|Tasks|Tareas|Tâches|Aufgaben|Завдання|Задачы|Тапсырмалар
task|Задача|Task|Tarea|Tâche|Aufgabe|Завдання|Задача|Тапсырма
note|Заметка|Note|Nota|Note|Notiz|Нотатка|Нататка|Жазба
noTasks|Пока нет задач|No tasks yet|Aún no hay tareas|Aucune tâche|Noch keine Aufgaben|Поки немає завдань|Пакуль няма задач|Әзірге тапсырма жоқ
sortAlphabetical|А-Я|A-Z|A-Z|A-Z|A-Z|А-Я|А-Я|А-Я
sortCreated|Создано|Created|Creado|Créé|Erstellt|Створено|Створана|Жасалған
sortUpdated|Изменено|Updated|Actualizado|Modifié|Geändert|Змінено|Зменена|Өзгертілген
sortManual|Вручную|Manual|Manual|Manuel|Manuell|Вручну|Уручную|Қолмен
saveTask|Сохранить задачу|Save task|Guardar tarea|Enregistrer la tâche|Aufgabe speichern|Зберегти завдання|Захаваць задачу|Тапсырманы сақтау
taskFromVoiceHint|Текст записи станет задачей в выбранном проекте.|The recording text will become a task in the selected project.|El texto de la grabación se convertirá en una tarea del proyecto elegido.|Le texte de l’enregistrement deviendra une tâche du projet choisi.|Der Aufnahmetext wird zu einer Aufgabe im gewählten Projekt.|Текст запису стане завданням у вибраному проєкті.|Тэкст запісу стане задачай у выбраным праекце.|Жазба мәтіні таңдалған жобадағы тапсырмаға айналады.
localOnly|Все данные и ИИ работают только на этом устройстве.|All data and AI stay on this device.|Todos los datos y la IA permanecen en este dispositivo.|Toutes les données et l’IA restent sur cet appareil.|Alle Daten und KI bleiben auf diesem Gerät.|Усі дані та ШІ залишаються на цьому пристрої.|Усе даныя і ШІ застаюцца на гэтай прыладзе.|Барлық дерек пен ЖИ осы құрылғыда қалады.
    """.trimIndent().lineSequence().filter { it.isNotBlank() }.associate { line ->
        val cells = line.split('|')
        require(cells.size == 9) { "Incomplete Kasha localization: ${cells.first()}" }
        cells.first() to cells.drop(1)
    }

    fun has(key: String): Boolean = key in rows
    fun text(language: String, key: String): String? = rows[key]?.getOrNull(Languages.codes.indexOf(language).coerceAtLeast(0))
}
