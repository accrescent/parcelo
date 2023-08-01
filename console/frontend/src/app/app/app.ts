export interface App {
    id: string;
    label: string;
    version_code: number;
    version_name: string;
}

export interface Draft {
    id: string;
}

export interface DraftError {
    title: string;
    message: string;
}