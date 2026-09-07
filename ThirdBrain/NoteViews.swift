import SwiftUI

struct NoteView: View {
    @Bindable var app: AppController
    let note: BrainNote
    @State private var editing = false
    @State private var deleting = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        List {
            Section { Text(note.body).textSelection(.enabled).lineSpacing(5).padding(.vertical, 8) }
            Section("Источники · \(app.store.sources(for: note).count)") {
                ForEach(app.store.sources(for: note)) { source in
                    NavigationLink { CaptureView(app: app, capture: source) } label: { CaptureRow(capture: source) }
                }
            }
        }
        .navigationTitle(note.title)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("Редактировать", systemImage: "pencil") { editing = true }
                    ShareLink(item: note.title + "\n\n" + note.body) { Label("Поделиться текстом", systemImage: "square.and.arrow.up") }
                    Button("Удалить заметку", systemImage: "trash", role: .destructive) { deleting = true }
                } label: { Image(systemName: "ellipsis") }
            }
        }
        .sheet(isPresented: $editing) {
            TextEditorSheet(app: app, title: note.title, text: note.body) { title, body in
                app.attempt { note.title = title; note.body = body; note.updatedAt = .now; try app.store.save() }
            }
        }
        .confirmationDialog("Удалить заметку? Её аудиоисточники вернутся во «Входящие».", isPresented: $deleting, titleVisibility: .visible) {
            Button("Удалить заметку", role: .destructive) { if app.attempt({ try app.store.deleteNote(note) }) { dismiss() } }
        }
    }
}

struct CaptureView: View {
    @Bindable var app: AppController
    let capture: Capture
    var canDistribute = true
    @State private var editing = false
    @State private var deleting = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        List {
            Section {
                if capture.phase.isWorking { ProgressView(capture.phase.label) }
                if !capture.message.isEmpty { Label(capture.message, systemImage: "info.circle").font(.callout).foregroundStyle(.secondary) }
                if !capture.phase.isWorking && capture.noteID == nil {
                    Button("Повторить незавершённые этапы", systemImage: "arrow.clockwise") { app.enqueue(capture) }
                    Button("Загрузить модель и повторить", systemImage: "arrow.down.circle") { app.enqueue(capture, allowDownload: true) }
                }
            }
            Section(capture.draftEdited ? "Ваш текст" : capture.llmApplied ? "Оформленная заметка" : "Текст без обработки LLM") {
                Text(capture.textToSave.isEmpty ? "После распознавания здесь появится текст. Его также можно ввести вручную." : capture.textToSave)
                    .textSelection(.enabled).lineSpacing(4)
                if canDistribute && capture.noteID == nil {
                    Button("Выбрать проект", systemImage: "folder") { app.routingCapture = capture }
                        .disabled(capture.phase.isWorking || capture.textToSave.isEmpty)
                }
            }
            SourceView(app: app, capture: capture)
        }
        .navigationTitle(capture.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    if capture.noteID == nil {
                        Button("Редактировать текст", systemImage: "pencil") { editing = true }.disabled(capture.phase.isWorking)
                        Button("Удалить запись и аудио", systemImage: "trash", role: .destructive) { deleting = true }.disabled(capture.phase.isWorking)
                    }
                    if !capture.textToSave.isEmpty { ShareLink(item: capture.textToSave) { Label("Поделиться текстом", systemImage: "square.and.arrow.up") } }
                } label: { Image(systemName: "ellipsis") }
            }
        }
        .sheet(isPresented: $editing) {
            TextEditorSheet(app: app, title: capture.title, text: capture.textToSave) { title, body in
                app.attempt { capture.title = title; capture.preparedText = body; capture.draftEdited = true; try app.store.save() }
            }
        }
        .confirmationDialog("Удалить эту запись, транскрипт и оба аудиофайла без возможности восстановления?", isPresented: $deleting, titleVisibility: .visible) {
            Button("Удалить всё", role: .destructive) {
                app.player.stop()
                if app.attempt({ try app.store.deleteCapture(capture) }) { dismiss() }
            }
        }
    }
}

struct TextEditorSheet: View {
    @Bindable var app: AppController
    @State private var title: String
    @State private var text: String
    let onSave: (String, String) -> Bool
    @Environment(\.dismiss) private var dismiss
    init(app: AppController, title: String, text: String, onSave: @escaping (String, String) -> Bool) {
        self.app = app; _title = State(initialValue: title); _text = State(initialValue: text); self.onSave = onSave
    }
    var body: some View {
        NavigationStack {
            Form {
                TextField("Заголовок", text: $title, axis: .vertical).font(.headline)
                TextEditor(text: $text).frame(minHeight: 300).accessibilityLabel("Текст заметки")
            }
            .navigationTitle("Редактирование").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить") { if onSave(title, text) { dismiss() } }
                        .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .safeAreaInset(edge: .bottom) { RecorderBar(app: app).padding(12).glassEffect(in: .capsule).padding() }
        }.interactiveDismissDisabled()
    }
}
