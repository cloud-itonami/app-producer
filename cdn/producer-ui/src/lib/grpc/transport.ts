import { browser } from '$app/environment';

const grpcApiUrl = browser
	? (import.meta.env.PUBLIC_GRPC_API_URL || '/xrpc')
	: (process.env.GRPC_API_URL || 'http://localhost:25326');

async function getClerkToken(): Promise<string | undefined> {
	if (!browser) return undefined;
	return document.cookie
		.split('; ')
		.find((row) => row.startsWith('__session='))
		?.split('=')[1];
}

const auth = {
	async resolve() {
		const h: Record<string, string> = { 'content-type': 'application/json' };
		const token = await getClerkToken();
		if (token) h['authorization'] = `Bearer ${token}`;
		return h;
	}
};

export async function connectPost<T>(method: string, body = {}, extraHeaders?: Record<string, string>): Promise<T> {
	const headers = {
		...(await auth.resolve()),
		...(extraHeaders ?? {})
	};
	const response = await fetch(`${grpcApiUrl || 'https://atproto.etzhayyim.com'}/xrpc/com.etzhayyim.apps.producer.${method}`, {
		method: 'POST',
		headers,
		body: JSON.stringify(body)
	});
	if (!response.ok) {
		const errorBody = await response.text().catch(() => '');
		throw new Error(`producer.${method}: HTTP ${response.status} ${errorBody.slice(0, 200)}`);
	}
	return response.json() as Promise<T>;
}
