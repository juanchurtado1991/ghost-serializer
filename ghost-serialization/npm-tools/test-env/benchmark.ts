export interface PageInfo {
    count: number;
    pages: number;
    next?: string;
    prev?: string;
}

export interface LocationRef {
    name: string;
    url: string;
}

export enum CharacterStatus {
    Alive = "Alive",
    Dead = "Dead",
    unknown = "unknown"
}

export interface GhostCharacter {
    id: number;
    name: string;
    status: CharacterStatus;
    species: string;
    type: string;
    gender: string;
    origin: LocationRef;
    location: LocationRef;
    image: string;
    episode: string[];
    url: string;
    created: string;
}

export interface CharacterResponse {
    info: PageInfo;
    results: GhostCharacter[];
}
