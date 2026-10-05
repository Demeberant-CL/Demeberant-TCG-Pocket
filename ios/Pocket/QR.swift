import Foundation
import UIKit
import Vision

// Fixed QR version 9, byte mode, H, no ECI, quiet zone 4.
// ISO QR geometry and Reed-Solomon over GF(256), polynomial 0x11D.
// Masks are tried individually; only a bitmap decoded to the exact payload can leave this API.
enum QRMatrix {
    static let size = 53
    static func make(_ payload: String, mask: Int) throws -> [[Bool]] {
        let bytes = Array(payload.utf8)
        try require(bytes.count <= 98 && (0...7).contains(mask), "Payload QR demasiado grande.")
        var bits: [Bool] = []
        func append(_ n: Int, _ count: Int) { for i in (0..<count).reversed() { bits.append((n >> i) & 1 != 0) } }
        append(4, 4); append(bytes.count, 8)
        for byte in bytes { append(Int(byte), 8) }
        append(0, min(4, 800 - bits.count))
        while bits.count % 8 != 0 { bits.append(false) }
        var data = stride(from: 0, to: bits.count, by: 8).map { start in
            (0..<8).reduce(0) { ($0 << 1) | (bits[start + $1] ? 1 : 0) }
        }
        var pad = 0
        while data.count < 100 { data.append(pad % 2 == 0 ? 0xEC : 0x11); pad += 1 }
        func multiply(_ a: Int, _ b: Int) -> Int {
            var result = 0, x = a, y = b
            while y > 0 { if y & 1 != 0 { result ^= x }; y >>= 1; x <<= 1; if x & 0x100 != 0 { x ^= 0x11D } }
            return result
        }
        var generator = [1], root = 1
        for _ in 0..<24 {
            var next = Array(repeating: 0, count: generator.count + 1)
            for i in generator.indices { next[i] ^= generator[i]; next[i + 1] ^= multiply(generator[i], root) }
            generator = next; root = multiply(root, 2)
        }
        var blocks: [[Int]] = [], parity: [[Int]] = [], offset = 0
        for b in 0..<8 {
            let count = b < 4 ? 12 : 13
            let block = Array(data[offset..<(offset + count)]); offset += count; blocks.append(block)
            var remainder = block + Array(repeating: 0, count: 24)
            for i in block.indices {
                let factor = remainder[i]
                for j in generator.indices { remainder[i + j] ^= multiply(generator[j], factor) }
            }
            parity.append(Array(remainder.suffix(24)))
        }
        var codewords: [Int] = []
        for i in 0..<13 { for b in 0..<8 where i < blocks[b].count { codewords.append(blocks[b][i]) } }
        for i in 0..<24 { for b in 0..<8 { codewords.append(parity[b][i]) } }
        var modules = Array(repeating: Array(repeating: false, count: size), count: size)
        var fixed = modules
        func put(_ x: Int, _ y: Int, _ dark: Bool) {
            if x >= 0 && y >= 0 && x < size && y < size { modules[y][x] = dark; fixed[y][x] = true }
        }
        for i in 0..<size { put(6, i, i % 2 == 0); put(i, 6, i % 2 == 0) }
        for (cx, cy) in [(3,3), (size-4,3), (3,size-4)] {
            for dy in -4...4 { for dx in -4...4 { let distance = max(abs(dx),abs(dy)); put(cx + dx, cy + dy, distance != 2 && distance != 4) } }
        }
        for (i, cy) in [6,26,46].enumerated() { for (j,cx) in [6,26,46].enumerated() {
            if (i == 0 && j == 0) || (i == 0 && j == 2) || (i == 2 && j == 0) { continue }
            for dy in -2...2 { for dx in -2...2 { put(cx + dx, cy + dy, max(abs(dx),abs(dy)) != 1) } }
        } }
        let formatData = (2 << 3) | mask
        var rem = formatData
        for _ in 0..<10 { rem = (rem << 1) ^ ((rem >> 9) * 0x537) }
        let format = ((formatData << 10) | rem) ^ 0x5412
        func f(_ i: Int) -> Bool { (format >> i) & 1 != 0 }
        for i in 0..<6 { put(8, i, f(i)) }
        put(8,7,f(6)); put(8,8,f(7)); put(7,8,f(8))
        for i in 9..<15 { put(14-i,8,f(i)) }
        for i in 0..<8 { put(size-1-i,8,f(i)) }
        for i in 8..<15 { put(8,size-15+i,f(i)) }
        put(8,size-8,true)
        var v = 9
        for _ in 0..<12 { v = (v << 1) ^ ((v >> 11) * 0x1F25) }
        let version = (9 << 12) | v
        for i in 0..<18 {
            let a = size - 11 + i % 3, b = i / 3, dark = (version >> i) & 1 != 0
            put(a,b,dark); put(b,a,dark)
        }
        func masked(_ x: Int, _ y: Int) -> Bool {
            switch mask {
            case 0: return (x+y)%2 == 0
            case 1: return y%2 == 0
            case 2: return x%3 == 0
            case 3: return (x+y)%3 == 0
            case 4: return (x/3+y/2)%2 == 0
            case 5: return x*y%2+x*y%3 == 0
            case 6: return (x*y%2+x*y%3)%2 == 0
            default: return ((x+y)%2+x*y%3)%2 == 0
            }
        }
        var bit = 0, right = size - 1
        while right >= 1 {
            if right == 6 { right = 5 }
            for vertical in 0..<size {
                let y = ((right+1)&2) == 0 ? size-1-vertical : vertical
                for x in [right, right-1] where !fixed[y][x] {
                    let raw = bit < codewords.count*8 ? (codewords[bit/8] >> (7-bit%8))&1 != 0 : false
                    modules[y][x] = raw != masked(x,y); bit += 1
                }
            }
            right -= 2
        }
        try require(bit == 292*8, "Geometría QR no válida.")
        return modules
    }
}
enum QRExport {
    static func decoded(_ image: UIImage) throws -> String? {
        guard let cg = image.cgImage else { throw PocketError("PNG no válido.") }
        let request = VNDetectBarcodesRequest(); request.symbologies = [.qr]
        try VNImageRequestHandler(cgImage: cg).perform([request])
        return request.results?.first?.payloadStringValue
    }
    static func png(payload: String, alternate: Bool = false) throws -> Data {
        var readable = 0
        for mask in 0..<8 {
            let matrix = try QRMatrix.make(payload, mask: mask)
            let format = UIGraphicsImageRendererFormat(); format.scale = 1; format.opaque = true
            let image = UIGraphicsImageRenderer(size: CGSize(width: 976, height: 976), format: format).image { renderer in
                let ctx = renderer.cgContext
                ctx.setFillColor(UIColor.white.cgColor); ctx.fill(CGRect(x: 0, y: 0, width: 976, height: 976))
                for y in 0..<53 {
                    let fraction = CGFloat((y+4)*16) / 975
                    ctx.setFillColor(UIColor(red: 22*fraction/255, green: (123-48*fraction)/255, blue: (150+7*fraction)/255, alpha: 1).cgColor)
                    for x in 0..<53 where matrix[y][x] { ctx.fill(CGRect(x: (x+4)*16, y: (y+4)*16, width: 16, height: 16)) }
                }
            }
            guard let png = image.pngData(), let exported = UIImage(data: png), try decoded(image) == payload, try decoded(exported) == payload else { continue }
            readable += 1
            if alternate && readable < 2 { continue }
            return png
        }
        throw PocketError("No se encontró un QR legible. No se exportó ninguna imagen.")
    }
}
