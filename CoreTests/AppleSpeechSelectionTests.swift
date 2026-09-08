import Foundation
import Testing
@testable import BrainCore

private let russian = [Locale(identifier: "ru-RU")]

@Test func appleRussianDictationIsUsedWhenNewModelLacksRussian() {
    let plan = AppleSpeechSelection.candidates(localeID: "ru-RU", speechAvailable: true,
        speechSupported: [Locale(identifier: "en-US")], speechInstalled: [],
        dictationSupported: russian, dictationInstalled: russian)
    #expect(plan.count == 1)
    #expect(plan.first?.engine == .dictationTranscriber)
    #expect(plan.first?.isInstalled == true)
}

@Test func installedDictationPrecedesUninstalledNewModel() {
    let plan = AppleSpeechSelection.candidates(localeID: "ru-RU", speechAvailable: true,
        speechSupported: russian, speechInstalled: [], dictationSupported: russian, dictationInstalled: russian)
    #expect(plan.map(\.engine) == [.dictationTranscriber, .speechTranscriber])
}

@Test func newModelWinsWhenBothAreInstalled() {
    let plan = AppleSpeechSelection.candidates(localeID: "ru-RU", speechAvailable: true,
        speechSupported: russian, speechInstalled: russian, dictationSupported: russian, dictationInstalled: russian)
    #expect(plan.map(\.engine) == [.speechTranscriber, .dictationTranscriber])
}

@Test func unavailableHardwareExcludesNewModel() {
    let plan = AppleSpeechSelection.candidates(localeID: "ru-RU", speechAvailable: false,
        speechSupported: russian, speechInstalled: russian, dictationSupported: russian, dictationInstalled: russian)
    #expect(plan.map(\.engine) == [.dictationTranscriber])
}

@Test func unsupportedRussianProducesNoCandidate() {
    let english = [Locale(identifier: "en-US")]
    let plan = AppleSpeechSelection.candidates(localeID: "ru-RU", speechAvailable: true,
        speechSupported: english, speechInstalled: english, dictationSupported: english, dictationInstalled: english)
    #expect(plan.isEmpty)
}

@Test func downloadableModelIsNotReportedAsInstalled() {
    let plan = AppleSpeechSelection.candidates(localeID: "ru-RU", speechAvailable: true,
        speechSupported: russian, speechInstalled: [], dictationSupported: russian, dictationInstalled: [])
    #expect(plan.count == 2)
    #expect(plan.allSatisfy { !$0.isInstalled })
}

@Test func localeNotationIsNormalized() {
    #expect(AppleSpeechSelection.match(russian, localeID: "ru_RU")?.identifier(.bcp47) == "ru-RU")
}

@Test func exactLocaleIsPreferredOverLanguageFallback() {
    let locales = [Locale(identifier: "en-GB"), Locale(identifier: "en-US")]
    #expect(AppleSpeechSelection.match(locales, localeID: "en-US")?.identifier(.bcp47) == "en-US")
}

@Test func otherRegionalModelIsNotReportedAsInstalled() {
    let plan = AppleSpeechSelection.candidates(localeID: "en-US", speechAvailable: true,
        speechSupported: [Locale(identifier: "en-US")], speechInstalled: [Locale(identifier: "en-GB")],
        dictationSupported: [], dictationInstalled: [])
    #expect(plan.first?.isInstalled == false)
}
