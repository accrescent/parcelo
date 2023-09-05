export interface App {
    id: string;
    label: string;
    version_code: number;
    version_name: string;
}

export interface Draft {
    id: string,
    app_id: string
    label: string,
    version_code: string,
    version_name: string,
}
