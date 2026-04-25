// AUTO-GENERATED - GHOST SERIALIZATION BRIDGE
import { ghostPrewarm, ghostDeserializeJs, ghostDeserializeBytesJs, memory } from "./ghost-standalone-wasm-js.mjs";
import type { Model } from "../ghost-models/Model";

export interface GhostModels {
    Model: Model;
}

export type ModelName = keyof GhostModels;

/**
 * Ensures the Ghost WASM engine is loaded and initialized.
 */
export async function ensureGhostReady(): Promise<void> {
    // Note: initGhostWasm logic is handled by the .mjs auto-initialization 
    // but we call ghostPrewarm to register serializers.
    ghostPrewarm();
}

/**
 * Deserializes a JSON string into a typed Kotlin/Wasm model.
 */
export function deserializeModelSync<T extends ModelName>(json: string, model: T): GhostModels[T] {
    const result = ghostDeserializeJs(json, model);
    if (result === null || result === undefined) throw new Error(`Failed to deserialize ${model}. Check if the model is registered.`);
    return result as GhostModels[T];
}

/**
 * Deserializes raw UTF-8 bytes into a typed Kotlin/Wasm model (Fastest Path).
 */
export function deserializeModelFromBytesSync<T extends ModelName>(bytes: Uint8Array, model: T): GhostModels[T] {
    const result = ghostDeserializeBytesJs(bytes, model);
    if (result === null || result === undefined) throw new Error(`Failed to deserialize ${model} from bytes.`);
    return result as GhostModels[T];
}

/**
 * Returns the current memory usage of the Ghost WASM linear memory.
 */
export function getGhostWasmMemoryByteLength(): number {
    if (!memory) return 0;
    return (memory as WebAssembly.Memory).buffer.byteLength;
}

// Re-export types for convenience
export type { Model };
