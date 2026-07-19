import { Code, ConnectError } from '@connectrpc/connect';
import { connectPost } from '$lib/grpc/transport';

function connectCodeToHttpStatus(code: Code): number {
	switch (code) {
		case Code.InvalidArgument:
			return 400;
		case Code.Unauthenticated:
			return 401;
		case Code.PermissionDenied:
			return 403;
		case Code.NotFound:
			return 404;
		case Code.AlreadyExists:
			return 409;
		case Code.ResourceExhausted:
			return 429;
		case Code.Unimplemented:
			return 501;
		case Code.Unavailable:
			return 503;
		case Code.DeadlineExceeded:
			return 504;
		default:
			return 500;
	}
}

function jsonResponse(payload: unknown, status = 200): Response {
	return new Response(JSON.stringify(payload), {
		status,
		headers: {
			'Content-Type': 'application/json'
		}
	});
}

export async function callStoryboardService(
	method: string,
	requestBody: unknown,
	headers: Record<string, string>
): Promise<Response> {
	try {
		const result = await connectPost(method, requestBody ?? {}, headers);
		return jsonResponse(result, 200);
	} catch (error) {
		if (error instanceof ConnectError) {
			return jsonResponse(
				{
					error: error.rawMessage || error.message,
					code: error.code
				},
				connectCodeToHttpStatus(error.code)
			);
		}

		return jsonResponse({ error: 'Unexpected error calling StoryboardService' }, 500);
	}
}

export function unsupportedApiResponse(route: string, reason: string): Response {
	return jsonResponse(
		{
			error: 'Not implemented',
			route,
			reason
		},
		501
	);
}
