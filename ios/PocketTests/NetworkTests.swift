import XCTest
import Foundation
import UIKit
@testable import Pocket

final class StubProtocol: URLProtocol {
    static let lock = NSLock()
    static var handler: ((URLRequest) throws -> (Int, Data))?
    static var requests: [URLRequest] = []
    static var scope = UUID().uuidString
    static func beginTest() { lock.lock(); defer { lock.unlock() }; scope = UUID().uuidString; requests = []; handler = nil }
    static func reset(_ block: @escaping (URLRequest) throws -> (Int,Data)) { lock.lock(); defer { lock.unlock() }; requests = []; handler = block }
    static func count() -> Int { lock.lock(); defer { lock.unlock() }; return requests.count }
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        Self.lock.lock()
        let current = request.value(forHTTPHeaderField:"X-Pocket-Test-Scope") == Self.scope
        if current { Self.requests.append(request) }
        let block = current ? Self.handler : nil
        Self.lock.unlock()
        if !current { client?.urlProtocol(self,didFailWithError:URLError(.cancelled)); return }
        if request.url?.path == "/stall" { return }
        do {
            let (status,data) = try XCTUnwrap(block)(request)
            let response = HTTPURLResponse(url:request.url!,statusCode:status,httpVersion:"HTTP/1.1",headerFields:["Content-Type":"application/json"] )!
            client?.urlProtocol(self,didReceive:response,cacheStoragePolicy:.notAllowed)
            client?.urlProtocol(self,didLoad:data); client?.urlProtocolDidFinishLoading(self)
        } catch { client?.urlProtocol(self,didFailWithError:error) }
    }
    override func stopLoading() {}
}
final class NetworkTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false; StubProtocol.beginTest() }
    func client() -> HTTP { let config = URLSessionConfiguration.ephemeral; config.protocolClasses = [StubProtocol.self]; config.httpAdditionalHeaders = ["X-Pocket-Test-Scope":StubProtocol.scope]; return HTTP(configuration:config) }
    func profile() -> AIProfile { AIProfile(name:"Synthetic",provider:"Gemini",model:"gemini-2.5-flash",endpoint:"") }
    func testAIFailedRequestIsSingleNoProviderFallback() async {
        StubProtocol.reset { _ in (429,Data("private-body-do-not-display".utf8)) }
        do { _ = try await ConnectedAI.request(profile:profile(),key:"synthetic",prompt:"synthetic",client:client()); XCTFail("Expected quota rejection") }
        catch { XCTAssertFalse(error.localizedDescription.contains("private-body")) }
        XCTAssertEqual(StubProtocol.count(),1)
    }
    func testAIIncompleteResponseRejectedWithoutRetry() async {
        StubProtocol.reset { _ in (200,Data("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}".utf8)) }
        do { _ = try await ConnectedAI.request(profile:profile(),key:"synthetic",prompt:"synthetic",client:client()); XCTFail("Expected incomplete rejection") } catch {}
        XCTAssertEqual(StubProtocol.count(),1)
    }
    func testAISuccessDoesNotIncludeThoughtParts() async throws {
        StubProtocol.reset { _ in (200,Data("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private thought\"},{\"text\":\"{\\\"cards\\\":[]}\"}]}}]}".utf8)) }
        let text = try await ConnectedAI.request(profile:profile(),key:"synthetic",prompt:"synthetic",client:client())
        XCTAssertEqual(text,"{\"cards\":[]}"); XCTAssertEqual(StubProtocol.count(),1)
    }
    func testResponseLimitEnforced() async {
        StubProtocol.reset { _ in (200,Data(repeating:32,count:1001)) }
        do { _ = try await client().get(URLRequest(url:URL(string:"https://test.invalid/data")!),limit:1000); XCTFail("Expected bounded read") } catch {}
    }
    func testCancelledRequestDoesNotRetry() async {
        StubProtocol.reset { _ in throw URLError(.cancelled) }
        do { _ = try await client().get(URLRequest(url:URL(string:"https://test.invalid/data")!),limit:1000); XCTFail("Expected cancel") } catch {}
        XCTAssertEqual(StubProtocol.count(),1)
    }
    func testSlowRequestTimesOutWithoutRetry() async {
        StubProtocol.reset { _ in XCTFail("Stalled request must not return a body"); return (200,Data()) }
        let start = Date()
        do { _ = try await client().get(URLRequest(url:URL(string:"https://test.invalid/stall")!,timeoutInterval:0.03),limit:1000); XCTFail("Expected timeout") } catch {}
        XCTAssertLessThan(Date().timeIntervalSince(start),10)
        XCTAssertEqual(StubProtocol.count(),1)
    }
    func testTournamentOverallBudgetCancelsPendingHTTP() async {
        StubProtocol.reset { _ in throw URLError(.timedOut) }
        do { _ = try await Tournaments.refresh(client:client(),timeout:1) { _,_ in }; XCTFail("Expected deadline") } catch {}
        XCTAssertLessThanOrEqual(StubProtocol.count(),1)
    }
    func testImageFallbackOnlyOn404AndNegativeCacheResetsManually() async throws {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        let png = UIGraphicsImageRenderer(size:CGSize(width:2,height:2),format:format).pngData { ctx in UIColor.blue.setFill(); ctx.fill(CGRect(x:0,y:0,width:2,height:2)) }
        StubProtocol.reset { request in request.url!.path.contains("/es/") ? (404,Data()) : (200,png) }
        let images = CardImages(client:client())
        _ = try await images.imageData("A1-001"); XCTAssertEqual(StubProtocol.count(),2)
        _ = try await images.imageData("A1-001"); XCTAssertEqual(StubProtocol.count(),3)
        _ = try await images.imageData("A1-001",retry:true); XCTAssertEqual(StubProtocol.count(),5)
    }
    func testImageTransientFailureNeverFallsBack() async {
        StubProtocol.reset { _ in (503,Data()) }; let images = CardImages(client:client())
        do { _ = try await images.imageData("A1-001"); XCTFail("Expected transient failure") } catch {}
        XCTAssertEqual(StubProtocol.count(),1)
    }
    func testCardDetailsIdentityAnd404LanguageFallback() async throws {
        StubProtocol.reset { request in request.url!.path.contains("/es/") ? (404,Data()) : (200,Data("{\"id\":\"A1-001\",\"hp\":60,\"effect\":\"Draw one\",\"types\":[\"Grass\"]}".utf8)) }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at:url) }
        let cache = RulesCache(url:url,client:client()), rules = try await cache.details("A1-001")
        XCTAssertEqual(rules.language,"en"); XCTAssertEqual(rules.element,"Grass"); XCTAssertEqual(StubProtocol.count(),2)
        _ = try await cache.details("A1-001"); XCTAssertEqual(StubProtocol.count(),2)
        StubProtocol.reset { _ in (503,Data()) }
        let cached = try await cache.details("A1-001",retry:true); XCTAssertEqual(cached.text,"Draw one"); XCTAssertEqual(StubProtocol.count(),1)
    }
    func testCardDetailsWrongIdentityRejected() async {
        StubProtocol.reset { _ in (200,Data("{\"id\":\"A1-002\"}".utf8)) }
        do { _ = try await RulesCache(url:FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString),client:client()).details("A1-001"); XCTFail("Expected identity check") } catch {}
        XCTAssertEqual(StubProtocol.count(),1)
    }
    func testModelDiscoveryConservativelyFiltersMetadata() async throws {
        StubProtocol.reset { _ in (200,Data("{\"models\":[{\"name\":\"models/gemini-2.5-flash\",\"supportedGenerationMethods\":[\"generateContent\"],\"outputTokenLimit\":8192},{\"name\":\"models/gemini-2.5-flash-image\",\"supportedGenerationMethods\":[\"generateContent\"]}]}".utf8)) }
        let names = try await ConnectedAI.models(profile:profile(),key:"synthetic",client:client())
        XCTAssertEqual(names,["gemini-2.5-flash"])
        XCTAssertFalse(ConnectedAI.accepts(provider:"Compatible",row:[:],id:"some-text-model"))
    }
    func testTournamentVerifiesPocketIdentityBeforeDownloadingStandings() async {
        let date = ISO8601DateFormatter().string(from:Date())
        StubProtocol.reset { request in
            if request.url!.path.hasSuffix("/details") { return (200,Data("{\"id\":\"different\",\"game\":\"POCKET\",\"isPublic\":true,\"decklists\":true}".utf8)) }
            return (200,Data("[{\"id\":\"abc\",\"game\":\"POCKET\",\"date\":\"\(date)\"}]".utf8))
        }
        do { _ = try await Tournaments.refresh(client:client()) { _,_ in }; XCTFail("No eligible tournament") } catch {}
        XCTAssertEqual(StubProtocol.count(),2)
    }
    func testTournamentNeverDownloadsNonPocketGame() async {
        StubProtocol.reset { _ in (200,Data("[{\"id\":\"abc\",\"game\":\"PTCG\",\"date\":\"2026-10-05T00:00:00Z\"}]".utf8)) }
        do { _ = try await Tournaments.refresh(client:client()) { _,_ in }; XCTFail("No pocket data") } catch {}
        XCTAssertEqual(StubProtocol.count(),1)
    }

}
