import SwiftUI

struct DistributionFlow: View {
    @Bindable var app: AppController
    let capture: Capture
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            ProjectPicker(app: app, capture: capture, onSaved: { dismiss() })
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Позже") { dismiss() } } }
        }
    }
}

struct ProjectPicker: View {
    @Bindable var app: AppController
    let capture: Capture
    let onSaved: () -> Void
    @State private var scores: [UUID: Int] = [:]
    @State private var ranking = false
    @State private var rankingMessage = ""
    @State private var creating = false
    private var canChoose: Bool { capture.phase.allowsEditing && !capture.textToSave.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    private var projects: [BrainProject] { app.store.orderedProjects(scores: scores) }
    private var rankingKey: [String] {
        [capture.phaseRaw, capture.textToSave, capture.localeID] + app.store.projects.flatMap {
            [$0.id.uuidString, $0.title, $0.details, $0.instruction, String($0.pinned)]
        }
    }
    var body: some View {
        List {
            Section {
                NavigationLink { CaptureView(app: app, capture: capture, canDistribute: false) } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(capture.title).font(.headline)
                        if !capture.textToSave.isEmpty { Text(capture.textToSave).font(.subheadline).foregroundStyle(.secondary).lineLimit(3) }
                        Text("Текст и аудиоисточник").font(.caption).foregroundStyle(.tint)
                    }
                }
                if capture.phase.isWorking { ProgressView(capture.phase.label) }
                if !capture.message.isEmpty { Text(capture.message).font(.footnote).foregroundStyle(.secondary) }
                if capture.phase == .failed {
                    Button("Повторить обработку") { app.enqueue(capture) }
                    SpeechModelDownloadButton { app.enqueue(capture, allowDownload: true) }
                }
            }
            if canChoose {
                if projects.contains(where: \.pinned) {
                    Section("Закреплённые") { ForEach(projects.filter(\.pinned)) { project in choice(project) } }
                }
                Section {
                    ForEach(projects.filter { !$0.pinned }) { project in choice(project) }
                } header: { Text("Остальные проекты") } footer: {
                    if ranking { HStack { ProgressView(); Text("Оцениваю соответствие. Выбирать уже можно.") } }
                    else { Text(rankingMessage.isEmpty ? "Закреплённые всегда выше остальных. Проект выбираете вы." : rankingMessage) }
                }
                Section { Button("Создать проект", systemImage: "folder.badge.plus") { creating = true } }
            } else if !capture.phase.isWorking {
                Section { Text("Откройте текст и аудиоисточник: можно ввести заметку вручную или повторить распознавание.").foregroundStyle(.secondary) }
            }
        }
        .navigationTitle("Выберите проект")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $creating) { ProjectEditor(app: app) }
        .task(id: rankingKey) {
            scores = [:]; rankingMessage = ""; ranking = false
            guard canChoose, app.store.projects.contains(where: { !$0.pinned }), !app.testing else { return }
            ranking = true
            do {
                let result = try await app.intelligence.rank(text: capture.textToSave, projects: app.store.projects.map(\.candidate), localeID: capture.localeID)
                try Task.checkCancellation()
                scores = result; ranking = false
                rankingMessage = "Незакреплённые проекты отсортированы локальной LLM: выше — подходящее. Все проекты остаются в списке."
            } catch {
                guard !Task.isCancelled else { return }
                ranking = false; scores = [:]
                rankingMessage = "Обычная сортировка по названию. " + error.localizedDescription
            }
        }
    }
    private func choice(_ project: BrainProject) -> some View {
        NavigationLink { NoteDestinationPicker(app: app, capture: capture, project: project, onSaved: onSaved) } label: {
            Label {
                VStack(alignment: .leading, spacing: 4) {
                    Text(project.title)
                    if !project.instruction.isEmpty { Text(project.instruction).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
                }
            } icon: { Image(systemName: project.pinned ? "pin.fill" : "folder") }
        }
        .contextMenu {
            Button(project.pinned ? "Открепить" : "Закрепить", systemImage: "pin") { app.attempt { try app.store.setPinned(project, !project.pinned) } }
        }
    }
}

struct NoteDestinationPicker: View {
    @Bindable var app: AppController
    let capture: Capture
    let project: BrainProject
    let onSaved: () -> Void
    @State private var saving = false
    var body: some View {
        List {
            Section { Button("Новая заметка", systemImage: "square.and.pencil") { save(into: nil) } }
            Section {
                ForEach(app.store.notes(in: project)) { note in
                    Button { save(into: note) } label: {
                        VStack(alignment: .leading, spacing: 5) {
                            Text(note.title).foregroundStyle(.primary)
                            Text(note.body).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                        }.padding(.vertical, 4)
                    }
                }
            } header: { Text("Добавить в конец существующей") } footer: {
                Text("Прежний текст не изменится. У нового фрагмента останется собственный транскрипт и аудиоисточник.")
            }
        }
        .disabled(saving || capture.noteID != nil || !capture.phase.allowsEditing)
        .navigationTitle(project.title)
        .navigationBarTitleDisplayMode(.inline)
    }
    private func save(into note: BrainNote?) {
        guard !saving else { return }
        saving = true
        if app.attempt({ try app.store.distribute(capture, project: project, existing: note) }) { onSaved() }
        else { saving = false }
    }
}
