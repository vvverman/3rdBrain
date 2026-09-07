import SwiftUI

struct ProjectsView: View {
    @Bindable var app: AppController
    @State private var creating = false
    @State private var editing: BrainProject?
    private var pinned: [BrainProject] { app.store.orderedProjects().filter(\.pinned) }
    var body: some View {
        List {
            if !pinned.isEmpty {
                Section("Закреплённые") {
                    ForEach(pinned) { project in row(project) }
                        .onMove { source, destination in
                            var ids = pinned.map(\.id); ids.move(fromOffsets: source, toOffset: destination)
                            app.attempt { try app.store.orderPins(ids) }
                        }
                }
            }
            Section("Проекты") {
                ForEach(app.store.orderedProjects().filter { !$0.pinned }) { project in row(project) }
            }
        }
        .navigationTitle("Проекты")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { if !pinned.isEmpty { EditButton() } }
            ToolbarItem(placement: .topBarTrailing) { Button("Создать проект", systemImage: "plus") { creating = true } }
        }
        .overlay {
            if app.store.projects.isEmpty {
                ContentUnavailableView("Для каждой темы — свой проект", systemImage: "folder.badge.plus", description: Text("Создайте проект и опишите, какие мысли в него складывать.")).allowsHitTesting(false)
            }
        }
        .sheet(isPresented: $creating) { ProjectEditor(app: app) }
        .sheet(item: $editing) { ProjectEditor(app: app, project: $0) }
    }
    private func row(_ project: BrainProject) -> some View {
        NavigationLink { ProjectNotesView(app: app, project: project) } label: {
            HStack(spacing: 14) {
                Image(systemName: project.pinned ? "pin.fill" : "folder").foregroundStyle(.tint)
                VStack(alignment: .leading, spacing: 4) {
                    Text(project.title).font(.headline)
                    if !project.details.isEmpty { Text(project.details).font(.subheadline).foregroundStyle(.secondary).lineLimit(2) }
                }
                Spacer()
                Text("\(app.store.notes(in: project).count)").font(.subheadline.monospacedDigit()).foregroundStyle(.secondary)
            }.padding(.vertical, 4)
        }
        .contextMenu {
            Button(project.pinned ? "Открепить" : "Закрепить", systemImage: "pin") { app.attempt { try app.store.setPinned(project, !project.pinned) } }
            Button("Редактировать", systemImage: "pencil") { editing = project }
        }
    }
}

struct ProjectNotesView: View {
    @Bindable var app: AppController
    let project: BrainProject
    @State private var editing = false
    @State private var creatingNote = false
    @State private var deleting = false
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        List {
            if !project.instruction.isEmpty {
                Section("Что сюда складывать") { Text(project.instruction).font(.callout).foregroundStyle(.secondary) }
            }
            Section("Заметки") {
                ForEach(app.store.notes(in: project)) { note in
                    NavigationLink { NoteView(app: app, note: note) } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(note.title).font(.headline)
                            Text(note.body).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                            Text(note.updatedAt, format: .dateTime.day().month().hour().minute()).font(.caption).foregroundStyle(.secondary)
                        }.padding(.vertical, 4)
                    }
                }
                if app.store.notes(in: project).isEmpty { Text("Пока нет заметок. Новую запись можно сохранить сюда после завершения.").foregroundStyle(.secondary) }
            }
        }
        .navigationTitle(project.title)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("Новая текстовая заметка", systemImage: "square.and.pencil") { creatingNote = true }
                    Button("Редактировать проект", systemImage: "pencil") { editing = true }
                    Button(project.pinned ? "Открепить" : "Закрепить", systemImage: "pin") { app.attempt { try app.store.setPinned(project, !project.pinned) } }
                    Button("Удалить пустой проект", systemImage: "trash", role: .destructive) { deleting = true }
                } label: { Image(systemName: "ellipsis") }
            }
        }
        .confirmationDialog("Удалить проект?", isPresented: $deleting, titleVisibility: .visible) {
            Button("Удалить", role: .destructive) { if app.attempt({ try app.store.deleteEmptyProject(project) }) { dismiss() } }
        }
        .sheet(isPresented: $editing) { ProjectEditor(app: app, project: project) }
        .sheet(isPresented: $creatingNote) {
            TextEditorSheet(app: app, title: "Новая заметка", text: "") { title, body in
                app.attempt {
                    app.store.context.insert(BrainNote(projectID: project.id, title: title, body: body))
                    try app.store.save()
                }
            }
        }
    }
}

struct ProjectEditor: View {
    @Bindable var app: AppController
    let project: BrainProject?
    @State private var title: String
    @State private var details: String
    @State private var instruction: String
    @Environment(\.dismiss) private var dismiss
    init(app: AppController, project: BrainProject? = nil) {
        self.app = app; self.project = project
        _title = State(initialValue: project?.title ?? "")
        _details = State(initialValue: project?.details ?? "")
        _instruction = State(initialValue: project?.instruction ?? "")
    }
    var body: some View {
        NavigationStack {
            Form {
                Section("Проект") {
                    TextField("Название", text: $title)
                    TextField("Описание", text: $details, axis: .vertical).lineLimit(2...5)
                }
                Section {
                    TextField("Какие мысли относятся к этому проекту?", text: $instruction, axis: .vertical).lineLimit(5...12)
                } header: { Text("Что сюда складывать") } footer: {
                    Text("ИИ учитывает это поле при сортировке проектов. Можно указать и то, что сюда не относится.")
                }
            }
            .navigationTitle(project == nil ? "Новый проект" : "Настройки проекта")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить") {
                        let ok = app.attempt {
                            if let project { project.title = title; project.details = details; project.instruction = instruction; try app.store.save() }
                            else { try app.store.addProject(title: title, details: details, instruction: instruction) }
                        }
                        if ok { dismiss() }
                    }.disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .safeAreaInset(edge: .bottom) { RecorderBar(app: app).padding(12).glassEffect(in: .capsule).padding() }
        }
    }
}
