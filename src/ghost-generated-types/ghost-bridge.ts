import type * as GhostWasm from "ghost-serialization-wasm";
export interface GhostModels {

}
type GhostEngine = typeof GhostWasm;
let _engine: GhostEngine | null = null;
let _wasmInstance: WebAssembly.Instance | null = null;
const _sharedEncoder = new TextEncoder();
const _factories: Record<string, (args: any[]) => any> = {

};
async function getGhostEngine(): Promise<GhostEngine> {
    if (_engine) return _engine;
    const mod = await import("ghost-serialization-wasm/ghost-serialization-wasm.uninstantiated.mjs");
    const { instance, exports } = await mod.instantiate({
        env: {
            ghost_create_object: (typeNamePtr: number, args: any) => {
                const typeName = _readString(typeNamePtr);
                return _factories[typeName]?.(args as any[]) ?? {};
            }
        }
    });
    function _readString(ptr: number): string {
        const mem = new Uint8Array((exports as any).memory.buffer);
        let len = 0; while (mem[ptr + len] !== 0) len++;
        return new TextDecoder().decode(mem.slice(ptr, ptr + len));
    }
    (exports as any).ghostPrewarm();
    _engine = exports as any;
    _wasmInstance = instance;
    return _engine!;
}
export async function ensureGhostReady() { await getGhostEngine(); }
export async function deserializeModel<K extends keyof GhostModels>(json: string, type: K): Promise<GhostModels[K]> {
    await getGhostEngine(); return deserializeModelSync(json, type);
}
export function deserializeModelSync<K extends keyof GhostModels>(json: string, type: K): GhostModels[K] {
    if (!_engine) throw new Error("Engine not ready");
    return _engine.ghostDeserializeBytesJs(_sharedEncoder.encode(json), type as string) as any;
}
export function deserializeModelFromBytesSync<K extends keyof GhostModels>(bytes: Uint8Array, type: K): GhostModels[K] {
    if (!_engine) throw new Error("Engine not ready");
    return _engine.ghostDeserializeBytesJs(bytes, type as string) as any;
}
export function getGhostWasmMemoryByteLength(): number { return (_wasmInstance?.exports.memory as any)?.buffer.byteLength ?? 0; }
