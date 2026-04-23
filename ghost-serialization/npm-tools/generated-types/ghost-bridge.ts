
/**
 * Ghost Serialization - Type Safety Bridge
 * Generated automatically. Do not edit.
 */

export interface GhostModels {
    CharacterResponse: import("../../ghost-models/CharacterResponse").CharacterResponse;
}

export async function deserializeModel<K extends keyof GhostModels>(json: string, type: K): Promise<GhostModels[K]> {
    // Dynamic import to avoid SSR crashes
    const { ghostDeserialize } = await import("ghost-serialization-wasm");
    const result = ghostDeserialize(json, type as string);
    if (!result) {
        throw new Error(`[Ghost] Failed to deserialize "${type}". This usually means the model was not synchronized or the WASM engine needs to be rebuilt. Run "npm run ghost:sync" to fix this.`);
    }
    return JSON.parse(result) as GhostModels[K];
}
