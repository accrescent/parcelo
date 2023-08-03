export interface Permissions {
	reviewer: boolean,
	publisher: boolean
}

export enum AuthError {
	BAD_REQUEST,
	NOT_WHITELISTED,
	UNKNOWN_ERROR
}
